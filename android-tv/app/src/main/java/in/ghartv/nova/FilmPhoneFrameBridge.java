package in.ghartv.nova;

/**
 * Narrow phone media bridge, injected by AndroidX WebKit into HTTPS documents
 * and their player frames. It never reads URLs, cookies, form values, network
 * traffic or DRM. Only one visible, unprotected HTML5 video is controllable.
 * Every parent accepts reports only from its single visible direct iframe.
 * No native JavaScript interface is exposed to untrusted sites.
 */
final class FilmPhoneFrameBridge {
    private FilmPhoneFrameBridge() {}

    static String documentStart() {
        return """
            (() => {
              'use strict';
              if (window.__ghartvPhoneBridgeInstalled) return;
              Object.defineProperty(window, '__ghartvPhoneBridgeInstalled', {value: true});
              const tag = 'ghartv-phone-media-v1';
              const visible = element => {
                const b = element.getBoundingClientRect();
                const style = getComputedStyle(element);
                return b.width >= 160 && b.height >= 90 &&
                  style.display !== 'none' && style.visibility !== 'hidden';
              };
              const oneVideo = () => {
                const videos = Array.from(document.querySelectorAll('video'))
                  .slice(0,24).filter(visible);
                const frames = Array.from(document.querySelectorAll('iframe'))
                  .slice(0,32).filter(visible);
                return videos.length === 1 && frames.length === 0 &&
                  !videos[0].mediaKeys ? videos[0] : null;
              };
              const safeMs = value => Number.isFinite(value) && value >= 0 ?
                Math.min(86400000, Math.round(value * 1000)) : 0;
              const sample = video => {
                let start = 0, end = 0, seekable = false;
                try {
                  const ranges = video.seekable;
                  if (ranges && ranges.length) {
                    start = ranges.start(0);
                    end = ranges.end(ranges.length - 1);
                    seekable = Number.isFinite(start) && Number.isFinite(end) &&
                      start >= 0 && end > start && end <= 86400;
                  }
                } catch (_) {}
                return {
                  state: video.ended ? 'paused' : video.paused ? 'paused' :
                    video.readyState >= 2 ? 'playing' : 'starting',
                  observable: true, positionMs: safeMs(video.currentTime),
                  durationMs: safeMs(video.duration), seekable,
                  seekStartMs: seekable ? safeMs(start) : 0,
                  seekEndMs: seekable ? safeMs(end) : 0
                };
              };
              const directChild = candidate => {
                const frames = Array.from(document.querySelectorAll('iframe'))
                  .slice(0,32).filter(visible);
                return frames.length === 1 &&
                  frames[0].contentWindow === candidate ? frames[0] : null;
              };
              const recentChild = () => {
                const state = window.__ghartvPhoneFrame;
                return state && Date.now() - state.at < 2500 &&
                  directChild(state.source) ? state : null;
              };
              const report = () => {
                if (window === window.top || location.protocol !== 'https:') return;
                const video = oneVideo();
                if (!video) return;
                window.parent.postMessage({tag, kind:'report', state:sample(video)}, '*');
              };
              window.addEventListener('message', event => {
                const data = event.data;
                if (!data || data.tag !== tag) return;
                if (data.kind === 'report') {
                  if (!event.origin.startsWith('https://') ||
                      !directChild(event.source)) return;
                  const state = data.state;
                  if (!state || !['playing','paused','starting'].includes(state.state) ||
                      state.observable !== true || typeof state.seekable !== 'boolean') return;
                  for (const key of ['positionMs','durationMs','seekStartMs','seekEndMs'])
                    if (!Number.isInteger(state[key]) || state[key] < 0 ||
                        state[key] > 86400000) return;
                  window.__ghartvPhoneFrame = {
                    source: event.source, at: Date.now(), state
                  };
                  // Nested frames relay only bounded playback state, never
                  // their DOM, their origin, the source URL or form contents.
                  if (window !== window.top && location.protocol === 'https:')
                    window.parent.postMessage({tag,kind:'report',state},'*');
                  return;
                }
                if (data.kind !== 'command' || window === window.top ||
                    event.source !== window.parent || location.protocol !== 'https:' ||
                    !['play','pause','seek'].includes(data.operation)) return;
                const video = oneVideo();
                if (!video) {
                  const child = recentChild();
                  if (child && (data.operation !== 'seek' || child.state.seekable))
                    child.source.postMessage(data, '*');
                  return;
                }
                try {
                  if (data.operation === 'play') {
                    const promise = video.play();
                    if (promise && promise.catch) promise.catch(() => {});
                  } else if (data.operation === 'pause') video.pause();
                  else {
                    if (!Number.isInteger(data.positionMs) ||
                        data.positionMs < 0 || data.positionMs > 86400000 ||
                        !video.seekable || !video.seekable.length) return;
                    const seconds = data.positionMs/1000;
                    let best = null, difference = Infinity;
                    for (let i = 0; i < video.seekable.length; ++i) {
                      const a = video.seekable.start(i), b = video.seekable.end(i);
                      if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) continue;
                      const target = Math.max(a, Math.min(b, seconds));
                      if (Math.abs(target-seconds) < difference) {
                        best = target; difference = Math.abs(target-seconds);
                      }
                    }
                    if (best === null) return;
                    video.currentTime = best;
                  }
                  setTimeout(report, 180);
                } catch (_) {}
              });
              if (window === window.top || location.protocol !== 'https:') return;
              setInterval(report, 850);
              document.addEventListener('visibilitychange', report);
            })();
            """;
    }
}
