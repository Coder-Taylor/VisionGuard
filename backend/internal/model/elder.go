package model

import "time"

type Elder struct {
	ID              uint              `gorm:"primaryKey" json:"id"`
	ElderID         string            `gorm:"uniqueIndex;size:32" json:"elderId"`
	Name            string            `gorm:"size:64" json:"name"`
	Gender          string            `gorm:"size:16" json:"gender"`
	BirthDate       string            `gorm:"size:16" json:"birthDate"`
	IDCard          string            `gorm:"size:32" json:"idCard"`
	BloodType       string            `gorm:"size:8" json:"bloodType"`
	Allergy         string            `gorm:"size:512" json:"allergy"`
	MedicalHistory  string            `gorm:"size:1024" json:"medicalHistory"`
	Address         string            `gorm:"size:256" json:"address"`
	Status          string            `gorm:"size:16;default:active" json:"status"` // active, archived
	CreatedBy       uint              `json:"createdBy"`
	CreatedAt       time.Time         `json:"createdAt"`
	UpdatedAt       time.Time         `json:"updatedAt"`
	EmergencyContacts []EmergencyContact `gorm:"foreignKey:ElderID;references:ElderID" json:"emergencyContacts,omitempty"`
	Guardians       []Guardianship    `gorm:"foreignKey:ElderID;references:ElderID" json:"guardians,omitempty"`
}

type EmergencyContact struct {
	ID       uint   `gorm:"primaryKey" json:"id"`
	ElderID  string `gorm:"index;size:32" json:"elderId"`
	Name     string `gorm:"size:64" json:"name"`
	Relation string `gorm:"size:32" json:"relation"`
	Phone    string `gorm:"size:32" json:"phone"`
}

type Guardianship struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	UserID    uint      `gorm:"index" json:"userId"`
	Nickname  string    `gorm:"size:64" json:"nickname"`
	Role      string    `gorm:"size:16;default:normal" json:"role"` // primary, normal
	CreatedAt time.Time `json:"createdAt"`
}

type Invitation struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	InviteID  string    `gorm:"uniqueIndex;size:32" json:"inviteId"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	InviterID uint      `json:"inviterId"`
	Invitee   string    `gorm:"size:64" json:"invitee"` // phone or email
	Message   string    `gorm:"size:256" json:"message"`
	Status    string    `gorm:"size:16;default:pending" json:"status"` // pending, accepted, declined, expired
	ExpiresAt time.Time `json:"expiresAt"`
	CreatedAt time.Time `json:"createdAt"`
}

type Transfer struct {
	ID           uint      `gorm:"primaryKey" json:"id"`
	TransferID   string    `gorm:"uniqueIndex;size:32" json:"transferId"`
	ElderID      string    `gorm:"index;size:32" json:"elderId"`
	FromUserID   uint      `json:"fromUserId"`
	ToUserID     uint      `json:"toUserId"`
	Status       string    `gorm:"size:16;default:pending_accept" json:"status"` // pending_accept, accepted, cancelled, completed
	CompletedAt  *time.Time `json:"completedAt,omitempty"`
	CreatedAt    time.Time `json:"createdAt"`
}
