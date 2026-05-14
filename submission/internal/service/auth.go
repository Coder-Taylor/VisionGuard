package service

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strconv"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/jry21223/vision-hub/backend/internal/config"
	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"
)

type AuthService struct {
	db    *gorm.DB
	redis *redis.Client
	cfg   *config.Config
}

func NewAuthService(db *gorm.DB, redis *redis.Client, cfg *config.Config) *AuthService {
	return &AuthService{db: db, redis: redis, cfg: cfg}
}

// ======================== XOR 加密 ========================

func (s *AuthService) xorEncrypt(input string) string {
	key := s.cfg.DeviceXORKey
	result := make([]byte, len(input))
	for i := 0; i < len(input); i++ {
		result[i] = input[i] ^ key
	}
	return hex.EncodeToString(result)
}

// ======================== Challenge-Response 认证 (一.1.i) ========================

type ChallengeResponse struct {
	ChallengeID string `json:"challengeId"`
	Nonce       string `json:"nonce"`
	Timestamp   int64  `json:"timestamp"`
}

func (s *AuthService) RequestChallenge(deviceID string) (*ChallengeResponse, error) {
	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}

	nonce := generateRandomString(10)
	ts := time.Now().Unix()
	challengeID := "c_" + generateRandomString(6)

	// XOR(device_code + nonce + timestamp, 0x4B)
	plaintext := dev.DeviceCode + nonce + strconv.FormatInt(ts, 10)
	encrypted := s.xorEncrypt(plaintext)

	// 存入 Redis，TTL 5 分钟
	key := "challenge:" + challengeID
	if err := s.redis.Set(context.Background(), key, encrypted, 5*time.Minute).Err(); err != nil {
		return nil, fmt.Errorf("redis set error: %w", err)
	}

	// 记录认证日志
	s.db.Create(&model.AuthLog{
		DeviceID: deviceID,
		LogType:  "auth_challenge",
		Message:  "challenge requested",
	})

	return &ChallengeResponse{
		ChallengeID: challengeID,
		Nonce:       nonce,
		Timestamp:   ts,
	}, nil
}

type VerifyChallengeReq struct {
	DeviceID    string `json:"deviceId"`
	ChallengeID string `json:"challengeId"`
	Sigin       string `json:"sigin"`
}

func (s *AuthService) VerifyChallenge(req VerifyChallengeReq) (string, error) {
	// 查 Redis
	key := "challenge:" + req.ChallengeID
	stored, err := s.redis.Get(context.Background(), key).Result()
	if err == redis.Nil {
		s.db.Create(&model.AuthLog{DeviceID: req.DeviceID, LogType: "auth_fail", Message: "challenge expired"})
		return "", fmt.Errorf("challenge expired or not found")
	}
	if err != nil {
		return "", fmt.Errorf("redis error: %w", err)
	}

	// 比对加密值
	if stored != req.Sigin {
		s.db.Create(&model.AuthLog{DeviceID: req.DeviceID, LogType: "auth_fail", Message: "signature mismatch"})
		return "", fmt.Errorf("signature verification failed")
	}

	// 删除已使用的 challenge
	s.redis.Del(context.Background(), key)

	// 签发设备 JWT（24h）
	token, err := s.issueDeviceJWT(req.DeviceID)
	if err != nil {
		return "", fmt.Errorf("jwt issue error: %w", err)
	}

	s.db.Create(&model.AuthLog{DeviceID: req.DeviceID, LogType: "auth_success", Message: "challenge verified, JWT issued"})

	return token, nil
}

// ======================== 设备 JWT (一.1.iii) ========================

func (s *AuthService) issueDeviceJWT(deviceID string) (string, error) {
	now := time.Now()
	claims := jwt.MapClaims{
		"deviceId": deviceID,
		"iat":      now.Unix(),
		"exp":      now.Add(24 * time.Hour).Unix(),
		"type":     "device",
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(s.cfg.JWTSecret))
}

func (s *AuthService) VerifyDeviceJWT(tokenStr string) (string, error) {
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return "", fmt.Errorf("invalid device token")
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return "", fmt.Errorf("invalid claims")
	}
	if claims["type"] != "device" {
		return "", fmt.Errorf("not a device token")
	}
	deviceID, ok := claims["deviceId"].(string)
	if !ok {
		return "", fmt.Errorf("invalid deviceId in token")
	}
	return deviceID, nil
}

