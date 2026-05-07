package model

import "time"

type Notification struct {
	ID             uint       `gorm:"primaryKey" json:"id"`
	MessageID      string     `gorm:"uniqueIndex;size:32" json:"messageId"`
	UserID         uint       `gorm:"index" json:"userId"`
	ElderID        string     `gorm:"index;size:32" json:"elderId"`
	Type           string     `gorm:"index;size:32" json:"type"` // alert, device, medicine, system
	Title          string     `gorm:"size:128" json:"title"`
	Body           string     `gorm:"size:512" json:"body"`
	Channel        string     `gorm:"size:16;default:app" json:"channel"` // app, sms, voice_call
	Priority       string     `gorm:"size:8;default:P2" json:"priority"`  // P0, P1, P2, P3
	Read           bool       `gorm:"default:false" json:"read"`
	ReadAt         *time.Time `json:"readAt,omitempty"`
	DeepLink       string     `gorm:"size:256" json:"deepLink,omitempty"`
	DeliveryStatus string     `gorm:"size:16;default:sent" json:"deliveryStatus"` // sent, delivered, failed
	CreatedAt      time.Time  `json:"createdAt"`
}
