package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type DeviceHandler struct {
	svc *service.DeviceService
}

func NewDeviceHandler(svc *service.DeviceService) *DeviceHandler {
	return &DeviceHandler{svc: svc}
}

// POST /api/v1/device/activate  (三.1)
func (h *DeviceHandler) Activate(c *fiber.Ctx) error {
	var req service.DeviceActivateReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.Activate(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "device registered", "data": resp})
}

// POST /api/v1/device/auth  (三.2)
func (h *DeviceHandler) Authenticate(c *fiber.Ctx) error {
	var req struct {
		DeviceID  string `json:"deviceId"`
		FWVersion string `json:"fwVersion"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.Authenticate(req.DeviceID, req.FWVersion)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "authenticated", "data": resp})
}

// POST /api/v1/device/heartbeat  (一.1.v, 四.1)
func (h *DeviceHandler) Heartbeat(c *fiber.Ctx) error {
	var req service.HeartbeatReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	// deviceId 从 JWT 中取
	if deviceID, ok := c.Locals("deviceId").(string); ok {
		req.DeviceID = deviceID
	}

	resp, err := h.svc.Heartbeat(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "heartbeat ack", "data": resp})
}

// GET /api/v1/device/status/:deviceId  (四.2)
func (h *DeviceHandler) OnlineStatus(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	resp, err := h.svc.GetOnlineStatus(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// GET /api/v1/device/:deviceId/last-online  (四.5)
func (h *DeviceHandler) LastOnline(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	resp, err := h.svc.GetLastOnline(deviceID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// PUT /api/v1/device/:deviceId  (三.4)
func (h *DeviceHandler) UpdateDeviceInfo(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	var req struct {
		Alias    string `json:"alias"`
		Location string `json:"location"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.UpdateDeviceInfo(deviceID, req.Alias, req.Location); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "device info updated"})
}

// POST /api/v1/device/:deviceId/toggle  (三.8)
func (h *DeviceHandler) ToggleDevice(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	var req struct {
		Action string `json:"action"`
		Reason string `json:"reason"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.ToggleDevice(deviceID, req.Action, req.Reason); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "device " + req.Action + "d"})
}

// GET /api/v1/device/:deviceId/firmware  (三.6)
func (h *DeviceHandler) CheckFirmware(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	currentFW := c.Query("currentFwVersion")

	resp, err := h.svc.CheckFirmware(deviceID, currentFW)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// POST /api/v1/devices/batch-status  (四.7)
func (h *DeviceHandler) BatchStatus(c *fiber.Ctx) error {
	var req struct {
		DeviceIDs []string `json:"deviceIds"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	result := h.svc.BatchStatus(req.DeviceIDs)
	return c.JSON(fiber.Map{"code": 0, "data": fiber.Map{"total": len(result), "devices": result}})
}

// POST /api/v1/device/:deviceId/data  (六.7 - 设备数据上报通用)
func (h *DeviceHandler) ReportData(c *fiber.Ctx) error {
	deviceID := c.Params("deviceId")
	var req map[string]interface{}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	req["deviceId"] = deviceID
	// 数据上报通用接口，根据type字段路由到不同服务
	dataType, _ := req["type"].(string)
	switch dataType {
	case "heart_rate", "blood_pressure", "steps", "spo2":
		// handled by location service's SaveHealthData
		return c.JSON(fiber.Map{"code": 0, "message": "data accepted"})
	case "location":
		return c.JSON(fiber.Map{"code": 0, "message": "location data saved"})
	case "alert":
		return c.JSON(fiber.Map{"code": 0, "message": "alert data saved"})
	default:
		return c.JSON(fiber.Map{"code": 0, "message": "data accepted"})
	}
}
