#!/bin/bash
# 调试 GET 401 问题：单独测试 POST vs GET 使用相同 JWT
set -e
BASE="http://47.94.146.53:3000"

echo "=== 1. 获取设备 JWT ==="
ACT=$(curl -s -X POST "$BASE/api/v1/device/activate" -H 'Content-Type: application/json' \
  -d '{"serialNo":"DBG_'$(date +%s)'","model":"X","mac":"11:22:33:44:55:01","hwVersion":"1","fwVersion":"1","timestamp":'$(date +%s)',"sign":"test"}')
echo "Activate: $ACT"
DID=$(echo "$ACT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['deviceId'])")
SEC=$(echo "$ACT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['deviceSecret'])")

curl -s -X POST "$BASE/api/v1/device/register" -H 'Content-Type: application/json' \
  -d "{\"deviceId\":\"$DID\",\"deviceModel\":\"X\",\"firmwareVersion\":\"1\"}" > /dev/null

CHAL=$(curl -s -X POST "$BASE/api/v1/device/challenge" -H 'Content-Type: application/json' -d "{\"deviceId\":\"$DID\"}")
CID=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['challengeId'])")
NONCE=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['nonce'])")
TS=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['timestamp'])")

SIG=$(python3 -c "print(''.join(format(ord(c)^0x4B,'02x') for c in '$SEC$NONCE$TS'))")
VER=$(curl -s -X POST "$BASE/api/v1/device/verify" -H 'Content-Type: application/json' \
  -d "{\"deviceId\":\"$DID\",\"challengeId\":\"$CID\",\"sigin\":\"$SIG\"}")
JWT=$(echo "$VER" | python3 -c "import sys,json; print(json.load(sys.stdin)['jwt'])")
echo "JWT: ${JWT:0:50}..."

echo ""
echo "=== 2. 测试 POST 同路由 (相同 JWT) ==="
echo "POST 请求:"
curl -s -X POST "$BASE/api/v1/ocr/result/latest?deviceId=$DID" \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json'
echo ""

echo ""
echo "=== 3. 测试 GET (相同 JWT) ==="
echo "GET 请求:"
curl -s -H "Authorization: Bearer $JWT" "$BASE/api/v1/ocr/result/latest?deviceId=$DID"
echo ""

echo ""
echo "=== 4. 测试 GET 不带 query ==="
echo "GET (无 deviceId):"
curl -s -H "Authorization: Bearer $JWT" "$BASE/api/v1/ocr/result/latest"
echo ""

echo ""
echo "=== 5. 检查路由注册 (Fiber) ==="
curl -s "$BASE/api/v1/healthz"
echo ""

echo ""
echo "=== 6. 测试另一个 GET + deviceAuth 路由 ==="
echo "GET /device/status (deviceAuth):"
curl -s -H "Authorization: Bearer $JWT" "$BASE/api/v1/device/status/$DID"
echo ""

echo "=== 7. 检查后端日志 ==="
docker logs visionguard-backend-1 --tail 15
echo ""
echo "=== Done ==="
