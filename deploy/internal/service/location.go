package service

import (
	"context"
	"fmt"
	"time"

	"github.com/jry21223/vision-hub/backend/internal/model"
	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"
)

type LocationService struct {
	db    *gorm.DB
	redis *redis.Client
}

func NewLocationService(db *gorm.DB, redis *redis.Client) *LocationService {
	return &LocationService{db: db, redis: redis}
}

// authorizeElderAccess 校验 userID 是 elderID 的监护人。
// 若仅给出 deviceID，先解析其 bound elderID，再校验。
// 返回解析后的 (deviceID, elderID)；任意一项为空表示未授权。
func (s *LocationService) authorizeElderAccess(userID uint, deviceID, elderID string) (string, string, error) {
	if elderID == "" && deviceID != "" {
		var binding model.Binding
		if err := s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding).Error; err != nil {
			return "", "", fmt.Errorf("device not bound to any elder")
		}
		elderID = binding.ElderID
	}
	if elderID == "" {
		return "", "", fmt.Errorf("elderId or deviceId is required")
	}
	var g model.Guardianship
	if err := s.db.Where("elder_id = ? AND user_id = ?", elderID, userID).First(&g).Error; err != nil {
		return "", "", fmt.Errorf("not a guardian of this elder")
	}
	if deviceID == "" {
		var binding model.Binding
		if s.db.Where("elder_id = ? AND status = ?", elderID, "bound").First(&binding).Error == nil {
			deviceID = binding.DeviceID
		}
	}
	return deviceID, elderID, nil
}

// ======================== 最新位置展示 (八.1) ========================

func (s *LocationService) GetLatestLocation(userID uint, deviceID, elderID string) (map[string]interface{}, error) {
	deviceID, _, err := s.authorizeElderAccess(userID, deviceID, elderID)
	if err != nil {
		return nil, err
	}
	if deviceID == "" {
		return nil, fmt.Errorf("no device found for this elder")
	}

	// 从 Redis 缓存读取
	cached, err := s.redis.Get(context.Background(), "device:location:"+deviceID).Result()
	if err != nil {
		// 从 DB 查
		var loc model.Location
		if s.db.Where("device_id = ?", deviceID).Order("created_at desc").First(&loc).Error != nil {
			return nil, fmt.Errorf("no location data")
		}
		return map[string]interface{}{
			"deviceId":     deviceID,
			"lat":          loc.Latitude,
			"lng":          loc.Longitude,
			"accuracy":     loc.Accuracy,
			"createdAt":    loc.CreatedAt.Format(time.RFC3339),
			"deviceOnline": false,
			"isRealtime":   false,
		}, nil
	}

	var lat, lng float64
	fmt.Sscanf(cached, "%f,%f", &lat, &lng)

	// 检查设备在线状态
	online := false
	if status, e := s.redis.Get(context.Background(), "device:status:"+deviceID).Result(); e == nil && status == "online" {
		online = true
	}

	return map[string]interface{}{
		"deviceId":     deviceID,
		"lat":          lat,
		"lng":          lng,
		"accuracy":     10.0,
		"createdAt":    time.Now().Format(time.RFC3339),
		"deviceOnline": online,
		"isRealtime":   online,
	}, nil
}

// ======================== 历史轨迹展示 (八.2) ========================

func (s *LocationService) GetTrajectory(userID uint, deviceID, elderID string, start, end time.Time) (interface{}, error) {
	deviceID, _, err := s.authorizeElderAccess(userID, deviceID, elderID)
	if err != nil {
		return nil, err
	}
	if deviceID == "" {
		return nil, fmt.Errorf("no device found for this elder")
	}

	var locations []model.Location
	s.db.Where("device_id = ? AND created_at BETWEEN ? AND ?", deviceID, start, end).
		Order("created_at asc").Find(&locations)

	type Point struct {
		Lat       float64 `json:"lat"`
		Lng       float64 `json:"lng"`
		CreatedAt string  `json:"createdAt"`
		Accuracy  float64 `json:"accuracy"`
	}

	var list []Point
	for _, loc := range locations {
		list = append(list, Point{
			Lat:       loc.Latitude,
			Lng:       loc.Longitude,
			CreatedAt: loc.CreatedAt.Format(time.RFC3339),
			Accuracy:  loc.Accuracy,
		})
	}

	return list, nil
}

