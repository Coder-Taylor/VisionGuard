#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <SparkFun_VL53L5CX_Library.h>
#include <MPU6050_tockn.h>
#include <HardwareSerial.h>
#include "Arduino.h"
#include "Audio.h"
#include "SPIFFS.h"
#include "ESP_I2S.h"
#include <SD.h>
#include <SPI.h>

// ==================== 设备唯一标识 ====================
#define DEVICE_UNIQUE_ID   "DEVICE_2026_ESP32_K210"
#define AUTH_KEY           0x4B

// ==================== 引脚 ====================
#define I2S_DOUT 7
#define I2S_BCLK 8
#define I2S_LRC 9
#define BTN_A 1
#define SD_CS 21

HardwareSerial kpuSerial(2);
#define KPU_RX 44
#define KPU_TX 43

HardwareSerial gpsSerial(1);
#define RXD1 3
#define TXD1 4

// ==================== 配置 ====================
const char* ssid     = "ssid";
const char* password = "password";
const char* deviceId = "DEVICE_001";

const char* registerUrl  = "http://172.20.10.3:8888/device_register";
const char* checkBindUrl = "http://172.20.10.3:8888/check_bind";
const char* imgUrl       = "http://172.20.10.3:8888/upload_image";
const char* textUrl      = "http://172.20.10.3:8888/get_ocr_text";
const char* alarmUrl     = "http://172.20.10.3:8888/upload_alarm";
const char* audioUrl     = "http://172.20.10.3:8888/upload_audio";

#define VOLUME_THRESHOLD 2000
#define SILENCE_SEC 1.2
#define MAX_REC_TIME 20

// ==================== 改进的运动/避障/摔倒参数 ====================
const unsigned long MOVE_CONFIRM_TIME = 2000;    // 持续运动2秒确认
const unsigned long STOP_CONFIRM_TIME = 3000;    // 持续静止3秒确认
const float ANGLE_DELTA_THRESHOLD = 3.0;          // 角度变化阈值

const int OBSTACLE_DANGER_DIST = 500;    // 危险距离(mm)
const int OBSTACLE_RECOVER_DIST = 800;   // 恢复距离(mm) - 关键：大于此值才解除报警
const unsigned long OBSTACLE_COOLDOWN = 5000;  // 报警冷却5秒

const float FALL_ANGLE_X = 55.0;   // X轴倾斜超过55度
const float FALL_ANGLE_Y = 50.0;   // Y轴倾斜超过50度
const unsigned long FALL_COOLDOWN = 10000;  // 摔倒报警冷却10秒

unsigned long playEndTime = 0;
const int PLAY_DURATION = 5000;

// ==================== 设备状态 ====================
enum DeviceStatus {
  WIFI_CONNECTING,
  DEVICE_REGISTERING,
  DEVICE_WAITING_BIND,
  DEVICE_ONLINE,
  DEVICE_OFFLINE
};
DeviceStatus currentStatus = WIFI_CONNECTING;

// ==================== 全局对象 ====================
Audio audio;
I2SClass i2s;
VL53L5CX myImager;
VL53L5CX_ResultsData measurementData;
MPU6050 mpu6050(Wire);

bool isRecording = false;
unsigned long silenceTimer;

// Base64 图片接收
bool photoReceiving = false;
String b64Buffer = "";
uint32_t imageLen = 0;
int photoCount = 0;

// 传感器
bool mpuExist = false;
bool lidarExist = false;
bool wifiOnline = false;

// ==================== 运动检测状态机 ====================
enum MoveState {
  STATE_STOPPED,        // 静止
  STATE_MOVING_CHECK,   // 检测到运动，确认中
  STATE_MOVING,         // 运动中（开启检测）
  STATE_STOPPING_CHECK  // 检测到静止，确认中
};
MoveState moveState = STATE_STOPPED;
unsigned long stateEnterTime = 0;

// ==================== 避障状态 ====================
bool obstacleAlarmActive = false;     // 当前是否在报警状态
unsigned long lastObstacleAlarm = 0;  // 上次报警时间
bool obstacleInDanger = false;        // 当前是否在危险距离内

// ==================== 摔倒状态 ====================
bool fallAlarmActive = false;
unsigned long lastFallAlarm = 0;

// GPS
struct BeiDouData {
  int hour, minute, second, day, month, year;
  float latitude, longitude, altitude;
  int satellites;
  char status;
};
BeiDouData beidouData;
String nmea_sentence;

float curAngleX = 0.0f;
float curAngleY = 0.0f;
float lastAngleX = 0.0f;
float lastAngleY = 0.0f;
int curLidarDist = 0;

// K210 方向
String kpuLineBuffer = "";

