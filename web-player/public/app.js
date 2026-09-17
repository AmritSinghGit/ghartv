const $ = (id) => document.getElementById(id);
const state = { connected: false, otpSent: false, channels: [], filtered: [], category: "All", language: "All", currentIndex: -1, currentChannel: null, programs: [], scope: [], scopeLabel: "All", retryAttempt: 0, playerError: "", playbackAbort: null, hls: null, shaka: null, ticker: null, busy: false, playbackGeneration: 0, focusGuideAfterLoad: false, widevineSupport: null, returnChannelId: null, chromeTimer: null, keyboardMode: false };

const viewer = window.GHARTV_VIEWER || {preview:false};
function localUrl(path){return new URL(String(path).replace(/^\/+/,""),document.baseURI).href;}
function languageName(v){const x=String(v||"").trim();return /^(punjabi|panjabi|ਪੰਜਾਬੀ|پنجابی)$/i.test(x)?"Punjabi":x;}
async function api(path, options = {}) {
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),25000);
  const cancel=()=>controller.abort();
  if(options.signal){if(options.signal.aborted)controller.abort();else options.signal.addEventListener("abort",cancel,{once:true});}
  try{
    const response=await fetch(localUrl(path),{...options,headers:{"content-type":"application/json",...(options.headers||{})},credentials:"same-origin",signal:controller.signal});
    const payload=await response.json().catch(()=>({message:`Request returned HTTP ${response.status}.`}));
    if(!response.ok)throw Object.assign(new Error(payload.message||"Request failed."),{status:response.status,code:payload.code});
    return payload;
  }finally{clearTimeout(timer);options.signal?.removeEventListener("abort",cancel);}
}

function setStatus(message, error = false) {
  $("loginStatus").textContent = message || "";
  $("loginStatus").classList.toggle("error", error);
}

function setConnected(connected, mobile = "") {
  state.connected = connected;
  $("accountButton").textContent = connected ? `JioTV ${mobile}` : "Connect JioTV";
  $("heroState").textContent = connected ? `Connected ${mobile}` : "Waiting to connect";
  $("heroNote").textContent = connected ? "Choose a channel below. Playback permission is checked when you press play." : "Use your own Jio number and OTP. An existing sign-in can be restored from this Mac’s protected Keychain. Sign out to remove it.";
  $("browse").classList.toggle("hidden", !connected);
  $("hero").classList.toggle("hidden", connected);
  $("logoutButton").classList.toggle("hidden", !connected);
  $("mobileField").classList.toggle("hidden", connected);
  $("otpField").classList.add("hidden");
  $("loginAction").classList.toggle("hidden", connected);
  $("loginTitle").textContent = connected ? "JioTV connected" : "Connect JioTV";
  $("loginCopy").textContent = connected ? `This local browser session is connected as ${mobile}.` : "Enter the 10-digit mobile number that receives your Jio OTP.";
  if(viewer.preview){
    $("heroNote").textContent="This temporary viewer does not use the Mac owner’s saved login. Sign in with your own eligible account; it is not saved to the Mac’s Keychain.";
    $("loginCopy").textContent=connected?"Your temporary session only. Logout does not affect the owner’s session.":"Sign in for this temporary test. Your session expires with the preview and is not retained in Keychain.";
  }
  if (connected && !state.channels.length) loadChannels();
}

async function loadStatus() {
  try {
    const result = await api("/api/auth/status");
    setConnected(result.connected, result.mobile);
  } catch { setConnected(false); }
}

let guideLoading=false;
const guideFeedback=document.createElement("div");guideFeedback.id="guideFeedback";guideFeedback.setAttribute("role","status");
$("resultCount").insertAdjacentElement("afterend",guideFeedback);
function guideMessage(message){
  guideFeedback.replaceChildren();
  if(!message)return;
  const text=document.createElement("p");text.textContent=message;
  const retry=document.createElement("button");retry.className="button secondary";retry.type="button";retry.textContent="Retry guide";retry.disabled=guideLoading;
  retry.onclick=()=>loadChannels(true);guideFeedback.append(text,retry);
}
async function loadChannels(force=false) {
  if(guideLoading)return;
  guideLoading=true;
  guideMessage("");
  $("resultCount").textContent = "Loading the live channel guide…";
  try {
    const result = await api("/api/channels"+(force?"?refresh=1":""));
    state.channels = (result.channels || []).map(c=>({...c,language:languageName(c.language)}));
    buildLanguages();
    buildCategories();
    filterChannels();
    guideLoading=false;
    if(result.guide?.status==="stale"||result.guide?.status==="partial_source")guideMessage(result.guide.message);
  } catch (error) {
    guideLoading=false;
    if(state.channels.length)filterChannels();else $("resultCount").textContent="Guide unavailable";
    guideMessage(state.channels.length?"The guide could not be refreshed. Your existing channel list and filters are preserved.":"The guide service did not respond successfully. Your sign-in has been kept. Retry the guide without reinstalling or signing out.");
  } finally {guideLoading=false;}
}

