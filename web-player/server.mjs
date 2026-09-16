import {previewContext,isPreviewActive} from './fabric-preview.mjs';
import { ownerRoute } from "./owner-gateway.mjs";
import { createServer } from "node:http";
import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { dirname, extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes, randomUUID } from "node:crypto";
import { Readable } from "node:stream";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const ROOT = dirname(fileURLToPath(import.meta.url));
const PUBLIC = join(ROOT, "public");
const HOST = process.env.GHARTV_WEB_HOST || "127.0.0.1";
const PORT = Number(process.env.GHARTV_WEB_PORT || 8790);
const APP_VERSION = "0.6.0-rc7-network-diagnostics";
const WEB_REPAIR = "RC6-WEB-FABRIC-R1";
const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
const STREAM_TTL_MS = 4 * 60 * 60 * 1000;
const MAX_BODY = 16 * 1024;
const MOBILE_USER_AGENT = "okhttp/4.2.2";
const PLAYER_USER_AGENT = "plaYtv/7.1.5 (Linux;Android 9) ExoPlayerLib/2.11.7";
const execFileAsync = promisify(execFile);
const KEYCHAIN_SERVICE = "in.ghartv.nova.web-player.jio-session";
const KEYCHAIN_ACCOUNT = "local-owner";

const JIO = Object.freeze({
  channels14: "https://jiotvapi.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?langId=6&devicetype=phone&os=android&usertype=JIO&version=396",
  channels31: "https://jiotvapi.cdn.jio.com/apis/v3.1/getMobileChannelList/get/?langId=6&os=android&devicetype=phone&usertype=JIO&version=389",
  dictionary: "https://jiotvapi.cdn.jio.com/apis/v1.3/dictionary/dictionary?langId=6",
  epg: "https://jiotvapi.cdn.jio.com/apis/v1.3/getepg/get?offset=0&channel_id=",
  otpSend: "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send",
  otpVerify: "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify",
  playback: "https://jiotvapi.media.jio.com/playback/apis/v1.1/geturl",
  tokenRefresh: "https://auth.media.jio.com/tokenservice/apis/v1/refreshtoken?langId=6",
  ssoRefresh: "https://tv.media.jio.com/apis/v2.0/loginotp/refresh?langId=6",
  logoBase: "https://jiotv.catchup.cdn.jio.com/dare_images/images/",
});

const sessions = new Map();
const streamTickets = new Map();
let catalogueCache = { expiresAt: 0, fetchedAt: 0, channels: [] };
let catalogueFlight = null;
let catalogueOutcome = { status: "not_loaded" };
let persistedAccount = null;
let persistedAccountLoaded = false;

function now() { return Date.now(); }
function id(bytes = 24) { return randomBytes(bytes).toString("base64url"); }
function value(input) { return typeof input === "string" ? input : ""; }

export function normalizeMobile(input) {
  let digits = String(input || "").replace(/\D/g, "");
  if (digits.startsWith("91") && digits.length === 12) digits = digits.slice(2);
  if (!/^[6-9]\d{9}$/.test(digits)) throw new Error("Enter a valid 10-digit Jio mobile number.");
  return digits;
}

function parseCookies(header = "") {
  const out = {};
  for (const part of header.split(";")) {
    const at = part.indexOf("=");
    if (at < 1) continue;
    out[part.slice(0, at).trim()] = decodeURIComponent(part.slice(at + 1).trim());
  }
  return out;
}

function sessionFor(req, res, create = true) {
  const cookies = parseCookies(req.headers.cookie);
  const preview=req.ghartvViewer?.preview?req.ghartvViewer:null;
  const cookieName=preview?'ghartv_preview_'+preview.id:'ghartv_web';
  let sid = cookies[cookieName];
  let session = sid ? sessions.get(sid) : null;
  if (session && (session.touchedAt + SESSION_TTL_MS < now() || (session.previewId||null)!==(preview?.id||null) || (session.previewId && !isPreviewActive(session.previewId)))) {
    sessions.delete(sid);
    session = null;
  }
  if (!session && create) {
    sid = id();
    session = { id: sid, touchedAt: now(), pendingMobile: "", account: null, previewId:preview?.id||null, prefix:preview?.prefix||"" };
    sessions.set(sid, session);
    res.setHeader("Set-Cookie", `${cookieName}=${encodeURIComponent(sid)}; HttpOnly; SameSite=Strict; Path=${preview?preview.prefix+"/":"/"}; Max-Age=${preview?Math.max(1,Math.floor((preview.expiresAt-now())/1000)):43200}${preview?"; Secure":""}`);
  }
  if (session) session.touchedAt = now();
  return session;
}

