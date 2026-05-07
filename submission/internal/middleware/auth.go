package middleware

import (
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

func UserAuth(authSvc *service.AuthService) fiber.Handler {
	return func(c *fiber.Ctx) error {
		header := c.Get("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "missing authorization header"})
		}

		token := strings.TrimPrefix(header, "Bearer ")
		userID, err := authSvc.VerifyUserJWT(token)
		if err != nil {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "invalid or expired token"})
		}

		c.Locals("userId", userID)
		return c.Next()
	}
}

func DeviceAuth(authSvc *service.AuthService) fiber.Handler {
	return func(c *fiber.Ctx) error {
		header := c.Get("Authorization")
		if header == "" || !strings.HasPrefix(header, "Bearer ") {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "missing authorization header"})
		}

		token := strings.TrimPrefix(header, "Bearer ")
		deviceID, err := authSvc.VerifyDeviceJWT(token)
		if err != nil {
			return c.Status(401).JSON(fiber.Map{"code": 401, "message": "invalid or expired token"})
		}

		c.Locals("deviceId", deviceID)
		return c.Next()
	}
}
