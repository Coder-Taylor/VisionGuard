#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <SparkFun_VL53L5CX_Library.h>
#include <MPU6050_tockn.h>
#include <HardwareSerial.h>
#include "Arduino.h"
#include "Audio.h"
#include "ESP_I2S.h"
#include <SD.h>
#include <SPI.h>
#include <Preferences.h>
#include "wordmap.h"

#define DEVICE_SERIAL_NO "SN_TEST_001"
#define DEVICE_MODEL     "ESP32_K210"
#define HW_VERSION       "1.0"
#define FW_VERSION       "1.0.0"
#define AUTH_KEY         0x4B

#define I2S_DOUT 2
#define I2S_BCLK 3
#define I2S_LRC  4
#define BTN_A 1
#define SD_CS 21

HardwareSerial kpuSerial(2);
#define KPU_RX 44
#define KPU_TX 43

HardwareSerial gpsSerial(1);
#define GPS_RX 16
#define GPS_TX 15

const char* BASE_URL = "http://47.94.146.53/vg";
const char* ssid     = "wuiPhone 16";
const char* password = "12345ssDLH";

#define VOLUME_THRESHOLD 2000
#define SILENCE_SEC 1.2
#define MAX_REC_TIME 20

const unsigned long MOVE_CONFIRM_TIME = 2000;   // AI辅助生成：DeepSeek-v4, 2026-5-1
const unsigned long STOP_CONFIRM_TIME = 3000;
const float ANGLE_DELTA_THRESHOLD = 3.0;    // AI辅助生成：DeepSeek-v4, 2026-5-1
const int OBSTACLE_DANGER_DIST = 500;
const int OBSTACLE_RECOVER_DIST = 800;
const unsigned long OBSTACLE_COOLDOWN = 5000;
const float FALL_ANGLE_X = 55.0;
const float FALL_ANGLE_Y = 50.0;
const unsigned long FALL_COOLDOWN = 10000;
const float FIXED_LAT = 34.8127;
const float FIXED_LNG = 114.3626;
unsigned long playEndTime = 0;
const int PLAY_DURATION = 5000;

enum DeviceStatus { WIFI_DISCONNECTED, DEVICE_ACTIVATING, DEVICE_REGISTERING, DEVICE_CHALLENGING, DEVICE_ONLINE, DEVICE_OFFLINE };
DeviceStatus currentStatus = WIFI_DISCONNECTED;

Preferences prefs;
String g_deviceId = "";
String g_deviceSecret = "";
String g_jwt = "";
unsigned long g_jwtExpiry = 0;

Audio audio;
I2SClass i2s;
SparkFun_VL53L5CX myImager;
VL53L5CX_ResultsData measurementData;
MPU6050 mpu6050(Wire);

bool wifiOnline = false;
bool isRecording = false;
unsigned long silenceTimer;
bool photoReceiving = false;
String b64Buffer = "";
uint32_t imageLen = 0;
bool lidarExist = false;

enum MoveState { STATE_STOPPED, STATE_MOVING_CHECK, STATE_MOVING, STATE_STOPPING_CHECK };
MoveState moveState = STATE_STOPPED;
unsigned long stateEnterTime = 0;
bool obstacleAlarmActive = false;
unsigned long lastObstacleAlarm = 0;
bool obstacleInDanger = false;
bool fallAlarmActive = false;
unsigned long lastFallAlarm = 0;

float curAngleX = 0.0f, curAngleY = 0.0f, lastAngleX = 0.0f, lastAngleY = 0.0f;
int curLidarDist = 0;
String kpuLineBuffer = "";
unsigned long lastHeartbeat = 0;

struct BeiDouData {
  int hour, minute, second, day, month, year;
  float latitude, longitude, altitude, speed;
  int satellites;
  char status;
};
BeiDouData beidouData;
String nmea_sentence = "";

