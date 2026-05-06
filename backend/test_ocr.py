import urllib.request, json, time

BASE = "http://47.94.146.53:3000"

def post(path, data):
    r = urllib.request.urlopen(urllib.request.Request(
        f"{BASE}{path}", data=json.dumps(data).encode(),
        headers={"Content-Type":"application/json"}))
    return json.loads(r.read())

# 1. 激活设备
act = post("/api/v1/device/activate", {
    "serialNo":"OCR_TEST_002","model":"ESP32_K210",
    "mac":"AA:BB:CC:DD:EE:FF","hwVersion":"1.0",
    "fwVersion":"1.0.0","timestamp":int(time.time()),"sign":"test"
})
dev_id = act["data"]["deviceId"]
secret = act["data"]["deviceSecret"]
print(f"激活成功 deviceId={dev_id}")

# 2. 注册
post("/api/v1/device/register", {"deviceId":dev_id,"deviceModel":"ESP32_K210","firmwareVersion":"1.0.0"})
print("注册成功")

# 3. 挑战
chal = post("/api/v1/device/challenge", {"deviceId":dev_id})
cid = chal["data"]["challengeId"]
nonce = chal["data"]["nonce"]
ts = str(chal["data"]["timestamp"])

# 4. XOR 签名
plain = secret + nonce + ts
sig = "".join(format(ord(c)^0x4B, "02x") for c in plain)

# 5. 验证获取 JWT
ver = post("/api/v1/device/verify", {"deviceId":dev_id,"challengeId":cid,"sigin":sig})
jwt = ver["data"]["jwt"]
print(f"认证成功 JWT={jwt[:30]}...")

# 6. 上传测试图片 (JSON 模式，传外部可访问的药品图片 URL)
up = post("/api/v1/ocr/image", {
    "deviceId": dev_id,
    "imageCategory": "medicine",
    "fileUrl": "https://img.zcool.cn/community/01b6d65e8c6f8ca801216518f0b2c2.jpg",
    "fileSize": 1000
})
print(f"上传: {up['message']}")

# 7. 等服务端调用豆包 (10s)
print("等待豆包识别...")
time.sleep(12)

# 8. 轮询结果
req = urllib.request.Request(
    f"{BASE}/api/v1/ocr/result/latest?deviceId={dev_id}",
    headers={"Authorization": f"Bearer {jwt}"}
)
res = json.loads(urllib.request.urlopen(req).read())
print(f"OCR结果: {json.dumps(res, ensure_ascii=False, indent=2)}")
