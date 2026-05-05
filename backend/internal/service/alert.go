package service

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
)

type AlertService struct {
	db    *gorm.DB
	redis *redis.Client
}

func NewAlertService(db *gorm.DB, redis *redis.Client) *AlertService {
	return &AlertService{db: db, redis: redis}
}

// ======================== 告警类型列表 (七.1) ========================

func (s *AlertService) GetAlertTypes() []map[string]interface{} {
	return []map[string]interface{}{
		{"type": "fall", "name": "摔倒告警", "defaultLevel": "critical", "autoPush": true},
		{"type": "obstacle", "name": "避障危险", "defaultLevel": "warning", "autoPush": true},
		{"type": "sos", "name": "紧急呼叫", "defaultLevel": "critical", "autoPush": true},
		{"type": "heart_rate_abnormal", "name": "心率异常", "defaultLevel": "warning", "autoPush": true},
		{"type": "low_battery", "name": "低电量", "defaultLevel": "info", "autoPush": false},
		{"type": "device_offline", "name": "设备离线", "defaultLevel": "warning", "autoPush": true},
		{"type": "geofence", "name": "电子围栏", "defaultLevel": "warning", "autoPush": true},
	}
}

// ======================== 告警触发与接收 (七.2) + 去重 (七.3) ========================

type AlertCreateReq struct {
	DeviceID    string  `json:"deviceId"`
	Timestamp   int64   `json:"timestamp"`
	AlertType   string  `json:"alertType"`
	AlertLevel  string  `json:"alertLevel"`
	Description string  `json:"description"`
	LocationLat float64 `json:"locationLat"`
	LocationLng float64 `json:"locationLng"`
	// ESP32-compat: 固件发嵌套 {lat,lng}，对应 CreateAlert 里兼容提取
	Location *struct {
		Lat float64 `json:"lat"`
		Lng float64 `json:"lng"`
	} `json:"location"`
	// ESP32-compat: 固件发 object，标准格式为 string
	SensorData interface{} `json:"sensorData"`
}

type AlertCreateResp struct {
	AlertID    string `json:"alertId"`
	AlertType  string `json:"alertType"`
	AlertLevel string `json:"alertLevel"`
	Status     string `json:"status"`
	CreatedAt  string `json:"createdAt"`
}

func (s *AlertService) CreateAlert(req AlertCreateReq) (*AlertCreateResp, error) {
	// 兼容嵌套 location 格式：从 Location 对象提取 lat/lng
	if req.Location != nil {
		req.LocationLat = req.Location.Lat
		req.LocationLng = req.Location.Lng
	}
	// 兼容 sensorData 为对象时序列化为 JSON 字符串
	var sensorDataStr string
	if req.SensorData != nil {
		switch v := req.SensorData.(type) {
		case string:
			sensorDataStr = v
		default:
			if b, err := json.Marshal(v); err == nil {
				sensorDataStr = string(b)
			}
		}
	}

	// 去重：同一设备、同一类型、在时间窗口内的告警视为同一事件
	dedupWindows := map[string]time.Duration{
		"fall":                120 * time.Second,
		"heart_rate_abnormal": 300 * time.Second,
	}
	window := 60 * time.Second
	if w, ok := dedupWindows[req.AlertType]; ok {
		window = w
	}

	var existing model.Alert
	if s.db.Where("device_id = ? AND alert_type = ? AND created_at > ?", req.DeviceID, req.AlertType, time.Now().Add(-window)).
		Order("created_at desc").First(&existing).Error == nil {
		// 去重：更新重复计数
		s.db.Model(&existing).Updates(map[string]interface{}{
			"duplicate_count": gorm.Expr("duplicate_count + 1"),
			"updated_at":      time.Now(),
		})
		return &AlertCreateResp{
			AlertID:    existing.AlertID,
			AlertType:  existing.AlertType,
			AlertLevel: existing.AlertLevel,
			Status:     existing.Status,
			CreatedAt:  existing.CreatedAt.Format(time.RFC3339),
		}, nil
	}

	// 获取关联的 elderID
	elderID := ""
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", req.DeviceID, "bound").First(&binding).Error == nil {
		elderID = binding.ElderID
	}

	alertID := "ALT_" + time.Now().Format("20060102") + "_" + generateRandomString(6)
	alert := model.Alert{
		AlertID:     alertID,
		DeviceID:    req.DeviceID,
		ElderID:     elderID,
		AlertType:   req.AlertType,
		AlertLevel:  req.AlertLevel,
		Status:      "pending",
		Description: req.Description,
		LocationLat: req.LocationLat,
		LocationLng: req.LocationLng,
		SensorData:  sensorDataStr,
	}

	if err := s.db.Create(&alert).Error; err != nil {
		return nil, fmt.Errorf("create alert error: %w", err)
	}

	// 生成通知
	s.createAlertNotifications(alert)

	return &AlertCreateResp{
		AlertID:   alertID,
		AlertType: alert.AlertType,
		AlertLevel: alert.AlertLevel,
		Status:    "pending",
		CreatedAt: alert.CreatedAt.Format(time.RFC3339),
	}, nil
}