float getLat() { return (beidouData.status == 'A' && beidouData.latitude != 0) ? beidouData.latitude : FIXED_LAT; }
float getLng() { return (beidouData.status == 'A' && beidouData.longitude != 0) ? beidouData.longitude : FIXED_LNG; }

void resetBeiDouData() {
  beidouData.hour = beidouData.minute = beidouData.second = 0;
  beidouData.day = beidouData.month = beidouData.year = 0;
  beidouData.latitude = beidouData.longitude = beidouData.altitude = beidouData.speed = 0.0;
  beidouData.satellites = 0;
  beidouData.status = 'V';
}

void parseRMC(String nmea) {
  int commaIndex[15], commaCount = 0;
  for (int i = 0; i < nmea.length() && commaCount < 15; i++)
    if (nmea.charAt(i) == ',') commaIndex[commaCount++] = i;
  if (commaCount < 11) return;
  beidouData.status = nmea.charAt(commaIndex[1] + 1);
  if (beidouData.status == 'A') {
    String lat = nmea.substring(commaIndex[2] + 1, commaIndex[3]);
    if (lat.length() > 0) {
      float d = lat.substring(0, 2).toFloat() + lat.substring(2).toFloat() / 60.0;
      beidouData.latitude = (nmea.charAt(commaIndex[3] + 1) == 'S') ? -d : d;
    }
    String lng = nmea.substring(commaIndex[4] + 1, commaIndex[5]);
    if (lng.length() > 0) {
      float d = lng.substring(0, 3).toFloat() + lng.substring(3).toFloat() / 60.0;
      beidouData.longitude = (nmea.charAt(commaIndex[5] + 1) == 'W') ? -d : d;
    }
  }
}

void parseGGA(String nmea) {
  int commaIndex[15], commaCount = 0;
  for (int i = 0; i < nmea.length() && commaCount < 15; i++)
    if (nmea.charAt(i) == ',') commaIndex[commaCount++] = i;
  if (commaCount < 14) return;
  String sats = nmea.substring(commaIndex[6] + 1, commaIndex[7]);
  if (sats.length() > 0) beidouData.satellites = sats.toInt();
}

void parseNMEA(String nmea) {
  if (nmea.startsWith("$GNRMC") || nmea.startsWith("$BDRMC")) parseRMC(nmea);
  else if (nmea.startsWith("$GNGGA") || nmea.startsWith("$BDGGA")) parseGGA(nmea);
}

void readBeiDouData() {
  while (gpsSerial.available() > 0) {
    char c = gpsSerial.read();
    if (c == '\n') { if (nmea_sentence.startsWith("$")) parseNMEA(nmea_sentence); nmea_sentence = ""; }
    else if (c != '\r') nmea_sentence += c;
  }
}

const char base64_chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
int base64_decode(const String& input, uint8_t* output) {
  int outLen = 0, i = 0, inLen = input.length();
  uint8_t buf[4]; int bufIdx = 0;
  while (inLen--) {
    char c = input[i++]; if (c == '=') break;
    const char* p = strchr(base64_chars, c); if (p == NULL) continue;
    buf[bufIdx++] = p - base64_chars;
    if (bufIdx == 4) {
      output[outLen++] = (buf[0] << 2) + ((buf[1] & 0x30) >> 4);
      output[outLen++] = ((buf[1] & 0x0F) << 4) + ((buf[2] & 0x3C) >> 2);
      output[outLen++] = ((buf[2] & 0x03) << 6) + buf[3];
      bufIdx = 0;
    }
  }
  if (bufIdx > 1) { output[outLen++] = (buf[0] << 2) + ((buf[1] & 0x30) >> 4); if (bufIdx > 2) output[outLen++] = ((buf[1] & 0x0F) << 4) + ((buf[2] & 0x3C) >> 2); }
  return outLen;
}

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
    char c = plaintext[i] ^ AUTH_KEY; char hex[3]; sprintf(hex, "%02x", (unsigned char)c); result += hex;
  }
  return result;
}