function buildLanguages(){
  const select=$("language"),languages=[...new Set(state.channels.map(c=>c.language).filter(Boolean))].sort((a,b)=>a.localeCompare(b));
  select.replaceChildren(...["All",...languages].map(x=>{const o=document.createElement("option");o.value=x;o.textContent=x==="All"?"All languages":x;o.selected=x===state.language;return o;}));
  const present=languages.includes("Punjabi");$("punjabiQuick").disabled=!present;
  $("punjabiQuick").title=present?"Filter the current catalogue to Punjabi":"No Punjabi-labelled channels returned by this catalogue";
  $("punjabiQuick").setAttribute("aria-pressed",String(state.language==="Punjabi"));
}
$("language").onchange=()=>{state.language=$("language").value;buildLanguages();filterChannels();};
$("punjabiQuick").onclick=()=>{state.language="Punjabi";buildLanguages();filterChannels();};
$("resetFilters").onclick=()=>{state.language="All";state.category="All";$("search").value="";buildLanguages();buildCategories();filterChannels();};
if(viewer.preview){
  document.querySelector(".owner-link")?.remove();
  $("previewNotice").textContent="Private temporary preview · separate sign-in · expires in 15 minutes";
}

function buildCategories() {
  const categories = ["All", ...new Set(state.channels.map((channel) => channel.category).filter(Boolean))];
  categories.sort((a, b) => a === "All" ? -1 : b === "All" ? 1 : a.localeCompare(b));
  $("categoryBar").replaceChildren(...categories.map((category) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `category${category === state.category ? " active" : ""}`;
    button.textContent = category;
    button.dataset.category=category; button.setAttribute("aria-pressed",String(category===state.category));
    button.tabIndex=category===state.category?0:-1;
    button.onclick = () => { state.category = category; buildCategories(); filterChannels();
      [...$("categoryBar").children].find(b=>b.dataset.category===category)?.focus(); };
    return button;
  }));
}

function filterChannels() {
  const query = $("search").value.trim().toLocaleLowerCase();
  state.filtered = state.channels.filter((channel) => {
    const category = state.category === "All" || channel.category === state.category;
    const searchable = `${channel.number} ${channel.name} ${channel.language} ${channel.category}`.toLocaleLowerCase();
    const language=state.language === "All" || channel.language === state.language;
    return category && language && (!query || searchable.includes(query) || (query==="ਪੰਜਾਬੀ" && channel.language==="Punjabi"));
  });
  renderedLimit=96;renderChannels();
}

function initials(name) {
  return String(name || "TV").split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

let renderedLimit=96;
function renderChannels() {
  const fragment = document.createDocumentFragment();
  for (const channel of state.filtered.slice(0,renderedLimit)) {
    const node = $("channelTemplate").content.firstElementChild.cloneNode(true);
    const image = node.querySelector("img");
    const fallback = node.querySelector(".channel-logo span");
    fallback.textContent = initials(channel.name);
    image.loading="lazy";image.decoding="async";
    image.src = channel.logoUrl;
    image.alt = `${channel.name} logo`;
    image.onerror = () => { image.style.display = "none"; fallback.style.display = "block"; };
    node.querySelector(".channel-number").textContent = `CH ${String(channel.number).padStart(3, "0")}${channel.subscription ? " · SUBSCRIPTION" : ""}`;
    node.querySelector("h3").textContent = channel.name;
    node.querySelector(".channel-detail").textContent = `${channel.language} · ${channel.category}`;
    node.dataset.channelId=channel.id;node.tabIndex=fragment.childNodes.length? -1:0;
    node.onclick = () => { state.returnChannelId=channel.id;state.scope=state.filtered.map(c=>c.id);state.scopeLabel=[state.language==="All"?"All languages":state.language,state.category,$("search").value.trim()?"search":""].filter(Boolean).join(" · ");playChannel(channel.id); };
    fragment.append(node);
  }
  $("channelGrid").replaceChildren(fragment);
  let more=document.getElementById("moreChannels");if(!more){more=document.createElement("button");more.id="moreChannels";more.className="button secondary";more.textContent="Show more channels";$("channelGrid").insertAdjacentElement("afterend",more);more.onclick=()=>{const old=renderedLimit;renderedLimit+=96;renderChannels();const card=$("channelGrid").children[old];if(card){card.tabIndex=0;card.focus();}};}
  more.hidden=renderedLimit>=state.filtered.length;
  $("resultCount").textContent = `${state.filtered.length.toLocaleString()} of ${state.channels.length.toLocaleString()} channels`;
  if (state.focusGuideAfterLoad) {
    state.focusGuideAfterLoad = false;
    requestAnimationFrame(() => $("channelGrid").querySelector(".channel-card")?.focus());
  }
}

function destroyPlayback() {
  state.playbackGeneration += 1;
  state.playbackAbort?.abort();state.playbackAbort=null;
  if (state.ticker) { clearInterval(state.ticker); state.ticker = null; }
  if (state.hls) { state.hls.destroy(); state.hls = null; }
  if (state.shaka) { state.shaka.destroy().catch(() => {}); state.shaka = null; }
  const video = $("video");
  video.pause();
  video.removeAttribute("src");
  video.load();
}

function epochMs(value) {
  const number = Number(value || 0);
  if (Number.isFinite(number)&&number>0) return number<100_000_000_000?number*1000:number>100_000_000_000_000?number/1000:number;
  const text=String(value||"").trim();
  if(/^\d{4}-\d{2}-\d{2}[ T]/.test(text)){
    const iso=text.replace(" ","T");const dated=Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(iso)?iso:iso+"+05:30");
    return Number.isFinite(dated)?dated:0;
  }
  return 0;
}

