import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { blocked, makeLimiter, makeStreams, accessEmail } from './gate.mjs';

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
    '/review/sync', '/review/feedback', '/config-status', '/api/anything-new', '/api/media/../owner-api/x', '/api/filmsx'])
    assert.ok(blocked(p), p);
  for (const p of ['/', '/index.html', '/app.js', '/styles.css', '/vendor/hls.min.js', '/viewer-context.js', '/api/health', '/api/auth/status',
    '/api/auth/otp/send', '/api/auth/otp/verify', '/api/auth/logout', '/api/channels', '/api/epg', '/api/playback', '/api/media/abc/seg.ts'])
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
  for (const k of ['s1', 's2', 's3']) assert.ok(s(`/api/media/${k}/seg1.ts`));
  assert.ok(s('/api/media/s1/seg2.ts'), 'existing stream continues');
  assert.ok(!s('/api/media/s4/seg1.ts'), 'fourth refused');
  assert.ok(s('/api/channels'), 'non-media unaffected');
  t = 15_000; s('/api/media/s1/seg3.ts'); s('/api/media/s2/seg3.ts');
  t = 21_000; assert.ok(s('/api/media/s4/seg1.ts'), 's3 idle > 20 s');
});
