import urllib.request, json, time

BASE = "http://47.94.146.53:3000"

def post(path, data, jwt=None):
    headers = {"Content-Type":"application/json"}
    if jwt:
        headers["Authorization"] = f"Bearer {jwt}"
    body = json.dumps(data).encode()
    req = urllib.request.Request(f"{BASE}{path}", data=body, headers=headers, method="POST")
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        print(f"   HTTP {e.code}: {e.read().decode()}")
        raise

def get(path, jwt):
    req = urllib.request.Request(f"{BASE}{path}")
    req.add_header("Authorization", f"Bearer {jwt}")
    try:
        return json.loads(urllib.request.urlopen(req).read())
    except urllib.error.HTTPError as e:
        print(f"   GET HTTP {e.code}: {e.read().decode()}")
        raise

# 1. 激活
print("1. 激活设备...")
act = post("/api/v1/device/activate", {
    "serialNo":"OCR_TEST_004","model":"ESP32_K210",
    "mac":"AA:BB:CC:DD:EE:FF","hwVersion":"1.0",
    "fwVersion":"1.0.0","timestamp":int(time.time()),"sign":"test"
})
dev_id = act["data"]["deviceId"]
secret = act["data"]["deviceSecret"]
print(f"   deviceId={dev_id}")

# 2. 注册
post("/api/v1/device/register", {"deviceId":dev_id,"deviceModel":"ESP32_K210","firmwareVersion":"1.0.0"})
print("2. 注册 OK")

# 3. 挑战
chal = post("/api/v1/device/challenge", {"deviceId":dev_id})
cid, nonce, ts = chal["challengeId"], chal["nonce"], str(chal["timestamp"])
print(f"3. 挑战 OK challengeId={cid}")

# 4. XOR 签名
sig = "".join(format(ord(c)^0x4B, "02x") for c in (secret + nonce + ts))
print(f"4. 签名 sig={sig[:20]}...")

# 5. 验证获取 JWT
ver = post("/api/v1/device/verify", {"deviceId":dev_id,"challengeId":cid,"sigin":sig})
jwt = ver["jwt"]
print(f"5. JWT OK {jwt[:40]}...")

# 6. 上传图片 (触发豆包) — 使用硬件路由
print("6. 上传图片 (触发豆包)...")
up = post("/api/v1/device/ocr/image", {
    "deviceId": dev_id,
    "imageCategory": "medicine",
    "fileUrl": "https://img.alicdn.com/imgextra/i3/2201407209617/O1CN01L0lJ0H1MKIz3qJGqp_!!2201407209617.jpg",
    "fileSize": 5000
}, jwt=jwt)
task_id = up.get("data", {}).get("taskId", "")
print(f"   上传 OK imageId={up['data']['imageId']}")

# 7. 等待豆包处理
print("7. 等待豆包 (15s)...")
for i in range(15):
    time.sleep(1)
    if i % 5 == 4:
        print(f"   ...{i+1}s")

# 8. 轮询 OCR 结果
print("8. 查询 OCR 结果...")
res = get(f"/api/v1/ocr/result/latest?deviceId={dev_id}", jwt)
print(json.dumps(res, ensure_ascii=False, indent=2))

# 9. 如果没结果，查 DB
if res.get("code") != 0:
    print("\n查数据库:")
    import subprocess
    subprocess.run([
        "docker","exec","visionguard-postgres-1",
        "psql","-U","postgres","-d","visionhub","-c",
        f"SELECT task_id,status,stage,speak_text,medicine_name,fail_reason,left(fail_detail,80) as fail_short FROM ocr_records WHERE device_id='{dev_id}' ORDER BY created_at DESC LIMIT 3;"
    ])
