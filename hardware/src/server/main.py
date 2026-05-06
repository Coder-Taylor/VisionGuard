from __future__ import annotations

import hashlib
import json
import logging
import os
import time
import uuid
from pathlib import Path
from typing import Any

from fastapi import FastAPI, Header, Request
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import BaseModel, Field

AUTH_KEY = 0x4B
EXPECTED_UNIQUE_CODE = "DEVICE_2026_ESP32_K210"
DEFAULT_OCR_TEXT = "药品图片已收到，正在生成用药建议"
DATABASE_URL = os.getenv("DATABASE_URL", "").strip()
AUTO_BIND_ON_REGISTER = os.getenv("AUTO_BIND_ON_REGISTER", "0").strip() == "1"
REQUIRE_POSTGRES = os.getenv("REQUIRE_POSTGRES", "0").strip() == "1"
DEVICE_OFFLINE_AFTER_MS = int(os.getenv("DEVICE_OFFLINE_AFTER_MS", "300000"))

PROJECT_ROOT = Path(__file__).resolve().parents[2]
RUNTIME_DIR = PROJECT_ROOT / "runtime"
UPLOADS_DIR = RUNTIME_DIR / "uploads"
IMAGES_DIR = UPLOADS_DIR / "images"
AUDIO_DIR = UPLOADS_DIR / "audio"
LOGS_DIR = RUNTIME_DIR / "logs"
STATE_PATH = RUNTIME_DIR / "state.json"
EVENTS_PATH = LOGS_DIR / "events.jsonl"

app = FastAPI(title="Smart Chest Device Cloud Server", version="1.1.0")
logger = logging.getLogger("device_server")

devices: dict[str, dict[str, Any]] = {}
users: dict[str, dict[str, Any]] = {}
sessions: dict[str, str] = {}
bindings: dict[str, str] = {}
alarms: list[dict[str, Any]] = []
locations: list[dict[str, Any]] = []
image_uploads: list[dict[str, Any]] = []
audio_uploads: list[dict[str, Any]] = []
medicine_records: list[dict[str, Any]] = []
latest_ocr_text_by_device: dict[str, str] = {}
postgres_enabled = False


class DeviceRegisterBody(BaseModel):
    deviceId: str = Field(..., min_length=1)
    uniqueCode: str = Field(..., min_length=1)
    token: str = Field(..., min_length=1)


class HeartbeatBody(BaseModel):
    deviceId: str = Field(..., min_length=1)
    battery: int | None = None
    firmwareVersion: str | None = None


class LocationBody(BaseModel):
    deviceId: str = Field(..., min_length=1)
    longitude: float
    latitude: float
    satellites: int | None = None


class AppRegisterBody(BaseModel):
    account: str = Field(..., min_length=1)
    password: str = Field(..., min_length=6)
    phone: str | None = None
    elderInfo: dict[str, Any] | str | None = None


class AppLoginBody(BaseModel):
    account: str = Field(..., min_length=1)
    password: str = Field(..., min_length=1)


class AppPasswordResetBody(BaseModel):
    account: str = Field(..., min_length=1)
    oldPassword: str = Field(..., min_length=1)
    newPassword: str = Field(..., min_length=6)


class ElderInfoBody(BaseModel):
    account: str = Field(..., min_length=1)
    elderInfo: dict[str, Any] | str


class BindBody(BaseModel):
    account: str = Field(..., min_length=1)
    deviceId: str = Field(..., min_length=1)


def ensure_dirs() -> None:
    for path in (RUNTIME_DIR, IMAGES_DIR, AUDIO_DIR, LOGS_DIR):
        path.mkdir(parents=True, exist_ok=True)


def now_ms() -> int:
    return int(time.time() * 1000)


def json_dumps(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, separators=(",", ":"))


def append_event(event_type: str, payload: dict[str, Any]) -> None:
    ensure_dirs()
    with EVENTS_PATH.open("a", encoding="utf-8") as file:
        file.write(json_dumps({"type": event_type, "time": now_ms(), **payload}) + "\n")


