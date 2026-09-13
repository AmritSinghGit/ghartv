const MAX_BODY_BYTES = 256 * 1024;
const MAX_BATCH_EVENTS = 50;
const MAX_ATTRIBUTES = 24;
const RETENTION_DAYS = 30;
const RATE_WINDOW_MS = 10 * 60 * 1000;
const RATE_LIMIT_EVENTS = 300;
const GLOBAL_RATE_LIMIT_EVENTS = 5000;
// Privacy boundary: this Worker never reads or stores the CF-Connecting-IP header.

const BANNED_KEY = /(mobile|phone|otp|password|passcode|secret|token|cookie|authorization|stream[_-]?url|license[_-]?url|headers?|device[_-]?id|android[_-]?id|subscriber|crmid|email|ssid|bssid|mac|serial|ip[_-]?address)/i;
const PHONE = /(?:\+?91[- ]?)?[6-9]\d{9}/g;
const URL_PATTERN = /https?:\/\/\S+/gi;
const SECRET_ASSIGNMENT = /(otp|password|passcode|token|cookie|authorization|secret)\s*[:=]\s*[^\s,;]+/gi;
const LONG_SECRET = /[A-Za-z0-9_-]{36,}(?:\.[A-Za-z0-9_-]{12,}){0,2}/g;
const SAFE_EVENT = /^[a-z][a-z0-9_]{1,63}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "ghartv-control", schema: 2, retention_days: RETENTION_DAYS });
    }
    if (request.method === "POST" && url.pathname === "/v1/events") {
      return cors(await ingest(request, env));
    }
    if (request.method === "POST" && url.pathname === "/v1/device/register") {
      return cors(await registerDevice(request, env));
    }
    if (request.method === "GET" && url.pathname === "/v1/device/commands") {
      return cors(await deviceCommands(request, env, url));
    }
    if (request.method === "POST" && url.pathname === "/v1/device/commands/ack") {
      return cors(await acknowledgeCommand(request, env));
    }
    if (request.method === "GET" && url.pathname === "/v1/admin/summary") {
      return adminSummary(request, env, url);
    }
    if (request.method === "GET" && url.pathname === "/v1/admin/export") {
      return adminExport(request, env, url);
    }
    if (request.method === "GET" && url.pathname === "/v1/admin/devices") {
      return adminDevices(request, env);
    }
    if (request.method === "POST" && url.pathname === "/v1/admin/devices/claim") {
      return claimDevice(request, env);
    }
    if (request.method === "POST" && url.pathname === "/v1/admin/commands") {
      return createCommand(request, env);
    }
    if (request.method === "POST" && url.pathname === "/v1/admin/purge") {
      if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
      const deleted = await purge(env);
      return json({ ok: true, deleted });
    }
    return json({ error: "not_found" }, 404);
  },

  async scheduled(_event, env) {
    await purge(env);
  },
};

async function registerDevice(request, env) {
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  if (!authorizedIngest(request, env)) return json({ error: "invalid_ingest_key" }, 401);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);
  const deviceId = safeHex(body.device_id, 32);
  const deviceSecret = safeHex(body.device_secret, 64);
  const pairingCode = String(body.pairing_code || "").trim();
  if (!deviceId || !deviceSecret || !/^\d{6}$/.test(pairingCode)) {
    return json({ error: "invalid_device_registration" }, 400);
  }
  const now = Date.now();
  const [secretHash, pairingHash] = await Promise.all([
    sha256(deviceSecret),
    sha256("ghartv-pair:" + pairingCode),
  ]);
  await env.DB.prepare(
    `INSERT INTO tv_devices (
       device_id, device_secret_hash, pairing_code_hash, pairing_expires_at,
       display_name, created_at, last_seen_at, app_version, version_code
     ) VALUES (?, ?, ?, ?, 'GharTV', ?, ?, ?, ?)
     ON CONFLICT(device_id) DO UPDATE SET
       device_secret_hash = excluded.device_secret_hash,
       pairing_code_hash = CASE WHEN tv_devices.paired_at IS NULL THEN excluded.pairing_code_hash ELSE tv_devices.pairing_code_hash END,
       pairing_expires_at = CASE WHEN tv_devices.paired_at IS NULL THEN excluded.pairing_expires_at ELSE tv_devices.pairing_expires_at END,
       last_seen_at = excluded.last_seen_at,
       app_version = excluded.app_version,
       version_code = excluded.version_code`
  ).bind(
    deviceId, secretHash, pairingHash, now + 10 * 60 * 1000,
    now, now, safeToken(body.app_version, 64, "unknown"),
    safeInteger(body.version_code, 0, 1000000, 0)
  ).run();
  const row = await env.DB.prepare(
    "SELECT paired_at FROM tv_devices WHERE device_id = ?"
  ).bind(deviceId).first();
  return json({ ok: true, paired: Boolean(row?.paired_at), pairing_expires_in_seconds: 600 });
}