// ==================== Base64 解码 ====================
const char base64_chars[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

int base64_decode(const String& input, uint8_t* output) {
  int outLen = 0, i = 0, inLen = input.length();
  uint8_t buf[4];
  int bufIdx = 0;
  while (inLen--) {
    char c = input[i++];
    if (c == '=') break;
    const char* p = strchr(base64_chars, c);
    if (p == NULL) continue;
    buf[bufIdx++] = p - base64_chars;
    if (bufIdx == 4) {
      output[outLen++] = (buf[0] << 2) + ((buf[1] & 0x30) >> 4);
      output[outLen++] = ((buf[1] & 0x0F) << 4) + ((buf[2] & 0x3C) >> 2);
      output[outLen++] = ((buf[2] & 0x03) << 6) + buf[3];
      bufIdx = 0;
    }
  }
  if (bufIdx > 1) {
    output[outLen++] = (buf[0] << 2) + ((buf[1] & 0x30) >> 4);
    if (bufIdx > 2)
      output[outLen++] = ((buf[1] & 0x0F) << 4) + ((buf[2] & 0x3C) >> 2);
  }
  return outLen;
}

// ==================== 安全认证 ====================
String calculateAuthToken() {
  String token = DEVICE_UNIQUE_ID;
  String encoded = "";
  for (int i = 0; i < token.length(); i++)
    encoded += (char)(token[i] ^ AUTH_KEY);
  return encoded;
}

// ==================== 网络通信 ====================
void registerDevice() {
  HTTPClient http;
  http.begin(registerUrl);
  http.addHeader("Content-Type", "application/json");
  String postData = "{\"deviceId\":\"" + String(deviceId) + "\",\"uniqueCode\":\"" + String(DEVICE_UNIQUE_ID) + "\",\"token\":\"" + calculateAuthToken() + "\"}";
  http.POST(postData);
  http.end();
  currentStatus = DEVICE_WAITING_BIND;
}

void checkDeviceBind() {
  HTTPClient http;
  http.begin(String(checkBindUrl) + "?deviceId=" + String(deviceId));
  int code = http.GET();
  String res = http.getString();
  http.end();
  if (res.indexOf("bind_success") != -1)
    currentStatus = DEVICE_ONLINE;
}

void uploadImageToCloud(uint8_t* buf, int len) {
  if (currentStatus != DEVICE_ONLINE || !wifiOnline || buf == NULL || len == 0) return;
  HTTPClient http;
  http.begin(imgUrl);
  http.addHeader("Content-Type", "image/jpeg");
  http.POST(buf, len);
  http.end();
}

String getOcrText() {
  if (currentStatus != DEVICE_ONLINE || !wifiOnline) return "";
  HTTPClient http;
  http.begin(textUrl);
  int code = http.GET();
  String res = "";
  if (code == 200) res = http.getString();
  http.end();
  return res;
}

void uploadFallAlarm(float angleX, float angleY) {
  if (currentStatus != DEVICE_ONLINE || !wifiOnline) return;
  HTTPClient http;
  http.begin(alarmUrl);
  http.addHeader("Content-Type", "application/json");
  String json = "{\"deviceId\":\"" + String(deviceId) + "\",\"alarmType\":\"fall\",\"angleX\":" + String(angleX, 2) + ",\"angleY\":" + String(angleY, 2) + ",\"longitude\":" + String(beidouData.longitude, 6) + ",\"latitude\":" + String(beidouData.latitude, 6) + "}";
  http.POST(json);
  http.end();
}

void uploadObstacleAlarm(int dist) {
  if (currentStatus != DEVICE_ONLINE || !wifiOnline) return;
  HTTPClient http;
  http.begin(alarmUrl);
  http.addHeader("Content-Type", "application/json");
  String json = "{\"deviceId\":\"" + String(deviceId) + "\",\"alarmType\":\"obstacle\",\"lidarDist\":" + String(dist) + ",\"longitude\":" + String(beidouData.longitude, 6) + ",\"latitude\":" + String(beidouData.latitude, 6) + "}";
  http.POST(json);
  http.end();
}

// ==================== 音频 ====================
void playSound(const char* filename) {
  if (millis() < playEndTime) return;
  audio.connecttoFS(SPIFFS, filename);
  playEndTime = millis() + PLAY_DURATION;
}

void playSingleWord(String word) {
  if (millis() < playEndTime) return;
  String path = "/words/" + word + ".wav";
  audio.connecttoFS(SPIFFS, path.c_str());
  playEndTime = millis() + 400;
}

void textToVoice(String text) {
  for (int i = 0; i < text.length(); i++) {
    String ch = text.substring(i, i + 1);
    playSingleWord(ch);
    delay(420);
  }
}

// ==================== GPS ====================
void resetBeiDouData() {
  beidouData.hour = beidouData.minute = beidouData.second = 0;
  beidouData.day = beidouData.month = beidouData.year = 0;
  beidouData.latitude = beidouData.longitude = beidouData.altitude = 0.0;
  beidouData.satellites = 0;
  beidouData.status = 'V';
}

void parseRMC(String nmea) {
  int commaIndex[15], commaCount = 0;
  for (int i = 0; i < nmea.length() && commaCount < 15; i++)
    if (nmea.charAt(i) == ',') commaIndex[commaCount++] = i;
  if (commaCount < 11) return;
  beidouData.status = nmea.charAt(commaIndex[1] + 1);
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
    if (c == '\n') {
      if (nmea_sentence.startsWith("$")) parseNMEA(nmea_sentence);
      nmea_sentence = "";
    } else if (c != '\r') {
      nmea_sentence += c;
    }
  }
}

