#include <WiFi.h>
#include <HTTPClient.h>
#include <HardwareSerial.h>
#include "Arduino.h"
#include "Audio.h"
#include "SD.h"
#include "SPI.h"
#include <Preferences.h>
#include "wordmap.h"

#define DEVICE_SERIAL_NO "SN_TEST_009"
#define DEVICE_MODEL     "ESP32_K210"
#define HW_VERSION       "1.0"
#define FW_VERSION       "1.0.0"
#define AUTH_KEY         0x4B

#define I2S_DOUT 2
#define I2S_BCLK 3
#define I2S_LRC  4
#define BTN_A 1
#define SD_CS 21

const char* BASE_URL = "http://47.94.146.53/vg";
const char* ssid     = "wuiPhone 16";
const char* password = "12345ssDLH";

const float FIXED_LAT = 34.8127;
const float FIXED_LNG = 114.3626;

Preferences prefs;
String g_deviceId = "";
String g_deviceSecret = "";
String g_jwt = "";
unsigned long g_jwtExpiry = 0;

Audio audio;

bool wifiOnline = false;
unsigned long onlineTime = 0;
int demoStep = 0;
unsigned long lastHb = 0;
unsigned long lastSerialActivity = 0;
bool demoCompleted = false;

// 音频播放控制
unsigned long playEndTime = 0;
const int PLAY_DURATION = 5000;

// OCR状态（独立于演示流程）
bool ocrInProgress = false;
int ocrPollCount = 0;
unsigned long lastOcrPoll = 0;

// JSON提取
String extractJsonValue(String json, String key) {
  String searchKey = "\"" + key + "\":\"";
  int start = json.indexOf(searchKey);
  if (start == -1) return "";
  start += searchKey.length();
  int end = json.indexOf("\"", start);
  if (end == -1) return "";
  return json.substring(start, end);
}

String httpPost(String path, String body, bool useJwt = false) {
  HTTPClient h;
  h.begin(String(BASE_URL) + path);
  h.setTimeout(5000);
  h.addHeader("Content-Type", "application/json");
  if (useJwt && g_jwt.length() > 0) h.addHeader("Authorization", "Bearer " + g_jwt);
  int httpCode = h.POST(body);
  String r = "";
  if (httpCode > 0) r = h.getString();
  h.end();
  return r;
}

String httpGet(String path, bool useJwt = false) {
  HTTPClient h;
  h.begin(String(BASE_URL) + path);
  h.setTimeout(5000);
  if (useJwt && g_jwt.length() > 0) h.addHeader("Authorization", "Bearer " + g_jwt);
  int httpCode = h.GET();
  String r = "";
  if (httpCode == 200) r = h.getString();
  h.end();
  return r;
}

String calcSign(String deviceSecret, String nonce, long timestamp) {
  String plaintext = deviceSecret + nonce + String(timestamp);
  String result = "";
  for (int i = 0; i < plaintext.length(); i++) {
    char c = plaintext[i] ^ AUTH_KEY;
    char hex[3]; sprintf(hex, "%02x", (unsigned char)c);
    result += hex;
  }
  return result;
}

bool deviceActivate() {
  String mac = WiFi.macAddress();
  String body = "{\"serialNo\":\"" + String(DEVICE_SERIAL_NO) + "\",\"model\":\"" + String(DEVICE_MODEL) +
                "\",\"mac\":\"" + mac + "\",\"hwVersion\":\"" + String(HW_VERSION) +
                "\",\"fwVersion\":\"" + String(FW_VERSION) + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"sign\":\"test\"}";
  String resp = httpPost("/api/v1/device/activate", body);
  g_deviceSecret = extractJsonValue(resp, "deviceSecret");
  g_deviceId = extractJsonValue(resp, "deviceId");
  if (g_deviceId.length() == 0) return false;
  prefs.putString("devId", g_deviceId);
  prefs.putString("devSecret", g_deviceSecret);
  return true;
}

bool deviceRegister() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"deviceModel\":\"" + String(DEVICE_MODEL) +
                "\",\"firmwareVersion\":\"" + String(FW_VERSION) + "\"}";
  return httpPost("/api/v1/device/register", body).indexOf("\"code\":0") != -1;
}

