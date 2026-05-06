package handler

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type OcrHandler struct {
	svc         *service.OcrService
	uploadDir   string
	baseURL     string
}

func NewOcrHandler(svc *service.OcrService, uploadDir, baseURL string) *OcrHandler {
	if uploadDir == "" {
		uploadDir = "uploads"
	}
	return &OcrHandler{svc: svc, uploadDir: uploadDir, baseURL: baseURL}
}

// POST /api/v1/ocr/image  (九.1)
// 支持两种模式:
//   - multipart/form-data: 硬件直传 JPEG 二进制 (Content-Type: multipart/form-data, field: "image")
//   - application/json:     Android base64 data URL (fileUrl 字段)
func (h *OcrHandler) UploadImage(c *fiber.Ctx) error {
	contentType := c.Get("Content-Type")

	// ── 模式一: multipart 二进制上传 (硬件 JPEG) ──
	if strings.HasPrefix(contentType, "multipart/form-data") {
		file, err := c.FormFile("image")
		if err != nil {
			return c.Status(400).JSON(fiber.Map{"code": 400, "message": "missing image file: " + err.Error()})
		}

		deviceID := c.FormValue("deviceId", "unknown")
		elderID := c.FormValue("elderId")
		category := c.FormValue("category", "medicine")

		src, err := file.Open()
		if err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot open uploaded file"})
		}
		defer src.Close()

		// 按设备分目录
		deviceDir := filepath.Join(h.uploadDir, deviceID, "images")
		if err := os.MkdirAll(deviceDir, 0755); err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot create upload directory"})
		}

		ext := filepath.Ext(file.Filename)
		if ext == "" {
			ext = ".jpg"
		}
		savedName := fmt.Sprintf("%s%s", time.Now().Format("20060102_150405"), ext)
		savedPath := filepath.Join(deviceDir, savedName)

		dst, err := os.Create(savedPath)
		if err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot save file"})
		}
		defer dst.Close()

		if _, err := io.Copy(dst, src); err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "write file error"})
		}

		// 构造访问 URL (相对于服务器根路径)
		fileURL := fmt.Sprintf("%s/%s/%s/images/%s", h.baseURL, h.uploadDir, deviceID, savedName)
		if h.baseURL == "" {
			fileURL = fmt.Sprintf("/%s/%s/images/%s", h.uploadDir, deviceID, savedName)
		}

		req := service.ImageUploadReq{
			ElderID:       elderID,
			DeviceID:      deviceID,
			ImageCategory: category,
			FileURL:       fileURL,
			FileSize:      file.Size,
			Format:        strings.TrimPrefix(ext, "."),
		}

		resp, err := h.svc.UploadImage(req)
		if err != nil {
			return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
		}
		return c.JSON(fiber.Map{"code": 0, "message": "image uploaded", "data": resp})
	}

	// ── 模式二: JSON 上传 (Android base64 data URL) ──
	var req service.ImageUploadReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.UploadImage(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "image uploaded", "data": resp})
}

// POST /api/v1/ocr/recognize  (九.2)
func (h *OcrHandler) CreateOcrTask(c *fiber.Ctx) error {
	var req struct {
		ImageID  string `json:"imageId"`
		Language string `json:"language"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if req.Language == "" {
		req.Language = "zh"
	}

	data, err := h.svc.CreateOcrTask(req.ImageID, req.Language)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "ocr task created", "data": data})
}

// GET /api/v1/ocr/result/:taskId  (九.3)
func (h *OcrHandler) GetOcrResult(c *fiber.Ctx) error {
	taskID := c.Params("taskId")
	data, err := h.svc.GetOcrResult(taskID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/ocr/poll/:taskId  (九.8)
func (h *OcrHandler) PollTask(c *fiber.Ctx) error {
	taskID := c.Params("taskId")
	data, err := h.svc.PollTask(taskID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// POST /api/v1/ocr/suggestion  (九.5)
func (h *OcrHandler) GenerateSuggestion(c *fiber.Ctx) error {
	var req struct {
		ImageID string `json:"imageId"`
		ElderID string `json:"elderId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	data, err := h.svc.GenerateSuggestion(req.ImageID, req.ElderID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "suggestion generation started", "data": data})
}

// POST /api/v1/ocr/feedback  (九.6)
func (h *OcrHandler) RecordFeedback(c *fiber.Ctx) error {
	var req struct {
		ImageID      string `json:"imageId"`
		SuggestionID string `json:"suggestionId"`
		Feedback     string `json:"feedback"`
		Comment      string `json:"comment"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.RecordFeedback(req.ImageID, req.SuggestionID, req.Feedback, req.Comment); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "feedback recorded"})
}

// GET /api/v1/ocr/result/latest  (硬件轮询最新识别结果)
func (h *OcrHandler) GetLatestResult(c *fiber.Ctx) error {
	deviceID := c.Query("deviceId")
	if deviceID == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "deviceId is required"})
	}
	record, err := h.svc.GetLatestResult(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": "no completed result yet"})
	}
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{
		"taskId":        record.TaskID,
		"ocrText":       record.OCRText,
		"confidence":    record.Confidence,
		"medicineName":  record.MedicineName,
		"specification": record.Specification,
		"indications":   record.Indications,
		"dosage":        record.Dosage,
		"riskLevel":     record.RiskLevel,
	}})
}

// GET /api/v1/ocr/records  (九.7)
func (h *OcrHandler) ListRecords(c *fiber.Ctx) error {
	elderID := c.Query("elderId")
	page := c.QueryInt("page", 1)
	pageSize := c.QueryInt("pageSize", 20)

	data, err := h.svc.ListRecords(elderID, page, pageSize)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}