// ==================== K210 通信 ====================
void readK210Cmd() {
  while (kpuSerial.available()) {
    char c = kpuSerial.read();
    if (c == '\n') {
      String line = kpuLineBuffer;
      kpuLineBuffer = "";
      line.trim();
      if (line.length() == 0) continue;

      // 障碍物方向（运动中始终播报）
      if (line == "$LEFT!" || line == "$MID!" || line == "$RIGHT!") {
        if (moveState == STATE_MOVING && obstacleAlarmActive) {
          if (line == "$LEFT!") playSound("/left.wav");
          else if (line == "$MID!") playSound("/mid.wav");
          else if (line == "$RIGHT!") playSound("/right.wav");
        }
        continue;
      }

      // 图片开始
      if (line.startsWith("$IMG_START:")) {
        int p = line.indexOf(':');
        int e = line.indexOf('!');
        if (p != -1 && e != -1) {
          imageLen = line.substring(p + 1, e).toInt();
          b64Buffer = "";
          photoReceiving = true;
        }
        continue;
      }

      // 图片结束
      if (line == "$IMG_END!") {
        photoReceiving = false;
        photoCount++;
        int bufSize = b64Buffer.length();
        uint8_t* jpgBuf = (uint8_t*)malloc(bufSize);
        int jpgLen = base64_decode(b64Buffer, jpgBuf);
        if (jpgLen > 0 && jpgBuf[0] == 0xFF && jpgBuf[1] == 0xD8) {
          uploadImageToCloud(jpgBuf, jpgLen);
          String ocrText = getOcrText();
          if (ocrText.length() > 0) textToVoice(ocrText);
        }
        free(jpgBuf);
        b64Buffer = "";
        continue;
      }

      if (photoReceiving) {
        b64Buffer += line;
      }
    } else if (c != '\r') {
      kpuLineBuffer += c;
      if (kpuLineBuffer.length() > 256) kpuLineBuffer = "";
    }
  }
}

void takeK210Photo() {
  while (kpuSerial.available()) kpuSerial.read();
  kpuLineBuffer = "";
  b64Buffer = "";
  photoReceiving = false;
  delay(30);
  kpuSerial.print("$TAKE_PHOTO!\r\n");
  kpuSerial.flush();
}

// ==================== 运动检测（状态机）====================
void updateMotionState() {
  mpu6050.update();
  curAngleX = mpu6050.getAngleX();
  curAngleY = mpu6050.getAngleY();

  float deltaX = abs(curAngleX - lastAngleX);
  float deltaY = abs(curAngleY - lastAngleY);
  bool isMoving = (deltaX + deltaY) > ANGLE_DELTA_THRESHOLD;

  lastAngleX = curAngleX;
  lastAngleY = curAngleY;

  switch (moveState) {
    case STATE_STOPPED:
      if (isMoving) {
        moveState = STATE_MOVING_CHECK;
        stateEnterTime = millis();
      }
      break;

    case STATE_MOVING_CHECK:
      if (!isMoving) {
        moveState = STATE_STOPPED;  // 短暂抖动，回静止
      } else if (millis() - stateEnterTime >= MOVE_CONFIRM_TIME) {
        moveState = STATE_MOVING;   // 确认运动
        Serial.println("[运动] 开始，开启检测");
      }
      break;

    case STATE_MOVING:
      if (!isMoving) {
        moveState = STATE_STOPPING_CHECK;
        stateEnterTime = millis();
      }
      break;

    case STATE_STOPPING_CHECK:
      if (isMoving) {
        moveState = STATE_MOVING;   // 又动了，回运动
      } else if (millis() - stateEnterTime >= STOP_CONFIRM_TIME) {
        moveState = STATE_STOPPED;  // 确认静止
        obstacleAlarmActive = false;
        fallAlarmActive = false;
        Serial.println("[静止] 停止，关闭检测");
      }
      break;
  }
}

