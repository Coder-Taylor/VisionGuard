package service

import (
	"fmt"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"gorm.io/gorm"
)

type ElderService struct {
	db *gorm.DB
}

func NewElderService(db *gorm.DB) *ElderService {
	return &ElderService{db: db}
}

// ======================== 创建老人档案 (二.1) ========================

type CreateElderReq struct {
	Name              string                  `json:"name"`
	Gender            string                  `json:"gender"`
	BirthDate         string                  `json:"birthDate"`
	IDCard            string                  `json:"idCard"`
	BloodType         string                  `json:"bloodType"`
	Allergy           string                  `json:"allergy"`
	MedicalHistory    string                  `json:"medicalHistory"`
	EmergencyContacts []model.EmergencyContact `json:"emergencyContacts"`
}

func (s *ElderService) Create(userID uint, req CreateElderReq) (*model.Elder, error) {
	elderID := "ELDER_" + generateRandomString(8)

	elder := model.Elder{
		ElderID:        elderID,
		Name:           req.Name,
		Gender:         req.Gender,
		BirthDate:      req.BirthDate,
		IDCard:         req.IDCard,
		BloodType:      req.BloodType,
		Allergy:        req.Allergy,
		MedicalHistory: req.MedicalHistory,
		Status:         "active",
		CreatedBy:      userID,
	}

	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&elder).Error; err != nil {
			return err
		}

		// 创建者自动设为主监护人
		g := model.Guardianship{
			ElderID: elderID,
			UserID:  userID,
			Role:    "primary",
		}
		if err := tx.Create(&g).Error; err != nil {
			return err
		}

		// 紧急联系人
		for i := range req.EmergencyContacts {
			req.EmergencyContacts[i].ElderID = elderID
			req.EmergencyContacts[i].ID = 0
			if err := tx.Create(&req.EmergencyContacts[i]).Error; err != nil {
				return err
			}
		}

		return nil
	})

	if err != nil {
		return nil, fmt.Errorf("create elder error: %w", err)
	}

	return &elder, nil
}

// ======================== 查询老人档案 (二.2) ========================

type ElderDetailResp struct {
	ElderID           string                  `json:"elderId"`
	Name              string                  `json:"name"`
	Gender            string                  `json:"gender"`
	BirthDate         string                  `json:"birthDate"`
	BloodType         string                  `json:"bloodType"`
	Allergy           string                  `json:"allergy"`
	MedicalHistory    string                  `json:"medicalHistory"`
	Address           string                  `json:"address"`
	Status            string                  `json:"status"`
	DeviceOnline      bool                    `json:"deviceOnline"`
	DeviceID          string                  `json:"deviceId,omitempty"`
	EmergencyContacts []model.EmergencyContact `json:"emergencyContacts"`
	Guardians         []GuardianInfo           `json:"guardians"`
	CreatedBy         uint                     `json:"createdBy"`
	CreatedAt         string                   `json:"createdAt"`
}

type GuardianInfo struct {
	Phone    string `json:"phone,omitempty"`
	UserID   uint   `json:"userId"`
	Nickname string `json:"nickname"`
	Role     string `json:"role"`
}

func (s *ElderService) GetDetail(elderID string, requestUserID uint) (*ElderDetailResp, error) {
	var elder model.Elder
	if err := s.db.Where("elder_id = ?", elderID).Preload("EmergencyContacts").Preload("Guardians").First(&elder).Error; err != nil {
		return nil, fmt.Errorf("elder not found")
	}

	// 验证请求者是监护人
	var requestRole string
	for _, g := range elder.Guardians {
		if g.UserID == requestUserID {
			requestRole = g.Role
			break
		}
	}
	if requestRole == "" {
		return nil, fmt.Errorf("not a guardian of this elder")
	}

	// 主监护人可查看完整详情，普通监护人仅可看到主监护人姓名
	guardians := make([]GuardianInfo, 0)
	for _, g := range elder.Guardians {
		if requestRole == "normal" && g.Role != "primary" {
			continue // 普通监护人隐藏其他协作者
		}
		var userPhone string
			var user model.User
			if s.db.First(&user, g.UserID).Error == nil {
				userPhone = user.Phone
			}
			info := GuardianInfo{UserID: g.UserID, Nickname: g.Nickname, Phone: userPhone, Role: g.Role}
		guardians = append(guardians, info)
	}

	// 查询绑定设备
	var deviceOnline bool
	var deviceID string
	var binding model.Binding
	if s.db.Where("elder_id = ? AND status = ?", elderID, "bound").First(&binding).Error == nil {
		deviceID = binding.DeviceID
		var dev model.Device
		if s.db.Where("device_id = ?", binding.DeviceID).First(&dev).Error == nil {
			deviceOnline = dev.Status == "online"
		}
	}

	return &ElderDetailResp{
		ElderID:           elder.ElderID,
		Name:              elder.Name,
		Gender:            elder.Gender,
		BirthDate:         elder.BirthDate,
		BloodType:         elder.BloodType,
		Allergy:           elder.Allergy,
		MedicalHistory:    elder.MedicalHistory,
		Address:           elder.Address,
		Status:            elder.Status,
		DeviceOnline:      deviceOnline,
		DeviceID:          deviceID,
		EmergencyContacts: elder.EmergencyContacts,
		Guardians:         guardians,
		CreatedBy:         elder.CreatedBy,
		CreatedAt:         elder.CreatedAt.Format(time.RFC3339),
	}, nil
}

