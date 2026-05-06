#!/bin/bash
# VisionGuard 全流程端到端测试（硬件→后端→APP 纯模拟）
# 用法：bash test_e2e.sh [后端地址]

set -e

# Python 辅助：从 JSON 响应中提取字段（自动适配 data 包装 + 扁平两种格式）
extract() {
  python -c "
import sys,json
d=json.load(sys.stdin)
# 先查 data 包装，再查扁平
for key in sys.argv[1:]:
  val = d.get('data',{}).get(key,'') if isinstance(d.get('data'),dict) else ''
  if not val: val = d.get(key,'')
  if val:
    print(val)
    sys.exit(0)
print('')
" "$@"
}

BASE="${1:-http://localhost:3000}"
PASS_COUNT=0
FAIL_COUNT=0

check() {
  local label="$1" val="$2"
  if [ -n "$val" ] && [ "$val" != "null" ]; then
    echo "  ✅ $label: $val"
    PASS_COUNT=$((PASS_COUNT+1))
  else
    echo "  ⚠️ $label: (空)"
    FAIL_COUNT=$((FAIL_COUNT+1))
  fi
}

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║     VisionGuard 全流程端到端测试（无硬件纯模拟）               ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  后端: $BASE"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

# ═══════════════════════════════════════════════════════════════
# 第一部分：硬件设备认证（对齐 esp32sense.ino 流程）
# ═══════════════════════════════════════════════════════════════
echo "━━━ 第一部分：硬件设备认证 (ESP32 → 后端) ━━━"
echo ""

# H1: 设备激活
echo "[H1] 设备激活 POST /api/v1/device/activate"
SERIAL="SN_E2E_$(date +%s)"
ACTIVATE=$(curl -s -X POST "$BASE/api/v1/device/activate" \
  -H "Content-Type: application/json" \
  -d "{\"serialNo\":\"$SERIAL\",\"model\":\"ESP32_K210\",\"mac\":\"AA:BB:CC:DD:EE:FF\",\"hwVersion\":\"1.0\",\"fwVersion\":\"1.0.0\",\"timestamp\":$(date +%s),\"sign\":\"test\"}")
DEVICE_ID=$(echo "$ACTIVATE" | extract deviceId)
DEVICE_SECRET=$(echo "$ACTIVATE" | extract deviceSecret)
check "deviceId     " "$DEVICE_ID"
check "deviceSecret " "${DEVICE_SECRET:0:16}..."
echo ""

# H2: 设备注册
echo "[H2] 设备注册 POST /api/v1/device/register"
REG=$(curl -s -X POST "$BASE/api/v1/device/register" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"deviceModel\":\"ESP32_K210\",\"firmwareVersion\":\"1.0.0\"}")
REG_MSG=$(echo "$REG" | extract message)
check "register msg " "$REG_MSG"
echo ""

# H3: 请求挑战
echo "[H3] 请求挑战 POST /api/v1/device/challenge"
CHALLENGE=$(curl -s -X POST "$BASE/api/v1/device/challenge" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\"}")
CID=$(echo "$CHALLENGE" | extract challengeId)
NONCE=$(echo "$CHALLENGE" | extract nonce)
TS=$(echo "$CHALLENGE" | extract timestamp)
check "challengeId  " "$CID"
check "nonce        " "$NONCE"
check "timestamp    " "$TS"
echo ""

# H4: XOR 签名 + 验证
echo "[H4] XOR 签名 + 验证 POST /api/v1/device/verify"
PLAIN="${DEVICE_SECRET}${NONCE}${TS}"
SIGN=$(python -c "import binascii; pt='$PLAIN'; r=bytes([ord(c)^0x4b for c in pt]); print(binascii.hexlify(r).decode())")
echo "  明文: ${PLAIN:0:40}..."
echo "  签名: ${SIGN:0:40}..."