// ======================== 用户注册 (一.2.i) ========================

func (s *AuthService) Register(username, password, email, phone string) error {
	// 密码复杂度校验（文档 一.2.i）
	if len(password) < 8 {
		return fmt.Errorf("password must be at least 8 characters")
	}

	var existing model.User
	q := s.db.Where("username = ?", username)
	if email != "" {
		q = q.Or("email = ?", email)
	}
	if phone != "" {
		q = q.Or("phone = ?", phone)
	}
	if q.First(&existing).Error == nil {
		return fmt.Errorf("username, email or phone already exists")
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return fmt.Errorf("password hash error: %w", err)
	}

	user := model.User{
		Username:     username,
		PasswordHash: string(hash),
		Email:        email,
		Phone:        phone,
		Status:       "active",
	}
	return s.db.Create(&user).Error
}

// ======================== 用户登录 (一.2.ii) ========================

type LoginResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	DisplayName  string `json:"display_name"`
	Phone        string `json:"phone"`
}

func (s *AuthService) Login(account, password string) (*LoginResponse, error) {
	var user model.User
	if err := s.db.Where("username = ? OR phone = ?", account, account).First(&user).Error; err != nil {
		return nil, fmt.Errorf("invalid username or password")
	}

	if user.Status == "locked" || user.Status == "disabled" {
		return nil, fmt.Errorf("account is %s", user.Status)
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password)); err != nil {
		s.recordLoginFail(user.ID)
		return nil, fmt.Errorf("invalid username or password")
	}

	// 登录成功，清除失败计数
	s.redis.Del(context.Background(), fmt.Sprintf("login_fail:%d", user.ID))

	// 签发 access_token (1h per document)
	accessToken, err := s.issueUserJWT(user.ID, user.Username)
	if err != nil {
		return nil, fmt.Errorf("jwt issue error: %w", err)
	}

	// 签发 refresh_token
	refreshToken := generateRandomString(32)
	refreshHash := hashToken(refreshToken)

	s.db.Create(&model.RefreshToken{
		UserID:    user.ID,
		TokenHash: refreshHash,
		ExpiresAt: time.Now().Add(30 * 24 * time.Hour),
	})

	return &LoginResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    3600,
		DisplayName:  user.DisplayName,
		Phone:        user.Phone,
	}, nil
}

func (s *AuthService) issueUserJWT(userID uint, username string) (string, error) {
	now := time.Now()
	claims := jwt.MapClaims{
		"userId":   userID,
		"username": username,
		"jti":      generateRandomString(16),
		"iat":      now.Unix(),
		"exp":      now.Add(1 * time.Hour).Unix(),
		"type":     "user",
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(s.cfg.JWTSecret))
}

