/** Select only a rendition supplied by the authenticated provider response. */
export function selectPlayback(payload, capabilities = {}) {
  const text = x => typeof x === 'string' ? x : '';
  const hls = text(payload.result), dash = text(payload.mpd?.result), license = text(payload.mpd?.key);
  if (!hls && !dash) throw Object.assign(new Error('The provider returned no playable stream.'), {status:422, code:'stream_unavailable'});
  const preferHls = capabilities.nativeHls === true && (capabilities.preferNativeHls === true || capabilities.widevine === false);
  const protocol = hls && (preferHls || !dash) ? 'hls' : 'dash';
  if (protocol === 'dash' && license && capabilities.widevine === false)
    throw Object.assign(new Error('This channel is protected with Widevine and the provider did not return a compatible HLS alternative. Use the Android TV app or a browser with Widevine enabled, such as Chrome or Edge. Your sign-in is still valid.'), {status:422, code:'browser_drm_unavailable'});
  return {protocol, url: protocol === 'hls' ? hls : dash, license: protocol === 'dash' ? license : '', alternativeProvided: Boolean(hls && dash)};
}