function cleanup() {
  for (const [sid, session] of sessions) if (session.touchedAt + SESSION_TTL_MS < now() || (session.previewId&&!isPreviewActive(session.previewId))) sessions.delete(sid);
  for (const [ticket, item] of streamTickets) if (item.createdAt + STREAM_TTL_MS < now()) streamTickets.delete(ticket);
}
setInterval(cleanup, 60_000).unref();

function validPersistedAccount(account) {
  return account && typeof account === "object"
    && /^\d{10}$/.test(value(account.mobile))
    && Boolean(value(account.authToken))
    && Boolean(value(account.ssoToken))
    && Boolean(value(account.deviceId));
}

async function readKeychainAccount() {
  if (process.platform !== "darwin" || process.env.GHARTV_DISABLE_KEYCHAIN === "1") return null;
  try {
    const { stdout } = await execFileAsync("security", ["find-generic-password", "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE, "-w"], { timeout: 5_000, maxBuffer: 64 * 1024 });
    const account = JSON.parse(stdout.trim());
    return validPersistedAccount(account) ? account : null;
  } catch {
    return null;
  }
}

async function restorePersistedAccount(session) {
  if (session.account || session.previewId) return;
  if (!persistedAccountLoaded) {
    persistedAccount = await readKeychainAccount();
    persistedAccountLoaded = true;
  }
  if (persistedAccount) session.account = { ...persistedAccount };
}

async function savePersistedAccount(account) {
  persistedAccount = validPersistedAccount(account) ? { ...account } : null;
  persistedAccountLoaded = true;
  if (!persistedAccount || process.platform !== "darwin" || process.env.GHARTV_DISABLE_KEYCHAIN === "1") return false;
  try {
    await execFileAsync("security", ["add-generic-password", "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE, "-w", JSON.stringify(persistedAccount), "-U"], { timeout: 5_000, maxBuffer: 64 * 1024 });
    return true;
  } catch {
    return false;
  }
}

async function deletePersistedAccount() {
  persistedAccount = null;
  persistedAccountLoaded = true;
  if (process.platform !== "darwin" || process.env.GHARTV_DISABLE_KEYCHAIN === "1") return;
  try {
    await execFileAsync("security", ["delete-generic-password", "-a", KEYCHAIN_ACCOUNT, "-s", KEYCHAIN_SERVICE], { timeout: 5_000, maxBuffer: 64 * 1024 });
  } catch {}
}

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer",
  });
  res.end(body);
}

function apiError(res, status, message, code = "request_failed") {
  json(res, status, { ok: false, code, message });
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw new Error("Request is too large.");
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function readBytes(req, limit = 2 * 1024 * 1024) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw Object.assign(new Error("License request is too large."), { status: 413 });
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function assertLocalOrigin(req) {
  const origin = req.headers.origin;
  if(req.ghartvViewer?.preview){if(origin!==req.ghartvViewer.origin)throw Object.assign(new Error("Preview origin rejected"),{status:403});return;}
  // Fabric preserves Origin while rewriting Host to the loopback upstream.
  // Accept ONLY the explicitly observed local player alias, never wildcard
  // localhost subdomains or arbitrary Forwarded/X-Forwarded-* headers.
  const allowed = new Set([`http://${HOST}:${PORT}`, `http://localhost:${PORT}`, `http://127.0.0.1:${PORT}`]);
  if(PORT === 8790) allowed.add("http://ghartv-api-8790.localhost:43918");
  const peer=req.socket.remoteAddress;
  const loopback=["127.0.0.1","::1","::ffff:127.0.0.1"].includes(peer);
  const site=req.headers["sec-fetch-site"];
  if(!loopback || (origin && !allowed.has(origin)) || (site && !["same-origin","none"].includes(site)))
    throw Object.assign(new Error("Player request origin rejected. Open the local player or the configured Fabric player address."),{status:403});
}

async function upstreamJson(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(options.timeout || 25_000), redirect: "follow" });
  const raw = await response.text();
  let payload = {};
  try { payload = raw ? JSON.parse(raw) : {}; } catch { payload = { message: raw.slice(0, 300) }; }
  if (!response.ok) {
    const error = new Error(payload.message || payload.errorMessage || `Provider returned HTTP ${response.status}.`);
    error.status = response.status;
    error.payload = payload;
    throw error;
  }
  return { response, payload };
}

function loginHeaders() {
  return { "user-agent": MOBILE_USER_AGENT, os: "android", devicetype: "phone", appname: "RJIL_JioTV", "content-type": "application/json" };
}

async function sendOtp(mobile) {
  const number = Buffer.from(`+91${mobile}`, "ascii").toString("base64");
  const response = await fetch(JIO.otpSend, {
    method: "POST",
    headers: loginHeaders(),
    body: JSON.stringify({ number }),
    signal: AbortSignal.timeout(25_000),
  });
  if (response.status !== 204 && !response.ok) {
    const raw = await response.text();
    let message = "Jio did not send the OTP.";
    try { message = JSON.parse(raw).message || message; } catch {}
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }
}

