package handler

import (
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type LocationHandler struct {
	svc *service.LocationService
}

func NewLocationHandler(svc *service.LocationService) *LocationHandler {
	return &LocationHandler{svc: svc}
}

func currentUserID(c *fiber.Ctx) (uint, bool) {
	uid, ok := c.Locals("userId").(uint)
	if !ok || uid == 0 {
		return 0, false
	}
	return uid, true
}

// GET /api/v1/location/latest  (八.1)
func (h *LocationHandler) GetLatestLocation(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	deviceID := c.Query("deviceId")
	elderID := c.Query("elderId")

	data, err := h.svc.GetLatestLocation(uid, deviceID, elderID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/location/trajectory  (八.2)
func (h *LocationHandler) GetTrajectory(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	deviceID := c.Query("deviceId")
	elderID := c.Query("elderId")
	startStr := c.Query("start")
	endStr := c.Query("end")

	start, _ := time.Parse(time.RFC3339, startStr)
	end, _ := time.Parse(time.RFC3339, endStr)
	if start.IsZero() {
		start = time.Now().Add(-6 * time.Hour)
	}
	if end.IsZero() {
		end = time.Now()
	}

	data, err := h.svc.GetTrajectory(uid, deviceID, elderID, start, end)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/device/:deviceId/running  (八.4)
func (h *LocationHandler) GetRunningData(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	deviceID := c.Params("deviceId")
	elderID := c.Query("elderId")

	data, err := h.svc.GetRunningData(uid, deviceID, elderID)
	if err != nil {
		return c.Status(404).JSON(fiber.Map{"code": 404, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// GET /api/v1/location/alert-markers  (八.5)
func (h *LocationHandler) GetAlertMarkers(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	elderID := c.Query("elderId")
	startStr := c.Query("start")
	endStr := c.Query("end")

	start, _ := time.Parse(time.RFC3339, startStr)
	end, _ := time.Parse(time.RFC3339, endStr)
	if start.IsZero() {
		start = time.Now().Add(-24 * time.Hour)
	}
	if end.IsZero() {
		end = time.Now()
	}

	var types []string
	if t := c.Query("alertTypes"); t != "" {
		for _, s := range strings.Split(t, ",") {
			s = strings.TrimSpace(s)
			if s != "" {
				types = append(types, s)
			}
		}
	}

	data, err := h.svc.GetAlertMarkers(uid, elderID, start, end, types)
	if err != nil {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// POST /api/v1/geofence  (八.6)
func (h *LocationHandler) CreateGeofence(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	var req service.GeofenceCreateReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	fence, err := h.svc.CreateGeofence(uid, req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "geofence created", "data": fence})
}

// GET /api/v1/geofences  (八.6)
func (h *LocationHandler) ListGeofences(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	elderID := c.Query("elderId")
	fences, err := h.svc.ListGeofences(uid, elderID)
	if err != nil {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": fences})
}

// DELETE /api/v1/geofence/:fenceId  (八.6)
func (h *LocationHandler) DeleteGeofence(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	fenceID := c.Params("fenceId")
	if err := h.svc.DeleteGeofence(uid, fenceID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "geofence deleted"})
}

// POST /api/v1/data/health  (六.1) — 设备调用，需 deviceAuth
func (h *LocationHandler) SaveHealthData(c *fiber.Ctx) error {
	deviceID, _ := c.Locals("deviceId").(string)
	if deviceID == "" {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "device token required"})
	}
	var req service.HealthDataReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	// 强制覆盖 deviceID 为 JWT 中身份，防止设备伪造他人健康数据
	req.DeviceID = deviceID

	resp, err := h.svc.SaveHealthData(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}

	if !resp["bound"].(bool) {
		return c.JSON(fiber.Map{"code": 0, "message": "data accepted but unbound", "data": resp})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "data accepted", "data": resp})
}

// GET /api/v1/data/health  (六.6)
func (h *LocationHandler) QueryHealthData(c *fiber.Ctx) error {
	uid, ok := currentUserID(c)
	if !ok {
		return c.Status(401).JSON(fiber.Map{"code": 401, "message": "user token required"})
	}
	elderID := c.Query("elderId")
	deviceID := c.Query("deviceId")
	dataType := c.Query("type")
	startStr := c.Query("start")
	endStr := c.Query("end")
	page := c.QueryInt("page", 1)
	pageSize := c.QueryInt("pageSize", 20)

	start, _ := time.Parse(time.RFC3339, startStr)
	end, _ := time.Parse(time.RFC3339, endStr)

	data, err := h.svc.QueryHealthData(uid, elderID, deviceID, dataType, start, end, page, pageSize)
	if err != nil {
		return c.Status(403).JSON(fiber.Map{"code": 403, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}
