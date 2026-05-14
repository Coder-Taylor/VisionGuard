package model

import "time"

type AuthLog struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	DeviceID  string    `gorm:"index;size:32" json:"deviceId"`
	LogType   string    `gorm:"index;size:32" json:"logType"` // auth_success, auth_fail, register, heartbeat, reconnect, offline, online
	Message   string    `gorm:"size:512" json:"message"`
	CreatedAt time.Time `json:"createdAt"`
}
