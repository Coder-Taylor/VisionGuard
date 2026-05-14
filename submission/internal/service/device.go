package service

import (
	"context"
	"fmt"
	"strconv"
	"sync"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/infra"
	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
)

type DeviceService struct {
	db    *gorm.DB
	redis *redis.Client
}

func NewDeviceService(db *gorm.DB, redis *redis.Client) *DeviceService {
	return &DeviceService{db: db, redis: redis}
}

// ======================== 设备首次激活注册 (三.1) ========================

type DeviceActivateReq struct {
	SerialNo  string `json:"serialNo"`
	Model     string `json:"model"`
	MAC       string `json:"mac"`
	HWVersion string `json:"hwVersion"`
	FWVersion string `json:"fwVersion"`
	Timestamp int64  `json:"timestamp"`
	Sign      string `json:"sign"`
}

type DeviceActivateResp struct {
	DeviceID     string `json:"deviceId"`
	DeviceSecret string `json:"deviceSecret"`
	Certificate  string `json:"certificate,omitempty"`
	ExpiresAt    string `json:"expiresAt"`
}

func (s *DeviceService) Activate(req DeviceActivateReq) (*DeviceActivateResp, error) {
	// 检查 serialNo 是否已注册：已存在则返回已有凭证（设备恢复场景）
	var existing model.Device
	if s.db.Where("serial_no = ?", req.SerialNo).First(&existing).Error == nil {
		// 更新设备信息（MAC/固件版本可能变化）
		s.db.Model(&existing).Updates(map[string]any{
			"mac":        req.MAC,
			"hw_version": req.HWVersion,
			"fw_version": req.FWVersion,
			"model":      req.Model,
		})
		return &DeviceActivateResp{
			DeviceID:     existing.DeviceID,
			DeviceSecret: existing.DeviceCode,
			ExpiresAt:    time.Now().AddDate(1, 0, 0).Format(time.RFC3339),
		}, nil
	}

	deviceID := "DEV_" + generateRandomString(8)
	deviceSecret := generateRandomString(32)

	dev := model.Device{
		DeviceID:   deviceID,
		DeviceCode: deviceSecret,
		SerialNo:   req.SerialNo,
		Model:      req.Model,
		MAC:        req.MAC,
		HWVersion:  req.HWVersion,
		FWVersion:  req.FWVersion,
		Status:     "registered",
		BindStatus: "unbound",
	}
	if err := s.db.Create(&dev).Error; err != nil {
		return nil, fmt.Errorf("create device error: %w", err)
	}

	return &DeviceActivateResp{
		DeviceID:     deviceID,
		DeviceSecret: deviceSecret,
		ExpiresAt:    time.Now().AddDate(1, 0, 0).Format(time.RFC3339),
	}, nil
}

// ======================== 设备首次接入注册 (一.1.ii) ========================

func (s *DeviceService) DeviceFirstRegister(deviceID, deviceModel, fwVersion, ip string) error {
	var dev model.Device
	err := s.db.Where("device_id = ?", deviceID).First(&dev).Error

	if err == gorm.ErrRecordNotFound {
		return fmt.Errorf("device not registered in system")
	}
	if err != nil {
		return fmt.Errorf("db error: %w", err)
	}

	if dev.Status == "disabled" {
		return fmt.Errorf("device is disabled")
	}

	now := time.Now()
	updates := map[string]interface{}{
		"model":      deviceModel,
		"fw_version": fwVersion,
		"ip":         ip,
		"status":     "registered", // 待认证状态，非 online
	}
	if dev.FirstAccessAt == nil {
		updates["first_access_at"] = now
	}
	updates["last_online_at"] = now

	return s.db.Model(&dev).Updates(updates).Error
}

// ======================== 设备认证获取Token (三.2) ========================

type DeviceAuthResp struct {
	AccessToken string `json:"accessToken"`
	ExpiresIn   int    `json:"expiresIn"`
	ServerTime  int64  `json:"serverTime"`
	BindStatus  string `json:"bindStatus"`
	ElderID     string `json:"elderId,omitempty"`
}

func (s *DeviceService) Authenticate(deviceID, fwVersion string) (*DeviceAuthResp, error) {
	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}
	if dev.Status == "disabled" {
		return nil, fmt.Errorf("device is disabled")
	}

	// 更新固件版本
	if fwVersion != "" {
		s.db.Model(&dev).Update("fw_version", fwVersion)
	}

	// 获取绑定状态
	bindStatus := "unbound"
	elderID := ""
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding).Error == nil {
		bindStatus = "bound"
		elderID = binding.ElderID
	}

	return &DeviceAuthResp{
		AccessToken: "", // token issued by AuthService via challenge
		ExpiresIn:   86400,
		ServerTime:  time.Now().Unix(),
		BindStatus:  bindStatus,
		ElderID:     elderID,
	}, nil
}