function jwtExpiry(jwt) {
  try {
    const payload = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString("utf8"));
    return Number(payload.exp || 0);
  } catch { return 0; }
}

async function verifyOtp(mobile, otp) {
  if (!/^\d{4,8}$/.test(String(otp || "").trim())) throw new Error("Enter the OTP sent by Jio.");
  const deviceId = randomUUID();
  const number = Buffer.from(`+91${mobile}`, "ascii").toString("base64");
  const body = {
    number,
    otp: String(otp).trim(),
    deviceInfo: {
      consumptionDeviceName: "GharTV local web review",
      info: { type: "android", platform: { name: "GharTV_Web" }, androidId: deviceId },
    },
  };
  const { payload } = await upstreamJson(JIO.otpVerify, {
    method: "POST", headers: loginHeaders(), body: JSON.stringify(body), timeout: 25_000,
  });
  if (!payload.ssoToken || !payload.authToken) throw new Error(payload.message || "Jio rejected the OTP.");
  const user = payload.sessionAttributes?.user || {};
  return {
    mobile,
    ssoToken: value(payload.ssoToken),
    authToken: value(payload.authToken),
    refreshToken: value(payload.refreshToken),
    deviceId: value(payload.deviceId) || deviceId,
    userId: value(user.uid),
    uniqueId: value(user.unique) || value(payload.deviceId) || deviceId,
    subscriberId: value(user.subscriberId),
    expiryEpochSeconds: jwtExpiry(value(payload.authToken)) || Math.floor(now() / 1000) + 864000,
  };
}

function stringMap(object) {
  if (!object || typeof object !== "object") return {};
  return Object.fromEntries(Object.entries(object).map(([key, label]) => [String(key), String(label || "Other")]));
}

function subscriptionHint(raw) {
  for (const key of ["isPremium", "isPaid", "premium", "paid", "subscriptionRequired", "requiresSubscription", "isSubscription", "isPayChannel", "payChannel"]) {
    const item = raw?.[key];
    if (item === true || Number(item) > 0 || ["true", "yes"].includes(String(item).toLowerCase())) return true;
  }
  if (raw && Object.hasOwn(raw, "isFree") && raw.isFree === false) return true;
  const text = ["accessType", "payType", "channelType", "entitlementType", "subscriptionType", "packageType", "offerType"]
    .map((key) => String(raw?.[key] || "").toLowerCase()).join(" ");
  return /paywall|subscription|required|premium|paid|ott pass/.test(text);
}

export function channelFromRaw(raw, number, categories = {}, languages = {}) {
  const channelId = String(raw.channel_id ?? raw.channelId ?? "");
  const categoryId = String(raw.channelCategoryId ?? raw.channel_category_id ?? "-1");
  const languageId = String(raw.channelLanguageId ?? raw.channel_language_id ?? "6");
  const logo = String(raw.logoUrl || "");
  return {
    id: channelId,
    number,
    name: String(raw.channel_name ?? raw.channelName ?? "Unknown channel"),
    category: categories[categoryId] || raw.channelCategoryName || raw.categoryName || "Other",
    language: languages[languageId] || raw.channelLanguageName || raw.languageName || "Other",
    languageId,
    logoUrl: logo.startsWith("http") ? logo : `${JIO.logoBase}${logo}`,
    catchupAvailable: Boolean(raw.isCatchupAvailable),
    subscription: subscriptionHint(raw),
  };
}

async function fetchCatalogue(force=false) {
  if (!force && catalogueCache.expiresAt > now() && catalogueCache.channels.length) {
    catalogueOutcome={status:"cached",fetched_at:catalogueCache.fetchedAt};
    return catalogueCache.channels;
  }
  if(catalogueFlight) return catalogueFlight;
  catalogueFlight=loadCatalogue().finally(()=>{catalogueFlight=null;});
  return catalogueFlight;
}