func (s *AlertService) createAlertNotifications(alert model.Alert) {
	if alert.ElderID == "" {
		return
	}

	var guardians []model.Guardianship
	s.db.Where("elder_id = ?", alert.ElderID).Find(&guardians)

	for _, g := range guardians {
		isPrimary := g.Role == "primary"
		// info 级别仅推送主监护人
		if alert.AlertLevel == "info" && !isPrimary {
			continue
		}

		s.db.Create(&model.Notification{
			MessageID: "MSG_" + generateRandomString(12),
			UserID:    g.UserID,
			ElderID:   alert.ElderID,
			Type:      "alert",
			Title:     alertTypeName(alert.AlertType),
			Body:      alert.Description,
			Channel:   "app",
			Priority:  alertLevelPriority(alert.AlertLevel),
			DeepLink:  "app://alert/" + alert.AlertID,
		})
	}
}

// ======================== 告警状态管理 (七.5) ========================

func (s *AlertService) UpdateAlertStatus(alertID, action string, operatorID uint, remark string) error {
	var alert model.Alert
	if err := s.db.Where("alert_id = ?", alertID).First(&alert).Error; err != nil {
		return fmt.Errorf("alert not found")
	}

	now := time.Now()
	switch action {
	case "confirm", "confirmed":
		alert.Status = "confirmed"
		alert.ConfirmedAt = &now
		alert.ConfirmedBy = operatorID
	case "resolve", "resolved":
		alert.Status = "resolved"
		alert.ResolvedAt = &now
		alert.ResolvedBy = operatorID
		alert.Resolution = remark
	case "close", "closed":
		alert.Status = "closed"
		alert.ClosedAt = &now
	default:
		return fmt.Errorf("unknown action: %s", action)
	}

	return s.db.Save(&alert).Error
}

// ======================== 告警历史查询 (七.6) ========================

func (s *AlertService) ListAlerts(userID uint, elderID, alertType, status string, start, end time.Time, page, pageSize int) (map[string]interface{}, error) {
	type AlertSummary struct {
		AlertID        string `json:"alertId"`
		DeviceID       string `json:"deviceId"`
		ElderID        string `json:"elderId"`
		AlertType      string `json:"alertType"`
		AlertLevel     string `json:"alertLevel"`
		Status         string `json:"status"`
		Description    string `json:"description"`
		CreatedAt      string `json:"createdAt"`
		ConfirmedAt    string `json:"confirmedAt,omitempty"`
		ResolvedAt     string `json:"resolvedAt,omitempty"`
		DuplicateCount int    `json:"duplicateCount"`
	}

	query := s.db.Model(&model.Alert{})
	if elderID != "" {
		query = query.Where("elder_id = ?", elderID)
	} else {
		var elderIDs []string
		s.db.Model(&model.Guardianship{}).Where("user_id = ?", userID).Pluck("elder_id", &elderIDs)
		if len(elderIDs) == 0 {
			return map[string]interface{}{"total": int64(0), "page": page, "pageSize": pageSize, "list": []AlertSummary{}}, nil
		}
		query = query.Where("elder_id IN ?", elderIDs)
	}
	if alertType != "" {
		query = query.Where("alert_type = ?", alertType)
	}
	if status != "" {
		query = query.Where("status = ?", status)
	}
	if !start.IsZero() {
		query = query.Where("created_at >= ?", start)
	}
	if !end.IsZero() {
		query = query.Where("created_at <= ?", end)
	}

	var total int64
	query.Count(&total)

	var alerts []model.Alert
	query.Order("created_at desc").Offset((page - 1) * pageSize).Limit(pageSize).Find(&alerts)

	var list []AlertSummary
	for _, a := range alerts {
		r := AlertSummary{
			AlertID:        a.AlertID,
			DeviceID:       a.DeviceID,
			ElderID:        a.ElderID,
			AlertType:      a.AlertType,
			AlertLevel:     a.AlertLevel,
			Status:         a.Status,
			Description:    a.Description,
			CreatedAt:      a.CreatedAt.Format(time.RFC3339),
			DuplicateCount: a.DuplicateCount,
		}
		if a.ConfirmedAt != nil {
			r.ConfirmedAt = a.ConfirmedAt.Format(time.RFC3339)
		}
		if a.ResolvedAt != nil {
			r.ResolvedAt = a.ResolvedAt.Format(time.RFC3339)
		}
		list = append(list, r)
	}

	return map[string]interface{}{
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
		"list":     list,
	}, nil
}

// ======================== 告警详情 (七.7) ========================