// ======================== 设备心跳处理 (一.1.v, 四.1) ========================

type HeartbeatReq struct {
	DeviceID  string `json:"deviceId"`
	Timestamp int64  `json:"timestamp"`
	Battery   int    `json:"battery"`
	RSSI      int    `json:"rssi"`
	Location  *struct {
		Lat float64 `json:"lat"`
		Lng float64 `json:"lng"`
	} `json:"location,omitempty"`
}

type HeartbeatResp struct {
	ServerTime    int64 `json:"serverTime"`
	NextInterval  int   `json:"nextInterval"`
	StatusChanged bool  `json:"statusChanged"`
}

func (s *DeviceService) Heartbeat(req HeartbeatReq) (*HeartbeatResp, error) {
	var dev model.Device
	if err := s.db.Where("device_id = ?", req.DeviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}

	wasOffline := dev.Status == "offline"
	now := time.Now()

	updates := map[string]interface{}{
		"status":            "online",
		"last_heartbeat_at": now,
		"last_online_at":    now,
		"battery":           req.Battery,
		"rssi":              req.RSSI,
	}

	if req.Location != nil {
		updates["latitude"] = req.Location.Lat
		updates["longitude"] = req.Location.Lng
	}

	s.db.Model(&dev).Updates(updates)

	// Redis 在线状态，TTL = 心跳间隔 2 倍（文档 四.2）
	key := "device:status:" + req.DeviceID
	s.redis.Set(context.Background(), key, "online", 180*time.Second)
	s.redis.Set(context.Background(), "device:last_hb:"+req.DeviceID, now.Unix(), 180*time.Second)

	// 若之前离线，记录重连
	if wasOffline {
		s.db.Create(&model.AuthLog{
			DeviceID: req.DeviceID,
			LogType:  "reconnect",
			Message:  "device reconnected after offline",
		})
	}

	// 存储定位数据
	if req.Location != nil {
		elderID := s.getDeviceElderID(req.DeviceID)
		s.db.Create(&model.Location{
			DataID:    "loc_" + strconv.FormatInt(now.UnixNano(), 36),
			DeviceID:  req.DeviceID,
			ElderID:   elderID,
			Latitude:  req.Location.Lat,
			Longitude: req.Location.Lng,
		})
		// 更新 Redis 最新位置
		s.redis.Set(context.Background(), "device:location:"+req.DeviceID,
			fmt.Sprintf("%f,%f", req.Location.Lat, req.Location.Lng), 90*time.Second)
	}

	return &HeartbeatResp{
		ServerTime:    now.Unix(),
		NextInterval:  30,
		StatusChanged: wasOffline,
	}, nil
}

// ======================== 离线检测定时任务 (四.3) ========================

func (s *DeviceService) ScanOfflineDevices() {
	var devices []model.Device
	s.db.Where("status = ?", "online").Find(&devices)

	threshold := time.Now().Add(-90 * time.Second)
	for _, dev := range devices {
		if dev.LastHeartbeatAt == nil || dev.LastHeartbeatAt.Before(threshold) {
			s.markOffline(dev)
		}
	}
}

func (s *DeviceService) markOffline(dev model.Device) {
	now := time.Now()
	s.db.Model(&dev).Updates(map[string]interface{}{
		"status":          "offline",
		"last_offline_at": now,
	})
	s.redis.Set(context.Background(), "device:status:"+dev.DeviceID, "offline", 0)

	s.db.Create(&model.AuthLog{
		DeviceID: dev.DeviceID,
		LogType:  "offline",
		Message:  "device marked offline due to heartbeat timeout",
	})

	// 生成离线通知
	elderID := s.getDeviceElderID(dev.DeviceID)
	if elderID != "" {
		var guardians []model.Guardianship
		s.db.Where("elder_id = ?", elderID).Find(&guardians)
		infra.ParallelForEach(context.TODO(), guardians, func(_ context.Context, g model.Guardianship) error {
			return s.db.Create(&model.Notification{
				MessageID: "MSG_" + generateRandomString(12),
				UserID:    g.UserID,
				ElderID:   elderID,
				Type:      "device",
				Title:     "设备离线通知",
				Body:      "设备 " + dev.DeviceID + " 已离线",
				Channel:   "app",
				Priority:  "P1",
			}).Error
		})
	}
}

