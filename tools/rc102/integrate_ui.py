"""Apply the browser corrections once to the existing native files."""
from pathlib import Path
R=Path(__file__).resolve().parents[2]
def edit(name,fn):
 p=R/name;p.write_text(fn(p.read_text()))
def once(s,a,b):
 if s.count(a)!=1:raise ValueError('ANCHOR_CHANGED: '+a[:80])
 return s.replace(a,b)
edit('web-player/public/index.html',lambda s:once(s,'  <script defer src="./app.js"></script>','  <script defer src="./playback-engine.js"></script>\n  <script defer src="./app.js"></script>').replace('<a class="owner-link" href="./owner.html">Owner console ↗</a>','').replace('<video id="video" autoplay playsinline></video>','<video id="video" playsinline preload="metadata"></video>\n      <button id="tapToPlay" class="tap-to-play hidden" type="button">▶ Play channel</button>'))
edit('web-player/public/player.css',lambda s:s+'\n.tap-to-play{position:absolute;top:40%;left:50%;transform:translate(-50%,-50%);z-index:20;padding:18px 30px;border-radius:14px;background:#b9eed5;color:#112a20;border:0;font:700 18px system-ui;cursor:pointer}.tap-to-play:focus-visible{outline:3px solid white;outline-offset:6px}.tap-to-play.hidden{display:none}\n')
def app(s):
 start=s.index('async function supportsWidevine()');end=s.index('\nfunction protectedPlaybackMessage',start)
 s=s[:start]+'''async function supportsWidevine() {
  if (state.widevineSupport === null) state.widevineSupport = await GharTVPlayback.widevine();
  return state.widevineSupport;
}
function startVideo(video) {
  const generation=state.playbackGeneration;
  return GharTVPlayback.play(video,()=>{if(generation===state.playbackGeneration){$("tapToPlay").classList.remove("hidden");$("playerStatus").textContent="Press Play to start";}},e=>{if(generation===state.playbackGeneration){$("playerStatus").textContent="Unable to play";state.playerError=e.message;$("playerProgrammeDescription").textContent=e.message;}});
}
'''+s[end:]
 s=s.replace('This channel uses Widevine protection. Open GharTV in Google Chrome on this Mac.','This channel uses Widevine protection. Use the Android TV app or a browser with Widevine enabled, such as Chrome or Edge. Other compatible channels can still play here.')
 s=s.replace('message.includes("Google Chrome") ? "Open in Chrome"','message.includes("Widevine") ? "Browser compatibility"')
 s=s.replace('await video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });','await startVideo(video);').replace('video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });','startVideo(video);')
 s=once(s,'body:JSON.stringify({channelId:channel.id})','body:JSON.stringify({channelId:channel.id,capabilities:{...GharTVPlayback.capabilities($("video")),widevine:await supportsWidevine()}})')
 s=once(s,'  const generation = state.playbackGeneration;\n  state.playbackAbort','  const generation = state.playbackGeneration;\n  $("tapToPlay").classList.add("hidden");\n  state.playbackAbort')
 s=s.replace('state.playerError="";$("playerStatus").textContent = "LIVE";','state.playerError="";$("tapToPlay").classList.add("hidden");$("playerStatus").textContent = "LIVE";')
 s=once(s,'    } else if (window.Hls?.isSupported()) {','''    } else if (GharTVPlayback.engine(video) === "native") {
      video.src = playback.url;
      video.load();
      $("playerStatus").textContent = "Starting video…";
      await startVideo(video);
    } else if (window.Hls?.isSupported()) {''')
 s=once(s,'    if (playback.protocol === "dash") {','''    video.addEventListener("error",()=>{if(generation===state.playbackGeneration){$("playerStatus").textContent="Channel unavailable in this browser";state.playerError="The provider stream could not be decoded or reached. Retry, choose another channel, or use the Android TV app.";$("playerProgrammeDescription").textContent=state.playerError;}},{once:true,signal:requestSignal});
    if (playback.protocol === "dash") {''')
 s=s.replace('if (video.paused) video.play().catch(() => {}); else video.pause();','if (video.paused) startVideo(video); else video.pause();')
 return s+'\n$("tapToPlay").onclick=()=>startVideo($("video"));\n'
edit('web-player/public/app.js',app)
def films(s):
 s=s.replace('/owner-api/films','/api/films').replace('<a href="/owner.html">Owner analytics</a>','').replace('<a href="/owner.html">Private workspace</a>','').replace('GharTV Nova RC10.1 ·','GharTV Nova RC10.2 ·')
 s=s.replace('Search FlixMomo without mixing it with your Jio channels. Open its player here where embedding is permitted, or use an isolated Brave / Chromium window on this computer.','Open FlixMomo in the browser you already use. Brave is not required. Optional automatic search and isolated Tor browsing use a compatible browser installed on this computer.')
 s=once(s,'<div class="status" id="status"','<p><a id="directProvider" href="https://flixmomo.app/" target="_blank" rel="noopener noreferrer">Open FlixMomo in this browser ↗</a><span id="directRouteNote"> · Direct connection</span></p><div class="status" id="status"')
 s=s.replace("'Install Brave or Chromium to enable server search'","'Direct browser link is ready; optional automatic search needs Chrome, Edge, Chromium or Brave'")
 s=s.replace("if(!executable)throw Error('INSTALL_BRAVE_OR_CHROMIUM_FIRST');","if(!executable)throw Error('OPTIONAL_BROWSER_MISSING_USE_DIRECT_LINK');")
 s=s.replace("'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',","'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome','/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge',")
 s=s.replace("'/usr/bin/chromium-browser'];","'/usr/bin/chromium-browser','/usr/bin/google-chrome','/usr/bin/microsoft-edge'];")
 s=once(s,"$('route').onchange=()=>{","$('route').onchange=()=>{$('directProvider').hidden=$('route').value==='tor';$('directRouteNote').textContent=$('route').value==='tor'?'Tor selected: direct-browser link disabled. Choose Direct HTTPS to use your current browser.':' · Direct connection';")
 return s
edit('web-player/film-search.mjs',films)
edit('web-player/test/films.test.mjs',lambda s:s.replace('/owner-api/films','/api/films').replace('INSTALL_BRAVE_OR_CHROMIUM_FIRST','OPTIONAL_BROWSER_MISSING_USE_DIRECT_LINK'))
print('BROWSER_UI_AND_OPTIONAL_FILMS_INTEGRATED')