// ==================== 避障检测 ====================
void obstacleDetect() {
  if (moveState != STATE_MOVING || !lidarExist) return;

  if (!myImager.isDataReady()) return;
  myImager.getRangingData(&measurementData);

  int minDis = 9999;
  for (int i = 0; i < 64; i++) {
    if (measurementData.distance_mm[i] > 10)
      minDis = min(minDis, (int)measurementData.distance_mm[i]);
  }
  curLidarDist = minDis;

  // 判断是否进入/离开危险距离
  if (minDis < OBSTACLE_DANGER_DIST) {
    obstacleInDanger = true;
  } else if (minDis > OBSTACLE_RECOVER_DIST) {
    obstacleInDanger = false;
    obstacleAlarmActive = false;  // 恢复后可再次报警
  }

  // 触发报警
  if (obstacleInDanger && !obstacleAlarmActive) {
    unsigned long now = millis();
    if (now - lastObstacleAlarm > OBSTACLE_COOLDOWN) {
      playSound("/evasion.wav");
      uploadObstacleAlarm(minDis);
      obstacleAlarmActive = true;
      lastObstacleAlarm = now;
      Serial.printf("[避障] 距离:%dmm\n", minDis);
    }
  }
}

// ==================== 摔倒检测 ====================
void fallDetect() {
  if (moveState != STATE_MOVING) return;

  float absX = abs(curAngleX);
  float absY = abs(curAngleY);
  bool isFall = (absX > FALL_ANGLE_X) || (absY > FALL_ANGLE_Y);

  if (isFall && !fallAlarmActive) {
    unsigned long now = millis();
    if (now - lastFallAlarm > FALL_COOLDOWN) {
      playSound("/fall.wav");
      uploadFallAlarm(curAngleX, curAngleY);
      fallAlarmActive = true;
      lastFallAlarm = now;
      Serial.printf("[摔倒] X:%.1f Y:%.1f\n", curAngleX, curAngleY);
    }
  }

  // 角度恢复正常后解除
  if (!isFall && fallAlarmActive) {
    fallAlarmActive = false;
  }
}

// ==================== 录音上传 ====================
void recordAndUpload() {
  if (currentStatus != DEVICE_ONLINE) return;
  uint8_t *wav_buf;
  size_t wav_len;
  wav_buf = i2s.recordWAV(MAX_REC_TIME, &wav_len);
  if (wifiOnline && wav_buf != nullptr) {
    HTTPClient http;
    http.begin(audioUrl);
    http.addHeader("Content-Type", "application/octet-stream");
    http.POST(wav_buf, wav_len);
    http.end();
  }
}

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  pinMode(BTN_A, INPUT_PULLUP);

  pinMode(KPU_TX, OUTPUT);
  digitalWrite(KPU_TX, HIGH);
  delay(300);

  kpuSerial.begin(115200, SERIAL_8N1, KPU_RX, KPU_TX);
  gpsSerial.begin(115200, SERIAL_8N1, RXD1, TXD1);

  SPIFFS.begin(true);
  SD.begin(SD_CS);

  audio.setPinout(I2S_BCLK, I2S_LRC, I2S_DOUT);
  audio.setVolume(21);

  i2s.setPinsPdmRx(42, 41);
  i2s.begin(I2S_MODE_PDM_RX, 16000, I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_MONO);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) delay(200);
  currentStatus = DEVICE_REGISTERING;
  registerDevice();

  Wire.begin();
  mpu6050.begin();
  mpu6050.calcGyroOffsets(true);
  mpuExist = true;

  lidarExist = myImager.begin();
  if (lidarExist) {
    myImager.setResolution(8 * 8);
    myImager.startRanging();
  }

  resetBeiDouData();
  Serial.println("READY");
}

// ==================== LOOP ====================
void loop() {
  audio.loop();
  wifiOnline = (WiFi.status() == WL_CONNECTED);

  static unsigned long lastCheck = 0;
  if (millis() - lastCheck > 2000) {
    lastCheck = millis();
    if (currentStatus == DEVICE_WAITING_BIND)
      checkDeviceBind();
  }

  // 按键
  static bool lastBtn = HIGH;
  bool nowBtn = digitalRead(BTN_A);
  if (nowBtn == LOW && lastBtn == HIGH) {
    delay(50);
    if (digitalRead(BTN_A) == LOW)
      takeK210Photo();
  }
  lastBtn = nowBtn;

  readK210Cmd();
  readBeiDouData();

  // 运动状态更新
  updateMotionState();

  // 运动中才检测
  if (moveState == STATE_MOVING) {
    obstacleDetect();
    fallDetect();
  }

  // 录音
  int16_t sample = i2s.read();
  if (sample != -1) {
    int vol = abs(sample);
    if (vol > VOLUME_THRESHOLD) {
      silenceTimer = millis();
      isRecording = true;
    }
    if (isRecording && (millis() - silenceTimer) > SILENCE_SEC * 1000) {
      recordAndUpload();
      isRecording = false;
    }
  }

  delay(5);
}