// ======================== 更新老人信息 (二.3) ========================

func (s *ElderService) UpdateInfo(elderID string, userID uint, updates map[string]interface{}) error {
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, userID, "primary").First(&g).Error; err != nil {
		return fmt.Errorf("only primary guardian can update elder info")
	}

	allowed := map[string]bool{"name": true, "gender": true, "birth_date": true, "blood_type": true, "allergy": true, "medical_history": true, "address": true}
	filtered := make(map[string]interface{})
	for k, v := range updates {
		if allowed[k] {
			filtered[k] = v
		}
	}

	return s.db.Model(&model.Elder{}).Where("elder_id = ?", elderID).Updates(filtered).Error
}

// ======================== 邀请协作监护人 (二.4) ========================

func (s *ElderService) InviteGuardian(elderID string, inviterID uint, invitee, message string) (*model.Invitation, error) {
	// 验证邀请者是主监护人
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, inviterID, "primary").First(&g).Error; err != nil {
		return nil, fmt.Errorf("only primary guardian can invite")
	}

	inviteID := "INV_" + generateRandomString(8)
	inv := model.Invitation{
		InviteID:  inviteID,
		ElderID:   elderID,
		InviterID: inviterID,
		Invitee:   invitee,
		Message:   message,
		Status:    "pending",
		ExpiresAt: time.Now().Add(48 * time.Hour),
	}

	if err := s.db.Create(&inv).Error; err != nil {
		return nil, fmt.Errorf("create invitation error: %w", err)
	}

	return &inv, nil
}

func (s *ElderService) AcceptInvitation(inviteID string, userID uint) error {
	var inv model.Invitation
	if err := s.db.Where("invite_id = ?", inviteID).First(&inv).Error; err != nil {
		return fmt.Errorf("invitation not found")
	}
	if inv.Status != "pending" {
		return fmt.Errorf("invitation is %s", inv.Status)
	}
	if time.Now().After(inv.ExpiresAt) {
		inv.Status = "expired"
		s.db.Save(&inv)
		return fmt.Errorf("invitation expired")
	}

	// 验证接收者身份：检查用户手机/邮箱是否匹配邀请
	var user model.User
	if err := s.db.First(&user, userID).Error; err != nil {
		return fmt.Errorf("user not found")
	}
	if user.Phone != inv.Invitee && user.Email != inv.Invitee {
		return fmt.Errorf("invitation does not match your identity")
	}

	// 检查是否已是监护人
	var existing model.Guardianship
	if s.db.Where("elder_id = ? AND user_id = ?", inv.ElderID, userID).First(&existing).Error == nil {
		return fmt.Errorf("already a guardian")
	}

	return s.db.Transaction(func(tx *gorm.DB) error {
		tx.Model(&inv).Update("status", "accepted")
		return tx.Create(&model.Guardianship{
			ElderID: inv.ElderID,
			UserID:  userID,
			Role:    "normal",
		}).Error
	})
}

// ======================== 转让主监护人 (二.5, 二.11) ========================

func (s *ElderService) TransferPrimary(elderID string, fromUserID, toUserID uint) (*model.Transfer, error) {
	// 验证 fromUser 是主监护人
	var fromG model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, fromUserID, "primary").First(&fromG).Error; err != nil {
		return nil, fmt.Errorf("sender is not primary guardian")
	}

	// 验证 toUser 是普通监护人
	var toG model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, toUserID, "normal").First(&toG).Error; err != nil {
		return nil, fmt.Errorf("target is not a normal guardian of this elder")
	}

	transferID := "TRS_" + generateRandomString(8)
	transfer := model.Transfer{
		TransferID: transferID,
		ElderID:    elderID,
		FromUserID: fromUserID,
		ToUserID:   toUserID,
		Status:     "pending_accept",
	}
	if err := s.db.Create(&transfer).Error; err != nil {
		return nil, err
	}

	return &transfer, nil
}