async function loadCatalogue() {
  const headers = { "user-agent": MOBILE_USER_AGENT };
  // Independent requests: an unavailable v1.4 endpoint must not suppress v3.1.
  // One in-flight refresh per process. Total deadline is the longest individual
  // request, not dictionary + v1.4 + v3.1 sequential waits.
  const [dictionary,...lists] = await Promise.allSettled([
    upstreamJson(JIO.dictionary,{headers,timeout:4000}),
    upstreamJson(JIO.channels14,{headers,timeout:10000}),
    upstreamJson(JIO.channels31,{headers,timeout:10000})
  ]);
  const sources=lists.filter(x=>x.status==="fulfilled"&&Array.isArray(x.value.payload.result)&&x.value.payload.result.length)
      .map(x=>x.value.payload);
  if(!sources.length){
    if(catalogueCache.channels.length && now()-catalogueCache.fetchedAt<=72*60*60*1000){
      catalogueOutcome={status:"stale",fetched_at:catalogueCache.fetchedAt,message:"Guide refresh unavailable. Showing the last successful guide; channel access is checked when you play."};
      return catalogueCache.channels;
    }
    throw Object.assign(new Error("The channel guide service did not return a usable guide. Retry guide; your sign-in has been kept."),{status:503});
  }
  const mapping=dictionary.status==="fulfilled"?dictionary.value.payload:{};
  const categories=stringMap(mapping.channelCategoryMapping),languages=stringMap(mapping.languageIdMapping);
  const merged = new Map();
  for (const raw of sources.flatMap(item=>item.result)) {
    const channelId = String(raw.channel_id ?? raw.channelId ?? "");
    if (!channelId || raw.channelIdForRedirect) continue;
    if (!merged.has(channelId)) merged.set(channelId, raw);
  }
  const used = new Set();
  let next = 1;
  const channels = [];
  for (const raw of merged.values()) {
    let requested = Number(raw.channel_order ?? raw.channelOrder ?? -1) + 1;
    if (!Number.isInteger(requested) || requested < 1 || requested > 9999 || used.has(requested)) {
      while (used.has(next)) next += 1;
      requested = next;
    }
    used.add(requested);
    next = Math.max(next, requested + 1);
    channels.push(channelFromRaw(raw, requested, categories, languages));
  }
  channels.sort((a, b) => a.number - b.number || a.name.localeCompare(b.name));

  if(!channels.length)throw Object.assign(new Error("The provider returned an empty guide. Your sign-in has been kept; retry guide."),{status:503});
  // Never overwrite a useful guide with an empty/error response.
  catalogueCache = { expiresAt: now() + 6 * 60 * 60 * 1000, fetchedAt:now(), channels };
  catalogueOutcome={status:sources.length===2?"fresh":"partial_source",fetched_at:catalogueCache.fetchedAt,
    message:sources.length===2?"":"One guide endpoint is unavailable. Showing channels from the responding endpoint."};
  return channels;
}

function playbackHeaders(account, channel) {
  return {
    Appkey: "NzNiMDhlYzQyNjJm",
    Devicetype: "phone",
    Os: "android",
    Deviceid: value(account.deviceId),
    Osversion: "13",
    Dm: "Google Pixel 5",
    Uniqueid: value(account.uniqueId || account.deviceId),
    Usergroup: "tvYR7NSNn7rymo3F",
    Languageid: value(channel.languageId || "6"),
    Userid: value(account.userId),
    Sid: randomUUID(),
    Crmid: value(account.subscriberId),
    Isott: "false",
    Channel_id: channel.id,
    Langid: value(channel.languageId),
    Camid: "",
    ssoToken: value(account.ssoToken),
    Accesstoken: value(account.authToken),
    Subscriberid: value(account.subscriberId),
    analyticsId: value(account.deviceId),
    Lbcookie: "1",
    Versioncode: "389",
    "content-type": "application/x-www-form-urlencoded",
    "user-agent": MOBILE_USER_AGENT,
  };
}

async function refreshAccount(account, persist = true) {
  if (!account?.refreshToken) return account;
  try {
    const { payload } = await upstreamJson(JIO.tokenRefresh, {
      method: "POST",
      headers: {
        accesstoken: value(account.authToken), uniqueid: value(account.uniqueId), "content-type": "application/json",
        "user-agent": "JioTV", os: "android", devicetype: "phone", versioncode: "396",
      },
      body: JSON.stringify({ appName: "RJIL_JioTV", deviceId: account.deviceId, refreshToken: account.refreshToken }),
    });
    if (payload.authToken) {
      account.authToken = payload.authToken;
      account.refreshToken = payload.refreshToken || account.refreshToken;
      account.expiryEpochSeconds = jwtExpiry(payload.authToken) || Math.floor(now() / 1000) + 864000;
      if(persist)await savePersistedAccount(account);
    }
  } catch {}
  return account;
}

function signedCookie(url) {
  const match = String(url).match(/[?&](__hdnea__=[^&]+)/);
  return match ? match[1] : "";
}

