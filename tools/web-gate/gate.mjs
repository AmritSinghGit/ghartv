#!/usr/bin/env node
// GharTV web gate: sits between the Cloudflare tunnel and the loopback-only web player (CSO ruling 6 Nov, stage 1).
// The tunnel points here; the gate forwards to the player with Host 127.0.0.1:8790. It does not change the player.
//   - Cloudflare Access identity: verifies Cf-Access-Jwt-Assertion (RS256, team keys, aud, iss, exp) and that the
//     email is one of the named household emails. Anything else: 403.
//   - Only the live-TV page and APIs are forwarded (allowlist); film/Tor, owner and every other path: 404.
//   - Rate limits per email: otp/send 5 per 10 min, otp/verify 10 per 10 min, playback 30 per min, the rest 300 per min.
//   - At most 3 streams at once (a stream = a /api/media session seen in the last 20 s).
// Config (environment): GATE_PORT (18792), UPSTREAM_PORT (8790), ACCESS_TEAM (https://<team>.cloudflareaccess.com),
// ACCESS_AUD (the Access application's AUD tag), ALLOWED_EMAILS (comma separated). Run with: nice -n 15 node gate.mjs
import { createServer, request } from 'node:http';
import { createPublicKey, verify } from 'node:crypto';

// Allowlist: only the live-TV page and its APIs reach the player. Everything else (films/FlixMomo and its Tor route,
// /owner*, /owner-api/*, /provider-access, /release*, /performance*, /support/*, /review/*, /config-status, and any
// route added later) is refused with 404. Checked against every route in web-player/*.mjs on 9e38de9.
export const ALLOWED = [/^\/$/, /^\/index\.html$/, /^\/(app|playback-engine|shaka-player\.compiled|viewer-context)\.js$/,
  /^\/(player|styles)\.css$/, /^\/vendor\/hls\.min\.js$/, /^\/favicon\.ico$/,
  /^\/api\/health$/, /^\/api\/auth\/(status|logout|otp\/send|otp\/verify)$/, /^\/api\/(channels|epg|playback)$/,
  /^\/api\/media\/[^/]+(\/.*)?$/];
export const LIMITS = [
  { name: 'otp-send', test: p => p === '/api/auth/otp/send', max: 5, windowMs: 600_000 },
  { name: 'otp-verify', test: p => p === '/api/auth/otp/verify', max: 10, windowMs: 600_000 },
  { name: 'playback', test: p => p === '/api/playback', max: 30, windowMs: 60_000 },
  { name: 'rest', test: () => true, max: 300, windowMs: 60_000 },
];
export const MAX_STREAMS = 3, STREAM_IDLE_MS = 20_000;

export function blocked(path) { return !ALLOWED.some(r => r.test(path)) || /(^|\/)\.\.(\/|$)/.test(path); }

/** Sliding-window counter per key; returns true when the request is allowed. */
export function makeLimiter(now = () => Date.now()) {
  const hits = new Map();
  return (email, path) => {
    const rule = LIMITS.find(l => l.test(path)), key = `${email}|${rule.name}`, t = now();
    const list = (hits.get(key) || []).filter(x => t - x < rule.windowMs);
    if (list.length >= rule.max) { hits.set(key, list); return false; }
    list.push(t); hits.set(key, list); return true;
  };
}

/** Streams: /api/media/<session>/...; a new session is refused while MAX_STREAMS others were active in the last 20 s. */
export function makeStreams(now = () => Date.now()) {
  const seen = new Map();
  return path => {
    const m = /^\/api\/media\/([^/]+)/.exec(path);
    if (!m) return true;
    const t = now();
    for (const [k, v] of seen) if (t - v > STREAM_IDLE_MS) seen.delete(k);
    if (!seen.has(m[1]) && seen.size >= MAX_STREAMS) return false;
    seen.set(m[1], t); return true;
  };
}

const b64url = s => Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');

/** Returns the verified email, or null. keys = JWKs from <team>/cdn-cgi/access/certs. */
export function accessEmail(token, { keys, aud, team, allowed, now = () => Date.now() }) {
  try {
    const [h, p, s] = String(token || '').split('.');
    if (!s) return null;
    const head = JSON.parse(b64url(h)), body = JSON.parse(b64url(p));
    if (head.alg !== 'RS256') return null;
    const jwk = keys.find(k => k.kid === head.kid);
    if (!jwk || !verify('RSA-SHA256', Buffer.from(`${h}.${p}`), createPublicKey({ key: jwk, format: 'jwk' }), b64url(s))) return null;
    const auds = Array.isArray(body.aud) ? body.aud : [body.aud];
    if (!auds.includes(aud) || body.iss !== team || !(body.exp * 1000 > now())) return null;
    const email = String(body.email || '').toLowerCase();
    return allowed.includes(email) ? email : null;
  } catch { return null; }
}

function main() {
  const team = (process.env.ACCESS_TEAM || '').replace(/\/$/, ''), aud = process.env.ACCESS_AUD || '';
  const allowed = (process.env.ALLOWED_EMAILS || '').split(',').map(e => e.trim().toLowerCase()).filter(Boolean);
  if (!team.startsWith('https://') || !aud || !allowed.length) { console.error('set ACCESS_TEAM, ACCESS_AUD and ALLOWED_EMAILS'); process.exit(2); }
  const port = Number(process.env.GATE_PORT || 18792), upstream = Number(process.env.UPSTREAM_PORT || 8790);
  let keys = [], fetched = 0;
  const loadKeys = async () => {
    if (Date.now() - fetched < 3_600_000 && keys.length) return keys;
    const r = await fetch(`${team}/cdn-cgi/access/certs`); keys = (await r.json()).keys || []; fetched = Date.now(); return keys;
  };
  const limit = makeLimiter(), streams = makeStreams();
  const deny = (res, code, text) => { res.writeHead(code, { 'content-type': 'text/plain' }); res.end(text + '\n'); };
  createServer(async (req, res) => {
    const path = new URL(req.url, 'http://gate').pathname;
    const email = accessEmail(req.headers['cf-access-jwt-assertion'], { keys: await loadKeys().catch(() => keys), aud, team, allowed });
    if (!email) return deny(res, 403, 'Not signed in for GharTV.');
    if (blocked(path)) return deny(res, 404, 'Not found.');
    if (!limit(email, path)) return deny(res, 429, 'Too many requests, wait a minute.');
    if (!streams(path)) return deny(res, 429, 'Three streams are already playing.');
    const headers = { ...req.headers, host: `127.0.0.1:${upstream}` };
    delete headers['cf-access-jwt-assertion'];
    // The player accepts only its own loopback origin (server.mjs allowlist), as for the tailnet proxy.
    if (headers.origin) headers.origin = `http://127.0.0.1:${upstream}`;
    delete headers.referer;
    const up = request({ host: '127.0.0.1', port: upstream, method: req.method, path: req.url, headers }, r => {
      res.writeHead(r.statusCode, r.headers); r.pipe(res);
    });
    up.on('error', () => deny(res, 502, 'GharTV is not running.'));
    req.pipe(up);
  }).listen(port, '127.0.0.1', () => console.log(`GharTV gate on 127.0.0.1:${port} -> 127.0.0.1:${upstream}`));
}

if (import.meta.url === `file://${process.argv[1]}`) main();