async function deviceCommands(request, env, url) {
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  const device = await authorizedDevice(request, env, safeHex(url.searchParams.get("device_id"), 32));
  if (!device) return json({ error: "unauthorized_device" }, 401);
  const now = Date.now();
  await env.DB.prepare(
    "UPDATE tv_devices SET last_seen_at = ? WHERE device_id = ?"
  ).bind(now, device.device_id).run();
  if (!device.paired_at) return json({ ok: true, paired: false, commands: [] });
  const rows = await env.DB.prepare(
    `SELECT command_id, command_type, payload_json, created_at, expires_at
     FROM tv_commands
     WHERE device_id = ? AND acknowledged_at IS NULL AND expires_at > ?
     ORDER BY created_at ASC LIMIT 10`
  ).bind(device.device_id, now).all();
  return json({
    ok: true,
    paired: true,
    commands: (rows.results || []).map((row) => ({
      id: row.command_id,
      type: row.command_type,
      payload: parseJson(row.payload_json),
      created_at: row.created_at,
      expires_at: row.expires_at,
    })),
  });
}

async function acknowledgeCommand(request, env) {
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);
  const deviceId = safeHex(body.device_id, 32);
  const commandId = safeUuid(body.command_id);
  const device = await authorizedDevice(request, env, deviceId);
  if (!device || !commandId) return json({ error: "unauthorized_device" }, 401);
  const result = await env.DB.prepare(
    `UPDATE tv_commands SET acknowledged_at = ?
     WHERE command_id = ? AND device_id = ? AND acknowledged_at IS NULL`
  ).bind(Date.now(), commandId, deviceId).run();
  return json({ ok: true, acknowledged: Number(result.meta?.changes || 0) === 1 });
}

async function adminDevices(request, env) {
  if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  const rows = await env.DB.prepare(
    `SELECT device_id, display_name, paired_at, created_at, last_seen_at,
            app_version, version_code
     FROM tv_devices WHERE paired_at IS NOT NULL ORDER BY last_seen_at DESC LIMIT 200`
  ).all();
  return json({ ok: true, devices: rows.results || [] });
}

async function claimDevice(request, env) {
  if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);
  const pairingCode = String(body.pairing_code || "").trim();
  if (!/^\d{6}$/.test(pairingCode)) return json({ error: "invalid_pairing_code" }, 400);
  const pairingHash = await sha256("ghartv-pair:" + pairingCode);
  const row = await env.DB.prepare(
    `SELECT device_id FROM tv_devices
     WHERE pairing_code_hash = ? AND pairing_expires_at > ? AND paired_at IS NULL
     ORDER BY created_at DESC LIMIT 1`
  ).bind(pairingHash, Date.now()).first();
  if (!row) return json({ error: "pairing_code_not_found_or_expired" }, 404);
  const displayName = safeToken(body.display_name, 48, "GharTV");
  await env.DB.prepare(
    `UPDATE tv_devices SET paired_at = ?, display_name = ?,
       pairing_code_hash = NULL, pairing_expires_at = NULL
     WHERE device_id = ? AND paired_at IS NULL`
  ).bind(Date.now(), displayName, row.device_id).run();
  return json({ ok: true, device_id: row.device_id, display_name: displayName });
}

