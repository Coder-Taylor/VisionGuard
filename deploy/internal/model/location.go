package model

import "time"

type Location struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	DataID    string    `gorm:"uniqueIndex;size:32" json:"dataId"`
	DeviceID  string    `gorm:"index;size:32" json:"deviceId"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	Latitude  float64   `json:"latitude"`
	Longitude float64   `json:"longitude"`
	Accuracy  float64   `json:"accuracy"`
	Speed     float64   `json:"speed"`
	Heading   float64   `json:"heading"`
	Address   string    `gorm:"size:256" json:"address,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
}

type Geofence struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	FenceID   string    `gorm:"uniqueIndex;size:32" json:"fenceId"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	FenceName string    `gorm:"size:64" json:"fenceName"`
	FenceType string    `gorm:"size:16" json:"fenceType"` // circle, polygon
	CenterLat float64   `json:"centerLat"`
	CenterLng float64   `json:"centerLng"`
	Radius    float64   `json:"radius"`          // for circle
	Vertices  string    `gorm:"type:text" json:"vertices,omitempty"` // JSON for polygon
	Enabled   bool      `gorm:"default:true" json:"enabled"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type HealthData struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	DataID    string    `gorm:"uniqueIndex;size:32" json:"dataId"`
	DeviceID  string    `gorm:"index;size:32" json:"deviceId"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	Type      string    `gorm:"index;size:32" json:"type"` // heart_rate, blood_pressure, steps, spo2
	Value     float64   `json:"value"`
	Unit      string    `gorm:"size:16" json:"unit"`
	Metadata  string    `gorm:"type:text" json:"metadata,omitempty"` // JSON
	Bound     bool      `gorm:"default:true" json:"bound"`
	CreatedAt time.Time `json:"createdAt"`
}
