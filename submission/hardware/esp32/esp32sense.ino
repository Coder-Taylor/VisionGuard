#include <WiFi.h>
#include <HTTPClient.h>
#include <HardwareSerial.h>
#include "Arduino.h"
#include "Audio.h"
#include "SD.h"
#include "SPI.h"
#include <Preferences.h>

#define DEVICE_SERIAL_NO "SN_TEST_009"
#define DEVICE_MODEL     "ESP32_K210"
#define HW_VERSION       "1.0"
#define FW_VERSION       "1.0.0"
#define AUTH_KEY         0x4B

#define I2S_DOUT 2
#define I2S_BCLK 3
#define I2S_LRC  4
#define SD_CS    21

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
int testStep = 0;
unsigned long lastHb = 0;

String httpPost(String path, String body, bool useJwt = false) {
  HTTPClient h; h.begin(String(BASE_URL) + path); h.addHeader("Content-Type", "application/json");
  if (useJwt && g_jwt.length() > 0) h.addHeader("Authorization", "Bearer " + g_jwt);
  h.POST(body); String r = h.getString(); h.end(); return r;
}

String httpGet(String path, bool useJwt = false) {
  HTTPClient h; h.begin(String(BASE_URL) + path);
  if (useJwt && g_jwt.length() > 0) h.addHeader("Authorization", "Bearer " + g_jwt);
  h.GET(); String r = h.getString(); h.end(); return r;
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
  int p1 = resp.indexOf("\"deviceSecret\":\"");
  if (p1 == -1) return false;
  p1 += 16; int p2 = resp.indexOf("\"", p1);
  g_deviceSecret = resp.substring(p1, p2);
  p1 = resp.indexOf("\"deviceId\":\"");
  if (p1 == -1) return false;
  p1 += 12; p2 = resp.indexOf("\"", p1);
  g_deviceId = resp.substring(p1, p2);
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
  int p1 = resp.indexOf("\"challengeId\":\"") + 15;
  int p2 = resp.indexOf("\"", p1);
  String challengeId = resp.substring(p1, p2);
  p1 = resp.indexOf("\"nonce\":\"") + 9; p2 = resp.indexOf("\"", p1);
  String nonce = resp.substring(p1, p2);
  p1 = resp.indexOf("\"timestamp\":") + 12; p2 = resp.indexOf(",", p1);
  if (p2 == -1) p2 = resp.indexOf("}", p1);
  long timestamp = resp.substring(p1, p2).toInt();
  String sign = calcSign(g_deviceSecret, nonce, timestamp);
  body = "{\"deviceId\":\"" + g_deviceId + "\",\"challengeId\":\"" + challengeId + "\",\"sigin\":\"" + sign + "\"}";
  resp = httpPost("/api/v1/device/verify", body);
  if (resp.indexOf("\"jwt\":\"") != -1) {
    p1 = resp.indexOf("\"jwt\":\"") + 7; p2 = resp.indexOf("\"", p1);
    g_jwt = resp.substring(p1, p2);
    g_jwtExpiry = millis() + 23 * 3600 * 1000;
    prefs.putString("jwt", g_jwt);
    return true;
  }
  return false;
}

void sendHeartbeat() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"battery\":85,\"rssi\":" + String(WiFi.RSSI()) +
                ",\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}}";
  httpPost("/api/v1/device/heartbeat", body, true);
  Serial.println("[心跳]");
}

void uploadFallAlarm(float x, float y) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"fall\",\"alertLevel\":\"critical\",\"description\":\"[测试]摔倒事件\"," +
                "\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}," +
                "\"sensorData\":{\"angleX\":" + String(x, 2) + ",\"angleY\":" + String(y, 2) + ",\"accelMagnitude\":9.2}}";
  Serial.println("[摔倒告警] " + httpPost("/api/v1/alert", body));
}