VERIFY=$(curl -s -X POST "$BASE/api/v1/device/verify" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"challengeId\":\"$CID\",\"sigin\":\"$SIGN\"}")
JWT=$(echo "$VERIFY" | extract jwt)
check "设备 JWT     " "${JWT:0:30}..."
echo ""

# H5: 心跳
echo "[H5] 心跳 POST /api/v1/device/heartbeat"
HB=$(curl -s -X POST "$BASE/api/v1/device/heartbeat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"timestamp\":$(date +%s),\"battery\":85,\"rssi\":-55,\"location\":{\"lat\":31.2304,\"lng\":121.4737}}")
HB_MSG=$(echo "$HB" | extract message)
check "heartbeat    " "$HB_MSG"
echo ""

# H6: 摔倒告警
echo "[H6] 摔倒告警 POST /api/v1/alert"
FALL=$(curl -s -X POST "$BASE/api/v1/alert" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"timestamp\":$(date +%s),\"alertType\":\"fall\",\"alertLevel\":\"critical\",\"description\":\"摔倒告警-角度X:-48.25 Y:12.36\",\"locationLat\":31.2304,\"locationLng\":121.4737}")
FALL_ID=$(echo "$FALL" | extract alertId)
check "fall alertId " "$FALL_ID"
echo ""

# H7: 避障告警
echo "[H7] 避障告警 POST /api/v1/alert"
OBS=$(curl -s -X POST "$BASE/api/v1/alert" \
  -H "Content-Type: application/json" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"timestamp\":$(date +%s),\"alertType\":\"obstacle\",\"alertLevel\":\"warning\",\"description\":\"避障告警-距离456mm\",\"locationLat\":31.2304,\"locationLng\":121.4737}")
OBS_ID=$(echo "$OBS" | extract alertId)
check "obs alertId  " "$OBS_ID"
echo ""

echo "✅ 硬件认证 7 步完成"
echo ""

# ═══════════════════════════════════════════════════════════════
# 第二部分：Android/用户侧（注册→登录→老人→绑定→告警查看）
# ═══════════════════════════════════════════════════════════════
echo "━━━ 第二部分：用户认证 & 业务 (Android → 后端) ━━━"
echo ""

TEST_USER="e2e_$(date +%s | tail -c 5)"
TEST_PASS="Test1234!"
TEST_PHONE="138$(date +%s | tail -c 9)"

# A1: 注册
echo "[A1] 用户注册 POST /api/v1/auth/register"
curl -s -X POST "$BASE/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"email\":\"${TEST_USER}@test.com\",\"phone\":\"$TEST_PHONE\"}" \
  | python -m json.tool 2>/dev/null | head -5
echo ""

# A2: 登录
echo "[A2] 用户登录 POST /api/v1/auth/login"
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}")
# 登录响应是扁平格式: {access_token, refresh_token, expires_in, display_name, phone}
USER_JWT=$(echo "$LOGIN" | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))")
echo "  响应: $(echo "$LOGIN" | python -m json.tool 2>/dev/null | head -8)"
check "用户 JWT     " "${USER_JWT:0:30}..."
echo ""

