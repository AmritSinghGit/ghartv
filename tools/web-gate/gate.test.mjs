import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { blocked, makeLimiter, makeStreams, accessEmail, originOk } from './gate.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'k1' };
const team = 'https://family.cloudflareaccess.com', aud = 'AUD1', allowed = ['owner@example.com'];
const enc = o => Buffer.from(JSON.stringify(o)).toString('base64url');
function token(body, head = { alg: 'RS256', kid: 'k1' }, key = privateKey) {
  const h = enc(head), p = enc(body);
  return `${h}.${p}.${sign('RSA-SHA256', Buffer.from(`${h}.${p}`), key).toString('base64url')}`;
}
const good = { aud: [aud], iss: team, exp: Math.floor(Date.now() / 1000) + 600, email: 'Owner@Example.com' };
const opts = { keys: [jwk], aud, team, allowed };

test('valid Access token for a named email passes', () => assert.equal(accessEmail(token(good), opts), 'owner@example.com'));
test('missing, unnamed, expired, wrong aud/iss, forged or none-alg tokens fail', () => {
  assert.equal(accessEmail(undefined, opts), null);
  assert.equal(accessEmail(token({ ...good, email: 'stranger@example.com' }), opts), null);
  assert.equal(accessEmail(token({ ...good, exp: 1 }), opts), null);
  assert.equal(accessEmail(token({ ...good, aud: ['OTHER'] }), opts), null);
  assert.equal(accessEmail(token({ ...good, iss: 'https://evil.cloudflareaccess.com' }), opts), null);
  const other = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey;
  assert.equal(accessEmail(token(good, undefined, other), opts), null);
  assert.equal(accessEmail(`${enc({ alg: 'none', kid: 'k1' })}.${enc(good)}.x`, opts), null);
});
test('film, Tor and owner paths are blocked; Jio paths are not', () => {
  for (const p of ['/api/films/search', '/api/films', '/flixmomo.html', '/owner.html', '/owner-api/fabric/status', '/owner-api/providers/inspect',
    '/provider-access.html', '/release-control/x', '/owner-api/release/x', '/owner-api/performance/latest', '/support/capture', '/support/download/1',
    '/review/sync', '/review/feedback', '/config-status', '/api/anything-new', '/api/media/abc', '/api/stream/t1/../../owner-api/x', '/api/stream/t1/a/../../../owner.html', '/api/stream/t.1', '/s/abc/api/stream/t1', '/api/filmsx'])
    assert.ok(blocked(p), p);
  for (const p of ['/', '/index.html', '/app.js', '/styles.css', '/vendor/hls.min.js', '/viewer-context.js', '/api/health', '/api/auth/status',
    '/api/auth/otp/send', '/api/auth/otp/verify', '/api/auth/logout', '/api/channels', '/api/epg', '/api/playback', '/api/stream/t1', '/api/stream/t1/manifest.mpd', '/api/stream/t1/video/720p/seg-00042.m4s', '/api/stream/t1/init.mp4', '/api/license/t1'])
    assert.ok(!blocked(p), p);
});
test('rate limits: otp/send 5 per 10 min, then recovers', () => {
  let t = 0; const lim = makeLimiter(() => t);
  for (let i = 0; i < 5; i++) assert.ok(lim('a', '/api/auth/otp/send'));
  assert.ok(!lim('a', '/api/auth/otp/send'));
  assert.ok(lim('b', '/api/auth/otp/send'), 'per email');
  t = 600_001; assert.ok(lim('a', '/api/auth/otp/send'));
});
test('rate limits: playback 30 per minute, rest 300 per minute', () => {
  const lim = makeLimiter(() => 0);
  for (let i = 0; i < 30; i++) assert.ok(lim('a', '/api/playback'));
  assert.ok(!lim('a', '/api/playback'));
  for (let i = 0; i < 300; i++) assert.ok(lim('a', '/api/channels'));
  assert.ok(!lim('a', '/api/epg'));
});
test('at most 3 streams; an idle stream frees its place after 20 s', () => {
  let t = 0; const s = makeStreams(() => t);
  for (const k of ['s1', 's2', 's3']) assert.ok(s(`/api/stream/${k}`));
  assert.ok(s('/api/stream/s1/manifest.mpd'), 'existing stream continues');
  assert.ok(s('/api/stream/s2/video/seg-1.m4s'), 'DASH segment of an existing stream continues');
  assert.ok(!s('/api/stream/s4/video/seg-1.m4s'), 'a fourth stream is refused on its segments too');
  assert.ok(!s('/api/stream/s4'), 'fourth refused');
  assert.ok(s('/api/license/s4'), 'licence requests are not counted');
  assert.ok(s('/api/channels'), 'other paths unaffected');
  t = 15_000; s('/api/stream/s1'); s('/api/stream/s2');
  t = 21_000; assert.ok(s('/api/stream/s4'), 's3 idle > 20 s');
});

test('a POST from another origin is refused; same origin, no Origin and GET pass', () => {
  const o = 'https://ghartv.vcnow.in';
  assert.ok(!originOk('POST', 'https://evil.example', o));
  assert.ok(!originOk('POST', 'null', o));
  assert.ok(!originOk('DELETE', 'http://127.0.0.1:8790', o));
  assert.ok(originOk('POST', o, o));
  assert.ok(originOk('POST', undefined, o));
  assert.ok(originOk('GET', 'https://evil.example', o));
});