bool deviceActivate() {
  String mac = WiFi.macAddress();
  String body = "{\"serialNo\":\"" + String(DEVICE_SERIAL_NO) + "\",\"model\":\"" + String(DEVICE_MODEL) +
                "\",\"mac\":\"" + mac + "\",\"hwVersion\":\"" + String(HW_VERSION) +
                "\",\"fwVersion\":\"" + String(FW_VERSION) + "\",\"timestamp\":" + String(time(nullptr)) + ",\"sign\":\"test\"}";
  String resp = httpPost("/api/v1/device/activate", body);
  int p1 = resp.indexOf("\"deviceSecret\":\""); if (p1 == -1) return false;
  p1 += 16; int p2 = resp.indexOf("\"", p1); g_deviceSecret = resp.substring(p1, p2);
  p1 = resp.indexOf("\"deviceId\":\""); if (p1 == -1) return false;
  p1 += 12; p2 = resp.indexOf("\"", p1); g_deviceId = resp.substring(p1, p2);
  prefs.putString("devId", g_deviceId); prefs.putString("devSecret", g_deviceSecret);
  return true;
}

bool deviceRegister() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"deviceModel\":\"" + String(DEVICE_MODEL) + "\",\"firmwareVersion\":\"" + String(FW_VERSION) + "\"}";
  return httpPost("/api/v1/device/register", body).indexOf("\"code\":0") != -1;
}

bool deviceChallenge() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\"}";
  String resp = httpPost("/api/v1/device/challenge", body);
  if (resp.indexOf("challengeId") == -1) return false;
  int p1 = resp.indexOf("\"challengeId\":\"") + 15; int p2 = resp.indexOf("\"", p1); String challengeId = resp.substring(p1, p2);
  p1 = resp.indexOf("\"nonce\":\"") + 9; p2 = resp.indexOf("\"", p1); String nonce = resp.substring(p1, p2);
  p1 = resp.indexOf("\"timestamp\":") + 12; p2 = resp.indexOf(",", p1); if (p2 == -1) p2 = resp.indexOf("}", p1);
  long timestamp = resp.substring(p1, p2).toInt();
  String sign = calcSign(g_deviceSecret, nonce, timestamp);
  body = "{\"deviceId\":\"" + g_deviceId + "\",\"challengeId\":\"" + challengeId + "\",\"sigin\":\"" + sign + "\"}";
  resp = httpPost("/api/v1/device/verify", body);
  if (resp.indexOf("\"jwt\":\"") != -1) {
    p1 = resp.indexOf("\"jwt\":\"") + 7; p2 = resp.indexOf("\"", p1); g_jwt = resp.substring(p1, p2);
    g_jwtExpiry = millis() + 23 * 3600 * 1000; prefs.putString("jwt", g_jwt); return true;
  }
  return false;
}

void sendHeartbeat() {
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"battery\":85,\"rssi\":" + String(WiFi.RSSI()) +
                ",\"location\":{\"lat\":" + String(getLat(), 6) + ",\"lng\":" + String(getLng(), 6) + "}}";
  httpPost("/api/v1/device/heartbeat", body, true);
}

void uploadFallAlarm(float x, float y) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"fall\",\"alertLevel\":\"critical\",\"description\":\"检测到疑似摔倒事件\"," +
                "\"location\":{\"lat\":" + String(getLat(), 6) + ",\"lng\":" + String(getLng(), 6) + "}," +
                "\"sensorData\":{\"angleX\":" + String(x, 2) + ",\"angleY\":" + String(y, 2) + ",\"accelMagnitude\":9.2}}";
  Serial.println("[摔倒告警] " + httpPost("/api/v1/alert", body));
}

void uploadObstacleAlarm(int d) {
  if (!wifiOnline) return;
  String body = "{\"deviceId\":\"" + g_deviceId + "\",\"timestamp\":" + String(time(nullptr)) +
                ",\"alertType\":\"obstacle\",\"alertLevel\":\"warning\",\"description\":\"检测到障碍物\"," +
                "\"location\":{\"lat\":" + String(getLat(), 6) + ",\"lng\":" + String(getLng(), 6) + "}," +
                "\"sensorData\":{\"lidarDist\":" + String(d) + "}}";
  Serial.println("[避障告警] " + httpPost("/api/v1/alert", body));
}