function providerMessage(payload, fallback) {
  for (const key of ["message", "errorMessage", "description", "statusMessage", "reason"]) {
    const candidate = payload?.[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate.trim();
    if (candidate && typeof candidate.message === "string") return candidate.message.trim();
  }
  return fallback;
}

async function authorizePlayback(session, channel, retry = true) {
  const headers = playbackHeaders(session.account, channel);
  const response = await fetch(JIO.playback, {
    method: "POST", headers, body: new URLSearchParams({ stream_type: "Seek", channel_id: channel.id }),
    signal: AbortSignal.timeout(25_000),
  });
  const raw = await response.text();
  let payload = {};
  try { payload = raw ? JSON.parse(raw) : {}; } catch {}
  if ([401, 419, 403].includes(response.status) && retry) {
    await refreshAccount(session.account, !session.previewId);
    return authorizePlayback(session, channel, false);
  }
  if ([401, 419].includes(response.status)) throw Object.assign(new Error("Your Jio session expired. Sign in again."), { status: 401 });
  if (response.status === 403) throw Object.assign(new Error(providerMessage(payload, "This channel is unavailable for this account or device.")), { status: 403 });
  if (!response.ok) throw Object.assign(new Error(providerMessage(payload, `Jio playback returned HTTP ${response.status}.`)), { status: response.status });
  const hls = value(payload.result);
  const dash = value(payload.mpd?.result);
  const license = value(payload.mpd?.key);
  if (!hls && !dash) throw Object.assign(new Error(providerMessage(payload, "Jio returned no browser-playable stream.")), { status: 422 });
  const protocol = dash ? "dash" : "hls";
  const selected = dash || hls;
  const ticket = id();
  const streamHeaders = { ...headers, "user-agent": PLAYER_USER_AGENT };
  const cookie = signedCookie(selected);
  if (cookie) streamHeaders.cookie = cookie;
  const streamUrl = new URL(selected);
  const licenseHeaders = license ? {
    "content-type": "application/octet-stream",
    appName: "RJIL_JioTV",
    "x-platform": "android",
    os: "android",
    devicetype: "phone",
    versionCode: "389",
    srno: randomUUID(),
    channelid: channel.id,
  } : null;
  streamTickets.set(ticket, {
    sessionId: session.id,
    prefix:session.prefix||"",
    createdAt: now(),
    streamUrl: streamUrl.href,
    headers: streamHeaders,
    authorization: cookie,
    allowedHosts: new Set([streamUrl.hostname]),
    licenseUrl: license,
    licenseHeaders,
  });
  return {
    ticket,
    url: protocol === "dash" ? `${session.prefix||""}/api/stream/${ticket}/manifest.mpd` : `${session.prefix||""}/api/stream/${ticket}`,
    protocol,
    drm: Boolean(license),
    licenseUrl: license ? `${session.prefix||""}/api/license/${ticket}` : "",
    channel: { id: channel.id, number: channel.number, name: channel.name },
  };
}

export function safeMediaUrl(input) {
  const url = new URL(input);
  if (url.protocol !== "https:") throw new Error("Only HTTPS media is allowed.");
  const host = url.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".local") || host === "0.0.0.0" || host === "::1" || /^127\./.test(host) || /^10\./.test(host) || /^192\.168\./.test(host) || /^169\.254\./.test(host)) {
    throw new Error("Private-network media targets are blocked.");
  }
  return url;
}

export function authorizedMediaUrl(input, authorization = "") {
  const url = safeMediaUrl(input);
  if (!authorization || url.searchParams.has("__hdnea__") || !(url.hostname === "jio.com" || url.hostname.endsWith(".jio.com"))) return url;
  return safeMediaUrl(`${url.href}${url.search ? "&" : "?"}${authorization}`);
}

export function mediaUrlCandidates(input, authorization = "") {
  const direct = safeMediaUrl(input);
  if (!authorization || direct.searchParams.has("__hdnea__") || !(direct.hostname === "jio.com" || direct.hostname.endsWith(".jio.com"))) return [direct];
  // Jio's Android player authenticates child HLS requests with the signed
  // cookie. Some CDN edges return 404 when that same token is also copied to
  // a child playlist's query string, so try the Android-compatible request
  // first and retain the query form as a fallback for edges that require it.
  return [direct, authorizedMediaUrl(direct.href, authorization)];
}

function mediaRoute(ticket, target) {
  return `${streamTickets.get(ticket)?.prefix||""}/api/stream/${ticket}?u=${Buffer.from(target).toString("base64url")}`;
}

export function rewriteHlsManifest(text, base, ticket, onUrl = () => {}) {
  const rewrite = (reference) => {
    const absolute = new URL(reference, base).href;
    onUrl(absolute);
    return mediaRoute(ticket, absolute);
  };
  return String(text).split(/\r?\n/).map((line) => {
    if (!line) return line;
    if (line.startsWith("#")) return line.replace(/URI="([^"]+)"/g, (_, uri) => `URI="${rewrite(uri)}"`);
    return rewrite(line.trim());
  }).join("\n");
}

export function mergeTicketCookies(ticket, values = []) {
  const pairs = values.map((item) => item.split(";", 1)[0].trim()).filter(Boolean);
  if (!pairs.length) return;
  const existing = new Map(String(ticket.headers.cookie || "").split(";").map((p) => p.trim()).filter(Boolean).map((p) => [p.split("=", 1)[0], p]));
  for (const pair of pairs) {
    existing.set(pair.split("=", 1)[0], pair);
    if (pair.startsWith("__hdnea__=")) ticket.authorization = pair;
  }
  ticket.headers.cookie = [...existing.values()].join("; ");
}