bool deviceChallenge() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\"}";
  String resp = httpPost("/api/v1/device/challenge", body);
  if (resp.indexOf("challengeId") == -1) return false;
  String challengeId = extractJsonValue(resp, "challengeId");
  String nonce = extractJsonValue(resp, "nonce");
  int tsPos = resp.indexOf("\"timestamp\":") + 12;
  int tsEnd = resp.indexOf(",", tsPos);
  if (tsEnd == -1) tsEnd = resp.indexOf("}", tsPos);
  long timestamp = resp.substring(tsPos, tsEnd).toInt();
  String sign = calcSign(g_deviceSecret, nonce, timestamp);
  body = "{\"deviceId\":\"" + g_deviceId + "\",\"challengeId\":\"" + challengeId + "\",\"sigin\":\"" + sign + "\"}";
  resp = httpPost("/api/v1/device/verify", body);
  g_jwt = extractJsonValue(resp, "jwt");
  if (g_jwt.length() > 0) {
    g_jwtExpiry = millis() + 23 * 3600 * 1000;
    prefs.putString("jwt", g_jwt);
    return true;
  }
  return false;
}

void sendHeartbeat() {
  if (!wifiOnline || g_deviceId.length() == 0) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"battery\":85,\"rssi\":" + String(WiFi.RSSI()) +
                ",\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}}";
  httpPost("/api/v1/device/heartbeat", body, true);
  Serial.println("[心跳]");
}

void uploadFallAlarm(float x, float y) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"fall\",\"alertLevel\":\"critical\",\"description\":\"检测到疑似摔倒事件\"," +
                "\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}," +
                "\"sensorData\":{\"angleX\":" + String(x, 2) + ",\"angleY\":" + String(y, 2) + ",\"accelMagnitude\":9.2}}";
  Serial.println("[摔倒告警] " + httpPost("/api/v1/alert", body, true));
}

void uploadObstacleAlarm(int d) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"obstacle\",\"alertLevel\":\"warning\",\"description\":\"检测到障碍物\"," +
                "\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}," +
                "\"sensorData\":{\"lidarDist\":" + String(d) + "}}";
  Serial.println("[避障告警] " + httpPost("/api/v1/alert", body, true));
}

// 音频播放
void playSound(const char* f) {
  if (millis() < playEndTime) return;
  audio.connecttoFS(SD, f);
  playEndTime = millis() + PLAY_DURATION;
}

void playSingleWord(String w) {
  if (millis() < playEndTime) return;
  String p = charToPath(w.c_str());
  if (p == "" || !SD.exists(p)) return;
  audio.connecttoFS(SD, p.c_str());
  playEndTime = millis() + 400;
}

void textToVoice(String text) {
  const char* s = text.c_str();
  int len = strlen(s);
  for (int i = 0; i < len; ) {
    char ch[4] = {0};
    int b = 1;
    if ((s[i] & 0x80) == 0) b = 1;
    else if ((s[i] & 0xE0) == 0xC0) b = 2;
    else if ((s[i] & 0xF0) == 0xE0) b = 3;
    else b = 4;
    strncpy(ch, s + i, b);
    i += b;
    // 跳过中文标点
    if (strcmp(ch, "，") == 0 || strcmp(ch, "。") == 0 || strcmp(ch, "！") == 0 ||
        strcmp(ch, "、") == 0 || strcmp(ch, "？") == 0 || strcmp(ch, "；") == 0 ||
        strcmp(ch, "：") == 0 || strcmp(ch, "（") == 0 || strcmp(ch, "）") == 0 ||
        strcmp(ch, "“") == 0 || strcmp(ch, "”") == 0 || strcmp(ch, "—") == 0) { 
      delay(600); continue; 
    }
    if (ch[0] == ' ' || ch[0] == ',' || ch[0] == '.' || ch[0] == ':' || 
        ch[0] == ';' || ch[0] == '!' || ch[0] == '?') continue;
    playSingleWord(String(ch));
    delay(420);
  }
}