void uploadImageToCloud(uint8_t* buf, int len) {
  if (!wifiOnline || !buf || !len) return;
  HTTPClient h; h.begin(String(BASE_URL) + "/api/v1/device/ocr/image");
  h.addHeader("Content-Type", "application/json");
  if (g_jwt.length() > 0) h.addHeader("Authorization", "Bearer " + g_jwt);
  int code = h.POST("{\"deviceId\":\"" + g_deviceId + "\",\"imageCategory\":\"medicine\",\"fileUrl\":\"\",\"fileSize\":" + String(len) + ",\"format\":\"jpeg\"}");
  Serial.print("[图片上传] HTTP "); Serial.println(code);
  h.end();
}

String getOcrText() {
  if (!wifiOnline) return "";
  return httpGet("/api/v1/ocr/result/latest?deviceId=" + g_deviceId, true);
}

void recordAndUpload() {
  if (!wifiOnline) { Serial.println("[录音] 离线，跳过上传"); return; }
  uint8_t* b; size_t l;
  Serial.println("[录音] 开始录音...");
  b = i2s.recordWAV(MAX_REC_TIME, &l);
  if (b) {
    Serial.printf("[录音] 完成，大小: %d 字节\n", l);
    String body = "{\"type\":\"audio\",\"size\":" + String(l) + ",\"timestamp\":" + String(time(nullptr)) + "}";
    Serial.println("[录音上传] " + httpPost("/api/v1/device/" + g_deviceId + "/data", body, true));
  }
}

void playSound(const char* f) { if (millis() < playEndTime) return; audio.connecttoFS(SD, f); playEndTime = millis() + PLAY_DURATION; }
void playSingleWord(String w) { if (millis() < playEndTime) return; String p = charToPath(w.c_str()); if (p == "" || !SD.exists(p)) return; while (audio.isRunning()) { audio.loop(); delay(10); } audio.connecttoFS(SD, p.c_str()); playEndTime = millis() + 400; }
void textToVoice(String text) {
  const char* s = text.c_str(); int len = strlen(s);
  for (int i = 0; i < len; ) {
    char ch[4] = {0}; int b = 1;
    if ((s[i] & 0x80) == 0) b = 1; else if ((s[i] & 0xE0) == 0xC0) b = 2; else if ((s[i] & 0xF0) == 0xE0) b = 3; else b = 4;
    strncpy(ch, s + i, b); i += b;
    if (strcmp(ch, "，") == 0 || strcmp(ch, "。") == 0 || strcmp(ch, "！") == 0 || strcmp(ch, "、") == 0 || strcmp(ch, "？") == 0 || strcmp(ch, "；") == 0 || strcmp(ch, "：") == 0) { delay(600); continue; }
    if (ch[0] == ' ' || ch[0] == ',' || ch[0] == '.') continue;
    playSingleWord(String(ch)); delay(420);
  }
}

