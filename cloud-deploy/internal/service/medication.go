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
	Type    string `json:"type"`
	Title   string `json:"title"`
	Body    string `json:"body"`
	SpeakAt string `json:"speakAt"`
}

func (s *MedicationService) GetPendingMessages(deviceID string) ([]PendingMessage, error) {
	var msgs []PendingMessage

	var binding model.Binding
	if err := s.db.Where("device_id = ? AND status = ?", deviceID, "bound").
		Order("bound_at desc").First(&binding).Error; err != nil {
		return msgs, nil
	}

	elderID := binding.ElderID

	var plans []model.MedicationPlan
	s.db.Where("elder_id = ? AND status = ?", elderID, "active").Find(&plans)

	now := time.Now()
	today := now.Format("2006-01-02")
	currentTime := now.Format("15:04")

	for _, p := range plans {
		if p.StartDate > today || (p.EndDate != "" && p.EndDate < today) {
			continue
		}
		for _, t := range strings.Split(p.Schedule, ",") {
			t = strings.TrimSpace(t)
			if t == "" {
				continue
			}
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

	var ocrRecord model.OcrRecord
	err := s.db.Where("elder_id = ? AND status = ?", elderID, "completed").
		Where("updated_at > ?", now.Add(-5*time.Minute)).
		Order("updated_at desc").First(&ocrRecord).Error
	if err == nil && ocrRecord.SpeakText != "" {
		msgs = append(msgs, PendingMessage{
			Type:    "ocr_result",
			Title:   "药品识别结果",
			Body:    ocrRecord.SpeakText,
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
	config     DoubaoConfig
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

// doubaoPrompt 豆包返回规则：
//   格式「药品名，用法用量，关键警告」
//   词元: 药品名 + 用量 + 频次 + 服用方式 + 禁忌 + 标准提示
//   约束: 纯文本 ≤60字，TTS朗读，无JSON/markdown
const doubaoPrompt = `你是一个药品识别助手，为视障老人提供语音播报。
请识别图片中的药品，直接返回一句简洁的中文播报文本。

规则：
1. 只返回纯文本，不要JSON、不要markdown、不要任何标记
2. 格式「药品名，用法用量，关键警告」
3. 控制在60字以内，适合TTS朗读
4. 若有严重过敏或禁忌请务必提及

示例：「阿莫西林胶囊，每次0.5克每日三次饭后服用。青霉素过敏者禁用，使用前需做皮试。」
示例：「布洛芬缓释胶囊，每次一粒每日两次。胃溃疡者禁用，饭后服用减少胃肠刺激。」`

type MedicineRecognitionResult struct {
	Status    string `json:"status"`
	SpeakText string `json:"speakText"`
	DrugName  string `json:"drugName,omitempty"`
}

func (d *DoubaoService) RecognizeMedicine(imageURL string) (*MedicineRecognitionResult, error) {
	if d.config.APIKey == "" || imageURL == "" {
		return &MedicineRecognitionResult{
			Status:    "mock",
			SpeakText: "药品识别服务暂未配置，请稍后再试。",
			DrugName:  "未知药品",
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
					{"type": "text", "text": doubaoPrompt},
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

	speakText := strings.TrimSpace(chatResp.Choices[0].Message.Content)
	drugName := speakText
	if idx := strings.IndexAny(speakText, "，。,.；;"); idx > 0 {
		drugName = speakText[:idx]
	}
	if len(drugName) > 20 {
		drugName = drugName[:20]
	}

	return &MedicineRecognitionResult{
		Status:    "ok",
		SpeakText: speakText,
		DrugName:  drugName,
	}, nil
}

func (d *DoubaoService) MockRecognizeMedicine(ocrText string) *MedicineRecognitionResult {
	if strings.Contains(ocrText, "阿莫西林") || strings.Contains(ocrText, "amoxicillin") {
		return &MedicineRecognitionResult{
			Status:    "ok",
			SpeakText: "阿莫西林胶囊，每次0.5克每日三次饭后服用。青霉素过敏者禁用，使用前需做皮试。",
			DrugName:  "阿莫西林胶囊",
		}
	}
	if strings.Contains(ocrText, "布洛芬") || strings.Contains(ocrText, "ibuprofen") {
		return &MedicineRecognitionResult{
			Status:    "ok",
			SpeakText: "布洛芬缓释胶囊，每次一粒每日两次。胃溃疡者禁用，饭后服用减少胃肠刺激。",
			DrugName:  "布洛芬缓释胶囊",
		}
	}
	return &MedicineRecognitionResult{
		Status:    "ok",
		SpeakText: ocrText + "，请按说明书服用，遵医嘱。",
		DrugName:  ocrText,
	}
}
