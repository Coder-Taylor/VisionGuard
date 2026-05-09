package handler

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

// 设备 ID 白名单：仅允许字母、数字、下划线、连字符。
// 防止从用户输入构造的目录名出现 ".." 或路径分隔符。
var safeDeviceIDRe = regexp.MustCompile(`^[A-Za-z0-9_-]{1,64}$`)

// 仅允许图片扩展名（小写，不带点）。
var allowedImageExt = map[string]string{
	"jpg":  ".jpg",
	"jpeg": ".jpg",
	"png":  ".png",
}

// 8MB 上限，与 Fiber 默认 4MB 不同；超过直接拒绝
const maxOCRImageBytes = 8 * 1024 * 1024

func randomFilenameSuffix() string {
	var b [6]byte
	_, _ = rand.Read(b[:])
	return hex.EncodeToString(b[:])
}

type OcrHandler struct {
	svc       *service.OcrService
	uploadDir string
	baseURL   string
}

func NewOcrHandler(svc *service.OcrService, uploadDir, baseURL string) *OcrHandler {
	if uploadDir == "" {
		uploadDir = "uploads"
	}
	return &OcrHandler{svc: svc, uploadDir: uploadDir, baseURL: baseURL}
}

// POST /api/v1/ocr/image
// 支持两种模式:
//   - multipart/form-data: 硬件直传 JPEG 二进制
//   - application/json:     Android base64 data URL
func (h *OcrHandler) UploadImage(c *fiber.Ctx) error {
	contentType := c.Get("Content-Type")

	if strings.HasPrefix(contentType, "multipart/form-data") {
		file, err := c.FormFile("image")
		if err != nil {
			return c.Status(400).JSON(fiber.Map{"code": 400, "message": "missing image file: " + err.Error()})
		}
		if file.Size > maxOCRImageBytes {
			return c.Status(413).JSON(fiber.Map{"code": 413, "message": "image too large"})
		}

		// 设备路由优先使用 JWT 子身份，避免设备 A 借自己的 token 上传到设备 B 的目录。
		// Android 路由没有 deviceAuth，仍允许通过 form 提供 deviceId（已与 elderId 一并归属校验在 service 层）。
		deviceID, _ := c.Locals("deviceId").(string)
		if deviceID == "" {
			deviceID = c.FormValue("deviceId")
		}
		if !safeDeviceIDRe.MatchString(deviceID) {
			return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid deviceId"})
		}

		elderID := c.FormValue("elderId")
		category := c.FormValue("category", "medicine")

		// 仅按上传文件名拿到的扩展名做白名单匹配
		rawExt := strings.ToLower(strings.TrimPrefix(filepath.Ext(file.Filename), "."))
		ext, ok := allowedImageExt[rawExt]
		if !ok {
			return c.Status(415).JSON(fiber.Map{"code": 415, "message": "unsupported image type"})
		}

		src, err := file.Open()
		if err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot open uploaded file"})
		}
		defer src.Close()

		deviceDir := filepath.Join(h.uploadDir, deviceID, "images")
		if err := os.MkdirAll(deviceDir, 0750); err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot create upload directory"})
		}

		// 时间戳到秒粒度并发会撞名，叠加 12 位随机后缀防覆盖。
		savedName := fmt.Sprintf("%s_%s%s", time.Now().Format("20060102_150405"), randomFilenameSuffix(), ext)
		savedPath := filepath.Join(deviceDir, savedName)

		dst, err := os.Create(savedPath)
		if err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "cannot save file"})
		}
		defer dst.Close()

		if _, err := io.Copy(dst, src); err != nil {
			return c.Status(500).JSON(fiber.Map{"code": 500, "message": "write file error"})
		}

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

// POST /api/v1/ocr/recognize
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

// GET /api/v1/ocr/result/:taskId
func (h *OcrHandler) GetOcrResult(c *fiber.Ctx) error {
	taskID := c.Params("taskId")
	data, err := h.svc.GetOcrResult(taskID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/ocr/poll/:taskId
func (h *OcrHandler) PollTask(c *fiber.Ctx) error {
	taskID := c.Params("taskId")
	data, err := h.svc.PollTask(taskID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// POST /api/v1/ocr/suggestion
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

// POST /api/v1/ocr/feedback
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

// GET /api/v1/ocr/result/latest — 硬件轮询最新识别结果（返回纯文本播报）
func (h *OcrHandler) GetLatestResult(c *fiber.Ctx) error {
	// 设备路由必须从 JWT 拿 deviceID，不接受 query 覆盖
	deviceID, _ := c.Locals("deviceId").(string)
	if deviceID == "" {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "device token required"})
	}
	record, err := h.svc.GetLatestResult(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": "no completed result yet"})
	}
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{
		"taskId":       record.TaskID,
		"speakText":    record.SpeakText,
		"medicineName": record.MedicineName,
		"createdAt":    record.CreatedAt.Format(time.RFC3339),
	}})
}

// GET /api/v1/ocr/records
func (h *OcrHandler) ListRecords(c *fiber.Ctx) error {
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	elderID := c.Query("elderId")
	page := c.QueryInt("page", 1)
	pageSize := c.QueryInt("pageSize", 20)

	data, err := h.svc.ListRecords(uid, elderID, page, pageSize)
	if err != nil {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}