func (s *AlertService) GetAlertDetail(alertID string) (map[string]interface{}, error) {
	var alert model.Alert
	if err := s.db.Where("alert_id = ?", alertID).First(&alert).Error; err != nil {
		return nil, fmt.Errorf("alert not found")
	}

	// 获取老人和设备信息
	elderInfo := map[string]interface{}{}
	if alert.ElderID != "" {
		var elder model.Elder
		if s.db.Where("elder_id = ?", alert.ElderID).First(&elder).Error == nil {
			elderInfo["elderId"] = elder.ElderID
			elderInfo["name"] = elder.Name
		}
	}

	deviceInfo := map[string]interface{}{"deviceId": alert.DeviceID}
	var dev model.Device
	if s.db.Where("device_id = ?", alert.DeviceID).First(&dev).Error == nil {
		deviceInfo["battery"] = dev.Battery
		deviceInfo["rssi"] = dev.RSSI
	}

	timeline := []map[string]interface{}{
		{"action": "created", "at": alert.CreatedAt.Format(time.RFC3339), "by": "system"},
	}
	if alert.ConfirmedAt != nil {
		timeline = append(timeline, map[string]interface{}{
			"action": "confirmed", "at": alert.ConfirmedAt.Format(time.RFC3339), "by": fmt.Sprintf("%d", alert.ConfirmedBy),
		})
	}
	if alert.ResolvedAt != nil {
		timeline = append(timeline, map[string]interface{}{
			"action": "resolved", "at": alert.ResolvedAt.Format(time.RFC3339), "by": fmt.Sprintf("%d", alert.ResolvedBy),
		})
	}

	return map[string]interface{}{
		"alertId":        alert.AlertID,
		"alertType":      alert.AlertType,
		"alertLevel":     alert.AlertLevel,
		"status":         alert.Status,
		"createdAt":      alert.CreatedAt.Format(time.RFC3339),
		"elder":          elderInfo,
		"device":         deviceInfo,
		"location":       map[string]interface{}{"lat": alert.LocationLat, "lng": alert.LocationLng},
		"sensorData":     alert.SensorData,
		"timeline":       timeline,
		"duplicateCount": alert.DuplicateCount,
	}, nil
}

// ======================== 告警统计 (七.9) ========================

func (s *AlertService) GetStatistics(elderID, period, date string) (map[string]interface{}, error) {
	var start, end time.Time
	now := time.Now()
	switch period {
	case "day":
		t, _ := time.Parse("2006-01-02", date)
		start = t
		end = t.Add(24 * time.Hour)
	case "week":
		t, _ := time.Parse("2006-01-02", date)
		start = t.AddDate(0, 0, -6)
		end = t.Add(24 * time.Hour)
	case "month":
		t, _ := time.Parse("2006-01-02", date)
		start = t.AddDate(0, -1, 0)
		end = t.Add(24 * time.Hour)
	default:
		start = now.AddDate(0, 0, -7)
		end = now
	}

	query := s.db.Model(&model.Alert{}).Where("created_at BETWEEN ? AND ?", start, end)
	if elderID != "" {
		query = query.Where("elder_id = ?", elderID)
	}

	var totalAlerts int64
	query.Count(&totalAlerts)

	typeStats := make(map[string]int64)
	levelStats := make(map[string]int64)
	statusStats := make(map[string]int64)

	// 按类型统计
	typeRows, _ := query.Select("alert_type, count(*)").Group("alert_type").Rows()
	if typeRows != nil {
		for typeRows.Next() {
			var t string; var c int64
			typeRows.Scan(&t, &c); typeStats[t] = c
		}
		typeRows.Close()
	}

	// 按等级统计
	levelRows, _ := query.Select("alert_level, count(*)").Group("alert_level").Rows()
	if levelRows != nil {
		for levelRows.Next() {
			var l string; var c int64
			levelRows.Scan(&l, &c); levelStats[l] = c
		}
		levelRows.Close()
	}

	// 按状态统计
	statusRows, _ := query.Select("status, count(*)").Group("status").Rows()
	if statusRows != nil {
		for statusRows.Next() {
			var s string; var c int64
			statusRows.Scan(&s, &c); statusStats[s] = c
		}
		statusRows.Close()
	}

	return map[string]interface{}{
		"period":      fmt.Sprintf("%s ~ %s", start.Format("2006-01-02"), end.Format("2006-01-02")),
		"totalAlerts": totalAlerts,
		"byType":      typeStats,
		"byLevel":     levelStats,
		"byStatus":    statusStats,
	}, nil
}

// ======================== Helpers ========================

func alertTypeName(t string) string {
	names := map[string]string{
		"fall":                 "摔倒告警",
		"obstacle":             "避障危险",
		"sos":                  "紧急呼叫",
		"heart_rate_abnormal":  "心率异常",
		"low_battery":          "低电量",
		"device_offline":       "设备离线",
		"geofence":             "电子围栏",
	}
	if n, ok := names[t]; ok {
		return n
	}
	return t
}

func alertLevelPriority(level string) string {
	priorities := map[string]string{
		"critical": "P0",
		"warning":  "P1",
		"info":     "P2",
		"notice":   "P3",
	}
	if p, ok := priorities[level]; ok {
		return p
	}
	return "P2"
}
