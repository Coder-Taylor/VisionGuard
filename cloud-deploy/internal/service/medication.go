package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"gorm.io/gorm"
)

type MedicationService struct {
	db *gorm.DB
}

func NewMedicationService(db *gorm.DB) *MedicationService {
	return &MedicationService{db: db}
}

// ======================== 用药计划 CRUD ========================

func (s *MedicationService) CreatePlan(plan *model.MedicationPlan) error {
	plan.PlanID = fmt.Sprintf("MP%s", time.Now().Format("20060102150405"))
	plan.Status = "active"
	return s.db.Create(plan).Error
}

func (s *MedicationService) UpdatePlan(planID string, userID uint, updates map[string]interface{}) error {
	var plan model.MedicationPlan
	if err := s.db.Where("plan_id = ? AND created_by = ?", planID, userID).First(&plan).Error; err != nil {
		return err
	}
	return s.db.Model(&plan).Updates(updates).Error
}

func (s *MedicationService) DeletePlan(planID string, userID uint) error {
	return s.db.Where("plan_id = ? AND created_by = ?", planID, userID).Delete(&model.MedicationPlan{}).Error
}

func (s *MedicationService) ListPlans(elderID string) ([]model.MedicationPlan, error) {
	var plans []model.MedicationPlan
	err := s.db.Where("elder_id = ?", elderID).Order("created_at desc").Find(&plans).Error
	return plans, err
}

func (s *MedicationService) GetPlan(planID string) (*model.MedicationPlan, error) {
	var plan model.MedicationPlan
	err := s.db.Where("plan_id = ?", planID).First(&plan).Error
	return &plan, err
}

// ======================== 硬件轮询：待推送消息 ========================

type PendingMessage struct {
	Type    string `json:"type"`    // medication / ocr_result / notification
	Title   string `json:"title"`
	Body    string `json:"body"`
	SpeakAt string `json:"speakAt"` // ISO8601, 硬件播放时间
}

// GetPendingMessages 供硬件轮询（deviceId → 查绑定 → 查老人 → 查当前用药提醒 + OCR 结果）
func (s *MedicationService) GetPendingMessages(deviceID string) ([]PendingMessage, error) {
	var msgs []PendingMessage

	// 1. 查设备绑定的老人
	var binding model.Binding
	if err := s.db.Where("device_id = ? AND status = ?", deviceID, "bound").
		Order("bound_at desc").First(&binding).Error; err != nil {
		return msgs, nil // 未绑定，返回空
	}

	elderID := binding.ElderID

	// 2. 查活跃用药计划，生成当前时间窗口 (±15min) 的提醒
	var plans []model.MedicationPlan
	s.db.Where("elder_id = ? AND status = ?", elderID, "active").Find(&plans)

	now := time.Now()
	today := now.Format("2006-01-02")
	currentTime := now.Format("15:04")

	for _, p := range plans {
		if p.StartDate > today || (p.EndDate != "" && p.EndDate < today) {
			continue
		}
		var schedule []string
		if err := json.Unmarshal([]byte(p.Schedule), &schedule); err != nil {
			continue
		}
		for _, t := range schedule {
			// ±3 分钟窗口
			if timeWithin(t, currentTime, 3) {
				msgs = append(msgs, PendingMessage{
					Type:    "medication",
					Title:   "用药提醒",
					Body:    fmt.Sprintf("该吃%s了，%s", p.DrugName, p.Dosage),
					SpeakAt: now.Format(time.RFC3339),
				})
			}
		}
	}

	// 3. 查最新 OCR 识别结果（最近 5 分钟内的）
	var ocrRecord model.OcrRecord
	err := s.db.Where("elder_id = ? AND status = ?", elderID, "completed").
		Where("updated_at > ?", now.Add(-5*time.Minute)).
		Order("updated_at desc").First(&ocrRecord).Error
	if err == nil && ocrRecord.OCRText != "" {
		msgs = append(msgs, PendingMessage{
			Type:    "ocr_result",
			Title:   "药品识别结果",
			Body:    ocrRecord.OCRText,
			SpeakAt: now.Format(time.RFC3339),
		})
	}

	return msgs, nil
}

func timeWithin(scheduled, current string, windowMinutes int) bool {
	t1, err1 := time.Parse("15:04", scheduled)
	t2, err2 := time.Parse("15:04", current)
	if err1 != nil || err2 != nil {
		return false
	}
	diff := t2.Sub(t1)
	if diff < 0 {
		diff = -diff
	}
	return diff <= time.Duration(windowMinutes)*time.Minute
}

