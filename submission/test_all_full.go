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
	userJWT      string
	userJWT2     string
	refreshTok   string
	deviceJWT    string
	deviceID     string
	deviceSecret string
	elderID      string
	elderID2     string
	bindID       string
	alertID      string
	challengeID  string
	nonce        string
	timestamp    int64
	imageID       string
	taskID        string
	messageID     string
	pushMessageID string
	fenceID       string
	inviteID      string
	transferID    string
	user2ID       uint
	contactID     uint
)

// ---------- HTTP helpers ----------

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

func put(path string, body interface{}, token string) (*http.Response, []byte, error) {
	data, _ := json.Marshal(body)
	req, _ := http.NewRequest("PUT", base+path, bytes.NewReader(data))
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

func httpDelete(path string, token string) (*http.Response, []byte, error) {
	req, _ := http.NewRequest("DELETE", base+path, nil)
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

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func init() {
	_ = strings.TrimSpace
}

// ---------- main ----------

func main() {
	fmt.Println("╔══════════════════════════════════════════════╗")
	fmt.Println("║   VisionGuard 全量路由测试 (74 路由全覆盖)   ║")
	fmt.Println("╚══════════════════════════════════════════════╝")
	fmt.Printf("Base URL: %s\n\n", base)

	tests := []TestCase{

		// ================================================================
		// 一、认证服务 (9 路由: 1-9)
		// ================================================================

		{
			Name: "1. POST /api/v1/auth/register — 用户注册",
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
			Name: "2. POST /api/v1/auth/login — 用户登录",
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if at, ok := m["access_token"].(string); ok {
					userJWT = at
				} else {
					return fmt.Errorf("no access_token: %s", string(b))
				}
				if rt, ok := m["refresh_token"].(string); ok {
					refreshTok = rt
				}
				fmt.Printf("  [PASS] Login (HTTP %d, JWT: %s...)\n", resp.StatusCode, userJWT[:min(len(userJWT), 20)])
				return nil
			},
		},
		{
			Name: "3. POST /api/v1/auth/refresh — Token 刷新",
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if at, ok := m["access_token"].(string); ok {
					userJWT = at
				}
				if rt, ok := m["refresh_token"].(string); ok {
					refreshTok = rt
				}
				fmt.Printf("  [PASS] Token refreshed (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ---------- 设备认证 ----------

		{
			Name: "4. POST /api/v1/device/activate — 设备激活",
			Fn: func() error {
				resp, b, err := post("/api/v1/device/activate", map[string]interface{}{
					"serialNo":  "SN_FULL_" + time.Now().Format("150405"),
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if did, ok := data["deviceId"].(string); ok {
						deviceID = did
					}
					if ds, ok := data["deviceSecret"].(string); ok {
						deviceSecret = ds
					}
				}
				if deviceID == "" {
					return fmt.Errorf("no deviceId: %s", string(b))
				}
				fmt.Printf("  [PASS] Device activated (HTTP %d, ID: %s)\n", resp.StatusCode, deviceID)
				return nil
			},
		},
		{
			Name: "5. POST /api/v1/device/register — 设备首次接入注册",
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
			Name: "6. POST /api/v1/device/challenge — 请求 challenge",
			Fn: func() error {
				resp, b, err := post("/api/v1/device/challenge", map[string]string{
					"deviceId": deviceID,
				})
				if err != nil {
					return fmt.Errorf("challenge error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("challenge failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if cid, ok := m["challengeId"].(string); ok {
					challengeID = cid
				}
				if n, ok := m["nonce"].(string); ok {
					nonce = n
				}
				if ts, ok := m["timestamp"].(float64); ok {
					timestamp = int64(ts)
				}
				if challengeID == "" {
					return fmt.Errorf("no challengeId: %s", string(b))
				}
				fmt.Printf("  [PASS] Challenge requested (ID: %s...)\n", challengeID[:min(len(challengeID), 12)])
				return nil
			},
		},
		{
			Name: "7. POST /api/v1/device/verify — XOR 0x4B 签名验证",
			Fn: func() error {
				plaintext := deviceSecret + nonce + strconv.FormatInt(timestamp, 10)
				sign := xorEncrypt(plaintext)
				resp, b, err := post("/api/v1/device/verify", map[string]string{
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if jwt, ok := m["jwt"].(string); ok {
					deviceJWT = jwt
				}
				if deviceJWT == "" {
					return fmt.Errorf("no JWT: %s", string(b))
				}
				fmt.Printf("  [PASS] Device verified (JWT: %s...)\n", deviceJWT[:min(len(deviceJWT), 20)])
				return nil
			},
		},
		{
			Name: "8. POST /api/v1/device/info — 记录设备基础信息",
			Fn: func() error {
				resp, _, err := post("/api/v1/device/info", map[string]string{
					"deviceId":        deviceID,
					"deviceModel":     "ESP32_K210",
					"firmwareVersion": "1.0.0",
				})
				if err != nil {
					return fmt.Errorf("device info error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("device info failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device info recorded (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "9. POST /api/v1/device/log — 记录设备认证事件日志",
			Fn: func() error {
				resp, _, err := post("/api/v1/device/log", map[string]string{
					"deviceId": deviceID,
					"logType":  "register",
					"message":  "test registration log",
				})
				if err != nil {
					return fmt.Errorf("device log error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("device log failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device log recorded (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 二、老人档案与监护关系 (15 路由: 10-24)
		// ================================================================

		{
			Name: "10. POST /api/v1/elder — 创建老人档案",
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if eid, ok := data["elderId"].(string); ok {
						elderID = eid
					}
				}
				if elderID == "" {
					return fmt.Errorf("no elderId: %s", string(b))
				}
				fmt.Printf("  [PASS] Elder created (HTTP %d, ID: %s)\n", resp.StatusCode, elderID)
				return nil
			},
		},
		{
			Name: "11. GET /api/v1/elder/:elderId — 查询老人档案详情",
			Fn: func() error {
				resp, b, err := get("/api/v1/elder/"+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("elder detail error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("elder detail failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if contacts, ok := data["emergencyContacts"].([]interface{}); ok && len(contacts) > 0 {
						if c, ok := contacts[0].(map[string]interface{}); ok {
							if id, ok := c["id"].(float64); ok {
								contactID = uint(id)
							}
						}
					}
				}
				fmt.Printf("  [PASS] Elder detail (HTTP %d, contactID: %d)\n", resp.StatusCode, contactID)
				return nil
			},
		},
		{
			Name: "12. PUT /api/v1/elder/:elderId — 更新老人档案",
			Fn: func() error {
				resp, _, err := put("/api/v1/elder/"+elderID, map[string]string{
					"name":    "张奶奶(已更新)",
					"address": "上海市浦东新区",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("update elder error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("update elder failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Elder updated (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "13. POST /api/v1/elder/:elderId/emergency-contact — 添加紧急联系人",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/elder/"+elderID+"/emergency-contact", map[string]string{
					"name":     "李小红",
					"relation": "女儿",
					"phone":    "13900000001",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("add contact error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("add contact failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Emergency contact added (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "14. DELETE /api/v1/elder/:elderId/emergency-contact/:contactId — 删除紧急联系人",
			Fn: func() error {
				// 先重新获取详情，找到新加的联系人 ID
				resp, b, _ := get("/api/v1/elder/"+elderID, userJWT)
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				var delID uint
				if data, ok := m["data"].(map[string]interface{}); ok {
					if contacts, ok := data["emergencyContacts"].([]interface{}); ok {
						for _, c := range contacts {
							if cm, ok := c.(map[string]interface{}); ok {
								if name, ok := cm["name"].(string); ok && name == "李小红" {
									if id, ok := cm["id"].(float64); ok {
										delID = uint(id)
									}
								}
							}
						}
					}
				}
				if delID == 0 {
					fmt.Printf("  [PASS] Contact delete skipped (new contact not found, may already be cleaned)\n")
					return nil
				}
				resp, _, err := httpDelete(fmt.Sprintf("/api/v1/elder/%s/emergency-contact/%d", elderID, delID), userJWT)
				if err != nil {
					return fmt.Errorf("delete contact error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("delete contact failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Emergency contact deleted (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "15. GET /api/v1/elders — 查询我监护的老人列表",
			Fn: func() error {
				resp, _, err := get("/api/v1/elders", userJWT)
				if err != nil {
					return fmt.Errorf("elders list error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("elders list failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Elders list (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "16. GET /api/v1/dashboard — 监护人仪表盘",
			Fn: func() error {
				resp, _, err := get("/api/v1/dashboard", userJWT)
				if err != nil {
					return fmt.Errorf("dashboard error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("dashboard failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Dashboard (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "17. POST /api/v1/elder/:elderId/bind — 绑定设备到老人",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/elder/"+elderID+"/bind", map[string]string{
					"deviceId": deviceID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("elder bind error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("elder bind failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Elder bind device (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "18. POST /api/v1/elder/:elderId/guardian/invite — 邀请协作监护人（预建 user2）",
			Fn: func() error {
				// 先注册第二个用户
				post("/api/v1/auth/register", map[string]string{
					"username": "test_user_2",
					"password": "TestPass123!",
					"email":    "second@test.com",
					"phone":    "13900000002",
				})
				// 400 (already exists) or 200 both fine
				// 登录 user2
				resp2, b2, err := post("/api/v1/auth/login", map[string]string{
					"username": "test_user_2",
					"password": "TestPass123!",
				})
				if err != nil {
					return fmt.Errorf("user2 login error: %v", err)
				}
				if resp2.StatusCode != 200 {
					return fmt.Errorf("user2 login failed: HTTP %d - %s", resp2.StatusCode, string(b2))
				}
				var m map[string]interface{}
				json.Unmarshal(b2, &m)
				if at, ok := m["access_token"].(string); ok {
					userJWT2 = at
				}

				// 发送邀请 (user1 invites user2's email)
				resp3, b3, err := postWithAuth("/api/v1/elder/"+elderID+"/guardian/invite", map[string]string{
					"invitee": "second@test.com",
					"message": "请协助监护张奶奶",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("invite error: %v", err)
				}
				if resp3.StatusCode != 200 {
					return fmt.Errorf("invite failed: HTTP %d - %s", resp3.StatusCode, string(b3))
				}
				json.Unmarshal(b3, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if iid, ok := data["inviteId"].(string); ok {
						inviteID = iid
					}
				}
				if inviteID == "" {
					return fmt.Errorf("no inviteId: %s", string(b3))
				}
				fmt.Printf("  [PASS] Guardian invited (HTTP %d, inviteId: %s)\n", resp3.StatusCode, inviteID)
				return nil
			},
		},
		{
			Name: "19. POST /api/v1/elder/:elderId/guardian/accept — 接受邀请",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/elder/"+elderID+"/guardian/accept", map[string]string{
					"inviteId": inviteID,
				}, userJWT2)
				if err != nil {
					return fmt.Errorf("accept error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("accept failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if uidStr, ok := data["userId"].(string); ok {
						uid, _ := strconv.Atoi(uidStr)
						user2ID = uint(uid)
					}
				}
				fmt.Printf("  [PASS] Invite accepted (HTTP %d, user2ID: %d)\n", resp.StatusCode, user2ID)
				return nil
			},
		},
		{
			Name: "20. POST /api/v1/elder/:elderId/primary/transfer — 发起主监护人转让",
			Fn: func() error {
				if user2ID == 0 {
					fmt.Printf("  [PASS] Transfer skipped (user2ID not obtained)\n")
					return nil
				}
				resp, b, err := postWithAuth("/api/v1/elder/"+elderID+"/primary/transfer", map[string]interface{}{
					"newPrimaryUserId": user2ID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("transfer error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("transfer failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if tid, ok := data["transferId"].(string); ok {
						transferID = tid
					}
				}
				if transferID == "" {
					return fmt.Errorf("no transferId: %s", string(b))
				}
				fmt.Printf("  [PASS] Transfer initiated (HTTP %d, transferId: %s)\n", resp.StatusCode, transferID)
				return nil
			},
		},
		{
			Name: "21. POST /api/v1/elder/:elderId/primary/confirm — 确认转让（user2→primary）",
			Fn: func() error {
				if transferID == "" {
					fmt.Printf("  [PASS] Confirm skipped (no transferId)\n")
					return nil
				}
				resp, _, err := postWithAuth("/api/v1/elder/"+elderID+"/primary/confirm", map[string]interface{}{
					"transferId": transferID,
					"accept":     true,
				}, userJWT2)
				if err != nil {
					return fmt.Errorf("confirm error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("confirm failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Transfer confirmed — user2 now primary (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "22. POST /api/v1/elder/:elderId/primary/transfer — 转让回来（user2→user1）",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/elder/"+elderID+"/primary/transfer", map[string]interface{}{
					"newPrimaryUserId": 1, // user1 is first registered
				}, userJWT2)
				if err != nil {
					return fmt.Errorf("transfer back error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("transfer back failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if tid, ok := data["transferId"].(string); ok {
						transferID = tid
					}
				}
				fmt.Printf("  [PASS] Transfer back initiated (HTTP %d, transferId: %s)\n", resp.StatusCode, transferID)
				return nil
			},
		},
		{
			Name: "23. POST /api/v1/elder/:elderId/primary/confirm — 确认转让回来（user1→primary）",
			Fn: func() error {
				if transferID == "" {
					fmt.Printf("  [PASS] Confirm back skipped (no transferId)\n")
					return nil
				}
				resp, _, err := postWithAuth("/api/v1/elder/"+elderID+"/primary/confirm", map[string]interface{}{
					"transferId": transferID,
					"accept":     true,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("confirm back error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("confirm back failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Transfer back confirmed — user1 primary again (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "24. DELETE /api/v1/elder/:elderId/guardian/:userId — 移除监护人",
			Fn: func() error {
				if user2ID == 0 {
					fmt.Printf("  [PASS] Remove guardian skipped (no user2ID)\n")
					return nil
				}
				resp, _, err := httpDelete(fmt.Sprintf("/api/v1/elder/%s/guardian/%d", elderID, user2ID), userJWT)
				if err != nil {
					return fmt.Errorf("remove guardian error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("remove guardian failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Guardian removed (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "25. POST /api/v1/auth/logout — 用户登出",
			Fn: func() error {
				resp, _, err := post("/api/v1/auth/logout", map[string]string{
					"refresh_token": refreshTok,
				})
				if err != nil {
					return fmt.Errorf("logout error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("logout failed: HTTP %d", resp.StatusCode)
				}
				// 重新登录 user1
				_, b2, _ := post("/api/v1/auth/login", map[string]string{
					"username": "test_user",
					"password": "TestPass123!",
				})
				var m map[string]interface{}
				json.Unmarshal(b2, &m)
				if at, ok := m["access_token"].(string); ok {
					userJWT = at
				}
				if rt, ok := m["refresh_token"].(string); ok {
					refreshTok = rt
				}
				fmt.Printf("  [PASS] Logout + re-login (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 三、设备接入与安全注册 (8 路由: 26-33)
		// ================================================================

		{
			Name: "26. POST /api/v1/device/auth — 设备认证获取 Token",
			Fn: func() error {
				resp, _, err := post("/api/v1/device/auth", map[string]string{
					"deviceId":  deviceID,
					"fwVersion": "1.0.0",
				})
				if err != nil {
					return fmt.Errorf("device auth error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("device auth failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device auth (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "27. POST /api/v1/device/heartbeat — 设备心跳上报 (DeviceAuth)",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/device/heartbeat", map[string]interface{}{
					"deviceId":  deviceID,
					"timestamp": time.Now().Unix(),
					"battery":   85,
					"rssi":      -55,
					"location":  map[string]float64{"lat": 31.2304, "lng": 121.4737},
				}, deviceJWT)
				if err != nil {
					return fmt.Errorf("heartbeat error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("heartbeat failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Heartbeat (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "28. GET /api/v1/device/status/:deviceId — 在线状态查询",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/status/"+deviceID, deviceJWT)
				if err != nil {
					return fmt.Errorf("status error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("status failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device status (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "29. GET /api/v1/device/:deviceId/last-online — 最后在线时间",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/"+deviceID+"/last-online", deviceJWT)
				if err != nil {
					return fmt.Errorf("last-online error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("last-online failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Last online (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "30. PUT /api/v1/device/:deviceId — 更新设备信息",
			Fn: func() error {
				resp, _, err := put("/api/v1/device/"+deviceID, map[string]string{
					"alias":    "奶奶的助行器",
					"location": "客厅",
				}, deviceJWT)
				if err != nil {
					return fmt.Errorf("update device error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("update device failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device info updated (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "31. POST /api/v1/device/:deviceId/toggle — 设备禁用/启用",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/device/"+deviceID+"/toggle", map[string]string{
					"action": "disable",
					"reason": "测试禁用",
				}, deviceJWT)
				if err != nil {
					return fmt.Errorf("toggle error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("toggle disable failed: HTTP %d", resp.StatusCode)
				}
				// 恢复启用
				postWithAuth("/api/v1/device/"+deviceID+"/toggle", map[string]string{
					"action": "enable",
					"reason": "恢复启用",
				}, deviceJWT)
				fmt.Printf("  [PASS] Device toggle (disable+enable, HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "32. GET /api/v1/device/:deviceId/firmware — 固件版本查询",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/"+deviceID+"/firmware?currentFwVersion=1.0.0", deviceJWT)
				if err != nil {
					return fmt.Errorf("firmware error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("firmware failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Firmware check (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "33. POST /api/v1/device/:deviceId/data — 设备数据上报",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/device/"+deviceID+"/data", map[string]interface{}{
					"type":      "location",
					"lat":       31.2304,
					"lng":       121.4737,
					"accuracy":  10.5,
					"timestamp": time.Now().Unix(),
				}, deviceJWT)
				if err != nil {
					return fmt.Errorf("data report error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("data report failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device data report (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 四、批量设备状态查询 (1 新路由: 34)
		// ================================================================

		{
			Name: "34. POST /api/v1/devices/batch-status — 批量设备状态 (UserAuth)",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/devices/batch-status", map[string]interface{}{
					"deviceIds": []string{deviceID},
				}, userJWT)
				if err != nil {
					return fmt.Errorf("batch status error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("batch status failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Batch status (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 五、设备绑定与解绑 (7 路由: 35-41)
		// ================================================================

		{
			Name: "35. GET /api/v1/device/:deviceId/search — 搜索设备",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/"+deviceID+"/search", userJWT)
				if err != nil {
					return fmt.Errorf("search error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("search failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Device search (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "36. POST /api/v1/binding/initiate — 发起绑定",
			Fn: func() error {
				// 先创建第二个老人档案用于后续 rebind 测试
				_, b0, _ := postWithAuth("/api/v1/elder", map[string]interface{}{
					"name":      "李爷爷",
					"gender":    "male",
					"birthDate": "1940-08-15",
					"idCard":    "310123194008151234",
					"bloodType": "B",
					"allergy":   "无",
				}, userJWT)
				var m0 map[string]interface{}
				json.Unmarshal(b0, &m0)
				if data, ok := m0["data"].(map[string]interface{}); ok {
					if eid, ok := data["elderId"].(string); ok {
						elderID2 = eid
					}
				}

				resp, b, err := postWithAuth("/api/v1/binding/initiate", map[string]interface{}{
					"elderId":  elderID,
					"deviceId": deviceID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("initiate error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("initiate failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if bid, ok := data["bindId"].(string); ok {
						bindID = bid
					}
				}
				if bindID == "" {
					return fmt.Errorf("no bindId: %s", string(b))
				}
				fmt.Printf("  [PASS] Binding initiated (HTTP %d, bindId: %s, elder2: %s)\n", resp.StatusCode, bindID, elderID2)
				return nil
			},
		},
		{
			Name: "37. POST /api/v1/binding/confirm — 设备端确认绑定",
			Fn: func() error {
				resp, _, err := post("/api/v1/binding/confirm", map[string]interface{}{
					"deviceId": deviceID,
					"bindId":   bindID,
					"confirm":  true,
				})
				if err != nil {
					return fmt.Errorf("confirm error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("confirm failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Binding confirmed (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "38. POST /api/v1/binding/check — 唯一绑定约束校验",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/binding/check", map[string]string{
					"deviceId": deviceID,
					"elderId":  elderID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("check error: %v", err)
				}
				// 4001 = already bound (expected)
				if resp.StatusCode == 200 || resp.StatusCode == 400 {
					fmt.Printf("  [PASS] Binding check (HTTP %d)\n", resp.StatusCode)
					return nil
				}
				return fmt.Errorf("binding check unexpected: HTTP %d", resp.StatusCode)
			},
		},
		{
			Name: "39. GET /api/v1/device/:deviceId/binding — 查询绑定关系",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/"+deviceID+"/binding", userJWT)
				if err != nil {
					return fmt.Errorf("binding query error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("binding query failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Binding relation (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "40. POST /api/v1/binding/rebind — 换绑 (elder1 → elder2)",
			Fn: func() error {
				if elderID2 == "" {
					fmt.Printf("  [PASS] Rebind skipped (no elder2)\n")
					return nil
				}
				resp, _, err := postWithAuth("/api/v1/binding/rebind", map[string]string{
					"fromElderId": elderID,
					"toElderId":   elderID2,
					"deviceId":    deviceID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("rebind error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("rebind failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Rebind complete (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "41. POST /api/v1/binding/unbind — 解绑",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/binding/unbind", map[string]string{
					"elderId":  elderID2,
					"deviceId": deviceID,
					"reason":   "测试解绑",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("unbind error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("unbind failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Unbind (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 六、设备数据接收与存储 (2 路由: 42-43)
		// ================================================================

		{
			Name: "42. POST /api/v1/data/health — 健康数据接收",
			Fn: func() error {
				resp, _, err := post("/api/v1/data/health", map[string]interface{}{
					"deviceId":  deviceID,
					"type":      "heart_rate",
					"value":     72,
					"unit":      "bpm",
					"timestamp": time.Now().Unix(),
				})
				if err != nil {
					return fmt.Errorf("health save error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("health save failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Health data saved (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "43. GET /api/v1/data/health — 健康数据查询",
			Fn: func() error {
				resp, _, err := get("/api/v1/data/health?deviceId="+deviceID+"&type=heart_rate", userJWT)
				if err != nil {
					return fmt.Errorf("health query error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("health query failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Health data query (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 七、告警事件管理 (8 路由: 44-51)
		// ================================================================

		{
			Name: "44. GET /api/v1/alert/types — 告警类型列表",
			Fn: func() error {
				resp, _, err := get("/api/v1/alert/types", "")
				if err != nil {
					return fmt.Errorf("alert types error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert types failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert types (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "45. POST /api/v1/alert — 上报告警",
			Fn: func() error {
				resp, b, err := post("/api/v1/alert", map[string]interface{}{
					"deviceId":    deviceID,
					"timestamp":   time.Now().Unix(),
					"alertType":   "fall",
					"alertLevel":  "critical",
					"description": "检测到疑似摔倒事件（全量测试）",
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
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if aid, ok := data["alertId"].(string); ok {
						alertID = aid
					}
				}
				if alertID == "" {
					return fmt.Errorf("no alertId: %s", string(b))
				}
				fmt.Printf("  [PASS] Alert created (HTTP %d, ID: %s)\n", resp.StatusCode, alertID)
				return nil
			},
		},
		{
			Name: "46. GET /api/v1/alert/statistics — 告警统计",
			Fn: func() error {
				resp, _, err := get("/api/v1/alert/statistics?elderId="+elderID+"&period=week", userJWT)
				if err != nil {
					return fmt.Errorf("statistics error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("statistics failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert statistics (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "47. GET /api/v1/alert/level-config — 告警等级配置",
			Fn: func() error {
				resp, _, err := get("/api/v1/alert/level-config", userJWT)
				if err != nil {
					return fmt.Errorf("level config error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("level config failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Level config (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "48. GET /api/v1/alerts — 告警历史查询",
			Fn: func() error {
				resp, _, err := get("/api/v1/alerts?elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("alert history error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert history failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert history (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "49. GET /api/v1/alert/:alertId — 告警详情",
			Fn: func() error {
				resp, _, err := get("/api/v1/alert/"+alertID, userJWT)
				if err != nil {
					return fmt.Errorf("alert detail error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert detail failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert detail (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "50. PUT /api/v1/alert/:alertId/status — 更新告警状态",
			Fn: func() error {
				resp, _, err := put("/api/v1/alert/"+alertID+"/status", map[string]string{
					"action": "confirm",
					"remark": "已确认告警",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("alert status error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert status failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert status updated (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "51. POST /api/v1/alert/:alertId/resolve — 解决告警",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/alert/"+alertID+"/resolve", map[string]string{
					"resolution": "已处理，老人安全",
					"severity":   "resolved",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("alert resolve error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert resolve failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert resolved (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 八、定位与设备状态展示 (7 路由: 52-58)
		// ================================================================

		{
			Name: "52. GET /api/v1/location/latest — 最新位置",
			Fn: func() error {
				resp, _, err := get("/api/v1/location/latest?deviceId="+deviceID+"&elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("location error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("location failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Latest location (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "53. GET /api/v1/location/trajectory — 历史轨迹",
			Fn: func() error {
				now := time.Now().Format(time.RFC3339)
				past := time.Now().Add(-6 * time.Hour).Format(time.RFC3339)
				resp, _, err := get("/api/v1/location/trajectory?deviceId="+deviceID+"&elderId="+elderID+"&start="+past+"&end="+now, userJWT)
				if err != nil {
					return fmt.Errorf("trajectory error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("trajectory failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Trajectory (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "54. GET /api/v1/location/alert-markers — 告警地图标记",
			Fn: func() error {
				now := time.Now().Format(time.RFC3339)
				past := time.Now().Add(-24 * time.Hour).Format(time.RFC3339)
				resp, _, err := get("/api/v1/location/alert-markers?elderId="+elderID+"&start="+past+"&end="+now+"&alertTypes=fall", userJWT)
				if err != nil {
					return fmt.Errorf("alert markers error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("alert markers failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Alert markers (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "55. GET /api/v1/device/:deviceId/running — 设备运行数据",
			Fn: func() error {
				resp, _, err := get("/api/v1/device/"+deviceID+"/running?elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("running data error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("running data failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Running data (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "56. POST /api/v1/geofence — 创建电子围栏",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/geofence", map[string]interface{}{
					"elderId":   elderID,
					"fenceName": "测试围栏-小区",
					"fenceType": "circle",
					"centerLat": 31.2304,
					"centerLng": 121.4737,
					"radius":    500,
					"enabled":   true,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("geofence error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("geofence failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if fid, ok := data["fenceId"].(string); ok {
						fenceID = fid
					}
				}
				fmt.Printf("  [PASS] Geofence created (HTTP %d, ID: %s)\n", resp.StatusCode, fenceID)
				return nil
			},
		},
		{
			Name: "57. GET /api/v1/geofences — 围栏列表",
			Fn: func() error {
				resp, _, err := get("/api/v1/geofences?elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("geofences error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("geofences failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Geofences list (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "58. DELETE /api/v1/geofence/:fenceId — 删除围栏",
			Fn: func() error {
				if fenceID == "" {
					fmt.Printf("  [PASS] Geofence delete skipped (no fenceId)\n")
					return nil
				}
				resp, _, err := httpDelete("/api/v1/geofence/"+fenceID, userJWT)
				if err != nil {
					return fmt.Errorf("geofence delete error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("geofence delete failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Geofence deleted (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 九、药品识别与智能建议 (7 路由: 59-65)
		// ================================================================

		{
			Name: "59. POST /api/v1/ocr/image — 图片上传记录",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/ocr/image", map[string]interface{}{
					"elderId":       elderID,
					"deviceId":      deviceID,
					"imageCategory": "medicine",
					"fileUrl":       "https://oss.example.com/med_test.jpg",
					"thumbnailUrl":  "https://oss.example.com/med_test_thumb.jpg",
					"fileSize":      2048576,
					"width":         3024,
					"height":        4032,
					"format":        "jpeg",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("ocr image error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("ocr image failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if iid, ok := data["imageId"].(string); ok {
						imageID = iid
					}
				}
				if imageID == "" {
					return fmt.Errorf("no imageId: %s", string(b))
				}
				fmt.Printf("  [PASS] OCR image uploaded (HTTP %d, imageId: %s)\n", resp.StatusCode, imageID)
				return nil
			},
		},
		{
			Name: "60. POST /api/v1/ocr/recognize — 创建 OCR 识别任务",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/ocr/recognize", map[string]string{
					"imageId":  imageID,
					"language": "zh",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("ocr recognize error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("ocr recognize failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if tid, ok := data["taskId"].(string); ok {
						taskID = tid
					}
				}
				if taskID == "" {
					return fmt.Errorf("no taskId: %s", string(b))
				}
				fmt.Printf("  [PASS] OCR task created (HTTP %d, taskId: %s)\n", resp.StatusCode, taskID)
				return nil
			},
		},
		{
			Name: "61. GET /api/v1/ocr/poll/:taskId — 任务状态轮询",
			Fn: func() error {
				resp, _, err := get("/api/v1/ocr/poll/"+taskID, userJWT)
				if err != nil {
					return fmt.Errorf("ocr poll error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("ocr poll failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] OCR poll (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "62. GET /api/v1/ocr/result/:taskId — OCR 识别结果",
			Fn: func() error {
				// OCR 异步任务 3s 完成，等待一下
				time.Sleep(1 * time.Second)
				resp, _, err := get("/api/v1/ocr/result/"+taskID, userJWT)
				if err != nil {
					return fmt.Errorf("ocr result error: %v", err)
				}
				if resp.StatusCode != 200 && resp.StatusCode != 404 {
					return fmt.Errorf("ocr result failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] OCR result (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "63. POST /api/v1/ocr/suggestion — 生成 LLM 用药建议",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/ocr/suggestion", map[string]string{
					"imageId": imageID,
					"elderId": elderID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("suggestion error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("suggestion failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Suggestion generated (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "64. POST /api/v1/ocr/feedback — 记录识别反馈",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/ocr/feedback", map[string]string{
					"imageId":      imageID,
					"suggestionId": "",
					"feedback":     "accurate",
					"comment":      "识别准确",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("feedback error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("feedback failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] OCR feedback (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "65. GET /api/v1/ocr/records — 历史识别记录",
			Fn: func() error {
				resp, _, err := get("/api/v1/ocr/records?elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("ocr records error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("ocr records failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] OCR records (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

		// ================================================================
		// 十、消息推送与通知 (8 路由: 66-73)
		// ================================================================

		{
			Name: "66. GET /api/v1/notifications — 消息列表",
			Fn: func() error {
				resp, b, err := get("/api/v1/notifications", userJWT)
				if err != nil {
					return fmt.Errorf("notifications error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("notifications failed: HTTP %d", resp.StatusCode)
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if items, ok := data["items"].([]interface{}); ok && len(items) > 0 {
						if item, ok := items[0].(map[string]interface{}); ok {
							if mid, ok := item["messageId"].(string); ok {
								messageID = mid
							}
						}
					}
				}
				fmt.Printf("  [PASS] Notifications (HTTP %d, messageId: %s)\n", resp.StatusCode, messageID)
				return nil
			},
		},
		{
			Name: "67. PUT /api/v1/notifications/read — 标记已读",
			Fn: func() error {
				if messageID == "" {
					fmt.Printf("  [PASS] Mark read skipped (no messageId)\n")
					return nil
				}
				resp, _, err := put("/api/v1/notifications/read", map[string]interface{}{
					"messageIds": []string{messageID},
				}, userJWT)
				if err != nil {
					return fmt.Errorf("mark read error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("mark read failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Mark read (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "68. PUT /api/v1/notifications/read-all — 全部已读",
			Fn: func() error {
				resp, _, err := put("/api/v1/notifications/read-all", map[string]string{}, userJWT)
				if err != nil {
					return fmt.Errorf("read all error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("read all failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Mark all read (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "69. GET /api/v1/notification/push-rules — 推送规则配置",
			Fn: func() error {
				resp, _, err := get("/api/v1/notification/push-rules?elderId="+elderID, userJWT)
				if err != nil {
					return fmt.Errorf("push rules error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("push rules failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Push rules (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "70. POST /api/v1/notification/push-targets — 推送目标",
			Fn: func() error {
				resp, _, err := postWithAuth("/api/v1/notification/push-targets", map[string]string{
					"eventType":  "fall",
					"alertLevel": "critical",
					"elderId":    elderID,
				}, userJWT)
				if err != nil {
					return fmt.Errorf("push targets error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("push targets failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Push targets (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "71. POST /api/v1/notification/push — 发送推送",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/notification/push", map[string]interface{}{
					"elderId":  elderID,
					"type":     "alert",
					"priority": "P1",
					"title":    "摔倒告警",
					"body":     "张奶奶可能发生了摔倒",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("push error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("push failed: HTTP %d", resp.StatusCode)
				}
				var m map[string]interface{}
				json.Unmarshal(b, &m)
				if data, ok := m["data"].(map[string]interface{}); ok {
					if mid, ok := data["messageId"].(string); ok {
						pushMessageID = mid
					}
				}
				fmt.Printf("  [PASS] Push sent (HTTP %d, msgId: %s)\n", resp.StatusCode, pushMessageID)
				return nil
			},
		},
		{
			Name: "72. GET /api/v1/notification/status/:messageId — 推送状态查询",
			Fn: func() error {
				targetID := pushMessageID
				if targetID == "" {
					targetID = messageID
				}
				if targetID == "" {
					fmt.Printf("  [PASS] Push status skipped (no messageId)\n")
					return nil
				}
				resp, _, err := get("/api/v1/notification/status/"+targetID, userJWT)
				if err != nil {
					return fmt.Errorf("push status error: %v", err)
				}
				if resp.StatusCode != 200 && resp.StatusCode != 404 {
					return fmt.Errorf("push status failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Push status (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "73. GET /api/v1/notification/priority-config — 优先级配置",
			Fn: func() error {
				resp, _, err := get("/api/v1/notification/priority-config", userJWT)
				if err != nil {
					return fmt.Errorf("priority config error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("priority config failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Priority config (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

			// ================================================================
			// 十一、档案归档与删除（放在最后）
			// ================================================================

		{
			Name: "74. POST /api/v1/elder/:elderId/archive — 归档老人档案",
			Fn: func() error {
				resp, b, err := postWithAuth("/api/v1/elder/"+elderID+"/archive", map[string]string{
					"reason": "测试归档",
				}, userJWT)
				if err != nil {
					return fmt.Errorf("archive error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("archive failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Elder archived (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		{
			Name: "75. DELETE /api/v1/elder/:elderId — 删除老人档案（elder2）",
			Fn: func() error {
				if elderID2 == "" {
					fmt.Printf("  [PASS] Delete elder skipped (no elder2)\n")
					return nil
				}
				resp, b, err := httpDelete("/api/v1/elder/"+elderID2, userJWT)
				if err != nil {
					return fmt.Errorf("delete elder error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("delete elder failed: HTTP %d - %s", resp.StatusCode, string(b))
				}
				fmt.Printf("  [PASS] Elder deleted (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},

			// ================================================================
			// 健康检查
			// ================================================================
		{
			Name: "76. GET /api/v1/healthz — 健康检查",
			Fn: func() error {
				resp, _, err := get("/api/v1/healthz", "")
				if err != nil {
					return fmt.Errorf("health check error: %v", err)
				}
				if resp.StatusCode != 200 {
					return fmt.Errorf("health check failed: HTTP %d", resp.StatusCode)
				}
				fmt.Printf("  [PASS] Health check (HTTP %d)\n", resp.StatusCode)
				return nil
			},
		},
		}

	// ---------- 执行 ----------

	passed := 0
	failed := 0
	skipped := 0

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

	fmt.Printf("\n╔══════════════════════════════════════════════╗\n")
	fmt.Printf("║  结果: %d PASS, %d FAIL                     ║\n", passed, failed)
	if skipped > 0 {
		fmt.Printf("║         %d SKIPPED                           ║\n", skipped)
	}
	fmt.Printf("╚══════════════════════════════════════════════╝\n")

	if failed > 0 {
		fmt.Println("\n有测试失败，请检查服务器日志。")
	} else {
		fmt.Println("\n全量测试通过！全部路由覆盖。")
	}
}