function programmeStart(program) {
  return epochMs(program?.startEpoch ?? program?.startTime ?? program?.start);
}

function programmeEnd(program) {
  return epochMs(program?.endEpoch ?? program?.endTime ?? program?.end);
}

function programmeTitle(program) {
  return program?.showname || program?.title || "Programme information unavailable";
}

function programmeDescription(program) {
  return program?.description || program?.synopsis || program?.desc || "";
}

function formatTime(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return "";
  return date.toLocaleTimeString("en-IN", { timeZone:"Asia/Kolkata", hour: "numeric", minute: "2-digit" });
}

function currentProgramme(now = Date.now()) {
  return state.programs.find((program) => programmeStart(program) <= now && programmeEnd(program) > now) || null;
}

function seekRange() {
  if (state.shaka) {
    const range = state.shaka.seekRange();
    if (Number.isFinite(range.start) && Number.isFinite(range.end) && range.end > range.start) return range;
  }
  const ranges = $("video").seekable;
  if (ranges?.length) return { start: ranges.start(0), end: ranges.end(ranges.length - 1) };
  return null;
}

function canSeekProgramme(program) {
  const range = seekRange();
  if (!range) return false;
  const start = programmeStart(program) / 1000;
  return start >= range.start - 2 && start < range.end - 2;
}

function seekTo(value) {
  const range = seekRange();
  if (!range) return;
  const target = Math.max(range.start, Math.min(range.end - 1, Number(value)));
  $("video").currentTime = target;
  $("video").play().catch(() => {});
  updatePlayerClock();
}

function renderProgrammeInfo() {
  const current = currentProgramme();
  $("playerProgramme").textContent = current ? programmeTitle(current) : "Live now";
  $("playerProgrammeTime").textContent = current
    ? `${formatTime(programmeStart(current))} – ${formatTime(programmeEnd(current))}`
    : "Live";
  $("playerProgrammeDescription").textContent = state.playerError || (current ? programmeDescription(current) : "");
  const boundary=current?programmeEnd(current):Date.now();
  const next=[...state.programs].filter(p=>programmeStart(p)>=boundary&&programmeStart(p)>Date.now()).sort((a,b)=>programmeStart(a)-programmeStart(b))[0];
  $("playerNext").textContent=next?`UP NEXT  ${formatTime(programmeStart(next))} · ${programmeTitle(next)}`:"UP NEXT  Not listed by provider";
}

function renderProgrammeGuide() {
  const now = Date.now();
  const programs = [...state.programs].sort((a, b) => programmeStart(a) - programmeStart(b));
  const currentIndex = programs.findIndex((program) => programmeStart(program) <= now && programmeEnd(program) > now);
  const visible = currentIndex >= 0
    ? programs.slice(Math.max(0, currentIndex - 5), currentIndex + 7)
    : programs.slice(0, 12);
  const nodes = visible.map((program) => {
    const start = programmeStart(program);
    const end = programmeEnd(program);
    const isCurrent = start <= now && end > now;
    const isPast = end <= now;
    const seekable = isPast && canSeekProgramme(program);
    const button = document.createElement("button");
    button.type = "button";
    button.className = `programme-card${isCurrent ? " current" : ""}${isPast ? " past" : " upcoming"}`;
    button.disabled = !isCurrent && !seekable;
    button.replaceChildren();
    const time = document.createElement("span");
    time.className = "programme-card-time";
    time.textContent = `${formatTime(start)} – ${formatTime(end)}`;
    const title = document.createElement("strong");
    title.textContent = programmeTitle(program);
    const badge = document.createElement("small");
    badge.textContent = isCurrent ? "ON NOW" : seekable ? "WATCH FROM START" : isPast ? "PAST · OUTSIDE LIVE WINDOW" : "UP NEXT";
    button.append(time, title, badge);
    if (isCurrent) button.onclick = () => $("liveButton").click();
    if (seekable) button.onclick = () => seekTo(start / 1000 + 2);
    return button;
  });
  if (!nodes.length) {
    const empty = document.createElement("p");
    empty.className = "guide-empty";
    empty.textContent = "Jio has not listed programme information for this channel yet.";
    nodes.push(empty);
  }
  $("programmeRail").replaceChildren(...nodes);
  requestAnimationFrame(() => $("programmeRail").querySelector(".current")?.scrollIntoView({ inline: "center", block: "nearest" }));
}

