# ESP32 + K210 + APP FastAPI 服务端

本服务分为两层：

1. **硬件兼容接口 V1**：严格保留当前 ESP32 已写死的 6 个 HTTP 路径，不使用 `/api` 前缀。
2. **APP/云端增强接口 V1.1**：新增用户、老人信息、在线设备、绑定/解绑、历史告警、定位、心跳等接口。

核心原则：硬件安全判断仍在本地完成，云端只做鉴权、绑定、存储、查询、OCR/LLM 增强与 APP 展示支撑。

## 本地调试启动

```bash
cd esp-32project
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-server.txt
./scripts/run_server.sh
```

默认监听：

```text
http://0.0.0.0:8888
```

ESP32 当前写死地址：

```text
http://172.20.10.3:8888
```

## Linux + PostgreSQL 部署

云服务器建议使用 Ubuntu + PostgreSQL。

```sql
CREATE DATABASE blind_assist;
CREATE USER blind_assist_user WITH PASSWORD 'change_me';
GRANT ALL PRIVILEGES ON DATABASE blind_assist TO blind_assist_user;
\c blind_assist
GRANT ALL ON SCHEMA public TO blind_assist_user;
```

正式启动：

```bash
export DATABASE_URL='postgresql://blind_assist_user:change_me@127.0.0.1:5432/blind_assist'
export REQUIRE_POSTGRES=1
export AUTO_BIND_ON_REGISTER=0
uvicorn src.server.main:app --host 0.0.0.0 --port 8888
```

`AUTO_BIND_ON_REGISTER=0` 是成品推荐值：设备注册后等待 APP 绑定。比赛快速联调可临时设为 `1`。

## 数据库表

服务启动时会自动创建或补齐：

```text
devices
app_users
device_bindings
locations
alarms
image_uploads
audio_uploads
medicine_recognitions
```

## 硬件兼容接口 V1

### POST /device_register

请求：

```json
{
  "deviceId": "DEVICE_001",
  "uniqueCode": "DEVICE_2026_ESP32_K210",
  "token": "异或加密后的字符串"
}
```

鉴权：

```text
AUTH_KEY = 0x4B
token = uniqueCode 每个字符与 0x4B 做单字节异或
```

成功：

```json
{"code":200,"msg":"ok"}
```

### GET /check_bind

请求：

```text
/check_bind?deviceId=DEVICE_001
```

已绑定返回纯文本：

```text
bind_success
```

未绑定返回：

```text
not_bind
```

说明：当前服务会把 `/check_bind` 同时作为轻量心跳，更新设备在线时间。

### POST /upload_alarm

请求：

```json
{
  "deviceId": "DEVICE_001",
  "alarmType": "fall",
  "angleX": -52.35,
  "angleY": 12.60,
  "longitude": 115.123456,
  "latitude": 28.123456
}
```

或：

```json
{
  "deviceId": "DEVICE_001",
  "alarmType": "obstacle",
  "lidarDist": 456,
  "longitude": 115.123456,
  "latitude": 28.123456
}
```

服务端会按绑定关系把 `account` 写入告警记录，供 APP 查询。

### POST /upload_image

请求：

```text
Content-Type: image/jpeg
Body: JPEG 二进制裸流
```

推荐硬件后续追加设备号：

```text
/upload_image?deviceId=DEVICE_001
```

没有 `deviceId` 且内存模式只有一台设备时，服务端会自动归属到唯一设备。

### GET /get_ocr_text

返回纯文本。兼容旧硬件无参数调用，也支持：

```text
/get_ocr_text?deviceId=DEVICE_001
```

当前 OCR/LLM 是占位流程：上传图片后返回“云端OCR和用药建议服务待接入”的提示文本，后续可替换为真实 OCR + LLM。

### POST /upload_audio

请求：

```text
Content-Type: application/octet-stream
Body: WAV 二进制裸流
```

推荐硬件后续追加：

```text
/upload_audio?deviceId=DEVICE_001
```

## 硬件可选增强接口 V1.1

### POST /device_heartbeat

如果后续 ESP32 可以改代码，建议每 30 秒上报一次：

```json
{
  "deviceId": "DEVICE_001",
  "battery": 80,
  "firmwareVersion": "1.0.0"
}
```

当前不强制要求；旧硬件可继续用 `/check_bind` 轮询作为在线依据。

### POST /upload_location

如果需要每 5 分钟定位上传：

```json
{
  "deviceId": "DEVICE_001",
  "longitude": 115.123456,
  "latitude": 28.123456,
  "satellites": 8
}
```

旧硬件不改时，定位仅随 `/upload_alarm` 入库。

## APP/云端增强接口 V1.1

### POST /app/register

```json
{
  "account": "guardian001",
  "password": "12345678",
  "phone": "13800000000",
  "elderInfo": {
    "name": "张三",
    "age": 72,
    "medicalHistory": "高血压",
    "allergy": "青霉素"
  }
}
```

### POST /app/login

```json
{
  "account": "guardian001",
  "password": "12345678"
}
```

返回：

```json
{
  "code": 200,
  "msg": "ok",
  "token": "登录令牌",
  "account": "guardian001"
}
```

### POST /app/password/reset

```json
{
  "account": "guardian001",
  "oldPassword": "12345678",
  "newPassword": "87654321"
}
```

### POST /app/elder

```json
{
  "account": "guardian001",
  "elderInfo": {
    "name": "张三",
    "age": 72,
    "medicalHistory": "高血压",
    "allergy": "青霉素"
  }
}
```

可选请求头：

```text
Authorization: Bearer 登录令牌
```

### GET /app/devices/online

返回在线设备列表。默认 5 分钟内有 `/check_bind` 或 `/device_heartbeat` 的设备视为在线。

### POST /app/device/bind

```json
{
  "account": "guardian001",
  "deviceId": "DEVICE_001"
}
```

绑定成功后，硬件继续轮询 `/check_bind?deviceId=DEVICE_001` 会拿到 `bind_success`。

### POST /app/device/unbind

```json
{
  "account": "guardian001",
  "deviceId": "DEVICE_001"
}
```

### GET /app/alarms

```text
/app/alarms?account=guardian001&limit=50
```

只返回该账号绑定设备产生的告警数据，避免数据串扰。

### GET /app/device/{device_id}/latest

查询设备最新状态、绑定账号、最近告警、最近定位、最近 OCR/LLM 文本。

## 当前还未接入的外部服务

1. OCR 真实识别服务。
2. LLM 用药建议生成服务。
3. APP 厂商推送或 HTTPS 长连接。比赛演示版可由 APP 轮询 `/app/alarms`。
4. HTTPS/Nginx/systemd/Docker 生产部署脚本。
