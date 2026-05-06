# HTTP接口文档 + 安全认证规范

## 接口总览
1. **设备安全注册** `POST /device_register`
2. **设备绑定状态查询** `GET /check_bind`
3. **图片上传** `POST /upload_image`
4. **OCR识别文本获取** `GET /get_ocr_text`
5. **报警信息上传** `POST /upload_alarm`
6. **环境录音上传** `POST /upload_audio`

---

# 1. 设备安全注册接口
## 基本信息
- 请求路径：`http://172.20.10.3:8888/device_register`
- 请求方式：`POST`
- Content-Type：`application/json`
- 触发时机：ESP32 WiFi 连接成功后**仅执行一次**

## 请求体
```json
{
    "deviceId": "DEVICE_001",
    "uniqueCode": "DEVICE_2026_ESP32_K210",
    "token": "xxxxxxxxxxxx"
}
```

### 请求体字段说明
| 字段 | 类型 | 含义 |
| ---- | ---- | ---- |
| deviceId | 字符串 | 设备编号，固定配置 |
| uniqueCode | 字符串 | **设备硬件唯一码**，固定不可修改 |
| token | 字符串 | **加密校验令牌**，由设备端算法生成 |

## 安全认证算法（云端必须实现）
### 固定密钥
```
设备唯一码：DEVICE_2026_ESP32_K210
加密因子：0x4B
```

### 加密规则
1. 取 `uniqueCode` 字符串作为原始输入
2. 对**每个字符执行 ASCII 异或运算**：`字符 ^ 0x4B`
3. 拼接所有结果，生成最终 `token`

### 设备端实现代码（C++）
```cpp
String calculateAuthToken() {
  String token = "DEVICE_2026_ESP32_K210";
  String encoded = "";
  for (int i = 0; i < token.length(); i++) {
    encoded += (char)(token[i] ^ 0x4B);
  }
  return encoded;
}
```

### 云端对等实现代码（Python）
```python
def calc_token():
    unique_id = "DEVICE_2026_ESP32_K210"
    key = 0x4B
    res = ""
    for c in unique_id:
        res += chr(ord(c) ^ key)
    return res

# 校验设备上传的token
def verify_token(client_token):
    return client_token == calc_token()
```

## 响应体
- 无固定格式，设备不解析
## 备注
- 用于云端**合法性校验**，防止伪造设备接入
- 注册完成 → 设备进入等待绑定状态

---

# 2. 设备绑定状态查询接口
## 基本信息
- 请求路径：`http://172.20.10.3:8888/check_bind`
- 请求方式：`GET`
- 触发时机：未绑定状态下，**每2秒轮询一次**

## URL 请求参数
| 参数名 | 值示例 | 含义 |
|--------|--------|------|
| deviceId | DEVICE_001 | 设备唯一编号 |

## 响应规则
- **纯文本响应**
  - 已绑定：返回内容包含 `bind_success`
  - 未绑定：返回其他内容

## 备注
- 匹配成功 → 设备进入**正常工作模式**

---

# 3. 图片上传接口
## 基本信息
- 请求路径：`http://172.20.10.3:8888/upload_image`
- 请求方式：`POST`
- Content-Type：`image/jpeg`
- 触发时机：K210 图片传输完成（收到 `$IMG_END!`）

## 请求体
- JPEG 图片**二进制裸流**

## 响应体
- 无固定格式

## 备注
1. 仅**已绑定 + WiFi 在线**时上传
2. 上传完成后自动调用 `/get_ocr_text`

---

# 4. 获取OCR识别文本接口
## 基本信息
- 请求路径：`http://172.20.10.3:8888/get_ocr_text`
- 请求方式：`GET`
- 触发时机：图片上传成功后

## 请求参数
- 无

## 响应体
- 纯文本字符串
- 示例：`前方有障碍物，请绕行`

## 备注
- 云端返回文字 → ESP32 逐字播报本地 `/words/xx.wav` 语音库

---

# 5. 报警信息上传接口（摔倒/障碍物共用）
## 基本信息
- 请求路径：`http://172.20.10.3:8888/upload_alarm`
- 请求方式：`POST`
- Content-Type：`application/json`

## 5.1 摔倒报警 请求体
```json
{
    "deviceId":"DEVICE_001",
    "alarmType":"fall",
    "angleX":-52.35,
    "angleY":12.60,
    "longitude":115.123456,
    "latitude":28.123456
}
```

| 字段 | 含义 |
|------|------|
| deviceId | 设备ID |
| alarmType | fall=摔倒 |
| angleX | X轴倾角 |
| angleY | Y轴倾角 |
| longitude | 经度 |
| latitude | 纬度 |

## 5.2 障碍物报警 请求体
```json
{
    "deviceId":"DEVICE_001",
    "alarmType":"obstacle",
    "lidarDist":456,
    "longitude":115.123456,
    "latitude":28.123456
}
```

| 字段 | 含义 |
|------|------|
| alarmType | obstacle=障碍物 |
| lidarDist | 雷达距离（mm） |

## 备注
- 带锁机制，同一报警只上报一次
- 未绑定/离线不上报

---

# 6. 环境录音上传接口
## 基本信息
- 请求路径：`http://172.20.10.3:8888/upload_audio`
- 请求方式：`POST`
- Content-Type：`application/octet-stream`
- 触发：声音检测 → 静音1.2秒后自动上传

## 请求体
- 音频二进制裸流

## 备注
- 最大时长：20秒
- 仅在线绑定状态可上传

---

# 补充：ESP32 ↔ K210 串口通信协议
## 1. ESP32 → K210
| 指令 | 功能 |
|------|------|
| `$TAKE_PHOTO!` | 拍照并回传图片 |

## 2. K210 → ESP32
| 指令 | 功能 |
|------|------|
| `$LEFT!` | 障碍物在左侧 |
| `$MID!` | 障碍物在中间 |
| `$RIGHT!` | 障碍物在右侧 |
| `$IMG_START:1234!` | 开始传图，长度1234 |
| `$IMG_END!` | 传图结束 |

---

## 文档使用说明
1. 云端**必须实现安全Token算法**，否则设备无法通过认证
2. 所有接口保持路径、参数、格式完全一致
3. 串口协议为设备内部通信，不可更改