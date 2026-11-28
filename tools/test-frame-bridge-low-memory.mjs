import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import test from 'node:test';

// Execute the ACTUAL document-start script, not a rewritten mock of its logic.
const java = readFileSync(
  new URL('../android-tv/app/src/main/java/in/ghartv/nova/FilmPhoneFrameBridge.java',import.meta.url),
  'utf8'
);
const match = java.match(/return """\s*\n([\s\S]*?)\n\s*""";/);
assert.ok(match, 'Java text-block source present');
const script = match[1];
function bridgeFixture() {
  let hasVideo = false, frames = [], geometryReads = 0, styleReads = 0;
  const reports = [], scheduled = [], eventHandlers = new Map();
  const video = {
    mediaKeys: null,
    ended: false, paused: true, readyState: 3, currentTime: 0,
    duration: 50, seekable: { length: 0 },
    getBoundingClientRect() { geometryReads++; return {width: 500,height: 300}; }
  };
  const iframe = { getBoundingClientRect() { geometryReads++; return {width: 480,height: 270}; } };
  const document = {
    querySelector(name) { assert.equal(name,'video');return hasVideo?video:null; },
    querySelectorAll(name) {
      if (name === 'video') return hasVideo ? [video] : [];
      if (name === 'iframe') return frames;
      throw Error('Unexpected DOM selector');
    },
    addEventListener(name, fn) { eventHandlers.set(name, fn); }
  };
  const window = {
    top: {}, parent: { postMessage(payload) { reports.push(payload); } },
    addEventListener(name,fn) { eventHandlers.set(name,fn); }
  };
  vm.runInNewContext(script, {
    window,document,location:{protocol:'https:'},
    getComputedStyle() { styleReads++; return {display:'block',visibility:'visible'}; },
    setInterval(fn,period) { scheduled.push({fn,period}); },
    setTimeout(fn) { fn(); },Date
  }, {timeout:1500});
  return {
    reports, scheduled, handlers:eventHandlers,
    setVideo(value) { hasVideo=value; },setFrames(value) {frames=value?[iframe]:[];},
    readCosts() {return {geometryReads,styleReads};},
    tick() {assert.equal(scheduled.length,1);scheduled[0].fn();}
  };
}
test('idle HTTPS frame does not compute iframe geometry or styles on heartbeat', () => {
  const f=bridgeFixture();
  assert.equal(f.scheduled[0].period,850);
  f.setFrames(true);
  for(let i=0;i<20;i++) f.tick();
  assert.deepEqual(f.readCosts(),{geometryReads:0,styleReads:0});
  assert.equal(f.reports.length,0);
});
test('one visible plain video still reports state when dynamically added', () => {
  const f=bridgeFixture();
  f.tick(); f.setVideo(true); f.tick();
  assert.equal(f.reports.length,1);
  assert.equal(f.reports[0].kind,'report');
  assert.equal(f.reports[0].state.state,'paused');
  assert.equal(f.reports[0].state.observable,true);
});
test('visible iframe alongside a video prevents false ownership', () => {
  const f=bridgeFixture();
  f.setVideo(true); f.setFrames(true); f.tick();
  assert.equal(f.reports.length,0);
});