function mergeSetCookies(ticket, response) {
  const values = typeof response.headers.getSetCookie === "function" ? response.headers.getSetCookie() : [];
  mergeTicketCookies(ticket, values);
}

async function proxyStream(req, res, session, pathname, searchParams) {
  const match = pathname.match(/^\/api\/stream\/([A-Za-z0-9_-]+)(?:\/(.*))?$/);
  if (!match) return false;
  const ticketId = match[1];
  const ticket = streamTickets.get(ticketId);
  if (!ticket || ticket.createdAt + STREAM_TTL_MS < now() || ticket.sessionId !== session?.id) {
    apiError(res, 404, "This stream session expired. Choose the channel again.", "stream_expired");
    return true;
  }
  let target = ticket.streamUrl;
  if (searchParams.get("u")) {
    try { target = Buffer.from(searchParams.get("u"), "base64url").toString("utf8"); }
    catch { return apiError(res, 400, "Invalid media URL."); }
  } else if (match[2] && match[2] !== "manifest.mpd") {
    target = new URL(match[2], new URL(".", ticket.streamUrl)).href;
  }
  let candidates;
  try { candidates = mediaUrlCandidates(target, ticket.authorization); } catch (error) { apiError(res, 400, error.message); return true; }
  for (const candidate of candidates) {
    const providerMediaHost = candidate.hostname === "jio.com" || candidate.hostname.endsWith(".jio.com");
    if (!ticket.allowedHosts.has(candidate.hostname) && !providerMediaHost) {
      apiError(res, 403, "This media host was not declared by the selected channel.", "media_host_blocked");
      return true;
    }
    if (providerMediaHost) ticket.allowedHosts.add(candidate.hostname);
  }
  const headers = { ...ticket.headers };
  delete headers["content-type"];
  if (req.headers.range) headers.range = req.headers.range;
  let upstream;
  let url = candidates[0];
  const attemptStatuses = [];
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30_000);
  timeout.unref();
  try {
    for (let index = 0; index < candidates.length; index += 1) {
      url = candidates[index];
      upstream = await fetch(url, { headers, redirect: "follow", signal: controller.signal });
      attemptStatuses.push(upstream.status);
      const retryableAuthorizationMiss = [401, 403, 404].includes(upstream.status) && index + 1 < candidates.length;
      if (!retryableAuthorizationMiss) break;
      await upstream.body?.cancel().catch(() => {});
    }
  } catch {
    apiError(res, 502, "The channel media could not be reached.", "media_unreachable");
    return true;
  } finally {
    clearTimeout(timeout);
  }
  let finalUrl;
  try { finalUrl = safeMediaUrl(upstream.url); } catch (error) { apiError(res, 502, error.message); return true; }
  ticket.allowedHosts.add(finalUrl.hostname);
  mergeSetCookies(ticket, upstream);
  if (!upstream.ok && upstream.status !== 206) {
    console.warn(JSON.stringify({
      event: "stream_proxy_failure",
      requestKind: searchParams.get("u") ? "manifest_child" : "root_manifest",
      time_utc: new Date().toISOString(),
      time_ist: new Intl.DateTimeFormat("en-IN",{timeZone:"Asia/Kolkata",dateStyle:"short",timeStyle:"medium",hour12:false}).format(new Date()),
      attempts: attemptStatuses,
      authorizationCookie: Boolean(ticket.headers.cookie),
      providerHost: finalUrl.hostname.endsWith(".jio.com"),
    }));
    apiError(res, upstream.status, `The channel media returned HTTP ${upstream.status}.`, "media_response");
    return true;
  }
  const contentType = upstream.headers.get("content-type") || "application/octet-stream";
  const manifest = /mpegurl|m3u8/i.test(contentType) || /\.m3u8(?:$|\?)/i.test(finalUrl.href);
  if (manifest) {
    const source = await upstream.text();
    const rewritten = rewriteHlsManifest(source, finalUrl.href, ticketId, (absolute) => {
      try { ticket.allowedHosts.add(safeMediaUrl(absolute).hostname); } catch {}
    });
    res.writeHead(upstream.status, {
      "content-type": "application/vnd.apple.mpegurl; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": `http://${HOST}:${PORT}`,
      "x-content-type-options": "nosniff",
    });
    res.end(rewritten);
    return true;
  }
  const responseHeaders = { "content-type": contentType, "cache-control": "no-store", "accept-ranges": upstream.headers.get("accept-ranges") || "bytes" };
  if (upstream.headers.get("content-length")) responseHeaders["content-length"] = upstream.headers.get("content-length");
  if (upstream.headers.get("content-range")) responseHeaders["content-range"] = upstream.headers.get("content-range");
  res.writeHead(upstream.status, responseHeaders);
  if (upstream.body) {
    const media = Readable.fromWeb(upstream.body);
    media.on("error", () => { if (!res.destroyed) res.destroy(); });
    res.on("close", () => { if (!media.destroyed) media.destroy(); });
    media.pipe(res);
  } else res.end();
  return true;
}