// ======================== 在线状态查询 (四.2) ========================

type OnlineStatusResp struct {
	DeviceID                string `json:"deviceId"`
	Status                  string `json:"status"`
	LastHeartbeat           string `json:"lastHeartbeat"`
	ContinuousOnlineSeconds int64  `json:"continuousOnlineSeconds"`
}

func (s *DeviceService) GetOnlineStatus(deviceID string) (*OnlineStatusResp, error) {
	var (
		status string
		dev    model.Device
	)
	conc := infra.NewConcurrent(2)
	conc.Go(func() error {
		if val, err := s.redis.Get(context.Background(), "device:status:"+deviceID).Result(); err == nil {
			status = val
		}
		return nil
	})
	conc.Go(func() error {
		return s.db.Where("device_id = ?", deviceID).First(&dev).Error
	})
	if err := conc.WaitFirst(); err != nil {
		return nil, fmt.Errorf("device not found")
	}

	var continuousSeconds int64
	if dev.LastOnlineAt != nil && status == "online" {
		continuousSeconds = time.Since(*dev.LastOnlineAt).Milliseconds() / 1000
	}

	lastHB := ""
	if dev.LastHeartbeatAt != nil {
		lastHB = dev.LastHeartbeatAt.Format(time.RFC3339)
	}

	return &OnlineStatusResp{
		DeviceID:                deviceID,
		Status:                  status,
		LastHeartbeat:           lastHB,
		ContinuousOnlineSeconds: continuousSeconds,
	}, nil
}

// ======================== 最后在线时间查询 (四.5) ========================

type LastOnlineResp struct {
	DeviceID                 string `json:"deviceId"`
	Status                   string `json:"status"`
	LastOnlineTime           string `json:"lastOnlineTime"`
	LastOfflineTime          string `json:"lastOfflineTime,omitempty"`
	ContinuousOnlineDuration string `json:"continuousOnlineDuration"`
	Battery                  int    `json:"battery"`
}

func (s *DeviceService) GetLastOnline(deviceID string) (*LastOnlineResp, error) {
	var (
		dev    model.Device
		status string
	)
	conc := infra.NewConcurrent(2)
	conc.Go(func() error {
		return s.db.Where("device_id = ?", deviceID).First(&dev).Error
	})
	conc.Go(func() error {
		if val, err := s.redis.Get(context.Background(), "device:status:"+deviceID).Result(); err == nil {
			status = val
		}
		return nil
	})
	if err := conc.WaitFirst(); err != nil {
		return nil, fmt.Errorf("device not found")
	}
	if status == "" {
		status = "offline"
	}

	resp := &LastOnlineResp{
		DeviceID: deviceID,
		Status:   status,
		Battery:  dev.Battery,
	}
	if dev.LastOnlineAt != nil {
		resp.LastOnlineTime = dev.LastOnlineAt.Format(time.RFC3339)
	}
	if dev.LastOfflineAt != nil {
		resp.LastOfflineTime = dev.LastOfflineAt.Format(time.RFC3339)
	}
	if dev.LastOnlineAt != nil && status == "online" {
		d := time.Since(*dev.LastOnlineAt)
		resp.ContinuousOnlineDuration = formatDuration(d)
	}
	return resp, nil
}

// ======================== 设备运行状态展示 (四.6) ========================

type DeviceRunningStatusResp struct {
	DeviceID        string `json:"deviceId"`
	Alias           string `json:"alias"`
	Model           string `json:"model"`
	FirmwareVersion string `json:"firmwareVersion"`
	Status          string `json:"status"`
	Battery         int    `json:"battery"`
	RSSI            int    `json:"rssi"`
	LastHeartbeat   string `json:"lastHeartbeat"`
	LastLocation    *struct {
		Lat       float64 `json:"lat"`
		Lng       float64 `json:"lng"`
		Address   string  `json:"address"`
		UpdatedAt string  `json:"updatedAt"`
	} `json:"lastLocation,omitempty"`
	BindTime string `json:"bindTime,omitempty"`
}

