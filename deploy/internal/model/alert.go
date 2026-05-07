package model

import "time"

type Alert struct {
	ID             uint       `gorm:"primaryKey" json:"id"`
	AlertID        string     `gorm:"uniqueIndex;size:32" json:"alertId"`
	DeviceID       string     `gorm:"index;size:32" json:"deviceId"`
	ElderID        string     `gorm:"index;size:32" json:"elderId"`
	AlertType      string     `gorm:"index;size:32" json:"alertType"` // fall, obstacle, sos, heart_rate_abnormal, low_battery, device_offline, geofence
	AlertLevel     string     `gorm:"size:16" json:"alertLevel"`      // critical, warning, info, notice
	Status         string     `gorm:"index;size:16;default:pending" json:"status"` // pending, confirmed, resolved, closed
	Description    string     `gorm:"size:512" json:"description"`
	LocationLat    float64    `json:"locationLat"`
	LocationLng    float64    `json:"locationLng"`
	SensorData     string     `gorm:"type:text" json:"sensorData"` // JSON string
	DuplicateCount int        `gorm:"default:0" json:"duplicateCount"`
	Resolution     string     `gorm:"size:256" json:"resolution,omitempty"`
	Severity       string     `gorm:"size:16" json:"severity,omitempty"` // false_alarm, real
	ConfirmedAt    *time.Time `json:"confirmedAt,omitempty"`
	ConfirmedBy    uint       `json:"confirmedBy,omitempty"`
	ResolvedAt     *time.Time `json:"resolvedAt,omitempty"`
	ResolvedBy     uint       `json:"resolvedBy,omitempty"`
	ClosedAt       *time.Time `json:"closedAt,omitempty"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}
