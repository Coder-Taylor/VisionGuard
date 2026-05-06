import urllib.request, json, time, sys

BASE = "http://47.94.146.53:3000"

def post(path, data):
    body = json.dumps(data).encode()
    req = urllib.request.Request(f"{BASE}{path}", data=body,
        headers={"Content-Type":"application/json"})
    r = urllib.request.urlopen(req)
    return json.loads(r.read())

def get(path, jwt):
    req = urllib.request.Request(f"{BASE}{path}",
        headers={"Authorization": f"Bearer {jwt}"})
    return json.loads(urllib.request.urlopen(req).read())

# 1. 激活设备
print("1. 激活设备...")
act = post("/api/v1/device/activate", {
    "serialNo":"OCR_TEST_003","model":"ESP32_K210",
    "mac":"AA:BB:CC:DD:EE:FF","hwVersion":"1.0",
    "fwVersion":"1.0.0","timestamp":int(time.time()),"sign":"test"
})
print(f"   响应: {json.dumps(act, ensure_ascii=False)}")
dev_id = act["data"]["deviceId"]
secret = act["data"]["deviceSecret"]
print(f"   deviceId={dev_id} secret={secret[:8]}...")

# 2. 注册设备
print("2. 注册设备...")
reg = post("/api/v1/device/register", {
    "deviceId":dev_id,"deviceModel":"ESP32_K210","firmwareVersion":"1.0.0"
})
print(f"   响应: {json.dumps(reg, ensure_ascii=False)}")

# 3. 请求挑战
print("3. 请求挑战...")
chal = post("/api/v1/device/challenge", {"deviceId":dev_id})
print(f"   响应: {json.dumps(chal, ensure_ascii=False)}")
# challenge 响应是扁平格式 (不是 {data:{...}})
cid = chal["challengeId"]
nonce = chal["nonce"]
ts = str(chal["timestamp"])

# 4. XOR 签名
plain = secret + nonce + ts
sig = "".join(format(ord(c)^0x4B, "02x") for c in plain)
print(f"   plain={secret}+{nonce}+{ts}")
print(f"   sig={sig}")

# 5. 验证获取 JWT
print("5. 验证挑战...")
ver = post("/api/v1/device/verify", {
    "deviceId":dev_id,"challengeId":cid,"sigin":sig
})
print(f"   响应: {json.dumps(ver, ensure_ascii=False)}")
jwt = ver["jwt"]  # verify 也是扁平格式
print(f"   JWT={jwt[:40]}...")

# 6. 上传图片 -> 触发豆包识别 (使用硬件路由 deviceAuth，需 JWT)
print("6. 上传图片 (触发豆包)...")
body = json.dumps({
    "deviceId": dev_id,
    "imageCategory": "medicine",
    "fileUrl": "https://img.zcool.cn/community/01b6d65e8c6f8ca801216518f0b2c2.jpg",
    "fileSize": 1000
}).encode()
req = urllib.request.Request(f"{BASE}/api/v1/device/ocr/image", data=body,
    headers={"Content-Type":"application/json", "Authorization":f"Bearer {jwt}"})
up = json.loads(urllib.request.urlopen(req).read())
print(f"   上传响应: {json.dumps(up, ensure_ascii=False)}")

# 7. 等待豆包处理
print("7. 等待豆包识别 (12s)...")
time.sleep(12)

# 8. 轮询 OCR 结果
print("8. 查询 OCR 结果...")
res = get("/api/v1/ocr/result/latest?deviceId=" + dev_id, jwt)
print(json.dumps(res, ensure_ascii=False, indent=2))

# 9. 如果上面返回 404，查 DB 看记录状态
if res.get("code") == 404:
    print("\n查数据库确认记录状态:")
    import subprocess
    subprocess.run([
        "docker","exec","visionguard-postgres-1",
        "psql","-U","postgres","-d","visionhub","-c",
        f"SELECT task_id,status,stage,speak_text,medicine_name,file_url,fail_reason FROM ocr_records WHERE device_id='{dev_id}' ORDER BY created_at DESC LIMIT 3;"
    ])