func (s *DeviceService) GetRunningStatus(deviceID, elderID string) (*DeviceRunningStatusResp, error) {
	var (
		dev     model.Device
		status  string
		binding model.Binding
	)
	conc := infra.NewConcurrent(3)
	conc.Go(func() error {
		return s.db.Where("device_id = ?", deviceID).First(&dev).Error
	})
	conc.Go(func() error {
		if val, err := s.redis.Get(context.Background(), "device:status:"+deviceID).Result(); err == nil {
			status = val
		}
		return nil
	})
	conc.Go(func() error {
		s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding)
		return nil
	})
	if err := conc.WaitFirst(); err != nil {
		return nil, fmt.Errorf("device not found")
	}

	resp := &DeviceRunningStatusResp{
		DeviceID:        deviceID,
		Alias:           dev.Alias,
		Model:           dev.Model,
		FirmwareVersion: dev.FWVersion,
		Status:          status,
		Battery:         dev.Battery,
		RSSI:            dev.RSSI,
	}

	if dev.LastHeartbeatAt != nil {
		resp.LastHeartbeat = dev.LastHeartbeatAt.Format(time.RFC3339)
	}

	if dev.Latitude != 0 || dev.Longitude != 0 {
		resp.LastLocation = &struct {
			Lat       float64 `json:"lat"`
			Lng       float64 `json:"lng"`
			Address   string  `json:"address"`
			UpdatedAt string  `json:"updatedAt"`
		}{Lat: dev.Latitude, Lng: dev.Longitude}
	}

	if binding.BoundAt != nil {
		resp.BindTime = binding.BoundAt.Format(time.RFC3339)
	}

	return resp, nil
}

// ======================== 批量设备状态查询 (四.7) ========================

func (s *DeviceService) BatchStatus(deviceIDs []string) []map[string]interface{} {
	var mu sync.Mutex
	var result []map[string]interface{}
	_ = infra.ParallelForEach(context.TODO(), deviceIDs, func(_ context.Context, id string) error {
		status := "offline"
		if val, err := s.redis.Get(context.Background(), "device:status:"+id).Result(); err == nil {
			status = val
		}
		var dev model.Device
		hb := ""
		battery := 0
		if s.db.Where("device_id = ?", id).First(&dev).Error == nil {
			battery = dev.Battery
			if dev.LastHeartbeatAt != nil {
				hb = dev.LastHeartbeatAt.Format(time.RFC3339)
			}
		}
		mu.Lock()
		result = append(result, map[string]interface{}{
			"deviceId":      id,
			"status":        status,
			"battery":       battery,
			"lastHeartbeat": hb,
		})
		mu.Unlock()
		return nil
	})
	return result
}

// ======================== 设备信息更新 (三.4) ========================

func (s *DeviceService) UpdateDeviceInfo(deviceID string, alias, location string) error {
	return s.db.Model(&model.Device{}).Where("device_id = ?", deviceID).
		Updates(map[string]interface{}{
			"alias":            alias,
			"install_location": location,
		}).Error
}

// ======================== 设备禁用/启用 (三.8) ========================

func (s *DeviceService) ToggleDevice(deviceID, action, reason string) error {
	status := "disabled"
	if action == "enable" {
		status = "registered"
	}
	return s.db.Model(&model.Device{}).Where("device_id = ?", deviceID).
		Updates(map[string]interface{}{"status": status}).Error
}

// ======================== 固件版本查询 (三.6) ========================

type FirmwareResp struct {
	UpgradeAvailable bool   `json:"upgradeAvailable"`
	NewVersion       string `json:"newVersion,omitempty"`
	ReleaseNotes     string `json:"releaseNotes,omitempty"`
	FileSize         int64  `json:"fileSize,omitempty"`
	MD5              string `json:"md5,omitempty"`
	DownloadURL      string `json:"downloadUrl,omitempty"`
	Mandatory        bool   `json:"mandatory"`
	RetryCount       int    `json:"retryCount,omitempty"`
}

func (s *DeviceService) CheckFirmware(deviceID, currentFW string) (*FirmwareResp, error) {
	// stub: 当前无新固件
	return &FirmwareResp{UpgradeAvailable: false}, nil
}

// ======================== Helpers ========================

func (s *DeviceService) getDeviceElderID(deviceID string) string {
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding).Error == nil {
		return binding.ElderID
	}
	return ""
}

func (s *DeviceService) GetDeviceByID(deviceID string) (*model.Device, error) {
	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, err
	}
	return &dev, nil
}

func formatDuration(d time.Duration) string {
	h := int(d.Hours())
	m := int(d.Minutes()) % 60
	if h > 0 {
		return fmt.Sprintf("%dh %dm", h, m)
	}
	return fmt.Sprintf("%dm", m)
}