// OCR上传
String httpPostMultipartImage(const uint8_t* jpegData, int jpegLen, String deviceId) {
  if (!wifiOnline || jpegData == nullptr || jpegLen == 0) return "";

  String boundary = "----VisionGuard" + String(millis());
  String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n";
  String foot = "\r\n";
  String devPart = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"deviceId\"\r\n\r\n" + deviceId + "\r\n";
  String catPart = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\nmedicine\r\n";
  String endPart = "--" + boundary + "--\r\n";

  int bodyLen = head.length() + jpegLen + foot.length() + devPart.length() + catPart.length() + endPart.length();
  uint8_t* body = (uint8_t*)malloc(bodyLen);
  if (!body) return "";

  int pos = 0;
  memcpy(body + pos, head.c_str(), head.length()); pos += head.length();
  memcpy(body + pos, jpegData, jpegLen); pos += jpegLen;
  memcpy(body + pos, foot.c_str(), foot.length()); pos += foot.length();
  memcpy(body + pos, devPart.c_str(), devPart.length()); pos += devPart.length();
  memcpy(body + pos, catPart.c_str(), catPart.length()); pos += catPart.length();
  memcpy(body + pos, endPart.c_str(), endPart.length());

  WiFiClient client;
  client.setTimeout(30);
  if (!client.connect("47.94.146.53", 80)) { free(body); return ""; }

  client.print("POST /vg/api/v1/device/ocr/image HTTP/1.1\r\n");
  client.print("Host: 47.94.146.53\r\n");
  client.print("Content-Type: multipart/form-data; boundary=" + boundary + "\r\n");
  client.print("Content-Length: " + String(bodyLen) + "\r\n");
  if (g_jwt.length() > 0) client.print("Authorization: Bearer " + g_jwt + "\r\n");
  client.print("Connection: close\r\n\r\n");

  int written = 0;
  while (written < bodyLen) {
    int chunk = min(512, bodyLen - written);
    client.write(body + written, chunk);
    written += chunk;
    delay(2);
  }
  free(body);

  unsigned long deadline = millis() + 30000;
  String resp = "";
  bool headerEnd = false;
  while (millis() < deadline) {
    if (client.available()) {
      resp += (char)client.read();
      deadline = millis() + 5000;
      if (!headerEnd && resp.endsWith("\r\n\r\n")) headerEnd = true;
    }
    if (!client.connected() && !client.available()) break;
    delay(5);
  }
  client.stop();

  if (headerEnd) {
    int bodyStart = resp.indexOf("\r\n\r\n");
    resp = resp.substring(bodyStart + 4);
  }
  return resp;
}

String pollOcrResult(String deviceId) {
  return httpGet("/api/v1/ocr/result/latest?deviceId=" + deviceId, true);
}

// OCR触发（按键调用）
void doOCR() {
  if (ocrInProgress) {
    Serial.println("[OCR] 正在识别中，请稍候...");
    return;
  }

  Serial.println("\n===== [OCR] 药品识别 =====");
  
  File imgFile = SD.open("/test_medicine.jpg", FILE_READ);
  if (!imgFile) {
    Serial.println("[OCR] 未找到 /test_medicine.jpg");
    return;
  }
  
  int imgLen = imgFile.size();
  uint8_t* jpegBuf = (uint8_t*)malloc(imgLen);
  if (!jpegBuf) {
    Serial.println("[OCR] 内存不足");
    imgFile.close();
    return;
  }
  
  imgFile.read(jpegBuf, imgLen);
  imgFile.close();
  
  Serial.printf("[OCR] 读取图片 %d字节\n", imgLen);
  
  String resp = httpPostMultipartImage(jpegBuf, imgLen, g_deviceId);
  free(jpegBuf);
  
  Serial.print("[OCR上传] "); Serial.println(resp);
  
  if (resp.indexOf("\"taskId\"") != -1 || resp.indexOf("\"imageId\"") != -1) {
    Serial.println("[OCR] 上传成功，等待识别...");
    ocrInProgress = true;
    ocrPollCount = 0;
    lastOcrPoll = millis();
  } else {
    Serial.println("[OCR] 上传失败");
  }
}