func (s *ElderService) ConfirmTransfer(transferID string, accept bool, userID uint) error {
	var transfer model.Transfer
	if err := s.db.Where("transfer_id = ?", transferID).First(&transfer).Error; err != nil {
		return fmt.Errorf("transfer not found")
	}
	if transfer.Status != "pending_accept" {
		return fmt.Errorf("transfer is %s", transfer.Status)
	}
	if transfer.ToUserID != userID {
		return fmt.Errorf("not the target user")
	}

	if !accept {
		return s.db.Model(&transfer).Update("status", "cancelled").Error
	}

	now := time.Now()
	return s.db.Transaction(func(tx *gorm.DB) error {
		// 角色互换
		tx.Model(&model.Guardianship{}).Where("elder_id = ? AND user_id = ?", transfer.ElderID, transfer.FromUserID).Update("role", "normal")
		tx.Model(&model.Guardianship{}).Where("elder_id = ? AND user_id = ?", transfer.ElderID, transfer.ToUserID).Update("role", "primary")

		// 更新档案创建者
		tx.Model(&model.Elder{}).Where("elder_id = ?", transfer.ElderID).Update("created_by", transfer.ToUserID)

		// 更新转让记录
		tx.Model(&transfer).Updates(map[string]interface{}{
			"status":       "completed",
			"completed_at": now,
		})
		return nil
	})
}

// ======================== 移除协作监护人 (二.6) ========================

func (s *ElderService) RemoveGuardian(elderID string, operatorID, targetUserID uint) error {
	var opG model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ?", elderID, operatorID).First(&opG).Error; err != nil {
		return fmt.Errorf("operator is not a guardian")
	}

	// 主监护人可以移除任何人，普通监护人只能移除自己
	if opG.Role != "primary" && operatorID != targetUserID {
		return fmt.Errorf("only primary guardian can remove others")
	}

	// 不能移除主监护人
	var targetG model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ?", elderID, targetUserID).First(&targetG).Error; err != nil {
		return fmt.Errorf("target not found")
	}
	if targetG.Role == "primary" {
		return fmt.Errorf("cannot remove primary guardian, transfer first")
	}

	return s.db.Where("elder_id = ? AND user_id = ?", elderID, targetUserID).Delete(&model.Guardianship{}).Error
}

// ======================== 紧急联系人管理 (二.7) ========================

func (s *ElderService) AddEmergencyContact(elderID string, userID uint, contact model.EmergencyContact) error {
	if err := s.checkPrimary(elderID, userID); err != nil {
		return err
	}
	contact.ElderID = elderID
	return s.db.Create(&contact).Error
}

func (s *ElderService) DeleteEmergencyContact(elderID string, userID uint, contactID uint) error {
	if err := s.checkPrimary(elderID, userID); err != nil {
		return err
	}
	return s.db.Where("id = ? AND elder_id = ?", contactID, elderID).Delete(&model.EmergencyContact{}).Error
}

// ======================== 查询"我监护的老人"列表 (二.8) ========================

type MyElderItem struct {
	ElderID         string `json:"elderId"`
	Name            string `json:"name"`
	Role            string `json:"role"`
	DeviceOnline    bool   `json:"deviceOnline"`
	DeviceID        string `json:"deviceId,omitempty"`
	LatestHeartbeat string `json:"latestHeartbeat"`
}

func (s *ElderService) ListMyElders(userID uint) ([]MyElderItem, error) {
	var guardianships []model.Guardianship
	s.db.Where("user_id = ?", userID).Find(&guardianships)

	var items []MyElderItem
	for _, g := range guardianships {
		var elder model.Elder
		if s.db.Where("elder_id = ? AND status = ?", g.ElderID, "active").First(&elder).Error != nil {
			continue
		}

		item := MyElderItem{
			ElderID: g.ElderID,
			Name:    elder.Name,
			Role:    g.Role,
		}

		// 查找绑定的设备状态
		var binding model.Binding
		if s.db.Where("elder_id = ? AND status = ?", g.ElderID, "bound").First(&binding).Error == nil {
			var dev model.Device
			if s.db.Where("device_id = ?", binding.DeviceID).First(&dev).Error == nil {
				item.DeviceID = binding.DeviceID
					item.DeviceOnline = dev.Status == "online"
				if dev.LastHeartbeatAt != nil {
					item.LatestHeartbeat = dev.LastHeartbeatAt.Format(time.RFC3339)
				}
			}
		}

		items = append(items, item)
	}

	return items, nil
}

// ======================== 监护人仪表盘 (二.14) ========================