func (s *AuthService) VerifyUserJWT(tokenStr string) (uint, error) {
	token, err := jwt.Parse(tokenStr, func(t *jwt.Token) (interface{}, error) {
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid {
		return 0, fmt.Errorf("invalid user token")
	}
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return 0, fmt.Errorf("invalid claims")
	}
	if claims["type"] != "user" {
		return 0, fmt.Errorf("not a user token")
	}
	uid, ok := claims["userId"].(float64)
	if !ok {
		return 0, fmt.Errorf("invalid userId in token")
	}
	return uint(uid), nil
}

// ======================== Token 刷新 (一.2.iii) ========================

func (s *AuthService) RefreshToken(refreshToken string) (*LoginResponse, error) {
	refreshHash := hashToken(refreshToken)

	var rt model.RefreshToken
	if err := s.db.Where("token_hash = ?", refreshHash).First(&rt).Error; err != nil {
		return nil, fmt.Errorf("invalid refresh token")
	}
	if time.Now().After(rt.ExpiresAt) {
		s.db.Delete(&rt)
		return nil, fmt.Errorf("refresh token expired")
	}

	var user model.User
	if err := s.db.First(&user, rt.UserID).Error; err != nil {
		return nil, fmt.Errorf("user not found")
	}

	accessToken, err := s.issueUserJWT(user.ID, user.Username)
	if err != nil {
		return nil, fmt.Errorf("jwt issue error: %w", err)
	}
	newRefresh := generateRandomString(32)
	newHash := hashToken(newRefresh)

	// 先创建新 token，再删除旧 token（防止崩溃丢失）
	s.db.Create(&model.RefreshToken{
		UserID:    user.ID,
		TokenHash: newHash,
		ExpiresAt: time.Now().Add(30 * 24 * time.Hour),
	})
	s.db.Delete(&rt)

	return &LoginResponse{
		AccessToken:  accessToken,
		RefreshToken: newRefresh,
		ExpiresIn:    3600,
		DisplayName:  user.DisplayName,
		Phone:        user.Phone,
	}, nil
}

// ======================== 用户登出 (一.2.v) ========================

func (s *AuthService) Logout(refreshToken string) error {
	refreshHash := hashToken(refreshToken)
	s.db.Where("token_hash = ?", refreshHash).Delete(&model.RefreshToken{})
	return nil
}

// ======================== 修改密码 ========================

func (s *AuthService) ChangePassword(userID uint, oldPassword, newPassword string) error {
	if len(newPassword) < 8 {
		return fmt.Errorf("new password must be at least 8 characters")
	}
	var user model.User
	if err := s.db.First(&user, userID).Error; err != nil {
		return fmt.Errorf("user not found")
	}
	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(oldPassword)); err != nil {
		return fmt.Errorf("current password is incorrect")
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(newPassword), bcrypt.DefaultCost)
	if err != nil {
		return fmt.Errorf("password hash error: %w", err)
	}
	return s.db.Model(&user).Update("password_hash", string(hash)).Error
}

// ======================== 用户档案 ========================

type UserProfileResponse struct {
	UserID      uint   `json:"userId"`
	Username    string `json:"username"`
	DisplayName string `json:"displayName"`
	Email       string `json:"email"`
	Phone       string `json:"phone"`
	Status      string `json:"status"`
	CreatedAt   string `json:"createdAt"`
}

func (s *AuthService) GetProfile(userID uint) (*UserProfileResponse, error) {
	var user model.User
	if err := s.db.First(&user, userID).Error; err != nil {
		return nil, fmt.Errorf("user not found")
	}
	return &UserProfileResponse{
		UserID:      user.ID,
		Username:    user.Username,
		DisplayName: user.DisplayName,
		Email:       user.Email,
		Phone:       user.Phone,
		Status:      user.Status,
		CreatedAt:   user.CreatedAt.Format(time.RFC3339),
	}, nil
}

func (s *AuthService) UpdateProfile(userID uint, displayName string, phone string) error {
	if displayName == "" {
		return fmt.Errorf("displayName is required")
	}
	updates := map[string]interface{}{"display_name": displayName}
	if phone != "" {
		updates["phone"] = phone
	}
	return s.db.Model(&model.User{}).Where("id = ?", userID).Updates(updates).Error
}

// ======================== 账户安全策略 (一.2.vi) ========================

func (s *AuthService) recordLoginFail(userID uint) {
	key := fmt.Sprintf("login_fail:%d", userID)
	count, _ := s.redis.Incr(context.Background(), key).Result()
	s.redis.Expire(context.Background(), key, 30*time.Minute)

	if count >= 8 {
		s.db.Model(&model.User{}).Where("id = ?", userID).Update("status", "locked")
		s.redis.Del(context.Background(), key)
	}
}

// ======================== 设备基础信息记录 (一.1.viii) ========================

func (s *AuthService) RecordDeviceInfo(deviceID, deviceModel, fwVersion string) error {
	return s.db.Model(&model.Device{}).Where("device_id = ?", deviceID).
		Updates(map[string]interface{}{
			"model":      deviceModel,
			"fw_version": fwVersion,
		}).Error
}

// ======================== 设备认证日志 (一.1.ix) ========================

func (s *AuthService) LogAuthEvent(deviceID, logType, message string) error {
	return s.db.Create(&model.AuthLog{
		DeviceID: deviceID,
		LogType:  logType,
		Message:  message,
	}).Error
}

// ======================== Helpers ========================

func generateRandomString(length int) string {
	b := make([]byte, length/2+1)
	rand.Read(b)
	return hex.EncodeToString(b)[:length]
}

func GenerateRandomString(length int) string {
	return generateRandomString(length)
}

func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}