// ======================== 设备基础运行数据展示 (八.4) ========================

func (s *LocationService) GetRunningData(userID uint, deviceID, elderID string) (map[string]interface{}, error) {
	deviceID, _, err := s.authorizeElderAccess(userID, deviceID, elderID)
	if err != nil {
		return nil, err
	}
	if deviceID == "" {
		return nil, fmt.Errorf("no device found for this elder")
	}

	var dev model.Device
	if err := s.db.Where("device_id = ?", deviceID).First(&dev).Error; err != nil {
		return nil, fmt.Errorf("device not found")
	}

	status := "offline"
	if val, err := s.redis.Get(context.Background(), "device:status:"+deviceID).Result(); err == nil {
		status = val
	}

	lastHB := ""
	if dev.LastHeartbeatAt != nil {
		lastHB = dev.LastHeartbeatAt.Format(time.RFC3339)
	}

	bindTime := ""
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", deviceID, "bound").First(&binding).Error == nil && binding.BoundAt != nil {
		bindTime = binding.BoundAt.Format(time.RFC3339)
	}

	return map[string]interface{}{
		"deviceInfo": map[string]interface{}{
			"deviceId":        dev.DeviceID,
			"alias":           dev.Alias,
			"model":           dev.Model,
			"firmwareVersion": dev.FWVersion,
			"bindTime":        bindTime,
		},
		"realtimeStatus": map[string]interface{}{
			"status":        status,
			"battery":       dev.Battery,
			"rssi":          dev.RSSI,
			"lastHeartbeat": lastHB,
		},
	}, nil
}

// ======================== 电子围栏管理 (八.6) ========================

func (s *LocationService) CreateGeofence(userID uint, req GeofenceCreateReq) (*model.Geofence, error) {
	if _, _, err := s.authorizeElderAccess(userID, "", req.ElderID); err != nil {
		return nil, err
	}
	fenceID := "FENCE_" + generateRandomString(8)
	fence := model.Geofence{
		FenceID:   fenceID,
		ElderID:   req.ElderID,
		FenceName: req.FenceName,
		FenceType: req.FenceType,
		CenterLat: req.CenterLat,
		CenterLng: req.CenterLng,
		Radius:    req.Radius,
		Vertices:  req.Vertices,
		Enabled:   req.Enabled,
	}

	if err := s.db.Create(&fence).Error; err != nil {
		return nil, fmt.Errorf("create geofence error: %w", err)
	}
	return &fence, nil
}

type GeofenceCreateReq struct {
	ElderID   string  `json:"elderId"`
	FenceName string  `json:"fenceName"`
	FenceType string  `json:"fenceType"`
	CenterLat float64 `json:"centerLat"`
	CenterLng float64 `json:"centerLng"`
	Radius    float64 `json:"radius"`
	Vertices  string  `json:"vertices,omitempty"`
	Enabled   bool    `json:"enabled"`
}

func (s *LocationService) ListGeofences(userID uint, elderID string) ([]model.Geofence, error) {
	if _, _, err := s.authorizeElderAccess(userID, "", elderID); err != nil {
		return nil, err
	}
	var fences []model.Geofence
	s.db.Where("elder_id = ?", elderID).Find(&fences)
	return fences, nil
}

func (s *LocationService) DeleteGeofence(userID uint, fenceID string) error {
	var fence model.Geofence
	if err := s.db.Where("fence_id = ?", fenceID).First(&fence).Error; err != nil {
		return fmt.Errorf("fence not found")
	}
	if _, _, err := s.authorizeElderAccess(userID, "", fence.ElderID); err != nil {
		return err
	}
	return s.db.Where("fence_id = ?", fenceID).Delete(&model.Geofence{}).Error
}