function updatePlayerClock() {
  const video = $("video");
  renderProgrammeInfo();
  const range = seekRange();
  const timeline = $("playerTimeline");
  if (range && range.end - range.start > 5) {
    timeline.disabled = false;
    timeline.min = String(range.start);
    timeline.max = String(range.end);
    timeline.value = String(Math.max(range.start, Math.min(range.end, video.currentTime || range.end)));
    $("timelineStart").textContent = formatTime(range.start * 1000);
    const behind = Math.max(0, Math.round(range.end - (video.currentTime || range.end)));
    $("timelineNow").textContent = behind < 8 ? "LIVE" : `−${Math.floor(behind / 60)}:${String(behind % 60).padStart(2, "0")}`;
    $("liveButton").classList.toggle("behind", behind >= 8);
  } else {
    timeline.disabled = true;
    $("timelineStart").textContent = "LIVE";
    $("timelineNow").textContent = "LIVE";
  }
  $("playPauseButton").textContent = video.paused ? "▶" : "Ⅱ";
  $("playPauseButton").setAttribute("aria-label", video.paused ? "Play" : "Pause");
}

function startPlayerClock() {
  if (state.ticker) clearInterval(state.ticker);
  updatePlayerClock();
  renderProgrammeGuide();
  state.ticker = setInterval(updatePlayerClock, 1000);
}

async function fetchProgrammeGuide(channelId, generation, signal) {
  const current=await api(`/api/epg?channel_id=${encodeURIComponent(channelId)}&offset=0`,{signal}).catch(()=>({programs:[]}));
  if(generation!==state.playbackGeneration)return;
  const valid=(current.programs||[]).filter(p=>programmeStart(p)>0&&programmeEnd(p)>programmeStart(p));
  const apply=(all)=>{
    if(generation!==state.playbackGeneration)return;
    const unique=new Map();for(const p of all)unique.set(`${programmeTitle(p)}:${programmeStart(p)}`,p);
    state.programs=[...unique.values()].sort((a,b)=>programmeStart(a)-programmeStart(b));renderProgrammeInfo();renderProgrammeGuide();
  };
  apply(valid);
  const now=Date.now(),active=valid.find(p=>programmeStart(p)<=now&&programmeEnd(p)>now),boundary=active?programmeEnd(active):now;
  if(valid.some(p=>programmeStart(p)>now&&programmeStart(p)>=boundary))return;
  const tomorrow=await api(`/api/epg?channel_id=${encodeURIComponent(channelId)}&offset=1`,{signal}).catch(()=>({programs:[]}));
  apply([...valid,...(tomorrow.programs||[]).filter(p=>programmeStart(p)>0&&programmeEnd(p)>programmeStart(p))]);
}

function base64Url(input) {
  const bytes = new TextEncoder().encode(input);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function supportsWidevine() {
  if (state.widevineSupport !== null) return state.widevineSupport;
  if (!navigator.requestMediaKeySystemAccess) {
    state.widevineSupport = false;
    return false;
  }
  try {
    await navigator.requestMediaKeySystemAccess("com.widevine.alpha", [{
      initDataTypes: ["cenc"],
      distinctiveIdentifier: "optional",
      persistentState: "optional",
      sessionTypes: ["temporary"],
      audioCapabilities: [{ contentType: 'audio/mp4; codecs="mp4a.40.2"' }],
      videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }],
    }]);
    state.widevineSupport = true;
  } catch {
    state.widevineSupport = false;
  }
  return state.widevineSupport;
}

function protectedPlaybackMessage(error) {
  const code = Number(error?.code || error?.detail?.code || error?.detail?.data?.[0]);
  if (error?.code === "widevine_unavailable" || code === 6001 || code === 6020) {
    return "This channel uses Widevine protection. Open GharTV in Google Chrome on this Mac.";
  }
  return error?.message || (Number.isFinite(code) ? `Protected stream error ${code}.` : "The protected stream could not be opened.");
}

