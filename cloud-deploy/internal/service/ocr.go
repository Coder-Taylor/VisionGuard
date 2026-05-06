package service

import (
	"fmt"
	"strings"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"gorm.io/gorm"
)

type OcrService struct {
	db     *gorm.DB
	doubao *DoubaoService
}

func NewOcrService(db *gorm.DB, doubao *DoubaoService) *OcrService {
	return &OcrService{db: db, doubao: doubao}
}

// runDoubaoRecognition 异步调用豆包 API 识别药品
func (s *OcrService) runDoubaoRecognition(record model.OcrRecord) {
	result, err := s.doubao.RecognizeMedicine(record.FileURL)
	updates := map[string]interface{}{"stage": "doubao_recognizing"}

	if err != nil {
		updates["status"] = "failed"
		updates["stage"] = "doubao_failed"
		updates["fail_reason"] = "doubao_api_error"
		updates["fail_detail"] = err.Error()
	} else {
		updates["status"] = "completed"
		updates["stage"] = "completed"
		updates["progress"] = 100
		if result.DrugName != "" {
			updates["medicine_name"] = result.DrugName
			updates["generic_name"] = result.DrugName
			updates["ocr_text"] = result.DrugName + " " + result.Specification
		}
		if result.Specification != "" {
			updates["specification"] = result.Specification
		}
		if result.Indication != "" {
			updates["indications"] = result.Indication
		}
		if result.Usage != "" {
			updates["dosage"] = result.Usage
		}
		if len(result.Warnings) > 0 {
			updates["contraindications"] = strings.Join(result.Warnings, "; ")
		}
		if result.RiskLevel != "" {
			updates["risk_level"] = result.RiskLevel
		} else {
			updates["risk_level"] = "low"
		}
		if result.Confidence > 0 {
			updates["confidence"] = result.Confidence
		}
		if result.Status == "mock" {
			updates["fail_reason"] = "doubao_mock"
			updates["fail_detail"] = result.Message
		}
	}

	s.db.Model(&record).Updates(updates)
}

// ======================== 图片上传记录 (九.1) ========================

type ImageUploadReq struct {
	ElderID       string `json:"elderId"`
	DeviceID      string `json:"deviceId"`
	ImageCategory string `json:"imageCategory"`
	FileURL       string `json:"fileUrl"`
	ThumbnailURL  string `json:"thumbnailUrl"`
	FileSize      int64  `json:"fileSize"`
	Width         int    `json:"width"`
	Height        int    `json:"height"`
	Format        string `json:"format"`
}

type ImageUploadResp struct {
	ImageID     string `json:"imageId"`
	FileURL     string `json:"fileUrl"`
	ThumbnailURL string `json:"thumbnailUrl"`
	UploadedAt  string `json:"uploadedAt"`
}

func (s *OcrService) UploadImage(req ImageUploadReq) (*ImageUploadResp, error) {
	imageID := "IMG_" + generateRandomString(8)

	record := model.OcrRecord{
		TaskID:        "TASK_" + generateRandomString(8),
		ImageID:       imageID,
		ElderID:       req.ElderID,
		DeviceID:      req.DeviceID,
		ImageCategory: req.ImageCategory,
		FileURL:       req.FileURL,
		ThumbnailURL:  req.ThumbnailURL,
		FileSize:      req.FileSize,
		Status:        "pending",
		Stage:         "uploaded",
	}

	if err := s.db.Create(&record).Error; err != nil {
		return nil, fmt.Errorf("save image record error: %w", err)
	}

	// 异步调用豆包 API 识别药品
	go s.runDoubaoRecognition(record)

	return &ImageUploadResp{
		ImageID:      imageID,
		FileURL:      req.FileURL,
		ThumbnailURL: req.ThumbnailURL,
		UploadedAt:   time.Now().Format(time.RFC3339),
	}, nil
}

// ======================== OCR 任务处理（豆包识别）九.2 ========================

func (s *OcrService) CreateOcrTask(imageID, language string) (map[string]interface{}, error) {
	var existing model.OcrRecord
	if err := s.db.Where("image_id = ?", imageID).First(&existing).Error; err != nil {
		return nil, fmt.Errorf("image not found, upload first")
	}

	taskID := "OCR_" + generateRandomString(8)
	s.db.Model(&existing).Updates(map[string]interface{}{
		"task_id":  taskID,
		"language": language,
		"status":   "processing",
		"stage":    "doubao_recognizing",
		"progress": 10,
	})

	// 异步调用豆包 API 识别药品
	go s.runDoubaoRecognition(existing)

	return map[string]interface{}{
		"taskId":        taskID,
		"status":        "processing",
		"estimatedTime": 10,
	}, nil
}

// ======================== 查询 OCR 结果 (九.3) ========================