// ======================== 豆包 API 药品识别 ========================

type DoubaoConfig struct {
	APIKey string
	APIURL string
	Model  string
}

type DoubaoService struct {
	config  DoubaoConfig
	httpClient *http.Client
}

func NewDoubaoService(apiKey, apiURL string) *DoubaoService {
	return &DoubaoService{
		config: DoubaoConfig{
			APIKey: apiKey,
			APIURL: apiURL,
			Model:  "doubao-seed-1.6-vision",
		},
		httpClient: &http.Client{Timeout: 30 * time.Second},
	}
}

// RecognizeMedicine 调用豆包 API 识别药品
// POST https://ark.cn-beijing.volces.com/api/v3/chat/completions
func (d *DoubaoService) RecognizeMedicine(imageURL string) (*MedicineRecognitionResult, error) {
	if d.config.APIKey == "" || imageURL == "" {
		return &MedicineRecognitionResult{
			Status:    "mock",
			Message:   "豆包 API Key 未配置或无图片URL",
			RiskLevel: "low",
		}, nil
	}

	reqBody := map[string]interface{}{
		"model": d.config.Model,
		"messages": []map[string]interface{}{
			{
				"role": "user",
				"content": []map[string]interface{}{
					{
						"type":      "image_url",
						"image_url": map[string]string{"url": imageURL},
					},
					{
						"type": "text",
						"text": "请识别图片中的药品信息。严格返回以下 JSON 格式（只返回 JSON，不要任何其他文字，不要 markdown 标记）：{\"drugName\":\"药品通用名\",\"specification\":\"规格(如0.5g/片)\",\"indication\":\"适应症(简明)\",\"usage\":\"用法用量\",\"warnings\":[\"禁忌1\",\"禁忌2\"],\"riskLevel\":\"low/medium/high\",\"confidence\":0.0-1.0}。riskLevel 规则: 老人常见慎用药(如镇静类/强心苷/胰岛素)=high, 普通处方药=medium, 非处方药/保健品=low。confidence 是识别可信度估值。",
					},
				},
			},
		},
	}

	body, _ := json.Marshal(reqBody)
	httpReq, err := http.NewRequest("POST", d.config.APIURL+"/api/v3/chat/completions", bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", "Bearer "+d.config.APIKey)

	resp, err := d.httpClient.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("doubao API error: %w", err)
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("doubao API returned %d: %s", resp.StatusCode, string(respBody))
	}

	// 解析 OpenAI 兼容响应
	var chatResp struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(respBody, &chatResp); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	if len(chatResp.Choices) == 0 {
		return nil, fmt.Errorf("no response from doubao")
	}

	// 解析豆包返回的 JSON
	content := chatResp.Choices[0].Message.Content
	var result MedicineRecognitionResult
	if err := json.Unmarshal([]byte(content), &result); err != nil {
		// 如果不是纯 JSON，用原始文本
		result = MedicineRecognitionResult{
			Status:        "ok",
			DrugName:      content,
			Specification:  "请查看原文",
			Indication:    "请查看原文",
			Usage:         "请查看原文",
		}
	} else {
		result.Status = "ok"
	}
	return &result, nil
}

type MedicineRecognitionResult struct {
	Status        string   `json:"status"`
	Message       string   `json:"message,omitempty"`
	DrugName      string   `json:"drugName,omitempty"`
	Specification string   `json:"specification,omitempty"`
	Indication    string   `json:"indication,omitempty"`
	Usage         string   `json:"usage,omitempty"`
	Warnings      []string `json:"warnings,omitempty"`
	RiskLevel     string   `json:"riskLevel,omitempty"`
	Confidence    float64  `json:"confidence,omitempty"`
}

// MockRecognizeMedicine 模拟药品识别（无 API Key 时的兜底方案）
func (d *DoubaoService) MockRecognizeMedicine(ocrText string) *MedicineRecognitionResult {
	if strings.Contains(ocrText, "阿莫西林") || strings.Contains(ocrText, "amoxicillin") {
		return &MedicineRecognitionResult{
			Status:        "ok",
			DrugName:      "阿莫西林胶囊",
			Specification:  "0.5g",
			Indication:    "用于敏感菌所致呼吸道感染、泌尿生殖道感染等",
			Usage:         "口服，成人一次0.5g，每6-8小时1次",
			Warnings:      []string{"青霉素过敏者禁用", "使用前需做皮试"},
		}
	}
	return &MedicineRecognitionResult{
		Status:   "ok",
		DrugName: ocrText,
		Usage:    "请遵医嘱服用",
	}
}