void uploadObstacleAlarm(int d) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"obstacle\",\"alertLevel\":\"warning\",\"description\":\"[测试]障碍物检测\"," +
                "\"location\":{\"lat\":" + String(FIXED_LAT, 6) + ",\"lng\":" + String(FIXED_LNG, 6) + "}," +
                "\"sensorData\":{\"lidarDist\":" + String(d) + "}}";
  Serial.println("[避障告警] " + httpPost("/api/v1/alert", body));
}

String httpPostMultipartImage(const uint8_t* jpegData, int jpegLen, String deviceId) {
  if (!wifiOnline || jpegData == nullptr || jpegLen == 0) return "";

  String boundary = "----VisionGuard" + String(millis());
  String boundaryLine = "--" + boundary;

  String part1Head = boundaryLine + "\r\n";
  part1Head += "Content-Disposition: form-data; name=\"image\"; filename=\"photo.jpg\"\r\n";
  part1Head += "Content-Type: image/jpeg\r\n\r\n";
  String part1Foot = "\r\n";

  String part2 = boundaryLine + "\r\n";
  part2 += "Content-Disposition: form-data; name=\"deviceId\"\r\n\r\n";
  part2 += deviceId + "\r\n";

  String part3 = boundaryLine + "\r\n";
  part3 += "Content-Disposition: form-data; name=\"category\"\r\n\r\n";
  part3 += "medicine\r\n";

  String partEnd = boundaryLine + "--\r\n";

  int bodyLen = part1Head.length() + jpegLen + part1Foot.length()
              + part2.length() + part3.length() + partEnd.length();

  uint8_t* body = (uint8_t*)malloc(bodyLen);
  if (!body) return "";

  int pos = 0;
  memcpy(body + pos, part1Head.c_str(), part1Head.length()); pos += part1Head.length();
  memcpy(body + pos, jpegData, jpegLen);                    pos += jpegLen;
  memcpy(body + pos, part1Foot.c_str(), part1Foot.length()); pos += part1Foot.length();
  memcpy(body + pos, part2.c_str(), part2.length());        pos += part2.length();
  memcpy(body + pos, part3.c_str(), part3.length());        pos += part3.length();
  memcpy(body + pos, partEnd.c_str(), partEnd.length());    pos += partEnd.length();

  WiFiClient client;
  client.setTimeout(15);

  if (!client.connect("47.94.146.53", 80)) {
    free(body);
    client.stop();
    return "";
  }

  String fullPath = "/vg/api/v1/device/ocr/image";
  client.print("POST " + fullPath + " HTTP/1.1\r\n");
  client.print("Host: 47.94.146.53\r\n");
  client.print("Content-Type: multipart/form-data; boundary=" + boundary + "\r\n");
  client.print("Content-Length: " + String(bodyLen) + "\r\n");
  if (g_jwt.length() > 0) {
    client.print("Authorization: Bearer " + g_jwt + "\r\n");
  }
  client.print("Connection: close\r\n");
  client.print("\r\n");

  int written = 0;
  while (written < bodyLen) {
    int chunk = min(1024, bodyLen - written);
    client.write(body + written, chunk);
    written += chunk;
    delay(1);
  }
  free(body);

  unsigned long deadline = millis() + 15000;
  String resp = "";
  bool headerEnd = false;

  while (millis() < deadline) {
    if (client.available()) {
      char c = client.read();
      resp += c;
      deadline = millis() + 3000;
      if (!headerEnd && resp.endsWith("\r\n\r\n")) {
        headerEnd = true;
      }
    }
    if (!client.connected() && !client.available()) break;
    delay(5);
  }
  client.stop();

  if (headerEnd) {
    int bodyStart = resp.indexOf("\r\n\r\n");
    resp = resp.substring(bodyStart + 4);
  } else {
    Serial.print("[原始响应] "); Serial.println(resp);
    return "";
  }

  return resp;
}

String pollOcrResult(String deviceId) {
  return httpGet("/api/v1/ocr/result/latest?deviceId=" + deviceId, true);
}