async function playDash(video, playback, generation) {
  if (!window.shaka) throw new Error("The browser TV engine did not load.");
  shaka.polyfill.installAll();
  if (!shaka.Player.isBrowserSupported()) throw new Error("This browser does not support protected live television.");
  if (playback.drm && !(await supportsWidevine())) {
    throw Object.assign(new Error("This channel uses Widevine protection. Open GharTV in Google Chrome on this Mac."), { code: "widevine_unavailable" });
  }
  const player = new shaka.Player();
  state.shaka = player;
  await player.attach(video);
  const networking = player.getNetworkingEngine();
  networking.registerRequestFilter((type, request) => {
    if (type === shaka.net.NetworkingEngine.RequestType.LICENSE) {
      request.uris = [new URL(playback.licenseUrl, location.origin).href];
      return;
    }
    request.uris = request.uris.map((uri) => {
      if (uri.startsWith(location.origin)) return uri;
      if (!uri.startsWith("https://")) return uri;
      return `${location.origin}/api/stream/${playback.ticket}?u=${base64Url(uri)}`;
    });
  });
  if (playback.drm && playback.licenseUrl) {
    player.configure({ drm: { servers: { "com.widevine.alpha": new URL(playback.licenseUrl, location.origin).href } } });
  }
  player.addEventListener("error", (event) => {
    if (generation !== state.playbackGeneration) return;
    const message = protectedPlaybackMessage(event.detail);
    $("playerStatus").textContent = message.includes("Google Chrome") ? "Open in Chrome" : "Unable to play";
    state.playerError=message;$("playerProgrammeDescription").textContent = message;
  });
  await player.load(playback.url, null, "application/dash+xml");
  await video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
}

async function playChannel(channelId, autoRetry=false) {
  if(!autoRetry)state.retryAttempt=0;
  const channel = state.channels.find((item) => item.id === String(channelId));
  if (!channel) return;
  state.currentChannel = channel;
  state.programs = [];
  state.currentIndex = state.scope.indexOf(channel.id);
  $("playerScope").textContent=state.scopeLabel+" · "+state.scope.length+" channels · CH ± stays here";
  state.busy = true;
  destroyPlayback();
  const generation = state.playbackGeneration;
  state.playbackAbort=new AbortController();state.playerError="";
  const requestSignal=state.playbackAbort.signal;
  $("playerNumber").textContent = `CHANNEL ${String(channel.number).padStart(3, "0")} · ${channel.language} · ${channel.category}`;
  $("playerTitle").textContent = channel.name;
  $("playerProgramme").textContent = "Checking this account and preparing the live stream…";
  $("playerProgrammeTime").textContent = "Live";
  $("playerProgrammeDescription").textContent = "";
  $("guideChannelName").textContent = channel.name;
  $("programmeRail").replaceChildren();
  $("playerStatus").textContent = "Connecting…";
  const opening=!$("playerDialog").open;
  if(opening) { $("playerDialog").showModal(); $("nextChannel").focus(); }
  showPlayerChrome();
  try {
    fetchProgrammeGuide(channel.id,generation,requestSignal).catch(()=>{});
    const playback=await api("/api/playback",{method:"POST",body:JSON.stringify({channelId:channel.id}),signal:requestSignal});
    if(generation!==state.playbackGeneration)return;
    renderProgrammeInfo();renderProgrammeGuide();
    const video = $("video");
    video.addEventListener("playing", () => { if (generation === state.playbackGeneration){state.playerError="";$("playerStatus").textContent = "LIVE";} }, { once: true });
    video.addEventListener("waiting", () => { if (generation === state.playbackGeneration) $("playerStatus").textContent = "Buffering…"; }, { once: true });
    if (playback.protocol === "dash") {
      $("playerStatus").textContent = playback.drm ? "Opening protected stream…" : "Opening stream…";
      await playDash(video, playback, generation);
    } else if (window.Hls?.isSupported()) {
      state.hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 5, maxBufferLength: 20 });
      state.hls.attachMedia(video);
      state.hls.on(Hls.Events.MEDIA_ATTACHED, () => {if(generation===state.playbackGeneration)state.hls?.loadSource(playback.url);});
      state.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if(generation!==state.playbackGeneration)return;
        $("playerStatus").textContent = "Starting video…";
        video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
      });
      state.hls.on(Hls.Events.ERROR, (_, data) => {
        if (!data.fatal || generation !== state.playbackGeneration) return;
        const code=Number(data.response?.code||0);
        if(state.retryAttempt<1&&data.type===Hls.ErrorTypes.NETWORK_ERROR&&![401,403].includes(code)){
          state.retryAttempt++;$("playerStatus").textContent="Reconnecting once…";
          setTimeout(()=>{if(generation===state.playbackGeneration)playChannel(channel.id,true);},1000);return;
        }
        $("playerStatus").textContent=code===403?"Provider access required":"Unable to play";
        state.playerError="Try this channel again or select the next channel in "+state.scopeLabel+".";$("playerProgrammeDescription").textContent=state.playerError;
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = playback.url;
      $("playerStatus").textContent = "Starting video…";
      await video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
    } else throw new Error("This browser does not support HLS playback.");
    startPlayerClock();
  } catch (error) {
    if(generation!==state.playbackGeneration)return;
    const message = protectedPlaybackMessage(error);
    state.playerError=message;$("playerProgrammeDescription").textContent = message;
    $("playerStatus").textContent = error.status === 403 ? "Not included for this account" : message.includes("Google Chrome") ? "Open in Chrome" : "Unable to play";
    if (error.status === 401) setConnected(false);
  } finally { if(generation===state.playbackGeneration)state.busy = false; }
}

