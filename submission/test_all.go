//go:build ignore

package main

import (
	"bytes"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const base = "http://localhost:3000"

type TestCase struct {
	Name string
	Fn   func() error
}

var (
	deviceJWT   string
	userJWT     string
	refreshTok  string
	elderID     string
	deviceID    string
	deviceSecret string
	alertID     string
	bindID      string
	challengeID string
	nonce       string
	timestamp   int64
)

func post(path string, body interface{}) (*http.Response, []byte, error) {
	return postWithAuth(path, body, "")
}

func postWithAuth(path string, body interface{}, token string) (*http.Response, []byte, error) {
	data, _ := json.Marshal(body)
	req, _ := http.NewRequest("POST", base+path, bytes.NewReader(data))
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, nil, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return resp, b, nil
}

func get(path string, token string) (*http.Response, []byte, error) {
	req, _ := http.NewRequest("GET", base+path, nil)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, nil, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return resp, b, nil
}

func xorEncrypt(input string) string {
	key := byte(0x4B)
	result := make([]byte, len(input))
	for i := 0; i < len(input); i++ {
		result[i] = input[i] ^ key
	}
	return hex.EncodeToString(result)
}

func main() {
	fmt.Println("=== VisionGuard 全流程测试 ===")
	fmt.Printf("Base URL: %s\n", base)
	fmt.Println()

	tests := []TestCase{
		// ========== 一、认证服务 ==========
		{
			Name: "1. 用户注册",
			Fn: func() error {
				resp, b, err := post("/api/v1/auth/register", map[string]string{
					"username": "test_user",
					"password": "TestPass123!",
					"email":    "test@example.com",
					"phone":    "13800138888",
				})
				if err != nil {
					return fmt.Errorf("register error: %v", err)
				}
				// 400 "already exists" is OK — user from previous run
				if resp.StatusCode == 400 {
					fmt.Printf("  [PASS] Register (HTTP %d, user may already exist)\n", resp.StatusCode)
					return nil
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("register failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Register (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "2. 用户登录",
			Fn: func() error {
				resp, b, err := post("/api/v1/auth/login", map[string]string{
					"username": "test_user",
					"password": "TestPass123!",
				})
				if err != nil {
					return fmt.Errorf("login error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("login failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if at, ok := respMap["access_token"].(string); ok {
					userJWT = at
				} else {
					return fmt.Errorf("no access_token in response: %s", string(b))
				}
				if rt, ok := respMap["refresh_token"].(string); ok {
					refreshTok = rt
				}
				fmt.Printf("  [PASS] Login (HTTP %d, token: %s...)\n", resp.StatusCode, userJWT[:min(len(userJWT), 20)])
				return nil
			},
		},
		{
			Name: "3. Token 刷新",
			Fn: func() error {
				resp, b, err := post("/api/v1/auth/refresh", map[string]string{
					"refresh_token": refreshTok,
				})
				if err != nil {
					return fmt.Errorf("refresh error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("refresh failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if at, ok := respMap["access_token"].(string); ok {
					userJWT = at
				}
				if rt, ok := respMap["refresh_token"].(string); ok {
					refreshTok = rt
				}
				fmt.Printf("  [PASS] Token refreshed (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 三、设备激活 ==========
		{
			Name: "4. 设备激活注册",
			Fn: func() error {
				resp, b, err := post("/api/v1/device/activate", map[string]interface{}{
					"serialNo":  "SN_TEST_" + time.Now().Format("150405"),
					"model":     "ESP32_K210",
					"mac":       "AA:BB:CC:DD:EE:FF",
					"hwVersion": "1.0",
					"fwVersion": "1.0.0",
					"timestamp": time.Now().Unix(),
					"sign":      "test_sign",
				})
				if err != nil {
					return fmt.Errorf("activate error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("activate failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if data, ok := respMap["data"].(map[string]interface{}); ok {
					if did, ok := data["deviceId"].(string); ok {
						deviceID = did
					}
					if ds, ok := data["deviceSecret"].(string); ok {
						deviceSecret = ds
					}
				}
				if deviceID == "" {
					return fmt.Errorf("no deviceId in response: %s", string(b))
				}
				fmt.Printf("  [PASS] Device activated (HTTP %d, ID: %s)\n", resp.StatusCode, deviceID)
				return nil
			},
		},
		{
			Name: "5. 设备首次接入注册",
			Fn: func() error {
				resp, b, err := post("/api/v1/device/register", map[string]string{
					"deviceId":        deviceID,
					"deviceModel":     "ESP32_K210",
					"firmwareVersion": "1.0.0",
					"ip":              "127.0.0.1",
				})
				if err != nil {
					return fmt.Errorf("register error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("device register failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Device register (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "6. Challenge-Response + 获取设备 JWT (XOR 0x4B)",
			Fn: func() error {
				// Step 1: 请求 challenge
				resp, b, err := post("/api/v1/device/challenge", map[string]string{
					"deviceId": deviceID,
				})
				if err != nil {
					return fmt.Errorf("challenge error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("challenge failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if cid, ok := respMap["challengeId"].(string); ok {
					challengeID = cid
				}
				if n, ok := respMap["nonce"].(string); ok {
					nonce = n
				}
				if ts, ok := respMap["timestamp"].(float64); ok {
					timestamp = int64(ts)
				}
				if challengeID == "" {
					return fmt.Errorf("no challengeId in response: %s", string(b))
				}

				// Step 2: 计算 XOR 签名 (模拟设备端)
				plaintext := deviceSecret + nonce + strconv.FormatInt(timestamp, 10)
				sign := xorEncrypt(plaintext)

				// Step 3: 验证签名
				resp, b, err = post("/api/v1/device/verify", map[string]string{
					"deviceId":    deviceID,
					"challengeId": challengeID,
					"sigin":       sign,
				})
				if err != nil {
					return fmt.Errorf("verify error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("verify failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				json.Unmarshal(b, &respMap)
				if jwt, ok := respMap["jwt"].(string); ok {
					deviceJWT = jwt
				}
				if deviceJWT == "" {
					return fmt.Errorf("no device JWT in response: %s", string(b))
				}
				fmt.Printf("  [PASS] Challenge-Response (HTTP %d, device JWT: %s...)\n", resp.StatusCode, deviceJWT[:min(len(deviceJWT), 20)])
				return nil
			},
		},

		// ========== 二、老人档案 ==========
		{
			Name: "7. 创建老人档案",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/elder", map[string]interface{}{
					"name":           "张奶奶",
					"gender":         "female",
					"birthDate":      "1945-03-12",
					"idCard":         "310123194503121234",
					"bloodType":      "A",
					"allergy":        "青霉素",
					"medicalHistory": "高血压、糖尿病",
					"emergencyContacts": []map[string]string{
						{"name": "张小明", "relation": "儿子", "phone": "13900000000"},
					},
				}, userJWT)
				if err != nil {
					return fmt.Errorf("create elder error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("create elder failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if data, ok := respMap["data"].(map[string]interface{}); ok {
					if eid, ok := data["elderId"].(string); ok {
						elderID = eid
					}
				}
				if elderID == "" {
					return fmt.Errorf("no elderId in response: %s", string(b))
				}
				fmt.Printf("  [PASS] Elder created (HTTP %d, ID: %s)\n", resp.StatusCode, elderID)
				return nil
			},
		},
		{
			Name: "8. 查询老人档案",
			Fn: func() error {
				resp, b, _ := get("/api/v1/elder/"+elderID, userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("elder detail failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Elder detail (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "9. 查询我监护的老人列表",
			Fn: func() error {
				resp, b, _ := get("/api/v1/elders", userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("elders list failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Elders list (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "10. 监护人仪表盘",
			Fn: func() error {
				resp, b, _ := get("/api/v1/dashboard", userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("dashboard failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Dashboard (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 五、设备绑定 ==========
		{
			Name: "11. 搜索设备",
			Fn: func() error {
				resp, b, _ := get("/api/v1/device/"+deviceID+"/search", userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("search device failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Device search (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "12. 发起绑定",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/binding/initiate", map[string]interface{}{
					"elderId":  elderID,
					"deviceId": deviceID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("bind error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("initiate binding failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if data, ok := respMap["data"].(map[string]interface{}); ok {
					if bid, ok := data["bindId"].(string); ok {
						bindID = bid
					}
				}
				if bindID == "" {
					return fmt.Errorf("no bindId in response: %s", string(b))
				}
				fmt.Printf("  [PASS] Binding initiated (HTTP %d, ID: %s)\n", resp.StatusCode, bindID)
				return nil
			},
		},

		// ========== 七、告警 ==========
		{
			Name: "13. 告警类型列表",
			Fn: func() error {
				resp, b, _ := get("/api/v1/alert/types", "")
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert types failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Alert types (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "14. 上报告警",
			Fn: func() error {
				resp, b, err := post("/api/v1/alert", map[string]interface{}{
					"deviceId":    deviceID,
					"timestamp":   time.Now().Unix(),
					"alertType":   "fall",
					"alertLevel":  "critical",
					"description": "检测到疑似摔倒事件",
					"locationLat": 31.2304,
					"locationLng": 121.4737,
					"sensorData":  `{"accelMagnitude":9.2}`,
				})
				if err != nil {
					return fmt.Errorf("alert error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("create alert failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var respMap map[string]interface{}
				json.Unmarshal(b, &respMap)
				if data, ok := respMap["data"].(map[string]interface{}); ok {
					if aid, ok := data["alertId"].(string); ok {
						alertID = aid
					}
				}
				if alertID == "" {
					return fmt.Errorf("no alertId in response: %s", string(b))
				}
				fmt.Printf("  [PASS] Alert created (HTTP %d, ID: %s)\n", resp.StatusCode, alertID)
				return nil
			},
		},
		{
			Name: "15. 告警历史查询",
			Fn: func() error {
				resp, b, _ := get("/api/v1/alerts?elderId="+elderID, userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert history failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Alert history (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "16. 告警详情",
			Fn: func() error {
				resp, b, _ := get("/api/v1/alert/"+alertID, userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert detail failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Alert detail (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 十、通知 ==========
		{
			Name: "17. 消息列表",
			Fn: func() error {
				resp, b, _ := get("/api/v1/notifications", userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("notifications failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Notifications (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 四、心跳与状态 ==========
		{
			Name: "18. 设备心跳上报 (DeviceAuth)",
			Fn: func() error {
				resp, b, _ := postWithAuth("/api/v1/device/heartbeat", map[string]interface{}{
					"deviceId":  deviceID,
					"timestamp": time.Now().Unix(),
					"battery":   85,
					"rssi":      -55,
					"location":  map[string]float64{"lat": 31.2304, "lng": 121.4737},
				}, deviceJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("heartbeat failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Heartbeat (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 八、定位 ==========
		{
			Name: "19. 最新位置查询",
			Fn: func() error {
				resp, b, _ := get("/api/v1/location/latest?deviceId="+deviceID+"&elderId="+elderID, userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("location failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Latest location (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ========== 九、OCR ==========
		{
			Name: "20. 图片上传 (OCR)",
			Fn: func() error {
				resp, b, _ := postWithAuth("/api/v1/ocr/image", map[string]interface{}{
					"elderId":       elderID,
					"deviceId":      deviceID,
					"imageCategory": "medicine",
					"fileUrl":       "https://oss.example.com/test.jpg",
					"thumbnailUrl":  "https://oss.example.com/test_thumb.jpg",
					"fileSize":      2048576,
					"width":         3024,
					"height":        4032,
					"format":        "jpeg",
				}, userJWT)
				if resp.StatusCode != 200 {
					return fmt.Errorf("OCR upload failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] OCR image upload (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "21. 健康检查",
			Fn: func() error {
				resp, b, _ := get("/api/v1/healthz", "")
				if resp.StatusCode != 200 {
					return fmt.Errorf("health check failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Health check (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
	}

	passed := 0
	failed := 0
	for _, t := range tests {
		fmt.Printf("\n--- %s ---\n", t.Name)
		err := t.Fn()
		if err != nil {
			failed++
			fmt.Printf("  [FAIL] %v\n", err)
		} else {
			passed++
		}
	}

	fmt.Printf("\n=== 结果: %d PASS, %d FAIL ===\n", passed, failed)
	if failed > 0 {
		fmt.Println("有测试失败，请检查。")
	} else {
		fmt.Println("全流程测试通过！")
	}
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func init() {
	_ = strings.TrimSpace
}
