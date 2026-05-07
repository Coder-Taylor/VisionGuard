package service

import (
	"fmt"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"gorm.io/gorm"
)

type NotificationService struct {
	db *gorm.DB
}

func NewNotificationService(db *gorm.DB) *NotificationService {
	return &NotificationService{db: db}
}

// ======================== 消息中心列表 (十.5) ========================

func (s *NotificationService) ListMessages(userID uint, msgType, readStatus string, page, pageSize int) (map[string]interface{}, error) {
	query := s.db.Model(&model.Notification{}).Where("user_id = ?", userID)
	if msgType != "" {
		query = query.Where("type = ?", msgType)
	}
	if readStatus == "unread" {
		query = query.Where("read = ?", false)
	} else if readStatus == "read" {
		query = query.Where("read = ?", true)
	}

	var total int64
	query.Count(&total)

	var unreadCount int64
	s.db.Model(&model.Notification{}).Where("user_id = ? AND read = ?", userID, false).Count(&unreadCount)

	var msgs []model.Notification
	query.Order("created_at desc").Offset((page - 1) * pageSize).Limit(pageSize).Find(&msgs)

	type MsgItem struct {
		MessageID string `json:"messageId"`
		Type      string `json:"type"`
		Title     string `json:"title"`
		Body      string `json:"body"`
		Priority  string `json:"priority"`
		Read      bool   `json:"read"`
		CreatedAt string `json:"createdAt"`
		DeepLink  string `json:"deepLink,omitempty"`
	}

	var items []MsgItem
	for _, m := range msgs {
		items = append(items, MsgItem{
			MessageID: m.MessageID,
			Type:      m.Type,
			Title:     m.Title,
			Body:      m.Body,
			Priority:  m.Priority,
			Read:      m.Read,
			CreatedAt: m.CreatedAt.Format(time.RFC3339),
			DeepLink:  m.DeepLink,
		})
	}

	return map[string]interface{}{
		"total":       total,
		"unreadCount": unreadCount,
		"page":        page,
		"pageSize":    pageSize,
		"list":        items,
	}, nil
}

// ======================== 标记已读 (十.6) ========================

func (s *NotificationService) MarkRead(userID uint, messageIDs []string) (int, error) {
	result := s.db.Model(&model.Notification{}).
		Where("user_id = ? AND message_id IN ?", userID, messageIDs).
		Updates(map[string]interface{}{
			"read":    true,
			"read_at": time.Now(),
		})
	return int(result.RowsAffected), result.Error
}

func (s *NotificationService) MarkAllRead(userID uint) error {
	return s.db.Model(&model.Notification{}).
		Where("user_id = ? AND read = ?", userID, false).
		Updates(map[string]interface{}{
			"read":    true,
			"read_at": time.Now(),
		}).Error
}

// ======================== 推送目标选择 (十.2) ========================

type PushTarget struct {
	UserID      uint     `json:"userId"`
	Role        string   `json:"role"`
	Channels    []string `json:"channels"`
	PushAllowed bool     `json:"pushAllowed"`
}

func (s *NotificationService) GetPushTargets(eventType, alertLevel, elderID string) ([]PushTarget, error) {
	var guardians []model.Guardianship
	s.db.Where("elder_id = ?", elderID).Find(&guardians)

	var targets []PushTarget
	for _, g := range guardians {
		channels := []string{"app"}
		if alertLevel == "critical" {
			channels = append(channels, "sms", "voice_call")
		} else if alertLevel == "warning" {
			channels = append(channels, "sms")
		}

		// info 级别仅推送主监护人
		if alertLevel == "info" && g.Role != "primary" {
			continue
		}

		targets = append(targets, PushTarget{
			UserID:      g.UserID,
			Role:        g.Role,
			Channels:    channels,
			PushAllowed: true,
		})
	}

	return targets, nil
}

// ======================== 推送规则配置 (十.1) ========================

func (s *NotificationService) GetPushRules(elderID string, userID uint) map[string]interface{} {
	return map[string]interface{}{
		"pushRules": map[string]interface{}{
			"alert": map[string]interface{}{
				"critical": map[string]interface{}{"push": true, "channels": []string{"app", "sms", "voice_call"}, "allGuardians": true},
				"warning":  map[string]interface{}{"push": true, "channels": []string{"app", "sms"}, "allGuardians": true},
				"info":     map[string]interface{}{"push": true, "channels": []string{"app"}, "allGuardians": false},
			},
			"device": map[string]interface{}{
				"offline":     map[string]interface{}{"push": true, "channels": []string{"app", "sms"}, "allGuardians": true},
				"online":      map[string]interface{}{"push": false},
				"low_battery": map[string]interface{}{"push": true, "channels": []string{"app"}, "allGuardians": false},
			},
			"medicine": map[string]interface{}{
				"high_risk": map[string]interface{}{"push": true, "channels": []string{"app", "sms"}, "allGuardians": true},
				"low_risk":  map[string]interface{}{"push": false},
			},
		},
	}
}

// ======================== 推送状态查询 (十.7) ========================

func (s *NotificationService) GetPushStatus(messageID string) (map[string]interface{}, error) {
	var msg model.Notification
	if err := s.db.Where("message_id = ?", messageID).First(&msg).Error; err != nil {
		return nil, fmt.Errorf("message not found")
	}

	return map[string]interface{}{
		"messageId":      msg.MessageID,
		"deliveryStatus": msg.DeliveryStatus,
		"channel":        msg.Channel,
		"sentAt":         msg.CreatedAt.Format(time.RFC3339),
	}, nil
}

// ======================== 消息优先级配置 (十.8) ========================

func (s *NotificationService) GetPriorityConfig(userID uint) map[string]interface{} {
	return map[string]interface{}{
		"priorityConfig": []map[string]interface{}{
			{"eventType": "alert_critical", "priority": "P0", "channels": []string{"app", "sms", "voice_call"}, "description": "紧急告警（摔倒、SOS）"},
			{"eventType": "alert_warning", "priority": "P1", "channels": []string{"app", "sms"}, "description": "警告告警（避障、心率异常）"},
			{"eventType": "device_offline", "priority": "P1", "channels": []string{"app", "sms"}, "description": "设备离线"},
			{"eventType": "medicine_high_risk", "priority": "P1", "channels": []string{"app", "sms"}, "description": "高风险药品识别"},
			{"eventType": "alert_info", "priority": "P2", "channels": []string{"app"}, "description": "一般告警（低电量等）"},
			{"eventType": "system_notice", "priority": "P3", "channels": []string{"app"}, "description": "系统通知"},
		},
	}
}
