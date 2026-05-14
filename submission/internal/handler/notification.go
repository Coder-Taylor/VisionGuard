package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type NotificationHandler struct {
	svc *service.NotificationService
}

func NewNotificationHandler(svc *service.NotificationService) *NotificationHandler {
	return &NotificationHandler{svc: svc}
}

// GET /api/v1/notifications  (十.5)
func (h *NotificationHandler) ListMessages(c *fiber.Ctx) error {
	userID, ok := c.Locals("userId").(uint)
	if !ok || userID == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	msgType := c.Query("type")
	readStatus := c.Query("readStatus")
	page := c.QueryInt("page", 1)
	pageSize := c.QueryInt("pageSize", 20)

	data, err := h.svc.ListMessages(userID, msgType, readStatus, page, pageSize)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// PUT /api/v1/notifications/read  (十.6)
func (h *NotificationHandler) MarkRead(c *fiber.Ctx) error {
	userID, ok := c.Locals("userId").(uint)
	if !ok || userID == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	var req struct {
		MessageIDs []string `json:"messageIds"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	count, err := h.svc.MarkRead(userID, req.MessageIDs)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "messages marked as read", "data": fiber.Map{"markedCount": count}})
}

// PUT /api/v1/notifications/read-all  (十.6)
func (h *NotificationHandler) MarkAllRead(c *fiber.Ctx) error {
	userID, ok := c.Locals("userId").(uint)
	if !ok || userID == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	if err := h.svc.MarkAllRead(userID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "all messages marked as read"})
}

// GET /api/v1/notification/push-rules  (十.1)
func (h *NotificationHandler) GetPushRules(c *fiber.Ctx) error {
	elderID := c.Query("elderId")
	userID, ok := c.Locals("userId").(uint)
	if !ok || userID == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	rules := h.svc.GetPushRules(elderID, userID)
	return c.JSON(fiber.Map{"code": 0, "data": rules})
}

// POST /api/v1/notification/push-targets  (十.2)
func (h *NotificationHandler) GetPushTargets(c *fiber.Ctx) error {
	var req struct {
		EventType  string `json:"eventType"`
		AlertLevel string `json:"alertLevel"`
		ElderID    string `json:"elderId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	targets, err := h.svc.GetPushTargets(req.EventType, req.AlertLevel, req.ElderID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{"pushTargets": targets}})
}

// GET /api/v1/notification/status/:messageId  (十.7)
func (h *NotificationHandler) GetPushStatus(c *fiber.Ctx) error {
	messageID := c.Params("messageId")
	data, err := h.svc.GetPushStatus(messageID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/notification/priority-config  (十.8)
func (h *NotificationHandler) GetPriorityConfig(c *fiber.Ctx) error {
	userID, ok := c.Locals("userId").(uint)
	if !ok || userID == 0 {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "用户认证失败"})
	}
	config := h.svc.GetPriorityConfig(userID)
	return c.JSON(fiber.Map{"code": 0, "data": config})
}

// POST /api/v1/notification/push  (十.3 - App推送)
func (h *NotificationHandler) SendPush(c *fiber.Ctx) error {
	var req map[string]interface{}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	// stub: push sent
	return c.JSON(fiber.Map{
		"code":    0,
		"message": "push sent",
		"data":    fiber.Map{"messageId": "MSG_" + service.GenerateRandomString(12), "channel": "app", "sentAt": "now", "deliveryStatus": "sent"},
	})
}
