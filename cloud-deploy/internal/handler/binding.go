package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type BindingHandler struct {
	svc *service.BindingService
}

func NewBindingHandler(svc *service.BindingService) *BindingHandler {
	return &BindingHandler{svc: svc}
}

// GET /api/v1/device/:deviceId/search  (五.1)
func (h *BindingHandler) SearchDevice(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	resp, err := h.svc.SearchDevice(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// POST /api/v1/binding/initiate  (五.2)
func (h *BindingHandler) InitiateBinding(c *fiber.Ctx) error {
	var req struct {
		ElderID    string `json:"elderId"`
		DeviceID   string `json:"deviceId"`
		OperatorID uint   `json:"operatorId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if req.OperatorID == 0 {
		if uid, ok := c.Locals("userId").(uint); ok {
			req.OperatorID = uid
		}
	}

	resp, err := h.svc.InitiateBinding(req.ElderID, req.DeviceID, req.OperatorID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "binding initiated", "data": resp})
}

// POST /api/v1/binding/confirm  (五.3 - 设备端确认)
func (h *BindingHandler) ConfirmBinding(c *fiber.Ctx) error {
	var req struct {
		DeviceID string `json:"deviceId"`
		BindID   string `json:"bindId"`
		Confirm  bool   `json:"confirm"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.ConfirmBinding(req.DeviceID, req.BindID, req.Confirm)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "device bind confirmed", "data": resp})
}

// POST /api/v1/binding/check  (五.4)
func (h *BindingHandler) CheckBindConstraint(c *fiber.Ctx) error {
	var req struct {
		DeviceID string `json:"deviceId"`
		ElderID  string `json:"elderId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.CheckBindConstraint(req.DeviceID, req.ElderID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	if resp.CurrentElderID != "" {
		return c.JSON(fiber.Map{"code": 4001, "message": "device already bound", "data": resp})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// POST /api/v1/binding/unbind  (五.5)
func (h *BindingHandler) Unbind(c *fiber.Ctx) error {
	var req struct {
		ElderID  string `json:"elderId"`
		DeviceID string `json:"deviceId"`
		Reason   string `json:"reason"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	operatorID := uint(0)
	if uid, ok := c.Locals("userId").(uint); ok {
		operatorID = uid
	}

	if err := h.svc.Unbind(req.ElderID, req.DeviceID, operatorID, req.Reason); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{
		"code":    0,
		"message": "device unbound",
		"data":    fiber.Map{"deviceId": req.DeviceID, "newStatus": "unbound", "historyKept": true},
	})
}

// POST /api/v1/binding/rebind  (五.6)
func (h *BindingHandler) Rebind(c *fiber.Ctx) error {
	var req struct {
		FromElderID string `json:"fromElderId"`
		ToElderID   string `json:"toElderId"`
		DeviceID    string `json:"deviceId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	operatorID := uint(0)
	if uid, ok := c.Locals("userId").(uint); ok {
		operatorID = uid
	}

	if err := h.svc.Rebind(req.FromElderID, req.ToElderID, req.DeviceID, operatorID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "device rebound success"})
}

// GET /api/v1/device/:deviceId/binding  (五.9)
func (h *BindingHandler) GetBindRelation(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	resp, err := h.svc.GetBindRelation(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}
