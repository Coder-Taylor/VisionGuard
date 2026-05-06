package model

import "time"

type User struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	Username     string    `gorm:"uniqueIndex;size:64" json:"username"`
	DisplayName  string    `gorm:"size:64" json:"displayName"`
	PasswordHash string    `gorm:"size:256" json:"-"`
	Email        string    `gorm:"size:128" json:"email"`
	Phone        string    `gorm:"size:32" json:"phone"`
	Status       string    `gorm:"size:16;default:active" json:"status"` // active, locked, disabled
	CreatedAt    time.Time `json:"createdAt"`
	UpdatedAt    time.Time `json:"updatedAt"`
}

type RefreshToken struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	UserID    uint      `gorm:"index" json:"userId"`
	TokenHash string    `gorm:"uniqueIndex;size:256" json:"-"`
	ExpiresAt time.Time `json:"expiresAt"`
	CreatedAt time.Time `json:"createdAt"`
}