void readK210Cmd() {
  while (kpuSerial.available()) {
    char c = kpuSerial.read();
    if (c == '\n') { String l = kpuLineBuffer; kpuLineBuffer = ""; l.trim(); if (l.length() == 0) continue;
      if (l == "$LEFT!" || l == "$MID!" || l == "$RIGHT!") { if (moveState == STATE_MOVING && obstacleAlarmActive) { if (l == "$LEFT!") playSound("/left.wav"); else if (l == "$MID!") playSound("/mid.wav"); else playSound("/right.wav"); } continue; }
      if (l.startsWith("$IMG_START:")) { int p = l.indexOf(':'); int e = l.indexOf('!'); if (p != -1 && e != -1) { imageLen = l.substring(p + 1, e).toInt(); b64Buffer = ""; photoReceiving = true; } continue; }
      if (l == "$IMG_END!") {
        photoReceiving = false;
        int s = b64Buffer.length(); uint8_t* j = (uint8_t*)malloc(s); int jl = base64_decode(b64Buffer, j);
        if (jl > 0 && j[0] == 0xFF && j[1] == 0xD8) {
          uploadImageToCloud(j, jl);
          String t = "";
          for (int i = 0; i < 60; i++) { delay(1000); t = getOcrText(); if (t.indexOf("待接入") == -1 && t.length() > 0) break; }
          if (t.length() > 0) textToVoice(t);
        }
        free(j); b64Buffer = ""; continue;
      }
      if (photoReceiving) b64Buffer += l;
    } else if (c != '\r') { kpuLineBuffer += c; if (kpuLineBuffer.length() > 256) kpuLineBuffer = ""; }
  }
}

void takeK210Photo() { while (kpuSerial.available()) kpuSerial.read(); kpuLineBuffer = ""; b64Buffer = ""; photoReceiving = false; delay(30); kpuSerial.print("$TAKE_PHOTO!\r\n"); kpuSerial.flush(); }

void updateMotionState() {
  mpu6050.update(); curAngleX = mpu6050.getAngleX(); curAngleY = mpu6050.getAngleY();
  float d = abs(curAngleX - lastAngleX) + abs(curAngleY - lastAngleY); bool m = d > ANGLE_DELTA_THRESHOLD;
  lastAngleX = curAngleX; lastAngleY = curAngleY;
  switch (moveState) { case STATE_STOPPED: if (m) { moveState = STATE_MOVING_CHECK; stateEnterTime = millis(); } break;
    case STATE_MOVING_CHECK: if (!m) moveState = STATE_STOPPED; else if (millis() - stateEnterTime >= MOVE_CONFIRM_TIME) moveState = STATE_MOVING; break;
    case STATE_MOVING: if (!m) { moveState = STATE_STOPPING_CHECK; stateEnterTime = millis(); } break;
    case STATE_STOPPING_CHECK: if (m) moveState = STATE_MOVING; else if (millis() - stateEnterTime >= STOP_CONFIRM_TIME) { moveState = STATE_STOPPED; obstacleAlarmActive = false; fallAlarmActive = false; } break; }
}

void obstacleDetect() {
  if (moveState != STATE_MOVING || !lidarExist) return;
  if (!myImager.isDataReady()) return; if (!myImager.getRangingData(&measurementData)) return;
  int md = 9999; for (int i = 0; i < 64; i++) if (measurementData.distance_mm[i] > 10) md = min(md, (int)measurementData.distance_mm[i]);
  curLidarDist = md;
  if (md < OBSTACLE_DANGER_DIST) obstacleInDanger = true;
  else if (md > OBSTACLE_RECOVER_DIST) { obstacleInDanger = false; obstacleAlarmActive = false; }
  if (obstacleInDanger && !obstacleAlarmActive && millis() - lastObstacleAlarm > OBSTACLE_COOLDOWN) {
    playSound("/evasion.wav"); uploadObstacleAlarm(md); obstacleAlarmActive = true; lastObstacleAlarm = millis();
  }
}

void fallDetect() {
  if (moveState != STATE_MOVING) return;
  bool f = (abs(curAngleX) > FALL_ANGLE_X) || (abs(curAngleY) > FALL_ANGLE_Y);
  if (f && !fallAlarmActive && millis() - lastFallAlarm > FALL_COOLDOWN) { playSound("/fall.wav"); uploadFallAlarm(curAngleX, curAngleY); fallAlarmActive = true; lastFallAlarm = millis(); }
  if (!f && fallAlarmActive) fallAlarmActive = false;
}

