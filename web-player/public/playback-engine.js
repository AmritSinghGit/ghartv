/* No provider tokens, history or analytics. Shared native-browser selection. */
(function (root) {
  'use strict';
  const api = {
    capabilities(video, nav = navigator, win = window) {
      const nativeHls = Boolean(video.canPlayType('application/vnd.apple.mpegurl') || video.canPlayType('application/x-mpegURL'));
      const apple = /Apple/.test(nav.vendor || '') || (/AppleWebKit/.test(nav.userAgent || '') && !/Chrome|Chromium|Edg\//.test(nav.userAgent || ''));
      return {nativeHls, preferNativeHls: nativeHls && (apple || 'ManagedMediaSource' in win)};
    },
    engine(video, Hls = root.Hls, nav = navigator, win = window) {
      const cap = api.capabilities(video, nav, win);
      if (cap.preferNativeHls) return 'native';
      if (Hls && Hls.isSupported()) return 'hlsjs';
      return cap.nativeHls ? 'native' : 'unsupported';
    },
    async play(video, onGesture, onError) {
      try { await video.play(); return true; }
      catch (e) {
        if (e.name === 'NotAllowedError') onGesture();
        else if (e.name !== 'AbortError') onError(e);
        return false;
      }
    },
    async widevine(nav = navigator) {
      if (!nav.requestMediaKeySystemAccess) return false;
      let timer;
      try {
        await Promise.race([nav.requestMediaKeySystemAccess('com.widevine.alpha', [{initDataTypes:['cenc'], sessionTypes:['temporary'], distinctiveIdentifier:'optional', persistentState:'optional', videoCapabilities:[{contentType:'video/mp4; codecs="avc1.42E01E"'}]}]), new Promise((_, reject) => {timer=setTimeout(()=>reject(new Error('probe_timeout')), 2000);})]);
        return true;
      } catch { return false; } finally { clearTimeout(timer); }
    }
  };
  root.GharTVPlayback = api;
})(typeof window === 'undefined' ? globalThis : window);
