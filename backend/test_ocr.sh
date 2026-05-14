#!/bin/bash
set -e
BASE="http://47.94.146.53:3000"
echo "=== VisionGuard OCR 全链路测试 ==="

# 1. 激活
echo "1. 激活设备..."
ACT=$(curl -s -X POST "$BASE/api/v1/device/activate" -H 'Content-Type: application/json' -d '{"serialNo":"OCR_TEST_SHELL","model":"ESP32_K210","mac":"FF:FF:FF:FF:FF:FF","hwVersion":"1.0","fwVersion":"1.0.0","timestamp":1700000000,"sign":"test"}')
echo "   $ACT"
DID=$(echo "$ACT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['deviceId'])")
SEC=$(echo "$ACT" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['deviceSecret'])")
echo "   deviceId=$DID"

# 2. 注册
echo "2. 注册..."
curl -s -X POST "$BASE/api/v1/device/register" -H 'Content-Type: application/json' -d "{\"deviceId\":\"$DID\",\"deviceModel\":\"ESP32_K210\",\"firmwareVersion\":\"1.0.0\"}"
echo ""

# 3. 挑战
echo "3. 挑战..."
CHAL=$(curl -s -X POST "$BASE/api/v1/device/challenge" -H 'Content-Type: application/json' -d "{\"deviceId\":\"$DID\"}")
echo "   $CHAL"
CID=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['challengeId'])")
NONCE=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['nonce'])")
TS=$(echo "$CHAL" | python3 -c "import sys,json; print(json.load(sys.stdin)['timestamp'])")

# 4. XOR 签名
SIG=$(python3 -c "print(''.join(format(ord(c)^0x4B,'02x') for c in '$SEC$NONCE$TS'))")
echo "4. 签名=$SIG"

# 5. 验证
echo "5. 验证..."
VER=$(curl -s -X POST "$BASE/api/v1/device/verify" -H 'Content-Type: application/json' -d "{\"deviceId\":\"$DID\",\"challengeId\":\"$CID\",\"sigin\":\"$SIG\"}")
echo "   $VER"
JWT=$(echo "$VER" | python3 -c "import sys,json; print(json.load(sys.stdin)['jwt'])")
echo "   JWT=${JWT:0:50}..."

# 6. 创建测试图片（生成一个 1x1 JPEG 放在 static 目录下）
echo "6. 创建测试图片..."
mkdir -p /opt/visionguard/uploads/test/images
python3 -c "
import base64, struct
# 最小的 valid JPEG (1x1 红色像素)
jpeg = base64.b64decode('/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYI4RVJ1JicoKSorKztMTU5OXmR1hFSUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/9oADAMBAAIRAxEAPwA=')
with open('/opt/visionguard/uploads/test/images/test.jpg','wb') as f:
    f.write(jpeg)
print('test image created')
"
IMG_URL="$BASE/uploads/test/images/test.jpg"
echo "   图片URL: $IMG_URL"

# 7. 上传图片 -> 触发豆包
echo "7. 上传图片 (触发豆包)..."
UP=$(curl -s -X POST "$BASE/api/v1/device/ocr/image" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $JWT" \
  -d "{\"deviceId\":\"$DID\",\"imageCategory\":\"medicine\",\"fileUrl\":\"$IMG_URL\",\"fileSize\":1000}")
echo "   $UP"

# 8. 等待豆包
echo "8. 等待豆包识别 (12s)..."
sleep 12

# 9. 轮询 - 用 curl
echo "9. 查询 OCR 结果..."
RESULT=$(curl -s -H "Authorization: Bearer $JWT" "$BASE/api/v1/ocr/result/latest?deviceId=$DID")
echo "   $RESULT"

# 10. 查数据库
echo "10. 数据库记录:"
docker exec visionguard-postgres-1 psql -U postgres -d visionhub -c "SELECT task_id,status,stage,speak_text,medicine_name,fail_reason FROM ocr_records WHERE device_id='$DID' ORDER BY created_at DESC LIMIT 3;"

echo ""
echo "=== 测试完成 ==="