void setup() {
  Serial.begin(115200); pinMode(BTN_A, INPUT_PULLUP); pinMode(KPU_TX, OUTPUT); digitalWrite(KPU_TX, HIGH); delay(300);
  kpuSerial.begin(115200, SERIAL_8N1, KPU_RX, KPU_TX);
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX, GPS_TX);
  SD.begin(SD_CS);
  audio.setPinout(I2S_BCLK, I2S_LRC, I2S_DOUT); audio.setVolume(21);
  i2s.setPinsPdmRx(42, 41); i2s.begin(I2S_MODE_PDM_RX, 16000, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO);
  prefs.begin("device", false);
  g_deviceId = prefs.getString("devId", ""); g_deviceSecret = prefs.getString("devSecret", ""); g_jwt = prefs.getString("jwt", "");
  resetBeiDouData();

  Serial.println("WiFi连接中...");
  WiFi.begin(ssid, password);
  unsigned long wifiStart = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - wifiStart < 8000) delay(200);
  wifiOnline = (WiFi.status() == WL_CONNECTED);

  configTime(8 * 3600, 0, "ntp.aliyun.com", "pool.ntp.org");

  if (wifiOnline) {
    Serial.println("WiFi已连接");
    if (g_deviceId.length() == 0) {
      Serial.println("首次启动，激活设备...");
      currentStatus = DEVICE_ACTIVATING;
      if (deviceActivate()) { Serial.println("激活成功");
        if (deviceRegister()) { Serial.println("注册成功");
          if (deviceChallenge()) { Serial.println("认证成功，设备在线"); currentStatus = DEVICE_ONLINE; }
          else Serial.println("认证失败");
        } else Serial.println("注册失败");
      } else Serial.println("激活失败");
    } else {
      if (g_jwt.length() > 0 && millis() < g_jwtExpiry) { Serial.println("JWT有效，设备在线"); currentStatus = DEVICE_ONLINE; }
      else { currentStatus = DEVICE_CHALLENGING; if (deviceChallenge()) { Serial.println("认证成功，设备在线"); currentStatus = DEVICE_ONLINE; } else Serial.println("认证失败"); }
    }
  } else { Serial.println("WiFi失败，离线运行"); currentStatus = DEVICE_OFFLINE; }
  Wire.begin(5, 6); Wire.setClock(400000); delay(100);
  mpu6050.begin(); mpu6050.calcGyroOffsets(true);
  if (myImager.begin()) { myImager.setResolution(8 * 8); myImager.startRanging(); lidarExist = true; }
  else { lidarExist = false; }
  Serial.println("===== 系统就绪 =====");
}

void loop() {
  audio.loop(); wifiOnline = (WiFi.status() == WL_CONNECTED);
  readBeiDouData();
  if (currentStatus == DEVICE_OFFLINE && wifiOnline && g_deviceId.length() > 0) {
    currentStatus = DEVICE_CHALLENGING; if (deviceChallenge()) { Serial.println("认证成功，设备在线"); currentStatus = DEVICE_ONLINE; }
  }
  if (currentStatus == DEVICE_ONLINE) {
    if (millis() - lastHeartbeat > 30000) { lastHeartbeat = millis(); sendHeartbeat(); Serial.println("[心跳]"); }
    if (millis() > g_jwtExpiry && deviceChallenge()) { g_jwtExpiry = millis() + 23 * 3600 * 1000; Serial.println("[JWT刷新]"); }
  }
  static bool lb = HIGH; bool nb = digitalRead(BTN_A);
  if (nb == LOW && lb == HIGH) { delay(50); if (digitalRead(BTN_A) == LOW) { Serial.println("[按键] 拍照"); takeK210Photo(); } }
  lb = nb;
  readK210Cmd(); updateMotionState();
  if (moveState == STATE_MOVING) { obstacleDetect(); fallDetect(); }
  int16_t smp = i2s.read();
  if (smp != -1) { if (abs(smp) > VOLUME_THRESHOLD) { silenceTimer = millis(); isRecording = true; } if (isRecording && millis() - silenceTimer > SILENCE_SEC * 1000) { recordAndUpload(); isRecording = false; } }
  delay(5);
}