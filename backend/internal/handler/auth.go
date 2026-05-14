package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type AuthHandler struct {
	svc       *service.AuthService
	deviceSvc *service.DeviceService
}

func NewAuthHandler(svc *service.AuthService, deviceSvc *service.DeviceService) *AuthHandler {
	return &AuthHandler{svc: svc, deviceSvc: deviceSvc}
}

// POST /api/v1/device/challenge  (一.1.i)
func (h *AuthHandler) RequestChallenge(c *fiber.Ctx) error {
	var req struct {
		DeviceID string `json:"deviceId"`
	}
	if err := c.BodyParser(&req); err != nil || req.DeviceID == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "deviceId required"})
	}

	resp, err := h.svc.RequestChallenge(req.DeviceID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(resp)
}

// POST /api/v1/device/verify  (一.1.i)
func (h *AuthHandler) VerifyChallenge(c *fiber.Ctx) error {
	var req service.VerifyChallengeReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	jwt, err := h.svc.VerifyChallenge(req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"jwt": jwt})
}

// POST /api/v1/auth/register  (一.2.i)
func (h *AuthHandler) Register(c *fiber.Ctx) error {
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
		Email    string `json:"email"`
		Phone    string `json:"phone"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if req.Username == "" || req.Password == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "username and password required"})
	}

	if err := h.svc.Register(req.Username, req.Password, req.Email, req.Phone); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "register success"})
}

// POST /api/v1/auth/login  (一.2.ii)
func (h *AuthHandler) Login(c *fiber.Ctx) error {
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	resp, err := h.svc.Login(req.Username, req.Password)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(resp)
}

// POST /api/v1/auth/refresh  (一.2.iii)
func (h *AuthHandler) RefreshToken(c *fiber.Ctx) error {
	var req struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := c.BodyParser(&req); err != nil || req.RefreshToken == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "refresh_token required"})
	}

	resp, err := h.svc.RefreshToken(req.RefreshToken)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(resp)
}

// POST /api/v1/auth/logout  (一.2.v)
func (h *AuthHandler) Logout(c *fiber.Ctx) error {
	var req struct {
		RefreshToken string `json:"refresh_token"`
	}
	if err := c.BodyParser(&req); err != nil || req.RefreshToken == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "refresh_token required"})
	}

	h.svc.Logout(req.RefreshToken)
	return c.JSON(fiber.Map{"code": 0, "message": "logout success"})
}

// POST /api/v1/auth/change-password
func (h *AuthHandler) ChangePassword(c *fiber.Ctx) error {
	var req struct {
		OldPassword string `json:"oldPassword"`
		NewPassword string `json:"newPassword"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if req.OldPassword == "" || req.NewPassword == "" {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "oldPassword and newPassword required"})
	}

	userID := c.Locals("userId").(uint)
	if err := h.svc.ChangePassword(userID, req.OldPassword, req.NewPassword); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "password changed"})
}

// GET /api/v1/user/profile
func (h *AuthHandler) GetProfile(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	resp, err := h.svc.GetProfile(userID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// PUT /api/v1/user/profile
func (h *AuthHandler) UpdateProfile(c *fiber.Ctx) error {
	var req struct {
		DisplayName string `json:"displayName"`
		Phone       string `json:"phone"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	userID := c.Locals("userId").(uint)
	if err := h.svc.UpdateProfile(userID, req.DisplayName, req.Phone); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "profile updated"})
}

// POST /api/v1/device/register  (一.1.ii)
func (h *AuthHandler) DeviceFirstRegister(c *fiber.Ctx) error {
	var req struct {
		DeviceID        string `json:"deviceId"`
		DeviceModel     string `json:"deviceModel"`
		FirmwareVersion string `json:"firmwareVersion"`
		IP              string `json:"ip"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}
	if req.IP == "" {
		req.IP = c.IP()
	}

	if err := h.deviceSvc.DeviceFirstRegister(req.DeviceID, req.DeviceModel, req.FirmwareVersion, req.IP); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "register success"})
}

// POST /api/v1/device/info  (一.1.viii)
func (h *AuthHandler) RecordDeviceInfo(c *fiber.Ctx) error {
	var req struct {
		DeviceID        string `json:"deviceId"`
		DeviceModel     string `json:"deviceModel"`
		FirmwareVersion string `json:"firmwareVersion"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.RecordDeviceInfo(req.DeviceID, req.DeviceModel, req.FirmwareVersion); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "record success"})
}

// POST /api/v1/device/log  (一.1.ix)
func (h *AuthHandler) LogAuthEvent(c *fiber.Ctx) error {
	var req struct {
		DeviceID string `json:"deviceId"`
		LogType  string `json:"logType"`
		Message  string `json:"message"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.LogAuthEvent(req.DeviceID, req.LogType, req.Message); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "log success"})
}
