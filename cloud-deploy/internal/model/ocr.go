package model

import "time"

type OcrRecord struct {
	ID             uint      `gorm:"primaryKey" json:"id"`
	TaskID         string    `gorm:"uniqueIndex;size:32" json:"taskId"`
	ImageID        string    `gorm:"index;size:32" json:"imageId"`
	ElderID        string    `gorm:"index;size:32" json:"elderId"`
	DeviceID       string    `gorm:"index;size:32" json:"deviceId"`
	ImageCategory  string    `gorm:"size:32" json:"imageCategory"`
	FileURL        string    `gorm:"size:512" json:"fileUrl"`
	ThumbnailURL   string    `gorm:"size:512" json:"thumbnailUrl"`
	FileSize       int64     `json:"fileSize"`
	Status         string    `gorm:"index;size:16;default:processing" json:"status"` // processing, ocr_processing, ocr_completed, suggestion_generating, completed, failed
	Stage          string    `gorm:"size:32" json:"stage"`
	Progress       int       `gorm:"default:0" json:"progress"`
	Language       string    `gorm:"size:8;default:zh" json:"language"`
	OCRText        string    `gorm:"type:text" json:"ocrText,omitempty"`
	Confidence     float64   `json:"confidence,omitempty"`
	SpeakText      string    `gorm:"type:text" json:"speakText,omitempty"`
	MedicineName   string    `gorm:"size:128" json:"medicineName,omitempty"`
	GenericName    string    `gorm:"size:128" json:"genericName,omitempty"`
	Specification  string    `gorm:"size:64" json:"specification,omitempty"`
	Ingredients    string    `gorm:"size:256" json:"ingredients,omitempty"`
	Indications    string    `gorm:"size:512" json:"indications,omitempty"`
	Dosage         string    `gorm:"size:256" json:"dosage,omitempty"`
	Contraindications string `gorm:"size:512" json:"contraindications,omitempty"`
	MatchScore     float64   `json:"matchScore,omitempty"`
	RiskLevel      string    `gorm:"size:16" json:"riskLevel,omitempty"` // high, medium, low
	Suggestions    string    `gorm:"type:text" json:"suggestions,omitempty"` // JSON
	SuggestionStatus string  `gorm:"size:16" json:"suggestionStatus,omitempty"`
	FailReason     string    `gorm:"size:64" json:"failReason,omitempty"`
	FailDetail     string    `gorm:"size:256" json:"failDetail,omitempty"`
	RetryCount     int       `gorm:"default:0" json:"retryCount"`
	Feedback       string    `gorm:"size:16" json:"feedback,omitempty"`
	FeedbackComment string   `gorm:"size:256" json:"feedbackComment,omitempty"`
	CreatedAt      time.Time `json:"createdAt"`
	UpdatedAt      time.Time `json:"updatedAt"`
}
