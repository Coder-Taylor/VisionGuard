package service

import (
	"fmt"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"gorm.io/gorm"
)

type BindingService struct {
	db *gorm.DB
}

func NewBindingService(db *gorm.DB) *BindingService {
	return &BindingService{db: db}
}

// ======================== 在线设备搜索 (五.1) ========================

type DeviceSearchResp struct {
	DeviceID        string `json:"deviceId"`
	Model           string `json:"model"`
	RegisterStatus  string `json:"registerStatus"`
	BindStatus      string `json:"bindStatus"`
	FirmwareVersion string `json:"firmwareVersion"`
	CanBind         bool   `json:"canBind"`
}

func (s *BindingService) SearchDevice(deviceID string) (*DeviceSearchResp, error) {
	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}

	canBind := dev.BindStatus == "unbound" && dev.Status != "disabled"

	return &DeviceSearchResp{
		DeviceID:        dev.DeviceID,
		Model:           dev.Model,
		RegisterStatus:  dev.Status,
		BindStatus:      dev.BindStatus,
		FirmwareVersion: dev.FWVersion,
		CanBind:         canBind,
	}, nil
}

// ======================== 绑定发起 (五.2) ========================

type BindInitiateResp struct {
	BindID    string `json:"bindId"`
	Status    string `json:"status"`
	ExpiresAt string `json:"expiresAt,omitempty"`
	ElderID   string `json:"elderId,omitempty"`
	BoundAt   string `json:"boundAt,omitempty"`
}

func (s *BindingService) InitiateBinding(elderID, deviceID string, operatorID uint) (*BindInitiateResp, error) {
	// 校验操作者是该档案的监护人
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ?", elderID, operatorID).First(&g).Error; err != nil {
		return nil, fmt.Errorf("operator is not a guardian of this elder")
	}

	// 校验老人档案处于活跃状态
	var elder model.Elder
	if err := s.db.Where("elder_id = ? AND status = ?", elderID, "active").First(&elder).Error; err != nil {
		return nil, fmt.Errorf("elder profile is not active")
	}

	// 校验设备状态
	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}
	if dev.BindStatus == "bound" {
		return nil, fmt.Errorf("device already bound")
	}
	if dev.Status == "disabled" {
		return nil, fmt.Errorf("device is disabled")
	}

	// 校验是否已有 pending 绑定
	var pending model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "pending_device_confirm").
		Where("expires_at > ?", time.Now()).First(&pending).Error == nil {
		return nil, fmt.Errorf("device is being bound by another request")
	}

	bindID := "BIND_" + generateRandomString(8)
	expiresAt := time.Now().Add(5 * time.Minute)

	binding := model.Binding{
		BindID:    bindID,
		DeviceID:  deviceID,
		ElderID:   elderID,
		BoundBy:   operatorID,
		Status:    "pending_device_confirm",
		ExpiresAt: &expiresAt,
	}

	if err := s.db.Create(&binding).Error; err != nil {
		return nil, fmt.Errorf("create binding error: %w", err)
	}

	// MVP: hardware doesn't implement bind confirm, auto-confirm immediately
	now := time.Now()
	binding.Status = "bound"
	binding.BoundAt = &now
	binding.ExpiresAt = nil
	s.db.Save(&binding)

	dev.BindStatus = "bound"
	s.db.Save(&dev)

	return &BindInitiateResp{
		BindID:  bindID,
		Status:  "bound",
		ElderID: elderID,
		BoundAt: now.Format(time.RFC3339),
	}, nil
}

// ======================== 绑定确认（设备端）(五.3) ========================

type BindConfirmResp struct {
	BindID  string `json:"bindId"`
	Status  string `json:"status"`
	ElderID string `json:"elderId"`
	BoundAt string `json:"boundAt"`
}

func (s *BindingService) ConfirmBinding(deviceID, bindID string, confirm bool) (*BindConfirmResp, error) {
	var binding model.Binding
	if err := s.db.Where("bind_id = ? AND device_id = ?", bindID, deviceID).First(&binding).Error; err != nil {
		return nil, fmt.Errorf("binding not found")
	}
	if binding.Status != "pending_device_confirm" {
		return nil, fmt.Errorf("binding is %s", binding.Status)
	}
	if binding.ExpiresAt != nil && time.Now().After(*binding.ExpiresAt) {
		s.db.Model(&binding).Update("status", "expired")
		return nil, fmt.Errorf("binding expired")
	}

	if !confirm {
		s.db.Model(&binding).Update("status", "cancelled")
		return &BindConfirmResp{BindID: bindID, Status: "cancelled"}, nil
	}

	now := time.Now()
	err := s.db.Transaction(func(tx *gorm.DB) error {
		tx.Model(&binding).Updates(map[string]interface{}{
			"status":    "bound",
			"bound_at":  now,
		})

		tx.Model(&model.Device{}).Where("device_id = ?", deviceID).
			Update("bind_status", "bound")

		// 清理其他过期绑定请求
		tx.Model(&model.Binding{}).Where("device_id = ? AND status = ?", deviceID, "pending_device_confirm").
			Where("bind_id != ?", bindID).Update("status", "cancelled")

		return nil
	})

	if err != nil {
		return nil, fmt.Errorf("confirm binding error: %w", err)
	}

	return &BindConfirmResp{
		BindID:  bindID,
		Status:  "bound",
		ElderID: binding.ElderID,
		BoundAt: now.Format(time.RFC3339),
	}, nil
}

// ======================== 唯一绑定约束校验 (五.4) ========================

type BindCheckResp struct {
	DeviceID         string `json:"deviceId"`
	CurrentElderID   string `json:"currentElderId,omitempty"`
	CurrentElderName string `json:"currentElderName,omitempty"`
	BoundAt          string `json:"boundAt,omitempty"`
	Suggestion       string `json:"suggestion,omitempty"`
}

