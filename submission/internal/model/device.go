package model

import "time"

type Device struct {
	ID               uint       `gorm:"primaryKey" json:"id"`
	DeviceID         string     `gorm:"uniqueIndex;size:32" json:"deviceId"`
	DeviceCode       string     `gorm:"size:128" json:"-"` // device secret, never exposed
	SerialNo         string     `gorm:"size:64" json:"serialNo"`
	Model            string     `gorm:"size:64" json:"model"`
	MAC              string     `gorm:"size:32" json:"mac"`
	HWVersion        string     `gorm:"size:16" json:"hwVersion"`
	FWVersion        string     `gorm:"size:16" json:"fwVersion"`
	Alias            string     `gorm:"size:64" json:"alias"`
	InstallLocation  string     `gorm:"size:128" json:"installLocation"`
	Status           string     `gorm:"size:16;default:registered" json:"status"` // registered, online, offline, disabled
	BindStatus       string     `gorm:"size:16;default:unbound" json:"bindStatus"` // unbound, bound
	IP               string     `gorm:"size:32" json:"ip"`
	LastHeartbeatAt  *time.Time `json:"lastHeartbeatAt,omitempty"`
	LastOnlineAt     *time.Time `json:"lastOnlineAt,omitempty"`
	LastOfflineAt    *time.Time `json:"lastOfflineAt,omitempty"`
	Battery          int        `gorm:"default:0" json:"battery"`
	RSSI             int        `gorm:"default:0" json:"rssi"`
	Latitude         float64    `json:"latitude"`
	Longitude        float64    `json:"longitude"`
	Cert             string     `gorm:"size:2048" json:"cert,omitempty"`
	FirstAccessAt    *time.Time `json:"firstAccessAt,omitempty"`
	CreatedAt        time.Time  `json:"createdAt"`
	UpdatedAt        time.Time  `json:"updatedAt"`
}

type Binding struct {
	ID         uint       `gorm:"primaryKey" json:"id"`
	BindID     string     `gorm:"uniqueIndex;size:32" json:"bindId"`
	DeviceID   string     `gorm:"index;size:32" json:"deviceId"`
	ElderID    string     `gorm:"index;size:32" json:"elderId"`
	BoundBy    uint       `json:"boundBy"`
	Status     string     `gorm:"size:24;default:pending_device_confirm" json:"status"` // pending_device_confirm, bound, unbound
	BoundAt    *time.Time `json:"boundAt,omitempty"`
	UnboundAt  *time.Time `json:"unboundAt,omitempty"`
	UnboundReason string  `gorm:"size:256" json:"unboundReason,omitempty"`
	ExpiresAt  *time.Time `json:"expiresAt,omitempty"`
	CreatedAt  time.Time  `json:"createdAt"`
}
