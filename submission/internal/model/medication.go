package model

import "time"

// MedicationPlan — 用药计划（监护人制定，硬件轮询获取）
type MedicationPlan struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	PlanID    string    `gorm:"uniqueIndex;size:32" json:"planId"`
	ElderID   string    `gorm:"index;size:32" json:"elderId"`
	DrugName  string    `gorm:"size:128" json:"drugName"`
	Dosage    string    `gorm:"size:64" json:"dosage"`
	Frequency string    `gorm:"size:64" json:"frequency"`
	Schedule  string    `gorm:"size:512" json:"schedule"`          // JSON array: ["08:00","12:00","18:00"]
	StartDate string    `gorm:"size:10" json:"startDate"`          // 2026-05-06
	EndDate   string    `gorm:"size:10" json:"endDate"`
	Notes     string    `gorm:"size:256" json:"notes"`
	Status    string    `gorm:"size:16;default:active" json:"status"` // active / paused / completed
	CreatedBy uint      `json:"createdBy"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}