func (s *ElderService) Dashboard(userID uint, page int) (map[string]interface{}, error) {
	elders, _ := s.ListMyElders(userID)

	type DashItem struct {
		ElderID          string `json:"elderId"`
		Name             string `json:"name"`
		Role             string `json:"role"`
		DeviceStatus     string `json:"deviceStatus"`
		LastHeartbeat    string `json:"lastHeartbeat"`
		LastOffline      string `json:"lastOffline,omitempty"`
		AlertsToday      map[string]int `json:"alertsToday"`
		EmergencyContact *struct {
			Name  string `json:"name"`
			Phone string `json:"phone"`
		} `json:"emergencyContact,omitempty"`
	}

	var dashItems []DashItem
	for _, e := range elders {
		item := DashItem{
			ElderID: e.ElderID,
			Name:    e.Name,
			Role:    e.Role,
			AlertsToday: map[string]int{"critical": 0, "warning": 0},
		}

		if e.DeviceOnline {
			item.DeviceStatus = "online"
			item.LastHeartbeat = e.LatestHeartbeat
		} else {
			item.DeviceStatus = "offline"
		}

		// 最近24h告警计数
		since := time.Now().Add(-24 * time.Hour)
		var criticalCount, warningCount int64
		s.db.Model(&model.Alert{}).Where("elder_id = ? AND alert_level = ? AND created_at > ?", e.ElderID, "critical", since).Count(&criticalCount)
		s.db.Model(&model.Alert{}).Where("elder_id = ? AND alert_level = ? AND created_at > ?", e.ElderID, "warning", since).Count(&warningCount)
		item.AlertsToday["critical"] = int(criticalCount)
		item.AlertsToday["warning"] = int(warningCount)

		// 紧急联系人
		var ec model.EmergencyContact
		if s.db.Where("elder_id = ?", e.ElderID).First(&ec).Error == nil {
			item.EmergencyContact = &struct {
				Name  string `json:"name"`
				Phone string `json:"phone"`
			}{Name: ec.Name, Phone: ec.Phone}
		}

		dashItems = append(dashItems, item)
	}

	onlineDeviceCount := 0
	for _, item := range dashItems {
		if item.DeviceStatus == "online" {
			onlineDeviceCount++
		}
	}

	// 24h 告警总数
	var alertCount24h int64
	since := time.Now().Add(-24 * time.Hour)
	for _, e := range elders {
		var c int64
		s.db.Model(&model.Alert{}).Where("elder_id = ? AND created_at > ?", e.ElderID, since).Count(&c)
		alertCount24h += c
	}

	return map[string]interface{}{
		"total":             len(dashItems),
		"elders":            dashItems,
		"elderCount":        len(dashItems),
		"onlineDeviceCount": onlineDeviceCount,
		"alertCount24h":     alertCount24h,
	}, nil
}

// ======================== 删除老人档案 (二.10) ========================

func (s *ElderService) DeleteElder(elderID string, userID uint) error {
	if err := s.checkPrimary(elderID, userID); err != nil {
		return err
	}

	return s.db.Transaction(func(tx *gorm.DB) error {
		// 解绑所有设备
		tx.Model(&model.Binding{}).Where("elder_id = ? AND status = ?", elderID, "bound").
			Updates(map[string]interface{}{"status": "unbound", "unbound_at": time.Now()})

		// 先删子表（外键引用），再删主表
		tx.Where("elder_id = ?", elderID).Delete(&model.Guardianship{})
		tx.Where("elder_id = ?", elderID).Delete(&model.EmergencyContact{})
		tx.Where("elder_id = ?", elderID).Delete(&model.Invitation{})
		tx.Where("elder_id = ?", elderID).Delete(&model.Transfer{})
		tx.Where("elder_id = ?", elderID).Delete(&model.Elder{})
		return nil
	})
}

// ======================== 档案归档封存 (二.12) ========================

func (s *ElderService) ArchiveElder(elderID string, userID uint, reason string) error {
	if err := s.checkPrimary(elderID, userID); err != nil {
		return err
	}

	return s.db.Transaction(func(tx *gorm.DB) error {
		tx.Model(&model.Elder{}).Where("elder_id = ?", elderID).Update("status", "archived")
		tx.Model(&model.Binding{}).Where("elder_id = ? AND status = ?", elderID, "bound").
			Updates(map[string]interface{}{"status": "unbound", "unbound_at": time.Now(), "unbound_reason": "elder archived: " + reason})
		return nil
	})
}

// ======================== Helpers ========================

func (s *ElderService) checkPrimary(elderID string, userID uint) error {
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, userID, "primary").First(&g).Error; err != nil {
		return fmt.Errorf("only primary guardian can perform this action")
	}
	return nil
}