func (s *BindingService) CheckBindConstraint(deviceID, elderID string) (*BindCheckResp, error) {
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding).Error == nil {
		var elder model.Elder
		elderName := ""
		if s.db.Where("elder_id = ?", binding.ElderID).First(&elder).Error == nil {
			elderName = elder.Name
		}
		boundAt := ""
		if binding.BoundAt != nil {
			boundAt = binding.BoundAt.Format(time.RFC3339)
		}
		return &BindCheckResp{
			DeviceID:         deviceID,
			CurrentElderID:   binding.ElderID,
			CurrentElderName: elderName,
			BoundAt:          boundAt,
			Suggestion:       "请先解绑当前设备，再重新绑定",
		}, nil
	}

	return &BindCheckResp{DeviceID: deviceID}, nil
}

// ======================== 解绑流程 (五.5) ========================

func (s *BindingService) Unbind(elderID, deviceID string, operatorID uint, reason string) error {
	// 仅主监护人可解绑（文档 五.5）
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", elderID, operatorID, "primary").First(&g).Error; err != nil {
		return fmt.Errorf("only primary guardian can unbind device")
	}

	var binding model.Binding
	if err := s.db.Where("device_id = ? AND elder_id = ? AND status = ?", deviceID, elderID, "bound").First(&binding).Error; err != nil {
		return fmt.Errorf("binding not found")
	}

	now := time.Now()
	return s.db.Transaction(func(tx *gorm.DB) error {
		tx.Model(&binding).Updates(map[string]interface{}{
			"status":          "unbound",
			"unbound_at":      now,
			"unbound_reason":  reason,
		})

		tx.Model(&model.Device{}).Where("device_id = ?", deviceID).
			Updates(map[string]interface{}{
				"bind_status": "unbound",
			})
		return nil
	})
}

// ======================== 换绑流程 (五.6) ========================

func (s *BindingService) Rebind(fromElderID, toElderID, deviceID string, operatorID uint) error {
	// 校验操作者是原档案主监护人（文档 五.6）
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ? AND role = ?", fromElderID, operatorID, "primary").First(&g).Error; err != nil {
		return fmt.Errorf("only primary guardian can rebind device")
	}

	// 校验目标档案活跃
	var toElder model.Elder
	if err := s.db.Where("elder_id = ? AND status = ?", toElderID, "active").First(&toElder).Error; err != nil {
		return fmt.Errorf("target elder is not active")
	}

	// 校验操作者也是目标档案的监护人
	var g2 model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ?", toElderID, operatorID).First(&g2).Error; err != nil {
		return fmt.Errorf("operator is not a guardian of target elder")
	}

	return s.db.Transaction(func(tx *gorm.DB) error {
		now := time.Now()
		// 解绑原档案
		tx.Model(&model.Binding{}).Where("device_id = ? AND elder_id = ? AND status = ?", deviceID, fromElderID, "bound").
			Updates(map[string]interface{}{
				"status":     "unbound",
				"unbound_at": now,
			})

		// 绑定新档案（使用 pending 流程）
		bindID := "BIND_" + generateRandomString(8)
		return tx.Create(&model.Binding{
			BindID:   bindID,
			DeviceID: deviceID,
			ElderID:  toElderID,
			BoundBy:  operatorID,
			Status:   "bound",
			BoundAt:  &now,
		}).Error
	})
}

// ======================== 查询设备绑定关系 (五.9) ========================

type BindRelationResp struct {
	DeviceID       string        `json:"deviceId"`
	CurrentBinding *BindInfo     `json:"currentBinding,omitempty"`
	BindHistory    []BindHistory `json:"bindHistory"`
}

type BindInfo struct {
	ElderID   string `json:"elderId"`
	ElderName string `json:"elderName"`
	BoundAt   string `json:"boundAt"`
	BoundBy   uint   `json:"boundBy"`
}

type BindHistory struct {
	ElderID   string `json:"elderId"`
	ElderName string `json:"elderName"`
	BoundAt   string `json:"boundAt"`
	UnboundAt string `json:"unboundAt"`
	Reason    string `json:"reason"`
}

func (s *BindingService) GetBindRelation(deviceID string) (*BindRelationResp, error) {
	resp := &BindRelationResp{DeviceID: deviceID}

	var current model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&current).Error == nil {
		var elder model.Elder
		s.db.Where("elder_id = ?", current.ElderID).First(&elder)
		boundAt := ""
		if current.BoundAt != nil {
			boundAt = current.BoundAt.Format(time.RFC3339)
		}
		resp.CurrentBinding = &BindInfo{
			ElderID:   current.ElderID,
			ElderName: elder.Name,
			BoundAt:   boundAt,
			BoundBy:   current.BoundBy,
		}
	}

	// 历史记录
	var history []model.Binding
	s.db.Where("device_id = ?", deviceID).Order("created_at desc").Limit(10).Find(&history)
	for _, h := range history {
		if h.Status == "bound" {
			continue // current already shown
		}
		var elder model.Elder
		s.db.Where("elder_id = ?", h.ElderID).First(&elder)
		hi := BindHistory{ElderID: h.ElderID, ElderName: elder.Name}
		if h.BoundAt != nil {
			hi.BoundAt = h.BoundAt.Format(time.RFC3339)
		}
		if h.UnboundAt != nil {
			hi.UnboundAt = h.UnboundAt.Format(time.RFC3339)
		}
		hi.Reason = h.UnboundReason
		resp.BindHistory = append(resp.BindHistory, hi)
	}

	return resp, nil
}
