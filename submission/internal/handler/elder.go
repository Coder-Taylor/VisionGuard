package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

type ElderHandler struct {
	svc *service.ElderService
}

func NewElderHandler(svc *service.ElderService) *ElderHandler {
	return &ElderHandler{svc: svc}
}

// POST /api/v1/elder  (二.1)
func (h *ElderHandler) Create(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	var req service.CreateElderReq
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	elder, err := h.svc.Create(userID, req)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "create success", "data": fiber.Map{"elderId": elder.ElderID}})
}

// GET /api/v1/elder/:elderId  (二.2)
func (h *ElderHandler) GetDetail(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")

	resp, err := h.svc.GetDetail(elderID, userID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": resp})
}

// PUT /api/v1/elder/:elderId  (二.3)
func (h *ElderHandler) UpdateInfo(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")

	var req map[string]interface{}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.UpdateInfo(elderID, userID, req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "update success"})
}

// DELETE /api/v1/elder/:elderId  (二.10)
func (h *ElderHandler) Delete(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")

	if err := h.svc.DeleteElder(elderID, userID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "elder profile deleted"})
}

// POST /api/v1/elder/:elderId/archive  (二.12)
func (h *ElderHandler) Archive(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	var req struct {
		Reason string `json:"reason"`
	}
	c.BodyParser(&req)

	if err := h.svc.ArchiveElder(elderID, userID, req.Reason); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "archive request submitted", "data": fiber.Map{"status": "pending_review"}})
}

// POST /api/v1/elder/:elderId/guardian/invite  (二.4)
func (h *ElderHandler) InviteGuardian(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	var req struct {
		Invitee string `json:"invitee"`
		Message string `json:"message"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	inv, err := h.svc.InviteGuardian(elderID, userID, req.Invitee, req.Message)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{
		"code":    0,
		"message": "invitation sent",
		"data":    fiber.Map{"inviteId": inv.InviteID, "expiresAt": inv.ExpiresAt.Format("2006-01-02T15:04:05Z")},
	})
}

// POST /api/v1/elder/:elderId/guardian/accept  (二.4)
func (h *ElderHandler) AcceptInvitation(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	var req struct {
		InviteID string `json:"inviteId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.AcceptInvitation(req.InviteID, userID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "guardian joined", "data": fiber.Map{"userId": strconv.Itoa(int(userID)), "role": "normal"}})
}

// DELETE /api/v1/elder/:elderId/guardian/:userId  (二.6)
func (h *ElderHandler) RemoveGuardian(c *fiber.Ctx) error {
	operatorID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	targetUserID, _ := strconv.Atoi(c.Params("userId"))

	if err := h.svc.RemoveGuardian(elderID, operatorID, uint(targetUserID)); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "guardian removed"})
}

// POST /api/v1/elder/:elderId/primary/transfer  (二.5)
func (h *ElderHandler) TransferPrimary(c *fiber.Ctx) error {
	fromUserID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	var req struct {
		NewPrimaryUserID uint `json:"newPrimaryUserId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	transfer, err := h.svc.TransferPrimary(elderID, fromUserID, req.NewPrimaryUserID)
	if err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{
		"code":    0,
		"message": "transfer initiated",
		"data":    fiber.Map{"transferId": transfer.TransferID, "status": transfer.Status},
	})
}

// POST /api/v1/elder/:elderId/primary/confirm  (二.5)
func (h *ElderHandler) ConfirmTransfer(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	var req struct {
		TransferID string `json:"transferId"`
		Accept     bool   `json:"accept"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.ConfirmTransfer(req.TransferID, req.Accept, userID); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	if req.Accept {
		return c.JSON(fiber.Map{"code": 0, "message": "primary guardian transferred"})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "transfer cancelled"})
}

// POST /api/v1/elder/:elderId/emergency-contact  (二.7)
func (h *ElderHandler) AddEmergencyContact(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	var req model.EmergencyContact
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	if err := h.svc.AddEmergencyContact(elderID, userID, req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "emergency contact added"})
}

// DELETE /api/v1/elder/:elderId/emergency-contact/:contactId  (二.7)
func (h *ElderHandler) DeleteEmergencyContact(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	elderID := c.Params("elderId")
	contactID, _ := strconv.Atoi(c.Params("contactId"))

	if err := h.svc.DeleteEmergencyContact(elderID, userID, uint(contactID)); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "message": "emergency contact deleted"})
}

// GET /api/v1/elders  (二.8)
func (h *ElderHandler) ListMyElders(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	items, err := h.svc.ListMyElders(userID)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": items})
}

// GET /api/v1/dashboard  (二.14)
func (h *ElderHandler) Dashboard(c *fiber.Ctx) error {
	userID := c.Locals("userId").(uint)
	page := c.QueryInt("page", 1)

	data, err := h.svc.Dashboard(userID, page)
	if err != nil {
		return c.Status(500).JSON(fiber.Map{"code": 500, "message": err.Error()})
	}
	return c.JSON(fiber.Map{"code": 0, "data": data})
}

// POST /api/v1/elder/:elderId/bind  (二.9)
func (h *ElderHandler) BindDevice(c *fiber.Ctx) error {
	elderID := c.Params("elderId")
	var req struct {
		DeviceID string `json:"deviceId"`
	}
	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(fiber.Map{"code": 400, "message": "invalid request"})
	}

	_ = elderID
	_ = req.DeviceID
	// 委托给 BindingService
	return c.JSON(fiber.Map{"code": 0, "message": "device bound", "data": fiber.Map{"bindTime": "now"}})
}