void setup() {
  Serial.begin(115200);
  prefs.begin("device", false);
  g_deviceId = prefs.getString("devId", "");
  g_deviceSecret = prefs.getString("devSecret", "");
  g_jwt = prefs.getString("jwt", "");

  SD.begin(SD_CS);
  audio.setPinout(I2S_BCLK, I2S_LRC, I2S_DOUT);
  audio.setVolume(21);

  Serial.println("===== 告警上传测试 =====");
  Serial.println("WiFi连接中...");
  WiFi.begin(ssid, password);
  unsigned long wifiStart = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - wifiStart < 8000) delay(200);
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
    if (retry < 20) { Serial.println("时间已同步"); }
    else { Serial.println("时间同步失败"); }

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
        Serial.println("JWT有效，设备在线");
        onlineTime = millis();
      } else {
        if (deviceChallenge()) { Serial.println("认证成功，设备在线"); onlineTime = millis(); }
        else Serial.println("认证失败");
      }
    }
  } else {
    Serial.println("WiFi失败");
  }
  Serial.println("===== 开始模拟告警 =====");
}

void loop() {
  audio.loop();
  wifiOnline = (WiFi.status() == WL_CONNECTED);

  if (g_deviceId.length() > 0 && wifiOnline) {
    unsigned long elapsed = millis() - onlineTime;

    if (millis() - lastHb > 30000) { lastHb = millis(); sendHeartbeat(); }
    if (millis() > g_jwtExpiry && deviceChallenge()) { g_jwtExpiry = millis() + 23 * 3600 * 1000; Serial.println("[JWT刷新]"); }

    if (testStep == 0 && elapsed > 2000) {
      testStep = 1;
      Serial.println("\n[测试1] 摔倒");
      audio.connecttoFS(SD, "/fall.wav");
      while (audio.isRunning()) { audio.loop(); delay(10); }
      uploadFallAlarm(-65.0, 20.0);
    }
    if (testStep == 1 && elapsed > 17000) {
      testStep = 2;
      Serial.println("\n[测试2] 避障");
      audio.connecttoFS(SD, "/evasion.wav");
      while (audio.isRunning()) { audio.loop(); delay(10); }
      delay(500);
      audio.connecttoFS(SD, "/left.wav");
      while (audio.isRunning()) { audio.loop(); delay(10); }
      uploadObstacleAlarm(500);
    }
    if (testStep == 2 && elapsed > 20000) {
      testStep = 3;
      Serial.println("\n[测试3] OCR 药品识别");

      File root = SD.open("/");
      File f = root.openNextFile();
      Serial.println("SD卡文件:");
      while (f) { Serial.print("  "); Serial.println(f.name()); f = root.openNextFile(); }
      root.close();

      File imgFile = SD.open("/test_medicine.jpg", FILE_READ);
      if (imgFile) {
        int imgLen = imgFile.size();
        uint8_t* jpegBuf = (uint8_t*)malloc(imgLen);
        if (jpegBuf) {
          imgFile.read(jpegBuf, imgLen);
          Serial.printf("图片大小: %d bytes\n", imgLen);

          String resp = httpPostMultipartImage(jpegBuf, imgLen, g_deviceId);
          Serial.print("[OCR上传响应] ");
          Serial.println(resp);

          if (resp.length() > 0 && resp.indexOf("\"taskId\"") != -1) {
            Serial.println("[OCR] 等待识别结果...");
            String result = "";
            for (int i = 0; i < 120; i++) {
              delay(2000);
              result = pollOcrResult(g_deviceId);
              if (result.indexOf("待接入") == -1 && result.length() > 0) break;
            }
            Serial.println("[OCR结果] " + result);

            int p1 = result.indexOf("\"speakText\":\"");
            if (p1 != -1) {
              p1 += 13;
              int p2 = result.indexOf("\"", p1);
              Serial.print("[语音播报] "); Serial.println(result.substring(p1, p2));
            }
          } else {
            Serial.println("[OCR] 未获取到任务ID");
          }
          free(jpegBuf);
        }
        imgFile.close();
      } else {
        Serial.println("无测试图片 /test_medicine.jpg");
      }
      Serial.println("\n===== 测试完成 =====");
    }
  }
  delay(5);
}