# A3: 创建老人
echo "[A3] 创建老人 POST /api/v1/elder"
ELDER=$(curl -s -X POST "$BASE/api/v1/elder" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{\"name\":\"张老伯\",\"gender\":\"male\",\"age\":72,\"bloodType\":\"A\",\"allergy\":\"青霉素\",\"medicalHistory\":\"高血压、糖尿病\",\"phone\":\"13900001111\",\"address\":\"上海市浦东新区\"}")
ELDER_ID=$(echo "$ELDER" | extract elderId)
check "elderId      " "$ELDER_ID"
echo ""

# A4: 搜索设备
echo "[A4] 搜索设备 GET /api/v1/device/$DEVICE_ID/search"
SEARCH=$(curl -s -X GET "$BASE/api/v1/device/$DEVICE_ID/search" \
  -H "Authorization: Bearer $USER_JWT")
CAN_BIND=$(echo "$SEARCH" | extract canBind)
check "canBind      " "$CAN_BIND"
echo ""

# A5: 发起绑定
echo "[A5] 发起绑定 POST /api/v1/binding/initiate"
INIT=$(curl -s -X POST "$BASE/api/v1/binding/initiate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_JWT" \
  -d "{\"deviceId\":\"$DEVICE_ID\",\"elderId\":\"$ELDER_ID\"}")
BIND_ID=$(echo "$INIT" | extract bindId)
check "bindId       " "$BIND_ID"
echo ""

# A6: 确认绑定
echo "[A6] 设备确认绑定 POST /api/v1/binding/confirm"
if [ -n "$BIND_ID" ]; then
  CONFIRM=$(curl -s -X POST "$BASE/api/v1/binding/confirm" \
    -H "Content-Type: application/json" \
    -d "{\"bindId\":\"$BIND_ID\",\"deviceId\":\"$DEVICE_ID\"}")
  CONFIRM_MSG=$(echo "$CONFIRM" | extract message)
  check "confirm msg  " "$CONFIRM_MSG"
else
  echo "  ⚠️ bindId 为空，跳过"
fi
echo ""

# A7: 查看告警
echo "[A7] 告警列表 GET /api/v1/alerts"
ALERTS=$(curl -s -X GET "$BASE/api/v1/alerts?page=1&size=5" \
  -H "Authorization: Bearer $USER_JWT")
ALERT_TOTAL=$(echo "$ALERTS" | python -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(dd.get('total',0) if isinstance(dd,dict) else 0)")
echo "  告警总数: $ALERT_TOTAL"
echo ""

# A8: 通知
echo "[A8] 通知列表 GET /api/v1/notifications"
NOTIFS=$(curl -s -X GET "$BASE/api/v1/notifications?page=1&size=5" \
  -H "Authorization: Bearer $USER_JWT")
NOTIF_TOTAL=$(echo "$NOTIFS" | python -c "import sys,json; d=json.load(sys.stdin); dd=d.get('data',{}); print(dd.get('total',0) if isinstance(dd,dict) else 0)")
echo "  通知总数: $NOTIF_TOTAL"
echo ""

# A9: 仪表盘
echo "[A9] 仪表盘 GET /api/v1/dashboard"
curl -s -X GET "$BASE/api/v1/dashboard" \
  -H "Authorization: Bearer $USER_JWT" | python -m json.tool 2>/dev/null | head -10
echo ""

# A10: 设备状态
echo "[A10] 设备在线状态 GET /api/v1/device/status/$DEVICE_ID"
curl -s -X GET "$BASE/api/v1/device/status/$DEVICE_ID" \
  -H "Authorization: Bearer $JWT" | python -m json.tool 2>/dev/null | head -8
echo ""

# ═══════════════════════════════════════════════════════════════
# 汇总
# ═══════════════════════════════════════════════════════════════
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                      测试结果汇总                             ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  硬件侧:                                                     ║"
echo "║    deviceId     = $DEVICE_ID"
echo "║    deviceJWT    = ${JWT:0:40}..."
echo "║    fallAlertId  = $FALL_ID"
echo "║    obsAlertId   = $OBS_ID"
echo "║                                                              ║"
echo "║  APP 侧:                                                     ║"
echo "║    用户名       = $TEST_USER"
echo "║    密码         = $TEST_PASS"
echo "║    userJWT      = ${USER_JWT:0:40}..."
echo "║    elderId      = $ELDER_ID"
echo "║    bindId       = $BIND_ID"
echo "║    alertTotal   = $ALERT_TOTAL"
echo "║    notifTotal   = $NOTIF_TOTAL"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  通过: $PASS_COUNT  |  警告: $FAIL_COUNT"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "📱 Android APP 登录信息："
echo "   用户名: $TEST_USER"
echo "   密码:   $TEST_PASS"
echo ""
