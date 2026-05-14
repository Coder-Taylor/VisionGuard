// ============================================================
// VisionGuard TypeScript 类型定义 — 对齐 Android ApiModels.kt
// ============================================================

// ---- 通用响应封装 ----
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PaginatedData<T> {
  total: number;
  page: number;
  pageSize: number;
  list: T[];
}

// ---- 认证 ----
export interface LoginReq {
  username: string;
  password: string;
}

export interface RegisterReq {
  username: string;
  password: string;
  phone?: string;
  email?: string;
}

export interface AuthData {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userId: number;
  displayName: string;
  phone: string;
}

export interface ChangePasswordReq {
  oldPassword: string;
  newPassword: string;
}

export interface ResetPasswordReq {
  phone: string;
  code: string;
  newPassword: string;
}

// ---- 用户 ----
export interface UserProfileData {
  userId: number;
  username: string;
  displayName: string;
  phone: string;
  email: string;
  role?: string; // 前端根据 email 推断，VG 后端暂不返回
}

export interface UpdateProfileReq {
  displayName?: string;
  phone?: string;
}

// ---- 老人 ----
export interface ElderData {
  elderId: string;
  name: string;
  gender: string;
  age: number;
  bloodType: string;
  allergy: string;
  medicalHistory: string;
  status: string;
  deviceOnline: boolean;
  deviceId: string;
}

export interface GuardianInfo {
  userId: number;
  nickname: string;
  phone: string;
  role: string;
}

export interface ElderDetailData extends ElderData {
  birthday: string;
  lunarBirthday: string;
  guardians: GuardianInfo[];
  emergencyContacts: EmergencyContactInfo[];
}

export interface EmergencyContactInfo {
  contactId: number;
  name: string;
  relation: string;
  phone: string;
}

export interface CreateElderReq {
  name: string;
  gender: string;
  birthday: string;
  lunarBirthday?: string;
  bloodType?: string;
  allergy?: string;
  medicalHistory?: string;
}

export interface UpdateElderReq extends Partial<CreateElderReq> {}

export interface DashboardData {
  elderCount: number;
  onlineDeviceCount: number;
  alertCount24h: number;
  total: number;
  elders: ElderData[];
}

// ---- 设备 ----
export interface DeviceData {
  deviceId: string;
  serialNo: string;
  model: string;
  mac: string;
  status: string;
  bindStatus: string;
  battery: number;
  rssi: number;
  online: boolean;
  lastOnline: string;
  elderId: string;
}

export interface SearchDeviceData {
  deviceId: string;
  serialNo: string;
  model: string;
  status: string;
  canBind: boolean;
}

export interface InitiateBindingReq {
  deviceId: string;
  elderId: string;
}

export interface UnbindReq {
  deviceId: string;
}

// ---- 告警 ----
export type AlertType = 'fall' | 'obstacle' | 'sos' | 'heart_rate' | 'low_battery' | 'offline' | 'geofence';
export type AlertLevel = 'P0' | 'P1' | 'P2' | 'P3';
export type AlertStatus = 'pending' | 'confirmed' | 'resolved' | 'closed';

export interface AlertData {
  alertId: string;
  deviceId: string;
  elderId: string;
  alertType: AlertType;
  alertLevel: AlertLevel;
  status: AlertStatus;
  description: string;
  createdAt: string;
}

export interface AlertDetailData extends AlertData {
  elderName: string;
  deviceModel: string;
  timeline: TimelineEntry[];
  resolution: string;
}

export interface TimelineEntry {
  at: string;
  action: string;
  by: string;
}

export interface AlertStatisticsData {
  total: number;
  byType: Record<string, number>;
  byLevel: Record<string, number>;
  byStatus: Record<string, number>;
}

export interface UpdateAlertStatusReq {
  action: string;
  comment?: string;
}

// ---- 通知 ----
export interface NotificationData {
  messageId: string;
  title: string;
  body: string;
  type: string;
  priority: string;
  read: boolean;
  createdAt: string;
}

// ---- 定位 ----
export interface LocationData {
  lat: number;
  lng: number;
  accuracy: number;
  speed: number;
  heading: number;
  createdAt: string;
  deviceId: string;
}

export interface TrajectoryPoint {
  lat: number;
  lng: number;
  timestamp: string;
}

// ---- OCR ----
export interface OcrRecordData {
  taskId: string;
  imageId: string;
  thumbnailUrl: string;
  medicineName: string;
  ocrText: string;
  riskLevel: string;
  status: string;
  createdAt: string;
}

export interface OcrResultData {
  taskId: string;
  status: string;
  imageId: string;
  medicineName?: string;
  ocrText?: string;
  specification?: string;
  indications?: string;
  dosage?: string;
  contraindications?: string;
  confidence?: number;
  riskLevel?: string;
  failReason?: string;
  failDetail?: string;
  suggestion?: string;
}

// ---- 用药计划 ----
export interface MedicationPlanData {
  planId: string;
  elderId: string;
  drugName: string;
  dosage: string;
  frequency: string;
  schedule: string[];
  startDate: string;
  endDate: string;
  status: string;
}

export interface CreateMedicationPlanReq {
  elderId: string;
  drugName: string;
  dosage: string;
  frequency: string;
  schedule: string[];
  startDate: string;
  endDate: string;
}