// ======================== 定位与告警关联查看 (八.5) ========================

func (s *LocationService) GetAlertMarkers(userID uint, elderID string, start, end time.Time, alertTypes []string) (interface{}, error) {
	if _, _, err := s.authorizeElderAccess(userID, "", elderID); err != nil {
		return nil, err
	}

	query := s.db.Model(&model.Alert{}).Where("elder_id = ? AND created_at BETWEEN ? AND ?", elderID, start, end)
	if len(alertTypes) > 0 {
		query = query.Where("alert_type IN ?", alertTypes)
	}

	var alerts []model.Alert
	query.Find(&alerts)

	type Marker struct {
		AlertID    string  `json:"alertId"`
		AlertType  string  `json:"alertType"`
		AlertLevel string  `json:"alertLevel"`
		Lat        float64 `json:"lat"`
		Lng        float64 `json:"lng"`
		CreatedAt  string  `json:"createdAt"`
		Status     string  `json:"status"`
	}

	var markers []Marker
	for _, a := range alerts {
		markers = append(markers, Marker{
			AlertID:    a.AlertID,
			AlertType:  a.AlertType,
			AlertLevel: a.AlertLevel,
			Lat:        a.LocationLat,
			Lng:        a.LocationLng,
			CreatedAt:  a.CreatedAt.Format(time.RFC3339),
			Status:     a.Status,
		})
	}

	return markers, nil
}

// ======================== 健康数据接收 (六.1) ========================

func (s *LocationService) SaveHealthData(req HealthDataReq) (map[string]interface{}, error) {
	elderID := ""
	var binding model.Binding
	if s.db.Where("device_id = ? AND status = ?", req.DeviceID, "bound").First(&binding).Error == nil {
		elderID = binding.ElderID
	}

	dataID := "data_" + generateRandomString(12)
	hd := model.HealthData{
		DataID:   dataID,
		DeviceID: req.DeviceID,
		ElderID:  elderID,
		Type:     req.Type,
		Value:    req.Value,
		Unit:     req.Unit,
		Metadata: req.Metadata,
		Bound:    elderID != "",
	}

	if err := s.db.Create(&hd).Error; err != nil {
		return nil, fmt.Errorf("save health data error: %w", err)
	}

	return map[string]interface{}{
		"dataId":  dataID,
		"type":    req.Type,
		"elderId": elderID,
		"bound":   elderID != "",
	}, nil
}

type HealthDataReq struct {
	DeviceID  string  `json:"deviceId"`
	Timestamp int64   `json:"timestamp"`
	Type      string  `json:"type"`
	Value     float64 `json:"value"`
	Unit      string  `json:"unit"`
	Metadata  string  `json:"metadata"`
}

// ======================== 历史数据查询 (六.6) ========================

func (s *LocationService) QueryHealthData(userID uint, elderID, deviceID, dataType string, start, end time.Time, page, pageSize int) (map[string]interface{}, error) {
	if _, resolvedElder, err := s.authorizeElderAccess(userID, deviceID, elderID); err != nil {
		return nil, err
	} else {
		elderID = resolvedElder
	}
	query := s.db.Model(&model.HealthData{})
	if deviceID != "" {
		query = query.Where("device_id = ?", deviceID)
	}
	if elderID != "" {
		query = query.Where("elder_id = ?", elderID)
	}
	if dataType != "" {
		query = query.Where("type = ?", dataType)
	}
	if !start.IsZero() {
		query = query.Where("created_at >= ?", start)
	}
	if !end.IsZero() {
		query = query.Where("created_at <= ?", end)
	}

	var total int64
	query.Count(&total)

	var records []model.HealthData
	query.Order("created_at desc").Offset((page - 1) * pageSize).Limit(pageSize).Find(&records)

	return map[string]interface{}{
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
		"list":     records,
	}, nil
}
