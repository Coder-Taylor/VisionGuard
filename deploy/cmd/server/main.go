package main

import (
	"log"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/joho/godotenv"
	"github.com/jry21223/vision-hub/backend/internal/config"
	"github.com/jry21223/vision-hub/backend/internal/handler"
	"github.com/jry21223/vision-hub/backend/internal/infra"
	"github.com/jry21223/vision-hub/backend/internal/middleware"
	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/jry21223/vision-hub/backend/internal/service"
)

func main() {
	godotenv.Load()

	cfg := config.Load()
	db := infra.NewPostgres(cfg)
	rdb := infra.NewRedis(cfg)

	// Auto migrate
	db.AutoMigrate(
		&model.User{},
		&model.RefreshToken{},
		&model.Elder{},
		&model.EmergencyContact{},
		&model.Guardianship{},
		&model.Invitation{},
		&model.Transfer{},
		&model.Device{},
		&model.Binding{},
		&model.Alert{},
		&model.Notification{},
		&model.OcrRecord{},
		&model.AuthLog{},
		&model.Location{},
		&model.Geofence{},
		&model.HealthData{},
		&model.MedicationPlan{},
	)

	// Services
	authSvc := service.NewAuthService(db, rdb, cfg)
	deviceSvc := service.NewDeviceService(db, rdb)
	elderSvc := service.NewElderService(db)
	bindingSvc := service.NewBindingService(db)
	alertSvc := service.NewAlertService(db, rdb)
		notificationSvc := service.NewNotificationService(db)
		doubaoSvc := service.NewDoubaoService(cfg.DoubaoAPIKey, cfg.DoubaoAPIURL)
		ocrSvc := service.NewOcrService(db, doubaoSvc)
		locationSvc := service.NewLocationService(db, rdb)
		medicationSvc := service.NewMedicationService(db)
	
	authH := handler.NewAuthHandler(authSvc, deviceSvc)
	deviceH := handler.NewDeviceHandler(deviceSvc, cfg.DeviceActivationToken)
	elderH := handler.NewElderHandler(elderSvc)
	bindingH := handler.NewBindingHandler(bindingSvc)
	alertH := handler.NewAlertHandler(alertSvc)
	notificationH := handler.NewNotificationHandler(notificationSvc)
	ocrH := handler.NewOcrHandler(ocrSvc, "uploads", cfg.PublicBaseURL)
	locationH := handler.NewLocationHandler(locationSvc)
	medicationH := handler.NewMedicationHandler(medicationSvc, doubaoSvc)

	// Middleware
	userAuth := middleware.UserAuth(authSvc)
	deviceAuth := middleware.DeviceAuth(authSvc)

	app := fiber.New()

		// 静态文件 — OCR 上传的图片
		app.Static("/uploads", "./uploads")

		// ═══════════════════════════════════════════════════════════════
		// 路由注册（共 74 条，序号 1-74，业务标注保留）
	// 核对方法：Ctrl+F 搜 "// N." 从 1 数到 74
	// ═══════════════════════════════════════════════════════════════

	// ---- 健康检查 ----
	app.Get("/api/v1/healthz", func(c *fiber.Ctx) error { // 1.
		return c.JSON(fiber.Map{"status": "ok"})
	})

	// ---- 一、认证服务（9 路由：2.-10.）----
	app.Post("/api/v1/device/challenge", authH.RequestChallenge)   // 2. 一.1.i
	app.Post("/api/v1/device/verify", authH.VerifyChallenge)       // 3. 一.1.i
	app.Post("/api/v1/device/register", authH.DeviceFirstRegister) // 4. 一.1.ii
	app.Post("/api/v1/device/info", authH.RecordDeviceInfo)        // 5. 一.1.viii
	app.Post("/api/v1/device/log", authH.LogAuthEvent)             // 6. 一.1.ix
	app.Post("/api/v1/auth/register", authH.Register)              // 7. 一.2.i
	app.Post("/api/v1/auth/login", authH.Login)                    // 8. 一.2.ii
	app.Post("/api/v1/auth/refresh", authH.RefreshToken)           // 9. 一.2.iii
	app.Post("/api/v1/auth/logout", authH.Logout)                  // 10. 一.2.v
	app.Post("/api/v1/auth/change-password", userAuth, authH.ChangePassword) // 10a. 修改密码
	app.Get("/api/v1/user/profile", userAuth, authH.GetProfile)              // 10b. 获取用户档案
	app.Put("/api/v1/user/profile", userAuth, authH.UpdateProfile)           // 10c. 更新用户档案

	// ---- 三、设备接入与安全注册（8 路由：11.-18.）----
	app.Post("/api/v1/device/activate", deviceH.Activate)           // 11. 三.1
	app.Post("/api/v1/device/auth", deviceH.Authenticate)           // 12. 三.2

	// 四、设备心跳与在线状态（5 路由：13.-17.，加 20.）
	app.Post("/api/v1/device/heartbeat", deviceAuth, deviceH.Heartbeat)              // 13. 一.1.v / 四.1
	app.Get("/api/v1/device/status/:deviceId", deviceAuth, deviceH.OnlineStatus)     // 14. 四.2
	app.Get("/api/v1/device/:deviceId/last-online", deviceAuth, deviceH.LastOnline)  // 15. 四.5
	app.Put("/api/v1/device/:deviceId", deviceAuth, deviceH.UpdateDeviceInfo)        // 16. 三.4
	app.Post("/api/v1/device/:deviceId/toggle", deviceAuth, deviceH.ToggleDevice)    // 17. 三.8
	app.Get("/api/v1/device/:deviceId/firmware", deviceAuth, deviceH.CheckFirmware)  // 18. 三.6
	app.Post("/api/v1/device/:deviceId/data", deviceAuth, deviceH.ReportData)        // 19. 六.7
	app.Post("/api/v1/devices/batch-status", userAuth, deviceH.BatchStatus)          // 20. 四.7

	// ---- 二、老人档案与监护关系（15 路由：21.-35.）----
	app.Post("/api/v1/elder", userAuth, elderH.Create)                                         // 21. 二.1
	app.Get("/api/v1/elder/:elderId", userAuth, elderH.GetDetail)                              // 22. 二.2
	app.Put("/api/v1/elder/:elderId", userAuth, elderH.UpdateInfo)                             // 23. 二.3
	app.Delete("/api/v1/elder/:elderId", userAuth, elderH.Delete)                              // 24. 二.10
	app.Post("/api/v1/elder/:elderId/archive", userAuth, elderH.Archive)                       // 25. 二.12
	app.Post("/api/v1/elder/:elderId/guardian/invite", userAuth, elderH.InviteGuardian)        // 26. 二.4
	app.Post("/api/v1/elder/:elderId/guardian/accept", userAuth, elderH.AcceptInvitation)      // 27. 二.4
	app.Delete("/api/v1/elder/:elderId/guardian/:userId", userAuth, elderH.RemoveGuardian)     // 28. 二.6
	app.Post("/api/v1/elder/:elderId/primary/transfer", userAuth, elderH.TransferPrimary)      // 29. 二.5
	app.Post("/api/v1/elder/:elderId/primary/confirm", userAuth, elderH.ConfirmTransfer)       // 30. 二.5
	app.Post("/api/v1/elder/:elderId/emergency-contact", userAuth, elderH.AddEmergencyContact) // 31. 二.7
	app.Delete("/api/v1/elder/:elderId/emergency-contact/:contactId", userAuth, elderH.DeleteEmergencyContact) // 32. 二.7
	app.Post("/api/v1/elder/:elderId/bind", userAuth, elderH.BindDevice)                       // 33. 二.9
	app.Get("/api/v1/elders", userAuth, elderH.ListMyElders)                                   // 34. 二.8
	app.Get("/api/v1/dashboard", userAuth, elderH.Dashboard)                                   // 35. 二.14

	// ---- 五、设备绑定与解绑（7 路由：36.-42.）----
	app.Get("/api/v1/device/:deviceId/search", userAuth, bindingH.SearchDevice)       // 36. 五.1
	app.Post("/api/v1/binding/initiate", userAuth, bindingH.InitiateBinding)          // 37. 五.2
	app.Post("/api/v1/binding/confirm", deviceAuth, bindingH.ConfirmBinding)          // 38. 五.3（设备端调用，需 deviceAuth）
	app.Post("/api/v1/binding/check", userAuth, bindingH.CheckBindConstraint)         // 39. 五.4
	app.Post("/api/v1/binding/unbind", userAuth, bindingH.Unbind)                     // 40. 五.5
	app.Post("/api/v1/binding/rebind", userAuth, bindingH.Rebind)                     // 41. 五.6
	app.Get("/api/v1/device/:deviceId/binding", userAuth, bindingH.GetBindRelation)   // 42. 五.9

	// ---- 七、告警事件管理（8 路由：43.-50.）----
	app.Get("/api/v1/alert/types", alertH.GetAlertTypes)                   // 43. 七.1
	app.Post("/api/v1/alert", deviceAuth, alertH.CreateAlert)              // 44. 七.2（设备端调用，需 deviceAuth）
	app.Get("/api/v1/alerts", userAuth, alertH.ListAlerts)                 // 45. 七.6
	app.Get("/api/v1/alert/statistics", userAuth, alertH.GetStatistics)    // 46. 七.9
	app.Get("/api/v1/alert/level-config", userAuth, alertH.GetLevelConfig) // 47. 七.4
	app.Get("/api/v1/alert/:alertId", userAuth, alertH.GetAlertDetail)     // 48. 七.7
	app.Put("/api/v1/alert/:alertId/status", userAuth, alertH.UpdateAlertStatus) // 49. 七.5
	app.Post("/api/v1/alert/:alertId/resolve", userAuth, alertH.ResolveAlert)     // 50. 七.8

	// ---- 八、定位与设备状态展示（7 路由：51.-57.）----
	app.Get("/api/v1/location/latest", userAuth, locationH.GetLatestLocation)      // 51. 八.1
	app.Get("/api/v1/location/trajectory", userAuth, locationH.GetTrajectory)      // 52. 八.2
	app.Get("/api/v1/location/alert-markers", userAuth, locationH.GetAlertMarkers) // 53. 八.5
	app.Get("/api/v1/device/:deviceId/running", userAuth, locationH.GetRunningData) // 54. 八.4
	app.Post("/api/v1/geofence", userAuth, locationH.CreateGeofence)               // 55. 八.6
	app.Get("/api/v1/geofences", userAuth, locationH.ListGeofences)                // 56. 八.6
	app.Delete("/api/v1/geofence/:fenceId", userAuth, locationH.DeleteGeofence)    // 57. 八.6

	// ---- 六、设备数据接收与存储（2 路由：58.-59.）----
	app.Post("/api/v1/data/health", deviceAuth, locationH.SaveHealthData)          // 58. 六.1（设备端调用，需 deviceAuth）
	app.Get("/api/v1/data/health", userAuth, locationH.QueryHealthData)            // 59. 六.6

	// ---- 九、药品识别与智能建议（8 路由：60.-67.）----
	app.Post("/api/v1/ocr/image", userAuth, ocrH.UploadImage)               // 60. 九.1  Android JSON/base64
	app.Post("/api/v1/device/ocr/image", deviceAuth, ocrH.UploadImage)      // 60b.     硬件 JSON/JPEG (设备JWT)
	app.Post("/api/v1/ocr/recognize", userAuth, ocrH.CreateOcrTask)         // 61. 九.2
	app.Get("/api/v1/ocr/result/latest", deviceAuth, ocrH.GetLatestResult)  // 62. 九.9 硬件轮询（精确路由必须在 :taskId 前）
	app.Get("/api/v1/ocr/result/:taskId", userAuth, ocrH.GetOcrResult)      // 63. 九.3
	app.Get("/api/v1/ocr/poll/:taskId", userAuth, ocrH.PollTask)            // 64. 九.8
	app.Post("/api/v1/ocr/suggestion", userAuth, ocrH.GenerateSuggestion)   // 65. 九.5
	app.Post("/api/v1/ocr/feedback", userAuth, ocrH.RecordFeedback)         // 66. 九.6
	app.Get("/api/v1/ocr/records", userAuth, ocrH.ListRecords)              // 67. 九.7

	// ---- 十、消息推送与通知（8 路由：68.-75.）----
	app.Get("/api/v1/notifications", userAuth, notificationH.ListMessages)                    // 68. 十.5
	app.Put("/api/v1/notifications/read", userAuth, notificationH.MarkRead)                    // 69. 十.6
	app.Put("/api/v1/notifications/read-all", userAuth, notificationH.MarkAllRead)             // 70. 十.6
	app.Get("/api/v1/notification/push-rules", userAuth, notificationH.GetPushRules)           // 71. 十.1
	app.Post("/api/v1/notification/push-targets", userAuth, notificationH.GetPushTargets)      // 72. 十.2
	app.Post("/api/v1/notification/push", userAuth, notificationH.SendPush)                    // 73. 十.3
	app.Get("/api/v1/notification/status/:messageId", userAuth, notificationH.GetPushStatus)   // 74. 十.7
	app.Get("/api/v1/notification/priority-config", userAuth, notificationH.GetPriorityConfig) // 75. 十.8

	// ---- 十一、用药计划与管理（6 路由：76.-81.）----
	app.Post("/api/v1/medication/plan", userAuth, medicationH.CreatePlan)                       // 76. 十一.1
	app.Get("/api/v1/medication/plans/:elderId", userAuth, medicationH.ListPlans)               // 77. 十一.2
	app.Put("/api/v1/medication/plan/:planId", userAuth, medicationH.UpdatePlan)                // 78. 十一.3
	app.Delete("/api/v1/medication/plan/:planId", userAuth, medicationH.DeletePlan)             // 79. 十一.4
	app.Get("/api/v1/device/:deviceId/pending-messages", deviceAuth, medicationH.GetPendingMessages) // 80. 十一.5 硬件轮询
	app.Post("/api/v1/medication/recognize", userAuth, medicationH.RecognizeMedicine)           // 81. 十一.6 豆包识别

	// ═══════════════════════════════════════════════════════════════
	// 以上共 81 条路由，序号 1.-81.，业务标注（一/二/三…十一）保留
	// 核对：Ctrl+F 搜 "// N." （N=1 到 81）
	// ═══════════════════════════════════════════════════════════════

	// 定时任务：离线检测（每 10s 扫描，90s 无心跳标记 offline）
	go func() {
		ticker := time.NewTicker(10 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			deviceSvc.ScanOfflineDevices()
		}
	}()

	log.Printf("VisionGuard backend starting on :%s", cfg.ServerPort)
	if err := app.Listen(":" + cfg.ServerPort); err != nil {
		log.Fatalf("server error: %v", err)
	}
}