function stepChannel(delta) {
  if(!state.scope.length)return;
  const index=state.scope.indexOf(state.currentChannel?.id);
  const next=index<0?(delta>0?0:state.scope.length-1):(index+delta+state.scope.length)%state.scope.length;
  playChannel(state.scope[next]);
}

function openLogin() {
  setStatus("");
  $("loginDialog").showModal();
  requestAnimationFrame(() => (state.connected ? $("logoutButton") : state.otpSent ? $("otp") : $("mobile")).focus());
}

$("accountButton").onclick = openLogin;
$("connectHeroButton").onclick = openLogin;
$("closeLogin").onclick = () => $("loginDialog").close();
$("loginForm").onsubmit = async (event) => {
  event.preventDefault();
  if (state.connected) return;
  const action = $("loginAction");
  action.disabled = true;
  setStatus(state.otpSent ? "Verifying with Jio…" : "Requesting an OTP from Jio…");
  try {
    if (!state.otpSent) {
      const result = await api("/api/auth/otp/send", { method: "POST", body: JSON.stringify({ mobile: $("mobile").value }) });
      state.otpSent = true;
      $("mobileField").classList.add("hidden");
      $("otpField").classList.remove("hidden");
      action.textContent = "Connect and open guide";
      setStatus(`OTP sent to ${result.destination}.`);
      requestAnimationFrame(() => $("otp").focus());
    } else {
      const result = await api("/api/auth/otp/verify", { method: "POST", body: JSON.stringify({ otp: $("otp").value }) });
      state.focusGuideAfterLoad = true;
      setConnected(true, result.mobile);
      state.otpSent = false;
      action.textContent = "Send OTP";
      setStatus("");
      $("loginDialog").close();
      $("browse").scrollIntoView({ behavior: "smooth", block: "start" });
    }
  } catch (error) { setStatus(error.message, true); }
  finally { action.disabled = false; }
};
$("logoutButton").onclick = async () => {
  await api("/api/auth/logout", { method: "POST", body: "{}" }).catch(() => {});
  destroyPlayback();
  state.channels = []; state.filtered = []; state.otpSent = false;
  setConnected(false); $("loginDialog").close();
};
$("search").oninput = filterChannels;
$("closePlayer").onclick = closePlayer;
$("previousChannel").onclick = () => stepChannel(-1);
$("nextChannel").onclick = () => stepChannel(1);
$("retryChannel").onclick=()=>{if(state.currentChannel)playChannel(state.currentChannel.id);};
$("playerTimeline").oninput = (event) => {
  $("timelineNow").textContent = formatTime(Number(event.currentTarget.value) * 1000);
};
$("playerTimeline").onchange = (event) => seekTo(event.currentTarget.value);
$("rewindButton").onclick = () => seekTo($("video").currentTime - 15);
$("forwardButton").onclick = () => seekTo($("video").currentTime + 15);
$("playPauseButton").onclick = () => {
  const video = $("video");
  if (video.paused) video.play().catch(() => {}); else video.pause();
  updatePlayerClock();
};
$("liveButton").onclick = () => {
  const range = seekRange();
  if (range) seekTo(range.end - 1);
};
$("guideButton").onclick = () => {
  const guide = $("programmeGuide");
  const hidden = guide.classList.toggle("collapsed");
  $("guideButton").setAttribute("aria-expanded", String(!hidden));
  $("guideButton").textContent = hidden ? "Programmes" : "Hide programmes";
  showPlayerChrome();
};
$("video").addEventListener("play", updatePlayerClock);
$("video").addEventListener("pause", updatePlayerClock);
// One keyboard model: browse with arrows, select with Enter, change channels with Page keys.
function moveFocus(elements,event){
  const key=event.key,active=document.activeElement,list=[...elements].filter(e=>!e.disabled&&e.getClientRects().length);
  const index=list.indexOf(active);if(index<0)return false;
  const origin=active.getBoundingClientRect();let choices=[];
  for(const node of list){if(node===active)continue;const r=node.getBoundingClientRect(),dx=(r.left+r.right-origin.left-origin.right)/2,dy=(r.top+r.bottom-origin.top-origin.bottom)/2;
    const primary=key==="ArrowRight"?dx:key==="ArrowLeft"?-dx:key==="ArrowDown"?dy:-dy;
    const secondary=(key==="ArrowLeft"||key==="ArrowRight")?Math.abs(dy):Math.abs(dx);
    if(primary>3)choices.push({node,score:primary+secondary*4});}
  if(choices.length){choices.sort((a,b)=>a.score-b.score);const node=choices[0].node;if(node.classList.contains("channel-card")){list.forEach(e=>e.tabIndex=-1);node.tabIndex=0;}node.focus();node.scrollIntoView({block:"nearest",inline:"nearest"});}
  event.preventDefault();return true;
}
function showPlayerChrome(){
  clearTimeout(state.chromeTimer);$("playerDialog").classList.remove("chrome-hidden");
  if(!$("playerDialog").open)return;
  state.chromeTimer=setTimeout(()=>{if(!$("playerDialog").open||state.keyboardMode||!$("programmeGuide").classList.contains("collapsed"))return;
    $("playerDialog").focus({preventScroll:true});$("playerDialog").classList.add("chrome-hidden");},6500);
}
async function toggleFullscreen(){
  try{if(document.fullscreenElement)await document.exitFullscreen();else if(document.querySelector(".player-shell").requestFullscreen)await document.querySelector(".player-shell").requestFullscreen();
    else if($("video").webkitEnterFullscreen)$("video").webkitEnterFullscreen();else throw new Error("Unavailable");}
  catch{$("playerStatus").textContent="Fullscreen not available in this browser. The player still fills this window.";}
  showPlayerChrome();
}
function closePlayer(){
  clearTimeout(state.chromeTimer);
  if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});
  $("playerDialog").close();
}
$("fullscreenButton").onclick=toggleFullscreen;
$("video").addEventListener("dblclick",toggleFullscreen);
document.addEventListener("fullscreenchange",()=>{
  const full=!!document.fullscreenElement;$("fullscreenButton").setAttribute("aria-label",full?"Exit fullscreen":"Enter fullscreen");
  $("fullscreenButton").querySelector("span").textContent=full?"Window view":"Fullscreen";showPlayerChrome();
});
$("playerDialog").addEventListener("pointermove",()=>{state.keyboardMode=false;showPlayerChrome();});
$("playerDialog").addEventListener("pointerdown",()=>{state.keyboardMode=false;showPlayerChrome();});
$("playerDialog").addEventListener("close",()=>{
  clearTimeout(state.chromeTimer);destroyPlayback();$("playerDialog").classList.remove("chrome-hidden");
  const card=[...$("channelGrid").children].find(e=>e.dataset.channelId===state.returnChannelId);
  if(card){[...$("channelGrid").children].forEach(e=>e.tabIndex=-1);card.tabIndex=0;card.focus({preventScroll:true});}
});
$("playerDialog").addEventListener("cancel",event=>{event.preventDefault();if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});else closePlayer();});
document.addEventListener("keydown",event=>{
  if(event.defaultPrevented||event.altKey||event.metaKey||event.ctrlKey)return;
  const target=event.target,typing=target instanceof HTMLElement&&(target.isContentEditable||target.matches("textarea,select,input:not([type=range])"));
  if(typing){if(target===$("search")&&!$("playerDialog").open){
      if(event.key==="ArrowDown"){event.preventDefault();$("channelGrid").querySelector(".channel-card")?.focus();}
      else if(event.key==="Escape"){$("search").value="";filterChannels();event.preventDefault();}
    }return;}
  if($("loginDialog").open||document.getElementById("comfortSettings")?.open||document.getElementById("idlePrompt")?.open)return;
  if($("playerDialog").open){
    state.keyboardMode=true;const hidden=$("playerDialog").classList.contains("chrome-hidden");showPlayerChrome();
    if(event.key==="Escape"){event.preventDefault();if(document.fullscreenElement)document.exitFullscreen().catch(()=>{});else closePlayer();return;}
    if(event.repeat&&["PageDown","PageUp","n","p"].includes(event.key))return;
    if(event.key==="PageDown"||event.key.toLowerCase()==="n"){event.preventDefault();stepChannel(1);return;}
    if(event.key==="PageUp"||event.key.toLowerCase()==="p"){event.preventDefault();stepChannel(-1);return;}
    if(event.key.toLowerCase()==="f"){event.preventDefault();toggleFullscreen();return;}
    if(event.key===" "){event.preventDefault();$("playPauseButton").click();return;}
    if(event.key.startsWith("Arrow")){
      if(target===$("playerTimeline"))return;
      if(hidden||target===$("playerDialog")){event.preventDefault();$("nextChannel").focus();return;}
      moveFocus($("playerDialog").querySelectorAll("button,input:not(:disabled)"),event);
    }
    return;
  }
  if(event.key==="/"){event.preventDefault();$("search").focus();return;}
  if(target.closest?.("#categoryBar")&&["ArrowLeft","ArrowRight","Home","End"].includes(event.key)){
    const list=[...$("categoryBar").children],i=list.indexOf(target),next=event.key==="Home"?0:event.key==="End"?list.length-1:(i+(event.key==="ArrowRight"?1:-1)+list.length)%list.length;
    list.forEach(e=>e.tabIndex=-1);list[next].tabIndex=0;list[next].focus();event.preventDefault();return;}
  if(target.closest?.("#channelGrid")&&event.key.startsWith("Arrow")){
    const cards=[...$("channelGrid").children],index=cards.indexOf(target.closest(".channel-card"));
    if(event.key==="ArrowDown"&&index>=cards.length-6&&renderedLimit<state.filtered.length){
      const id=target.closest(".channel-card")?.dataset.channelId;renderedLimit+=96;renderChannels();
      const keep=[...$("channelGrid").children].find(e=>e.dataset.channelId===id);if(keep)keep.focus();
    }
    moveFocus($("channelGrid").children,event);
  }
});
loadStatus();

