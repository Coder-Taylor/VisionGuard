package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type MedicationHandler struct {
	svc     *service.MedicationService
	doubao  *service.DoubaoService
}

func NewMedicationHandler(svc *service.MedicationService, doubao *service.DoubaoService) *MedicationHandler {
	return &MedicationHandler{svc: svc, doubao: doubao}
}

// ---- 监护人端 API (UserAuth) ----

// POST /api/v1/medication/plan — 创建用药计划
func (h *MedicationHandler) CreatePlan(c *fiber.Ctx) error {
	var plan model.MedicationPlan
	if err := c.BodyParser(&plan); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	plan.CreatedBy = uid

	if err := h.svc.CreatePlan(&plan); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "plan created", "data": plan})
}

// GET /api/v1/medication/plans/:elderId — 获取老人用药计划列表
func (h *MedicationHandler) ListPlans(c *fiber.Ctx) error {
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	elderID := c.Params("elderId")
	plans, err := h.svc.ListPlans(uid, elderID)
	if err != nil {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": err.Error()})
	}
	if plans == nil {
		plans = []model.MedicationPlan{}
	}
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{"list": plans}})
}

// PUT /api/v1/medication/plan/:planId — 更新用药计划
func (h *MedicationHandler) UpdatePlan(c *fiber.Ctx) error {
	planID := c.Params("planId")
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	var updates map[string]interface{}
	if err := c.BodyParser(&updates); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if err := h.svc.UpdatePlan(planID, uid, updates); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "plan updated"})
}

// DELETE /api/v1/medication/plan/:planId — 删除用药计划
func (h *MedicationHandler) DeletePlan(c *fiber.Ctx) error {
	planID := c.Params("planId")
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	if err := h.svc.DeletePlan(planID, uid); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "plan deleted"})
}

// ---- 硬件端 API (DeviceAuth) ----

// GET /api/v1/device/:deviceId/pending-messages — 硬件轮询待推送消息（用药提醒 + OCR 结果）
func (h *MedicationHandler) GetPendingMessages(c *fiber.Ctx) error {
	urlDeviceID := c.Params("deviceId")
	jwtDeviceID, _ := c.Locals("deviceId").(string)
	if jwtDeviceID == "" {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "device token required"})
	}
	if urlDeviceID != jwtDeviceID {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": "device token does not match url"})
	}
	msgs, err := h.svc.GetPendingMessages(jwtDeviceID)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	if msgs == nil {
		msgs = []service.PendingMessage{}
	}
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{"messages": msgs}})
}

// ---- 豆包 API 药品识别 (UserAuth) ----

// POST /api/v1/medication/recognize — 调用豆包识别药品（占位）
func (h *MedicationHandler) RecognizeMedicine(c *fiber.Ctx) error {
	var req struct {
		ImageURL string `json:"imageUrl"`
		OcrText  string `json:"ocrText"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	// 优先用 OCR 文字做模拟识别
	if req.OcrText != "" {
		result := h.doubao.MockRecognizeMedicine(req.OcrText)
		return c.JSON(fiber.Map{"code": 0, "data": result})
	}

	// 调用豆包 API（当前占位）
	result, err := h.doubao.RecognizeMedicine(req.ImageURL)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": result})
}