async function proxyLicense(req, res, session, pathname) {
  const match = pathname.match(/^\/api\/license\/([A-Za-z0-9_-]+)$/);
  if (!match) return false;
  if (req.method !== "POST") {
    apiError(res, 405, "License requests must use POST.", "method_not_allowed");
    return true;
  }
  const ticket = streamTickets.get(match[1]);
  if (!ticket || ticket.createdAt + STREAM_TTL_MS < now() || ticket.sessionId !== session?.id) {
    apiError(res, 404, "This stream session expired. Choose the channel again.", "stream_expired");
    return true;
  }
  if (!ticket.licenseUrl || !ticket.licenseHeaders) {
    apiError(res, 422, "This channel did not provide a Widevine license endpoint.", "license_unavailable");
    return true;
  }
  let licenseUrl;
  try { licenseUrl = safeMediaUrl(ticket.licenseUrl); }
  catch (error) { apiError(res, 502, error.message, "license_url_invalid"); return true; }
  const challenge = await readBytes(req);
  const response = await fetch(licenseUrl, {
    method: "POST",
    headers: { ...ticket.headers, ...ticket.licenseHeaders },
    body: challenge,
    redirect: "follow",
    signal: AbortSignal.timeout(30_000),
  });
  const body = Buffer.from(await response.arrayBuffer());
  if (!response.ok) {
    apiError(res, response.status, `Jio license request returned HTTP ${response.status}.`, "license_response");
    return true;
  }
  res.writeHead(200, {
    "content-type": response.headers.get("content-type") || "application/octet-stream",
    "content-length": body.length,
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  res.end(body);
  return true;
}

async function serveStatic(res, pathname) {
  const requested = pathname === "/" ? "/index.html" : pathname;
  const vendor = requested === "/vendor/hls.min.js";
  const root = vendor ? join(ROOT, "node_modules", "hls.js", "dist") : PUBLIC;
  const candidate = vendor ? join(root, "hls.min.js") : normalize(join(root, requested));
  if (!candidate.startsWith(`${root}/`) && candidate !== root) return false;
  try {
    const info = await stat(candidate);
    if (!info.isFile()) return false;
    const type = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".svg": "image/svg+xml" }[extname(candidate)] || "application/octet-stream";
    res.writeHead(200, {
      "content-type": type,
      "content-length": info.size,
      "cache-control": "no-store",
      "content-security-policy": "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https:; media-src 'self' blob:; connect-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
      "referrer-policy": "no-referrer",
      "x-content-type-options": "nosniff",
      "x-frame-options": "DENY",
    });
    createReadStream(candidate).pipe(res);
    return true;
  } catch { return false; }
}

async function handleApi(req, res, url) {
  const session = sessionFor(req, res, true);
  await restorePersistedAccount(session);
  if (await proxyLicense(req, res, session, url.pathname)) return;
  if (await proxyStream(req, res, session, url.pathname, url.searchParams)) return;
  if (req.method === "GET" && url.pathname === "/api/health") {
    json(res, 200, { ok: true, service: "ghartv-web-player", version: APP_VERSION, commit: process.env.GHARTV_WEB_SHA || "working-tree", host: HOST, port: PORT });
    return;
  }
  if (req.method === "GET" && url.pathname === "/api/auth/status") {
    json(res, 200, { ok: true, connected: Boolean(session.account), mobile: session.account ? `••••••${session.account.mobile.slice(-4)}` : "" });
    return;
  }
  if (req.method === "POST") assertLocalOrigin(req);
  if (req.method === "POST" && url.pathname === "/api/auth/otp/send") {
    const body = await readJson(req);
    const mobile = normalizeMobile(body.mobile);
    await sendOtp(mobile);
    session.pendingMobile = mobile;
    json(res, 200, { ok: true, destination: `••••••${mobile.slice(-4)}` });
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/auth/otp/verify") {
    if (!session.pendingMobile) return apiError(res, 409, "Send an OTP first.", "otp_not_sent");
    const body = await readJson(req);
    session.account = await verifyOtp(session.pendingMobile, body.otp);
    const persisted = session.previewId ? false : await savePersistedAccount(session.account);
    session.pendingMobile = "";
    json(res, 200, { ok: true, connected: true, persistentLogin: persisted, mobile: `••••••${session.account.mobile.slice(-4)}` });
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/auth/logout") {
    session.account = null;
    session.pendingMobile = "";
    if(!session.previewId)await deletePersistedAccount();
    for (const [ticket, item] of streamTickets) if (item.sessionId === session.id) streamTickets.delete(ticket);
    json(res, 200, { ok: true });
    return;
  }
  if (req.method === "GET" && url.pathname === "/api/channels") {
    if (!session.account) return apiError(res, 401, "Connect your Jio number first.", "auth_required");
    const channels = await fetchCatalogue(url.searchParams.get("refresh")==="1");
    json(res, 200, { ok: true, guide:catalogueOutcome, channels, count: channels.length, provider: { id: "jio", label: "JioTV", authorization: "experimental_owner_local" } });
    return;
  }
  if (req.method === "GET" && url.pathname === "/api/epg") {
    if (!session.account) return apiError(res, 401, "Connect your Jio number first.", "auth_required");
    const channelId = String(url.searchParams.get("channel_id") || "");
    if (!/^\d{1,8}$/.test(channelId)) return apiError(res, 400, "Invalid channel.");
    const offset = Number(url.searchParams.get("offset") || 0);
    if (!Number.isInteger(offset) || offset < -7 || offset > 1) return apiError(res, 400, "Invalid programme-guide day.");
    const endpoint = JIO.epg.replace("offset=0", `offset=${offset}`);
    const { payload } = await upstreamJson(`${endpoint}${encodeURIComponent(channelId)}&langId=6`, { headers: { "user-agent": MOBILE_USER_AGENT }, timeout: 18_000 });
    json(res, 200, { ok: true, programs: Array.isArray(payload.epg) ? payload.epg : [] });
    return;
  }
  if (req.method === "POST" && url.pathname === "/api/playback") {
    if (!session.account) return apiError(res, 401, "Connect your Jio number first.", "auth_required");
    const body = await readJson(req);
    const channels = await fetchCatalogue();
    const channel = channels.find((item) => item.id === String(body.channelId || ""));
    if (!channel) return apiError(res, 404, "Channel not found.");
    const playback = await authorizePlayback(session, channel);
    json(res, 200, { ok: true, ...playback, source: { provider: "JioTV", authorization: "experimental_owner_local", mode: playback.protocol === "dash" ? "DASH Widevine through loopback proxy" : "HLS through loopback proxy" } });
    return;
  }
  apiError(res, 404, "Not found.", "not_found");
}

export function createAppServer() {
  return createServer(async (req, res) => {
    const url = new URL(req.url || "/", `http://${HOST}:${PORT}`);
    if(!['127.0.0.1','localhost','::1'].includes(HOST))return apiError(res,403,'Loopback-only owner service.','invalid_bind');
    if(!['127.0.0.1:'+PORT,'localhost:'+PORT,'[::1]:'+PORT].includes(req.headers.host||''))return apiError(res,403,'Host rejected.','invalid_host');
    const began=Date.now();
    res.once('finish',()=>{if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/owner-api/'))console.info(JSON.stringify({event:'http_request',time_ist:new Intl.DateTimeFormat('en-IN',{timeZone:'Asia/Kolkata',dateStyle:'short',timeStyle:'medium',hour12:false}).format(new Date()),time_utc:new Date().toISOString(),route:/^\/api\/(stream|license)\//.test(url.pathname)?'/api/media/[redacted]':url.pathname,status:res.statusCode,duration_ms:Date.now()-began}));});
    try {
      req.ghartvViewer=previewContext(req,res,url);if(req.ghartvViewer.handled)return;
      if(req.method==='GET'&&url.pathname==='/viewer-context.js'){
        res.writeHead(200,{'Content-Type':'text/javascript','Cache-Control':'no-store'});
        res.end('window.GHARTV_VIEWER='+JSON.stringify({preview:!!req.ghartvViewer.preview})+';');return;
      }
      if(req.method==='GET'&&url.pathname==='/api/health'){json(res,200,{ok:true,service:'ghartv-web-player',version:APP_VERSION,commit:process.env.GHARTV_WEB_SHA||'working-tree',host:HOST,port:PORT,timezone:'Asia/Kolkata',owner_reader:!req.ghartvViewer.preview,web_revision:WEB_REPAIR,base_source:process.env.GHARTV_WEB_SHA||'working-tree',web_overlay:process.env.GHARTV_WEB_OVERLAY||'not_verified',fabric_preview:{isolated_sessions:true,owner_routes_exposed:false,header_gate:true}});return;}
      if(!req.ghartvViewer.preview && await ownerRoute(req,res,url))return;
      if (url.pathname.startsWith("/api/")) await handleApi(req, res, url);
      else if (!(await serveStatic(res, url.pathname))) apiError(res, 404, "Not found.");
    } catch (error) {
      if (res.headersSent) {
        if (!res.destroyed) res.destroy();
        return;
      }
      const status = Number(error.status) || (error instanceof SyntaxError ? 400 : 502);
      apiError(res, status, error.message || "The request failed.", status === 401 ? "auth_required" : "provider_error");
    }
  });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = createAppServer();
  server.listen(PORT, HOST, () => {
    process.stdout.write(`GharTV web player ${APP_VERSION} listening at http://${HOST}:${PORT}\n`);
  });
}