async function createCommand(request, env) {
  if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  if (!env.DB) return json({ error: "control_database_unavailable" }, 503);
  const body = await readJson(request);
  if (!body) return json({ error: "invalid_json" }, 400);
  const deviceId = safeHex(body.device_id, 32);
  const type = safeToken(body.type, 24, "");
  const message = scrubString(body.message || "").slice(0, 180);
  if (!deviceId || type !== "message" || message.length < 1) {
    return json({ error: "invalid_command" }, 400);
  }
  const device = await env.DB.prepare(
    "SELECT paired_at FROM tv_devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!device?.paired_at) return json({ error: "device_not_paired" }, 404);
  const now = Date.now();
  const lifetime = safeInteger(body.expires_in_seconds, 60, 86400, 3600);
  const commandId = crypto.randomUUID();
  await env.DB.prepare(
    `INSERT INTO tv_commands (
       command_id, device_id, command_type, payload_json, created_at, expires_at
     ) VALUES (?, ?, 'message', ?, ?, ?)`
  ).bind(commandId, deviceId, JSON.stringify({ message }), now, now + lifetime * 1000).run();
  return json({ ok: true, command_id: commandId, expires_at: now + lifetime * 1000 });
}

async function ingest(request, env) {
  if (!env.DB) return json({ error: "collector_database_unavailable" }, 503);
  const suppliedKey = request.headers.get("X-GharTV-Ingest-Key") || "";
  if (!env.INGEST_KEY || !constantTimeEqual(suppliedKey, env.INGEST_KEY)) {
    return json({ error: "invalid_ingest_key" }, 401);
  }

  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > MAX_BODY_BYTES) return json({ error: "payload_too_large" }, 413);
  const raw = await request.text();
  if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) return json({ error: "payload_too_large" }, 413);

  let batch;
  try { batch = JSON.parse(raw); }
  catch { return json({ error: "invalid_json" }, 400); }
  if (!batch || batch.schema !== "ghartv.telemetry.batch.v1" || !Array.isArray(batch.events)) {
    return json({ error: "invalid_schema" }, 400);
  }
  if (batch.events.length < 1 || batch.events.length > MAX_BATCH_EVENTS) {
    return json({ error: "invalid_batch_size" }, 400);
  }

  const installHash = safeToken(batch.install_hash, 64, "unknown");
  const sessionId = safeToken(batch.session_id, 64, "unknown");
  const appVersion = safeToken(batch.app_version, 64, "unknown");
  const versionCode = safeInteger(batch.version_code, 0, 1000000, 0);
  const device = scrubObject(batch.device || {});
  if (!/^[a-f0-9]{16,64}$/i.test(installHash)) return json({ error: "invalid_install_hash" }, 400);

  const [rate, globalRate] = await Promise.all([
    incrementRate(env, installHash, batch.events.length),
    incrementRate(env, "__global__", batch.events.length),
  ]);
  if (rate > RATE_LIMIT_EVENTS || globalRate > GLOBAL_RATE_LIMIT_EVENTS) {
    return json({ error: "rate_limited", retry_after_seconds: 600 }, 429, { "Retry-After": "600" });
  }

  const now = Date.now();
  const statements = [];
  const accepted = [];
  let rejected = 0;
  let redactions = 0;

  for (const incoming of batch.events) {
    const result = sanitizeEvent(incoming, now);
    if (!result.event) {
      rejected += 1;
      continue;
    }
    redactions += result.redactions;
    const event = result.event;
    accepted.push(event.id);
    statements.push(env.DB.prepare(
      `INSERT OR IGNORE INTO telemetry_events (
        event_id, received_at, client_ts, install_hash, session_id,
        event_name, app_version, version_code, screen,
        manufacturer, model, android_release, android_api, locale, network,
        reference, attributes_json
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      event.id,
      now,
      event.timestamp,
      installHash,
      sessionId,
      event.name,
      appVersion,
      versionCode,
      event.screen,
      safeToken(device.manufacturer, 40, "unknown"),
      safeToken(device.model, 80, "unknown"),
      safeToken(device.android_release, 24, "unknown"),
      safeInteger(device.android_api, 0, 1000, 0),
      safeToken(device.locale, 32, "unknown"),
      safeToken(device.network, 24, "unknown"),
      event.reference,
      JSON.stringify(event.attributes)
    ));
  }

  if (statements.length) await env.DB.batch(statements);
  return json({
    ok: true,
    accepted: accepted.length,
    rejected,
    redactions,
    retention_days: RETENTION_DAYS,
  });
}

function sanitizeEvent(incoming, now) {
  if (!incoming || typeof incoming !== "object") return { event: null, redactions: 0 };
  const id = safeToken(incoming.id, 80, "");
  const name = safeToken(incoming.name, 64, "");
  if (!id || !SAFE_EVENT.test(name)) return { event: null, redactions: 0 };
  const timestamp = safeInteger(incoming.timestamp, 0, now + 24 * 60 * 60 * 1000, now);
  const reference = safeToken(incoming.reference, 24, "");
  const screen = safeToken(incoming.screen, 48, "unknown");
  const state = { redactions: 0 };
  const attributes = scrubObject(incoming.attributes || {}, state);
  return { event: { id, name, timestamp, reference, screen, attributes }, redactions: state.redactions };
}

function scrubObject(value, state = { redactions: 0 }, depth = 0) {
  if (!value || typeof value !== "object" || Array.isArray(value) || depth > 3) return {};
  const output = {};
  let count = 0;
  for (const [key, rawValue] of Object.entries(value)) {
    if (count >= MAX_ATTRIBUTES) break;
    if (BANNED_KEY.test(key)) {
      state.redactions += 1;
      continue;
    }
    const safeKey = safeToken(key, 48, "field");
    output[safeKey] = scrubValue(rawValue, state, depth + 1);
    count += 1;
  }
  return output;
}

function scrubValue(value, state, depth) {
  if (value === null || value === undefined) return null;
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  if (Array.isArray(value)) return value.slice(0, 24).map((item) => scrubValue(item, state, depth + 1));
  if (typeof value === "object") return scrubObject(value, state, depth + 1);
  const original = String(value);
  const clean = scrubString(original);
  if (clean !== original) state.redactions += 1;
  return clean;
}

function scrubString(value) {
  return String(value)
    .replace(/[\r\n]+/g, " ")
    .replace(SECRET_ASSIGNMENT, "$1=[REDACTED]")
    .replace(PHONE, "[PHONE_REMOVED]")
    .replace(URL_PATTERN, "[URL_REMOVED]")
    .replace(LONG_SECRET, "[SECRET_REMOVED]")
    .replace(/\s{2,}/g, " ")
    .trim()
    .slice(0, 240);
}

async function incrementRate(env, installHash, count) {
  const bucket = Math.floor(Date.now() / RATE_WINDOW_MS);
  await env.DB.prepare(
    `INSERT INTO ingest_windows (install_hash, window_bucket, event_count, updated_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(install_hash, window_bucket)
     DO UPDATE SET event_count = event_count + excluded.event_count, updated_at = excluded.updated_at`
  ).bind(installHash, bucket, count, Date.now()).run();
  const result = await env.DB.prepare(
    "SELECT event_count FROM ingest_windows WHERE install_hash = ? AND window_bucket = ?"
  ).bind(installHash, bucket).first();
  return Number(result?.event_count || count);
}

async function adminSummary(request, env, url) {
  if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  const days = clamp(Number(url.searchParams.get("days") || 7), 1, RETENTION_DAYS);
  const since = Date.now() - days * 24 * 60 * 60 * 1000;
  const [totals, events, failures, versions, devices, daily] = await Promise.all([
    env.DB.prepare(
      `SELECT COUNT(*) AS events, COUNT(DISTINCT install_hash) AS installations
       FROM telemetry_events WHERE received_at >= ?`
    ).bind(since).first(),
    env.DB.prepare(
      `SELECT event_name, COUNT(*) AS count FROM telemetry_events
       WHERE received_at >= ? GROUP BY event_name ORDER BY count DESC LIMIT 40`
    ).bind(since).all(),
    env.DB.prepare(
      `SELECT
         json_extract(attributes_json, '$.stage') AS stage,
         json_extract(attributes_json, '$.http_status') AS http_status,
         json_extract(attributes_json, '$.failed_channel_id') AS failed_channel_id,
         json_extract(attributes_json, '$.error_type') AS error_type,
         json_extract(attributes_json, '$.fingerprint') AS fingerprint,
         COUNT(*) AS count
       FROM telemetry_events
       WHERE received_at >= ? AND event_name IN ('app_error','app_crash','playback_failure')
       GROUP BY stage, http_status, failed_channel_id, error_type, fingerprint
       ORDER BY count DESC LIMIT 100`
    ).bind(since).all(),
    env.DB.prepare(
      `SELECT app_version, version_code, COUNT(*) AS events, COUNT(DISTINCT install_hash) AS installations
       FROM telemetry_events WHERE received_at >= ?
       GROUP BY app_version, version_code ORDER BY version_code DESC`
    ).bind(since).all(),
    env.DB.prepare(
      `SELECT manufacturer, model, android_api, COUNT(DISTINCT install_hash) AS installations
       FROM telemetry_events WHERE received_at >= ?
       GROUP BY manufacturer, model, android_api ORDER BY installations DESC LIMIT 40`
    ).bind(since).all(),
    env.DB.prepare(
      `SELECT date(received_at / 1000, 'unixepoch') AS day,
              COUNT(*) AS events, COUNT(DISTINCT install_hash) AS installations
       FROM telemetry_events WHERE received_at >= ?
       GROUP BY day ORDER BY day`
    ).bind(since).all(),
  ]);
  return json({
    ok: true,
    generated_at: new Date().toISOString(),
    window_days: days,
    retention_days: RETENTION_DAYS,
    totals: totals || { events: 0, installations: 0 },
    events: events.results || [],
    failures: failures.results || [],
    versions: versions.results || [],
    devices: devices.results || [],
    daily: daily.results || [],
  });
}

async function adminExport(request, env, url) {
  if (!authorizedAdmin(request, env)) return json({ error: "unauthorized" }, 401);
  const days = clamp(Number(url.searchParams.get("days") || 7), 1, RETENTION_DAYS);
  const limit = clamp(Number(url.searchParams.get("limit") || 5000), 1, 5000);
  const since = Date.now() - days * 24 * 60 * 60 * 1000;
  const rows = await env.DB.prepare(
    `SELECT event_id, received_at, client_ts, install_hash, session_id, event_name,
            app_version, version_code, screen, manufacturer, model, android_release,
            android_api, locale, network, reference, attributes_json
     FROM telemetry_events WHERE received_at >= ? ORDER BY received_at DESC LIMIT ?`
  ).bind(since, limit).all();
  const events = (rows.results || []).map((row) => ({
    ...row,
    attributes: parseJson(row.attributes_json),
    attributes_json: undefined,
  }));
  return json({ ok: true, window_days: days, count: events.length, events });
}

async function purge(env) {
  if (!env.DB) return 0;
  const cutoff = Date.now() - RETENTION_DAYS * 24 * 60 * 60 * 1000;
  const oldWindow = Math.floor((Date.now() - 24 * 60 * 60 * 1000) / RATE_WINDOW_MS);
  const result = await env.DB.prepare("DELETE FROM telemetry_events WHERE received_at < ?").bind(cutoff).run();
  await env.DB.prepare("DELETE FROM ingest_windows WHERE window_bucket < ?").bind(oldWindow).run();
  return Number(result.meta?.changes || 0);
}

function authorizedAdmin(request, env) {
  const auth = request.headers.get("Authorization") || "";
  const supplied = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  return Boolean(env.ADMIN_TOKEN && constantTimeEqual(supplied, env.ADMIN_TOKEN));
}

function authorizedIngest(request, env) {
  const supplied = request.headers.get("X-GharTV-Ingest-Key") || "";
  return Boolean(env.INGEST_KEY && constantTimeEqual(supplied, env.INGEST_KEY));
}

async function authorizedDevice(request, env, deviceId) {
  if (!deviceId) return null;
  const auth = request.headers.get("Authorization") || "";
  const supplied = auth.startsWith("Device ") ? safeHex(auth.slice(7), 64) : "";
  if (!supplied) return null;
  const row = await env.DB.prepare(
    "SELECT device_id, device_secret_hash, paired_at FROM tv_devices WHERE device_id = ?"
  ).bind(deviceId).first();
  if (!row) return null;
  const suppliedHash = await sha256(supplied);
  return constantTimeEqual(suppliedHash, row.device_secret_hash) ? row : null;
}

async function readJson(request) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > 16 * 1024) return null;
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).length > 16 * 1024) return null;
    const value = JSON.parse(raw);
    return value && typeof value === "object" && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

async function sha256(value) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(String(value)));
  return [...new Uint8Array(bytes)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function safeHex(value, length) {
  const clean = String(value || "").trim().toLowerCase();
  return new RegExp(`^[a-f0-9]{${length}}$`).test(clean) ? clean : "";
}

function safeUuid(value) {
  const clean = String(value || "").trim().toLowerCase();
  return /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(clean)
    ? clean : "";
}

function constantTimeEqual(left, right) {
  const a = new TextEncoder().encode(String(left));
  const b = new TextEncoder().encode(String(right));
  let result = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index++) result |= (a[index % (a.length || 1)] || 0) ^ (b[index % (b.length || 1)] || 0);
  return result === 0;
}

function safeToken(value, max, fallback) {
  const clean = String(value ?? "").trim().replace(/[^A-Za-z0-9 ._+\/-]/g, "_");
  return (clean || fallback).slice(0, max);
}

function safeInteger(value, min, max, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.trunc(clamp(number, min, max)) : fallback;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function parseJson(value) {
  try { return JSON.parse(value || "{}"); }
  catch { return {}; }
}

function json(value, status = 200, headers = {}) {
  return cors(new Response(JSON.stringify(value), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...headers },
  }));
}

function cors(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Headers", "Content-Type, X-GharTV-Ingest-Key, Authorization");
  headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
}