// Local-only playback comfort. Never use playback progress as user activity.
let comfortConfig={enabled:true,minutes:60,guide:false};
try{comfortConfig={...comfortConfig,...JSON.parse(localStorage.getItem("ghartv_comfort_v1")||"{}")};}catch{}
let idleTimer=null,idleGrace=null,resting=false;
const comfortButton=document.createElement("button");comfortButton.className="button secondary";comfortButton.textContent="Playback & comfort";$("resetFilters").insertAdjacentElement("afterend",comfortButton);
const comfortDialog=document.createElement("dialog");comfortDialog.id="comfortSettings";comfortDialog.innerHTML='<form method="dialog" style="padding:24px"><h2>Playback & comfort</h2><label><input id="stillEnabled" type="checkbox"> Ask: Still watching?</label><p><label>Minutes without input <input id="stillMinutes" type="number" min="1" max="240" required></label></p><label><input id="stillGuide" type="checkbox"> Return to guide instead of Resume screen</label><p><button value="cancel" formnovalidate>Cancel</button> <button id="saveComfort" value="save">Save</button></p></form>';document.body.append(comfortDialog);
comfortButton.onclick=()=>{$("stillEnabled").checked=comfortConfig.enabled;$("stillMinutes").value=comfortConfig.minutes;$("stillGuide").checked=comfortConfig.guide;comfortDialog.showModal();};
// Save in the submit action, not the asynchronously dispatched dialog close event.
comfortDialog.querySelector("form").addEventListener("submit",event=>{
 event.preventDefault();
 if(event.submitter?.value!=="save"){comfortDialog.close("cancel");return;}
 if(!event.currentTarget.reportValidity())return;
 const n=Number($("stillMinutes").value);if(!Number.isInteger(n)||n<1||n>240)return;
 comfortConfig={enabled:$("stillEnabled").checked,minutes:n,guide:$("stillGuide").checked};
 try{localStorage.setItem("ghartv_comfort_v1",JSON.stringify(comfortConfig));}catch{}
 comfortDialog.close("save");activity();
});
const idlePrompt=document.createElement("dialog");idlePrompt.id="idlePrompt";idlePrompt.innerHTML='<div style="padding:28px"><h2 id="idleTitle">Still watching?</h2><p id="idleCopy">Streaming stops in 30 seconds without a response.</p><button id="keepWatching">Keep watching</button> <button id="restGuide">Back to guide</button></div>';document.body.append(idlePrompt);
function activity(){if(resting||idlePrompt.open)return;clearTimeout(idleTimer);if(comfortConfig.enabled&&$("playerDialog").open)idleTimer=setTimeout(warnIdle,Math.max(1,Math.min(240,comfortConfig.minutes))*60000);}
function warnIdle(){if(!$("playerDialog").open)return;$("idleTitle").textContent="Still watching?";$("idleCopy").textContent="Streaming stops in 30 seconds without a response.";$("keepWatching").textContent="Keep watching";idlePrompt.showModal();$("keepWatching").focus();idleGrace=setTimeout(()=>{resting=true;destroyPlayback();if(comfortConfig.guide){idlePrompt.close();closePlayer();resting=false;}else{$("idleTitle").textContent="Your TV is resting";$("idleCopy").textContent="Streaming has stopped. Resume the same channel live.";$("keepWatching").textContent="Resume live TV";}},30000);}
$("keepWatching").onclick=()=>{clearTimeout(idleGrace);idlePrompt.close();const resume=resting;resting=false;if(resume&&state.currentChannel)playChannel(state.currentChannel.id);activity();};
$("restGuide").onclick=()=>{clearTimeout(idleGrace);idlePrompt.close();resting=false;closePlayer();};
idlePrompt.addEventListener("cancel",e=>{e.preventDefault();$("keepWatching").click();});
for(const event of ["keydown","pointerdown","touchstart"])document.addEventListener(event,activity,{passive:true});
$("video").addEventListener("playing",()=>{if(!idleTimer)activity();});
$("playerDialog").addEventListener("close",()=>{clearTimeout(idleTimer);idleTimer=null;clearTimeout(idleGrace);if(idlePrompt.open)idlePrompt.close();resting=false;});