def save_state() -> None:
    ensure_dirs()
    STATE_PATH.write_text(
        json.dumps(
            {
                "devices": devices,
                "users": users,
                "bindings": bindings,
                "updatedAt": now_ms(),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def calc_token(unique_code: str = EXPECTED_UNIQUE_CODE) -> str:
    return "".join(chr(ord(char) ^ AUTH_KEY) for char in unique_code)


def verify_token(unique_code: str, client_token: str) -> bool:
    expected = calc_token(unique_code)
    return client_token == expected or client_token == expected.encode("latin1", errors="ignore").hex()


def hash_password(password: str, salt: str | None = None) -> str:
    real_salt = salt or uuid.uuid4().hex
    digest = hashlib.sha256(f"{real_salt}:{password}".encode("utf-8")).hexdigest()
    return f"{real_salt}${digest}"


def verify_password(password: str, stored: str) -> bool:
    try:
        salt, _ = stored.split("$", 1)
    except ValueError:
        return False
    return hash_password(password, salt) == stored


def db_connect():
    if not DATABASE_URL:
        return None
    import psycopg

    return psycopg.connect(DATABASE_URL)


def init_db() -> None:
    global postgres_enabled
    if not DATABASE_URL:
        postgres_enabled = False
        if REQUIRE_POSTGRES:
            raise RuntimeError("REQUIRE_POSTGRES=1 but DATABASE_URL is empty")
        return

    try:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS devices (
                        device_id TEXT PRIMARY KEY,
                        unique_code TEXT NOT NULL,
                        is_bound BOOLEAN NOT NULL DEFAULT FALSE,
                        online BOOLEAN NOT NULL DEFAULT TRUE,
                        registered_at BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL
                    )
                    """
                )
                cur.execute("ALTER TABLE devices ADD COLUMN IF NOT EXISTS online BOOLEAN NOT NULL DEFAULT TRUE")
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS app_users (
                        account TEXT PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        phone TEXT,
                        elder_info JSONB,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS device_bindings (
                        device_id TEXT PRIMARY KEY,
                        account TEXT NOT NULL REFERENCES app_users(account) ON DELETE CASCADE,
                        created_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS locations (
                        id BIGSERIAL PRIMARY KEY,
                        device_id TEXT NOT NULL,
                        longitude DOUBLE PRECISION NOT NULL,
                        latitude DOUBLE PRECISION NOT NULL,
                        satellites INTEGER,
                        uploaded_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS alarms (
                        id BIGSERIAL PRIMARY KEY,
                        device_id TEXT,
                        account TEXT,
                        alarm_type TEXT,
                        longitude DOUBLE PRECISION,
                        latitude DOUBLE PRECISION,
                        payload JSONB NOT NULL,
                        uploaded_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute("ALTER TABLE alarms ADD COLUMN IF NOT EXISTS account TEXT")
                cur.execute("ALTER TABLE alarms ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION")
                cur.execute("ALTER TABLE alarms ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION")
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS image_uploads (
                        id BIGSERIAL PRIMARY KEY,
                        device_id TEXT,
                        account TEXT,
                        file_path TEXT NOT NULL,
                        byte_size BIGINT NOT NULL,
                        uploaded_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute("ALTER TABLE image_uploads ADD COLUMN IF NOT EXISTS account TEXT")
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS audio_uploads (
                        id BIGSERIAL PRIMARY KEY,
                        device_id TEXT,
                        account TEXT,
                        file_path TEXT NOT NULL,
                        byte_size BIGINT NOT NULL,
                        uploaded_at BIGINT NOT NULL
                    )
                    """
                )
                cur.execute("ALTER TABLE audio_uploads ADD COLUMN IF NOT EXISTS account TEXT")
                cur.execute(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_recognitions (
                        id BIGSERIAL PRIMARY KEY,
                        device_id TEXT,
                        account TEXT,
                        image_path TEXT,
                        ocr_text TEXT NOT NULL,
                        llm_advice TEXT NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """
                )
            conn.commit()
    except Exception:
        postgres_enabled = False
        if REQUIRE_POSTGRES:
            raise
        logger.exception("PostgreSQL init failed; falling back to memory mode")
        return
    postgres_enabled = True


@app.on_event("startup")
def on_startup() -> None:
    ensure_dirs()
    init_db()


def ok(data: dict[str, Any] | None = None) -> JSONResponse:
    return JSONResponse({"code": 200, "msg": "ok", **(data or {})})


def fail(msg: str, status_code: int = 400, code: int | None = None) -> JSONResponse:
    return JSONResponse({"code": code or status_code, "msg": msg}, status_code=status_code)


def get_account_from_token(auth_header: str | None) -> str | None:
    if not auth_header:
        return None
    prefix = "Bearer "
    token = auth_header[len(prefix) :] if auth_header.startswith(prefix) else auth_header
    return sessions.get(token)


def normalize_elder_info(value: dict[str, Any] | str | None) -> dict[str, Any] | None:
    if value is None:
        return None
    if isinstance(value, dict):
        return value
    return {"text": value}


def account_for_device(device_id: str | None) -> str | None:
    if not device_id:
        return None
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT account FROM device_bindings WHERE device_id = %s", (device_id,))
                row = cur.fetchone()
                return row[0] if row else None
    return bindings.get(device_id)


def resolve_device_id(request: Request) -> str | None:
    device_id = request.query_params.get("deviceId") or request.headers.get("X-Device-Id")
    if device_id:
        return device_id
    if len(devices) == 1:
        return next(iter(devices.keys()))
    return None


def db_upsert_device(device_id: str, unique_code: str, is_bound: bool) -> None:
    if not postgres_enabled:
        return
    timestamp = now_ms()
    with db_connect() as conn:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO devices (device_id, unique_code, is_bound, online, registered_at, last_seen)
                VALUES (%s, %s, %s, TRUE, %s, %s)
                ON CONFLICT (device_id)
                DO UPDATE SET unique_code = EXCLUDED.unique_code,
                              online = TRUE,
                              last_seen = EXCLUDED.last_seen
                """,
                (device_id, unique_code, is_bound, timestamp, timestamp),
            )
            if is_bound:
                cur.execute("UPDATE devices SET is_bound = TRUE WHERE device_id = %s", (device_id,))
        conn.commit()


def db_mark_seen(device_id: str) -> None:
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("UPDATE devices SET online = TRUE, last_seen = %s WHERE device_id = %s", (now_ms(), device_id))
            conn.commit()
    elif device_id in devices:
        devices[device_id]["online"] = True
        devices[device_id]["lastSeen"] = now_ms()


def db_is_bound(device_id: str) -> bool:
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1 FROM device_bindings WHERE device_id = %s", (device_id,))
                has_binding = cur.fetchone() is not None
                cur.execute("SELECT is_bound FROM devices WHERE device_id = %s", (device_id,))
                row = cur.fetchone()
                is_bound = has_binding or bool(row and row[0])
                if is_bound:
                    cur.execute("UPDATE devices SET is_bound = TRUE, online = TRUE, last_seen = %s WHERE device_id = %s", (now_ms(), device_id))
                    conn.commit()
                return is_bound
    return device_id in bindings or bool(devices.get(device_id, {}).get("isBound"))


def db_insert_alarm(payload: dict[str, Any]) -> None:
    device_id = payload.get("deviceId")
    account = account_for_device(device_id)
    timestamp = now_ms()
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO alarms (device_id, account, alarm_type, longitude, latitude, payload, uploaded_at)
                    VALUES (%s, %s, %s, %s, %s, %s::jsonb, %s)
                    """,
                    (
                        device_id,
                        account,
                        payload.get("alarmType"),
                        payload.get("longitude"),
                        payload.get("latitude"),
                        json.dumps(payload, ensure_ascii=False),
                        timestamp,
                    ),
                )
            conn.commit()
    alarms.append({"account": account, "uploadedAt": timestamp, **payload})


def db_insert_file(table: str, device_id: str | None, path: Path, byte_size: int) -> None:
    if table not in {"image_uploads", "audio_uploads"}:
        return
    account = account_for_device(device_id)
    timestamp = now_ms()
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    f"INSERT INTO {table} (device_id, account, file_path, byte_size, uploaded_at) VALUES (%s, %s, %s, %s, %s)",
                    (device_id, account, str(path), byte_size, timestamp),
                )
            conn.commit()
    target = image_uploads if table == "image_uploads" else audio_uploads
    target.append({"deviceId": device_id, "account": account, "path": str(path), "bytes": byte_size, "uploadedAt": timestamp})


def db_insert_location(data: LocationBody) -> None:
    timestamp = now_ms()
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO locations (device_id, longitude, latitude, satellites, uploaded_at)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (data.deviceId, data.longitude, data.latitude, data.satellites, timestamp),
                )
            conn.commit()
    locations.append({**data.model_dump(), "uploadedAt": timestamp})


def db_insert_medicine(device_id: str | None, image_path: Path, advice: str) -> None:
    if not device_id:
        return
    account = account_for_device(device_id)
    latest_ocr_text_by_device[device_id] = advice
    timestamp = now_ms()
    record = {
        "deviceId": device_id,
        "account": account,
        "imagePath": str(image_path),
        "ocrText": "药品图片已上传，OCR服务待接入",
        "llmAdvice": advice,
        "createdAt": timestamp,
    }
    medicine_records.append(record)
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO medicine_recognitions (device_id, account, image_path, ocr_text, llm_advice, created_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (device_id, account, str(image_path), record["ocrText"], advice, timestamp),
                )
            conn.commit()


def latest_for_device(device_id: str) -> dict[str, Any]:
    device_alarms = [item for item in alarms if item.get("deviceId") == device_id]
    device_locations = [item for item in locations if item.get("deviceId") == device_id]
    return {
        "device": devices.get(device_id, {"deviceId": device_id}),
        "bindingAccount": bindings.get(device_id) or account_for_device(device_id),
        "latestAlarm": device_alarms[-1] if device_alarms else None,
        "latestLocation": device_locations[-1] if device_locations else None,
        "latestOcrText": latest_ocr_text_by_device.get(device_id, DEFAULT_OCR_TEXT),
    }


@app.get("/")
def root() -> dict[str, Any]:
    return {"code": 200, "msg": "ok", "version": app.version, "db": "postgresql" if postgres_enabled else "memory"}


@app.get("/health")
def health() -> dict[str, Any]:
    return {"code": 200, "msg": "ok", "db": "postgresql" if postgres_enabled else "memory", "time": now_ms()}


@app.post("/device_register")
def device_register(body: DeviceRegisterBody) -> JSONResponse:
    if body.uniqueCode != EXPECTED_UNIQUE_CODE:
        append_event("device_register_failed", {"deviceId": body.deviceId, "reason": "bad_uniqueCode"})
        return fail("bad uniqueCode", status_code=403)
    if not verify_token(body.uniqueCode, body.token):
        append_event("device_register_failed", {"deviceId": body.deviceId, "reason": "bad_token"})
        return fail("bad token", status_code=403)

    is_bound = AUTO_BIND_ON_REGISTER or body.deviceId in bindings
    timestamp = now_ms()
    devices[body.deviceId] = {
        "deviceId": body.deviceId,
        "uniqueCode": body.uniqueCode,
        "isBound": is_bound,
        "online": True,
        "registeredAt": timestamp,
        "lastSeen": timestamp,
    }
    db_upsert_device(body.deviceId, body.uniqueCode, is_bound)
    save_state()
    append_event("device_register", {"deviceId": body.deviceId, "isBound": is_bound})
    return ok()


@app.get("/check_bind", response_class=PlainTextResponse)
def check_bind(deviceId: str = "") -> str:
    if not deviceId:
        return "not_bind"
    db_mark_seen(deviceId)
    if db_is_bound(deviceId):
        return "bind_success"
    save_state()
    return "not_bind"


@app.post("/device_heartbeat")
def device_heartbeat(body: HeartbeatBody) -> JSONResponse:
    db_mark_seen(body.deviceId)
    devices.setdefault(body.deviceId, {"deviceId": body.deviceId})
    devices[body.deviceId].update({"online": True, "lastSeen": now_ms(), "battery": body.battery, "firmwareVersion": body.firmwareVersion})
    append_event("device_heartbeat", body.model_dump())
    save_state()
    return ok()


@app.post("/upload_location")
def upload_location(body: LocationBody) -> JSONResponse:
    db_mark_seen(body.deviceId)
    db_insert_location(body)
    append_event("upload_location", body.model_dump())
    save_state()
    return ok()


@app.post("/upload_image")
async def upload_image(request: Request) -> JSONResponse:
    body = await request.body()
    if not body:
        return fail("empty image")

    filename = f"{now_ms()}_{uuid.uuid4().hex[:8]}.jpg"
    path = IMAGES_DIR / filename
    ensure_dirs()
    path.write_bytes(body)

    device_id = resolve_device_id(request)
    if device_id:
        db_mark_seen(device_id)
    db_insert_file("image_uploads", device_id, path, len(body))
    advice = "药品图片已上传，云端OCR和用药建议服务待接入。请按药品说明书或医生医嘱服用。"
    db_insert_medicine(device_id, path, advice)
    append_event("upload_image", {"deviceId": device_id, "path": str(path), "bytes": len(body)})
    return ok()


@app.get("/get_ocr_text", response_class=PlainTextResponse)
def get_ocr_text(deviceId: str = "") -> str:
    if deviceId and deviceId in latest_ocr_text_by_device:
        return latest_ocr_text_by_device[deviceId]
    if latest_ocr_text_by_device:
        return next(reversed(latest_ocr_text_by_device.values()))
    return DEFAULT_OCR_TEXT


@app.post("/upload_alarm")
async def upload_alarm(request: Request) -> JSONResponse:
    try:
        payload = await request.json()
        if not isinstance(payload, dict):
            payload = {"raw": payload}
    except Exception:
        raw = await request.body()
        payload = {"raw": raw.decode("utf-8", errors="replace")}

    payload["receivedAt"] = now_ms()
    if payload.get("deviceId"):
        db_mark_seen(str(payload["deviceId"]))
    db_insert_alarm(payload)
    append_event("upload_alarm", payload)
    save_state()
    return ok()


@app.post("/upload_audio")
async def upload_audio(request: Request) -> JSONResponse:
    body = await request.body()
    if not body:
        return fail("empty audio")

    filename = f"{now_ms()}_{uuid.uuid4().hex[:8]}.wav"
    path = AUDIO_DIR / filename
    ensure_dirs()
    path.write_bytes(body)

    device_id = resolve_device_id(request)
    if device_id:
        db_mark_seen(device_id)
    db_insert_file("audio_uploads", device_id, path, len(body))
    append_event("upload_audio", {"deviceId": device_id, "path": str(path), "bytes": len(body)})
    return ok()


@app.post("/app/register")
def app_register(body: AppRegisterBody) -> JSONResponse:
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1 FROM app_users WHERE account = %s", (body.account,))
                if cur.fetchone():
                    return fail("account exists", status_code=409)
                cur.execute(
                    """
                    INSERT INTO app_users (account, password_hash, phone, elder_info, created_at, updated_at)
                    VALUES (%s, %s, %s, %s::jsonb, %s, %s)
                    """,
                    (
                        body.account,
                        hash_password(body.password),
                        body.phone,
                        json.dumps(normalize_elder_info(body.elderInfo), ensure_ascii=False),
                        now_ms(),
                        now_ms(),
                    ),
                )
            conn.commit()
    elif body.account in users:
        return fail("account exists", status_code=409)

    users[body.account] = {
        "account": body.account,
        "passwordHash": hash_password(body.password),
        "phone": body.phone,
        "elderInfo": normalize_elder_info(body.elderInfo),
        "createdAt": now_ms(),
        "updatedAt": now_ms(),
    }
    save_state()
    return ok()


@app.post("/app/login")
def app_login(body: AppLoginBody) -> JSONResponse:
    stored_hash: str | None = None
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT password_hash FROM app_users WHERE account = %s", (body.account,))
                row = cur.fetchone()
                stored_hash = row[0] if row else None
    else:
        stored_hash = users.get(body.account, {}).get("passwordHash")
    if not stored_hash or not verify_password(body.password, stored_hash):
        return fail("account or password error", status_code=401)
    token = uuid.uuid4().hex
    sessions[token] = body.account
    return ok({"token": token, "account": body.account})


@app.post("/app/password/reset")
def app_password_reset(body: AppPasswordResetBody) -> JSONResponse:
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT password_hash FROM app_users WHERE account = %s", (body.account,))
                row = cur.fetchone()
                if not row or not verify_password(body.oldPassword, row[0]):
                    return fail("account or old password error", status_code=401)
                cur.execute("UPDATE app_users SET password_hash = %s, updated_at = %s WHERE account = %s", (hash_password(body.newPassword), now_ms(), body.account))
            conn.commit()
    else:
        user = users.get(body.account)
        if not user or not verify_password(body.oldPassword, user["passwordHash"]):
            return fail("account or old password error", status_code=401)
        user["passwordHash"] = hash_password(body.newPassword)
        user["updatedAt"] = now_ms()
    save_state()
    return ok()


@app.post("/app/elder")
def app_update_elder(body: ElderInfoBody, authorization: str | None = Header(default=None)) -> JSONResponse:
    token_account = get_account_from_token(authorization)
    if token_account and token_account != body.account:
        return fail("account mismatch", status_code=403)
    elder_info = normalize_elder_info(body.elderInfo)
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("UPDATE app_users SET elder_info = %s::jsonb, updated_at = %s WHERE account = %s", (json.dumps(elder_info, ensure_ascii=False), now_ms(), body.account))
                if cur.rowcount == 0:
                    return fail("account not found", status_code=404)
            conn.commit()
    else:
        if body.account not in users:
            return fail("account not found", status_code=404)
        users[body.account]["elderInfo"] = elder_info
        users[body.account]["updatedAt"] = now_ms()
    save_state()
    return ok()


@app.get("/app/devices/online")
def app_online_devices() -> JSONResponse:
    cutoff = now_ms() - DEVICE_OFFLINE_AFTER_MS
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT d.device_id, d.is_bound, d.last_seen, b.account
                    FROM devices d
                    LEFT JOIN device_bindings b ON d.device_id = b.device_id
                    WHERE d.last_seen >= %s
                    ORDER BY d.last_seen DESC
                    """,
                    (cutoff,),
                )
                rows = cur.fetchall()
        return ok({"devices": [{"deviceId": r[0], "isBound": bool(r[1] or r[3]), "lastSeen": r[2], "account": r[3]} for r in rows]})

    result = []
    for device_id, item in devices.items():
        if item.get("lastSeen", 0) >= cutoff:
            result.append({**item, "isBound": device_id in bindings or item.get("isBound", False), "account": bindings.get(device_id)})
    return ok({"devices": result})


@app.post("/app/device/bind")
def app_bind_device(body: BindBody, authorization: str | None = Header(default=None)) -> JSONResponse:
    token_account = get_account_from_token(authorization)
    if token_account and token_account != body.account:
        return fail("account mismatch", status_code=403)
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1 FROM app_users WHERE account = %s", (body.account,))
                if not cur.fetchone():
                    return fail("account not found", status_code=404)
                cur.execute("SELECT account FROM device_bindings WHERE device_id = %s", (body.deviceId,))
                row = cur.fetchone()
                if row and row[0] != body.account:
                    return fail("device already bound", status_code=409)
                cur.execute(
                    """
                    INSERT INTO device_bindings (device_id, account, created_at)
                    VALUES (%s, %s, %s)
                    ON CONFLICT (device_id) DO UPDATE SET account = EXCLUDED.account
                    """,
                    (body.deviceId, body.account, now_ms()),
                )
                cur.execute("UPDATE devices SET is_bound = TRUE WHERE device_id = %s", (body.deviceId,))
            conn.commit()
    elif body.deviceId in bindings and bindings[body.deviceId] != body.account:
        return fail("device already bound", status_code=409)

    bindings[body.deviceId] = body.account
    devices.setdefault(body.deviceId, {"deviceId": body.deviceId})
    devices[body.deviceId]["isBound"] = True
    save_state()
    append_event("device_bind", body.model_dump())
    return ok()


@app.post("/app/device/unbind")
def app_unbind_device(body: BindBody, authorization: str | None = Header(default=None)) -> JSONResponse:
    token_account = get_account_from_token(authorization)
    if token_account and token_account != body.account:
        return fail("account mismatch", status_code=403)
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute("DELETE FROM device_bindings WHERE device_id = %s AND account = %s", (body.deviceId, body.account))
                cur.execute("UPDATE devices SET is_bound = FALSE WHERE device_id = %s", (body.deviceId,))
            conn.commit()
    if bindings.get(body.deviceId) == body.account:
        del bindings[body.deviceId]
    if body.deviceId in devices:
        devices[body.deviceId]["isBound"] = False
    save_state()
    append_event("device_unbind", body.model_dump())
    return ok()


@app.get("/app/alarms")
def app_alarms(account: str, limit: int = 50) -> JSONResponse:
    if postgres_enabled:
        with db_connect() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT id, device_id, alarm_type, longitude, latitude, payload, uploaded_at
                    FROM alarms
                    WHERE account = %s
                    ORDER BY uploaded_at DESC
                    LIMIT %s
                    """,
                    (account, limit),
                )
                rows = cur.fetchall()
        return ok(
            {
                "alarms": [
                    {
                        "id": r[0],
                        "deviceId": r[1],
                        "alarmType": r[2],
                        "longitude": r[3],
                        "latitude": r[4],
                        "payload": r[5],
                        "uploadedAt": r[6],
                    }
                    for r in rows
                ]
            }
        )
    return ok({"alarms": list(reversed([item for item in alarms if item.get("account") == account]))[:limit]})


@app.get("/app/device/{device_id}/latest")
def app_device_latest(device_id: str) -> JSONResponse:
    return ok(latest_for_device(device_id))