// OCR结果轮询
void checkOCRResult() {
  if (!ocrInProgress) return;
  
  if (millis() - lastOcrPoll > 2000 && ocrPollCount < 30) {
    lastOcrPoll = millis();
    String result = pollOcrResult(g_deviceId);
    ocrPollCount++;
    
    if (result.indexOf("\"code\":0") != -1 && result.indexOf("待接入") == -1 && result.length() > 10) {
      Serial.println("[OCR结果] " + result);
      
      int p1 = result.indexOf("\"speakText\":\"");
      if (p1 != -1) {
        p1 += 13;
        int p2 = result.indexOf("\"", p1);
        if (p2 != -1) {
          String speakText = result.substring(p1, p2);
          Serial.print("[语音播报] ");
          Serial.println(speakText);
          textToVoice(speakText);
        }
      }
      ocrInProgress = false;
      Serial.println("===== [OCR] 完成 =====\n");
    } else if (ocrPollCount >= 30) {
      Serial.println("[OCR] 超时");
      ocrInProgress = false;
    }
  }
}

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);
  
  prefs.begin("device", false);
  g_deviceId = prefs.getString("devId", "");
  g_deviceSecret = prefs.getString("devSecret", "");
  g_jwt = prefs.getString("jwt", "");

  if (!SD.begin(SD_CS)) {
    Serial.println("SD卡初始化失败");
  }
  
  audio.setPinout(I2S_BCLK, I2S_LRC, I2S_DOUT);
  audio.setVolume(21);

  Serial.println("===== 视障辅助系统 =====");
  Serial.println("WiFi连接中...");
  WiFi.begin(ssid, password);
  unsigned long wifiStart = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - wifiStart < 8000) {
    delay(200);
    yield();
  }
  wifiOnline = (WiFi.status() == WL_CONNECTED);

  if (wifiOnline) {
    Serial.println("WiFi已连接");

    configTime(8 * 3600, 0, "ntp.aliyun.com", "pool.ntp.org");
    struct tm timeinfo;
    int retry = 0;
    while (!getLocalTime(&timeinfo) && retry < 20) {
      delay(500);
      retry++;
    }
    if (retry < 20) Serial.println("时间已同步");
    else Serial.println("时间同步失败");

    if (g_deviceId.length() == 0) {
      Serial.println("首次启动，激活设备...");
      if (deviceActivate() && deviceRegister() && deviceChallenge()) {
        Serial.println("认证成功，设备在线");
        Serial.print("deviceId: "); Serial.println(g_deviceId);
        onlineTime = millis();
      } else Serial.println("认证失败");
    } else {
      Serial.print("deviceId: "); Serial.println(g_deviceId);
      if (g_jwt.length() > 0 && millis() < g_jwtExpiry) {
        Serial.println("认证成功，设备在线");
        onlineTime = millis();
      } else {
        if (deviceChallenge()) { 
          Serial.println("认证成功，设备在线"); 
          onlineTime = millis(); 
        } else Serial.println("认证失败");
      }
    }
  } else {
    Serial.println("WiFi失败");
  }
  
  pinMode(BTN_A, INPUT_PULLUP);
  
  Serial.println("===== 开始演示 =====");
  Serial.println("自动: 摔倒+避障 | 按键: OCR随时触发");
  Serial.println("========================\n");
  
  lastSerialActivity = millis();
}

void loop() {
  audio.loop();
  
  if (millis() - lastSerialActivity > 2000) {
    lastSerialActivity = millis();
  }
  
  wifiOnline = (WiFi.status() == WL_CONNECTED);

  if (g_deviceId.length() > 0 && wifiOnline) {
    // 心跳
    if (millis() - lastHb > 30000) {
      lastHb = millis();
      sendHeartbeat();
    }
    
    // JWT刷新
    if (millis() > g_jwtExpiry && g_jwtExpiry > 0) {
      if (deviceChallenge()) {
        g_jwtExpiry = millis() + 23 * 3600 * 1000;
        Serial.println("[JWT刷新]");
      }
    }

    // 按键随时触发OCR（不受演示流程影响）
    static bool lastBtn = HIGH;
    bool btn = digitalRead(BTN_A);
    if (btn == LOW && lastBtn == HIGH) {
      delay(50);
      if (digitalRead(BTN_A) == LOW) {
        Serial.println("\n[按键] OCR药品识别");
        doOCR();
      }
    }
    lastBtn = btn;

    // 自动演示流程
    if (!demoCompleted) {
      unsigned long elapsed = millis() - onlineTime;
      static unsigned long stepStartTime = 0;
      static int subStep = 0;
      
      if (demoStep == 0 && elapsed > 2000) {
        demoStep = 1;
        subStep = 0;
        stepStartTime = millis();
        Serial.println("\n[演示1] 摔倒检测");
        playSound("/fall.wav");
      }
      
      if (demoStep == 1) {
        if (subStep == 0 && millis() - stepStartTime > 2000) {
          uploadFallAlarm(-65.0, 20.0);
          subStep = 1;
          stepStartTime = millis();
        }
        else if (subStep == 1 && millis() - stepStartTime > 2000) {
          demoStep = 2;
          subStep = 0;
          stepStartTime = millis();
          Serial.println("\n[演示2] 避障检测");
          playSound("/evasion.wav");
        }
      }
      
      if (demoStep == 2) {
        if (subStep == 0 && millis() - stepStartTime > 1000) {
          subStep = 1;
          stepStartTime = millis();
          playSound("/left.wav");
        }
        else if (subStep == 1 && millis() - stepStartTime > 2000) {
          uploadObstacleAlarm(500);
          subStep = 2;
          stepStartTime = millis();
        }
        else if (subStep == 2 && millis() - stepStartTime > 2000) {
          demoStep = 3;
          Serial.println("\n[演示3] 等待按键触发OCR...");
          Serial.println("按BTN_A键进行药品识别\n");
          demoCompleted = true;
        }
      }
    }
  }
  
  // OCR结果轮询（独立于演示流程）
  checkOCRResult();
  
  delay(10);
  yield();
}