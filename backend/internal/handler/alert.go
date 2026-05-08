package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type AlertHandler struct {
	svc *service.AlertService
}

func NewAlertHandler(svc *service.AlertService) *AlertHandler {
	return &AlertHandler{svc: svc}
}

// GET /api/v1/alert/types  (七.1)
func (h *AlertHandler) GetAlertTypes(c *fiber.Ctx) error {
	types := h.svc.GetAlertTypes()
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{"alertTypes": types}})
}

// POST /api/v1/alert  (七.2)
func (h *AlertHandler) CreateAlert(c *fiber.Ctx) error {
	var req service.AlertCreateReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.CreateAlert(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}

	if resp.AlertID != "" {
		return c.JSON(fiber.Map{"code": 0, "message": "alert created", "data": resp})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "alert created", "data": resp})
}

// PUT /api/v1/alert/:alertId/status  (七.5)
func (h *AlertHandler) UpdateAlertStatus(c *fiber.Ctx) error {
	alertID := c.Params("alertId")
	var req struct {
		Action     string `json:"action"`
		OperatorID uint   `json:"operatorId"`
		Remark     string `json:"remark"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if req.OperatorID == 0 {
		if uid, ok := c.Locals("userId").(uint); ok {
			req.OperatorID = uid
		}
	}

	if err := h.svc.UpdateAlertStatus(alertID, req.Action, req.OperatorID, req.Remark); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "alert " + req.Action + "ed"})
}

// GET /api/v1/alerts  (七.6)
func (h *AlertHandler) ListAlerts(c *fiber.Ctx) error {
	elderID := c.Query("elderId")
	alertType := c.Query("alertType")
	status := c.Query("status")
	page := c.QueryInt("page", 1)
	pageSize := c.QueryInt("pageSize", 20)

	startStr := c.Query("start")
	endStr := c.Query("end")
	start := parseTime(startStr)
	end := parseTime(endStr)

	userID := uint(0)
	if uid, ok := c.Locals("userId").(uint); ok {
		userID = uid
	}

	data, err := h.svc.ListAlerts(userID, elderID, alertType, status, start, end, page, pageSize)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/alert/:alertId  (七.7)
func (h *AlertHandler) GetAlertDetail(c *fiber.Ctx) error {
	alertID := c.Params("alertId")
	data, err := h.svc.GetAlertDetail(alertID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// POST /api/v1/alert/:alertId/resolve  (七.8)
func (h *AlertHandler) ResolveAlert(c *fiber.Ctx) error {
	alertID := c.Params("alertId")
	var req struct {
		OperatorID uint   `json:"operatorId"`
		Resolution string `json:"resolution"`
		Severity   string `json:"severity"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if req.OperatorID == 0 {
		if uid, ok := c.Locals("userId").(uint); ok {
			req.OperatorID = uid
		}
	}

	if err := h.svc.UpdateAlertStatus(alertID, "resolve", req.OperatorID, req.Resolution); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "alert resolved"})
}

// GET /api/v1/alert/statistics  (七.9)
func (h *AlertHandler) GetStatistics(c *fiber.Ctx) error {
	elderID := c.Query("elderId")
	period := c.Query("period", "week")
	date := c.Query("date")

	data, err := h.svc.GetStatistics(elderID, period, date)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/alert/level-config  (七.4)
func (h *AlertHandler) GetLevelConfig(c *fiber.Ctx) error {
	return c.JSON(fiber.Map{
		"code": 0,
		"data": fiber.Map{
			"levelConfig": fiber.Map{
				"fall":                fiber.Map{"level": "critical", "pushAll": true, "voiceCall": true},
				"sos":                 fiber.Map{"level": "critical", "pushAll": true, "voiceCall": true},
				"obstacle":            fiber.Map{"level": "warning", "pushAll": true, "voiceCall": false},
				"heart_rate_abnormal": fiber.Map{"level": "warning", "pushAll": true, "voiceCall": false},
				"low_battery":         fiber.Map{"level": "info", "pushAll": false, "voiceCall": false},
				"device_offline":      fiber.Map{"level": "warning", "pushAll": true, "voiceCall": false},
			},
		},
	})
}