func (s *OcrService) GetOcrResult(taskID string) (map[string]interface{}, error) {
	var record model.OcrRecord
	if err := s.db.Where("task_id = ?", taskID).First(&record).Error; err != nil {
		return nil, fmt.Errorf("task not found")
	}

	result := map[string]interface{}{
		"taskId":  record.TaskID,
		"status":  record.Status,
		"imageId": record.ImageID,
	}

	if record.Status == "completed" {
		result["medicineName"] = record.MedicineName
		result["ocrText"] = record.OCRText
		result["specification"] = record.Specification
		result["indications"] = record.Indications
		result["dosage"] = record.Dosage
		result["contraindications"] = record.Contraindications
		result["confidence"] = record.Confidence
		result["riskLevel"] = record.RiskLevel
	} else if record.Status == "failed" {
		result["failReason"] = record.FailReason
		result["failDetail"] = record.FailDetail
		result["suggestion"] = "请确保光线充足、对焦清晰后重新拍摄"
	}

	return result, nil
}

// ======================== 任务状态轮询 (九.8) ========================

func (s *OcrService) PollTask(taskID string) (map[string]interface{}, error) {
	var record model.OcrRecord
	if err := s.db.Where("task_id = ?", taskID).First(&record).Error; err != nil {
		return nil, fmt.Errorf("task not found")
	}

	return map[string]interface{}{
		"taskId":   record.TaskID,
		"status":   record.Status,
		"stage":    record.Stage,
		"progress": record.Progress,
		"message":  s.progressMessage(record.Stage),
	}, nil
}

// ======================== LLM 建议生成 (九.5) ========================

func (s *OcrService) GenerateSuggestion(imageID, elderID string) (map[string]interface{}, error) {
	suggestionID := "SUG_" + generateRandomString(8)

	s.db.Model(&model.OcrRecord{}).Where("image_id = ?", imageID).Updates(map[string]interface{}{
		"suggestion_status": "generating",
	})

	// stub: mock LLM suggestion
	go func() {
		time.Sleep(5 * time.Second)
		suggestions := `{"rationality":"该药品用于预防心血管事件，用法用量合理","interaction":"注意：若老人同时服用布洛芬，可能增加出血风险","allergyRisk":"老人无阿司匹林过敏史，风险较低","specialNote":"老人有高血压病史，建议定期监测血压和凝血功能","dietAdvice":"避免饮酒，饮酒可能增加胃出血风险"}`
		s.db.Model(&model.OcrRecord{}).Where("image_id = ?", imageID).Updates(map[string]interface{}{
			"suggestion_status": "completed",
			"risk_level":        "low",
			"suggestions":       suggestions,
		})
	}()

	return map[string]interface{}{
		"suggestionId":  suggestionID,
		"status":        "generating",
		"estimatedTime": 10,
	}, nil
}

// ======================== 识别反馈 (九.6) ========================

func (s *OcrService) RecordFeedback(imageID, suggestionID, feedback, comment string) error {
	return s.db.Model(&model.OcrRecord{}).Where("image_id = ?", imageID).Updates(map[string]interface{}{
		"feedback":         feedback,
		"feedback_comment": comment,
	}).Error
}

// ======================== 历史识别记录查询 (九.7) ========================

func (s *OcrService) ListRecords(elderID string, page, pageSize int) (map[string]interface{}, error) {
	var total int64
	s.db.Model(&model.OcrRecord{}).Where("elder_id = ?", elderID).Count(&total)

	var records []model.OcrRecord
	s.db.Where("elder_id = ?", elderID).Order("created_at desc").
		Offset((page - 1) * pageSize).Limit(pageSize).Find(&records)

	type RecordItem struct {
		TaskID       string `json:"taskId"`
		ImageID      string `json:"imageId"`
		ThumbnailURL string `json:"thumbnailUrl"`
		MedicineName string `json:"medicineName"`
		OcrText      string `json:"ocrText"`
		RiskLevel    string `json:"riskLevel"`
		Status       string `json:"status"`
		CreatedAt    string `json:"createdAt"`
	}

	var items []RecordItem
	for _, r := range records {
		items = append(items, RecordItem{
			TaskID:       r.TaskID,
			ImageID:      r.ImageID,
			ThumbnailURL: r.ThumbnailURL,
			MedicineName: r.MedicineName,
			OcrText:      r.OCRText,
			RiskLevel:    r.RiskLevel,
			Status:       r.Status,
			CreatedAt:    r.CreatedAt.Format(time.RFC3339),
		})
	}

	return map[string]interface{}{
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
		"list":     items,
	}, nil
}

// ======================== 硬件轮询：最新 OCR 结果 ========================

func (s *OcrService) GetLatestResult(deviceID string) (*model.OcrRecord, error) {
	var record model.OcrRecord
	err := s.db.Where("device_id = ? AND status = ?", deviceID, "completed").
		Order("updated_at desc").First(&record).Error
	if err != nil {
		return nil, err
	}
	return &record, nil
}

func (s *OcrService) progressMessage(stage string) string {
	messages := map[string]string{
		"doubao_recognizing":    "豆包 AI 正在识别药品...",
		"doubao_completed":      "药品识别完成",
		"suggestion_generating": "正在生成用药建议...",
		"completed":             "处理完成",
	}
	if m, ok := messages[stage]; ok {
		return m
	}
	return "处理中..."
}
