import subprocess, json, time, os, struct, zlib, random, string

BASE = "http://47.94.146.53:3000"

def http(method, path, data=None, jwt=None):
    cmd = ["curl", "-s", "-X", method, f"{BASE}{path}"]
    if data is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(data)]
    if jwt:
        cmd += ["-H", f"Authorization: Bearer {jwt}"]
    out = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return json.loads(out.stdout)
    except json.JSONDecodeError:
        print(f"   RAW: {out.stdout}")
        raise

def die(msg, resp):
    print(f"   FAIL: {msg}")
    print(f"   Response: {json.dumps(resp, ensure_ascii=False)}")
    exit(1)

# ── 0. 测试图片 ──
def make_png(path):
    def chunk(ctype, data):
        c = ctype + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b'\x00\xff\x00\x00'
    ihdr = struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f: f.write(png)

print("0. 准备测试图片...")
make_png("/tmp/ocr_test.png")
subprocess.run(["docker", "exec", "visionguard-backend-1", "mkdir", "-p", "/uploads/_test"], capture_output=True)
subprocess.run(["docker", "cp", "/tmp/ocr_test.png", "visionguard-backend-1:/uploads/_test/test.png"], capture_output=True)
img_url = f"{BASE}/uploads/_test/test.png"
check = subprocess.run(["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", img_url], capture_output=True, text=True)
if check.stdout != "200":
    print(f"   图片不可访问 HTTP {check.stdout}"); exit(1)
print(f"   图片 HTTP 200 OK")

# ── 1. 认证 ──
suffix = ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
sn = f"OCR_T_{suffix}"

print(f"1. 激活 (serialNo={sn})...")
act = http("POST", "/api/v1/device/activate", {
    "serialNo": sn, "model": "ESP32_K210",
    "mac": f"FF:FF:FF:FF:{suffix[:2]}:{suffix[2:4]}",
    "hwVersion": "1.0", "fwVersion": "1.0.0",
    "timestamp": int(time.time()), "sign": "test"
})
if "data" not in act:
    die("activate", act)
did, sec = act["data"]["deviceId"], act["data"]["deviceSecret"]
print(f"   deviceId={did}")

http("POST", "/api/v1/device/register", {"deviceId": did, "deviceModel": "ESP32_K210", "firmwareVersion": "1.0.0"})
print("2. 注册 OK")

chal = http("POST", "/api/v1/device/challenge", {"deviceId": did})
if "challengeId" not in chal:
    die("challenge", chal)
sig = "".join(format(ord(c) ^ 0x4B, "02x") for c in (sec + chal["nonce"] + str(chal["timestamp"])))
print("3. 挑战 OK")

ver = http("POST", "/api/v1/device/verify", {"deviceId": did, "challengeId": chal["challengeId"], "sigin": sig})
if "jwt" not in ver:
    die("verify", ver)
jwt = ver["jwt"]
print(f"4. JWT OK ({jwt[:30]}...)")

# ── 2. 上传 → 豆包 ──
print("5. 上传图片 (触发豆包)...")
up = http("POST", "/api/v1/device/ocr/image", {
    "deviceId": did, "imageCategory": "medicine",
    "fileUrl": img_url, "fileSize": 100
}, jwt=jwt)
print(f"   {json.dumps(up, ensure_ascii=False)}")
if up.get("code") != 0:
    die("upload", up)

# ── 3. 等待 ──
print("6. 等待豆包 (15s)...")
for i in range(15):
    time.sleep(1)
    if i % 5 == 4: print(f"   {i+1}s...")

# ── 4. 轮询 ──
print("7. 查询 OCR 结果...")
res = http("GET", f"/api/v1/ocr/result/latest?deviceId={did}", jwt=jwt)
print(json.dumps(res, ensure_ascii=False, indent=2))

# ── 5. 查 DB ──
print("\n8. DB:")
subprocess.run(["docker", "exec", "visionguard-postgres-1", "psql", "-U", "postgres", "-d", "visionhub", "-c",
    f"SELECT task_id,status,stage,speak_text,medicine_name,fail_reason FROM ocr_records WHERE device_id='{did}' ORDER BY created_at DESC LIMIT 2;"])
print("\n=== Done ===")
