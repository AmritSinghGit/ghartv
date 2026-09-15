const $ = (id) => document.getElementById(id);
const state = { connected: false, otpSent: false, channels: [], filtered: [], category: "All", currentIndex: -1, currentChannel: null, programs: [], hls: null, shaka: null, ticker: null, busy: false, playbackGeneration: 0, focusGuideAfterLoad: false, widevineSupport: null };

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { "content-type": "application/json", ...(options.headers || {}) },
    credentials: "same-origin",
  });
  const payload = await response.json().catch(() => ({ message: `Request returned HTTP ${response.status}.` }));
  if (!response.ok) throw Object.assign(new Error(payload.message || "Request failed."), { status: response.status, code: payload.code });
  return payload;
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
  if (connected && !state.channels.length) loadChannels();
}

async function loadStatus() {
  try {
    const result = await api("/api/auth/status");
    setConnected(result.connected, result.mobile);
  } catch { setConnected(false); }
}

async function loadChannels() {
  $("resultCount").textContent = "Loading the live channel guide…";
  try {
    const result = await api("/api/channels");
    state.channels = result.channels || [];
    buildCategories();
    filterChannels();
  } catch (error) {
    $("resultCount").textContent = error.message;
  }
}

function buildCategories() {
  const categories = ["All", ...new Set(state.channels.map((channel) => channel.category).filter(Boolean))];
  categories.sort((a, b) => a === "All" ? -1 : b === "All" ? 1 : a.localeCompare(b));
  $("categoryBar").replaceChildren(...categories.map((category) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `category${category === state.category ? " active" : ""}`;
    button.textContent = category;
    button.onclick = () => { state.category = category; buildCategories(); filterChannels(); };
    return button;
  }));
}

function filterChannels() {
  const query = $("search").value.trim().toLocaleLowerCase();
  state.filtered = state.channels.filter((channel) => {
    const category = state.category === "All" || channel.category === state.category;
    const searchable = `${channel.number} ${channel.name} ${channel.language} ${channel.category}`.toLocaleLowerCase();
    return category && (!query || searchable.includes(query));
  });
  renderChannels();
}

function initials(name) {
  return String(name || "TV").split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

function renderChannels() {
  const fragment = document.createDocumentFragment();
  for (const channel of state.filtered) {
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
    node.onclick = () => playChannel(channel.id);
    fragment.append(node);
  }
  $("channelGrid").replaceChildren(fragment);
  $("resultCount").textContent = `${state.filtered.length.toLocaleString()} of ${state.channels.length.toLocaleString()} channels`;
  if (state.focusGuideAfterLoad) {
    state.focusGuideAfterLoad = false;
    requestAnimationFrame(() => $("channelGrid").querySelector(".channel-card")?.focus());
  }
}

function destroyPlayback() {
  state.playbackGeneration += 1;
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
  if (!Number.isFinite(number) || number <= 0) return 0;
  return number < 10_000_000_000 ? number * 1000 : number;
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
  $("playerProgrammeDescription").textContent = current ? programmeDescription(current) : "";
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

async function fetchProgrammeGuide(channelId) {
  const results = await Promise.all([-1, 0, 1].map((offset) =>
    api(`/api/epg?channel_id=${encodeURIComponent(channelId)}&offset=${offset}`).catch(() => ({ programs: [] }))
  ));
  const unique = new Map();
  for (const program of results.flatMap((result) => result.programs || [])) {
    const key = `${program.srno || program.showId || programmeTitle(program)}:${programmeStart(program)}`;
    unique.set(key, program);
  }
  return { programs: [...unique.values()].sort((a, b) => programmeStart(a) - programmeStart(b)) };
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
    $("playerProgrammeDescription").textContent = message;
  });
  await player.load(playback.url, null, "application/dash+xml");
  await video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
}

async function playChannel(channelId) {
  if (state.busy) return;
  const channel = state.channels.find((item) => item.id === String(channelId));
  if (!channel) return;
  state.currentChannel = channel;
  state.programs = [];
  state.currentIndex = state.channels.indexOf(channel);
  state.busy = true;
  destroyPlayback();
  const generation = state.playbackGeneration;
  $("playerNumber").textContent = `CHANNEL ${String(channel.number).padStart(3, "0")} · ${channel.language} · ${channel.category}`;
  $("playerTitle").textContent = channel.name;
  $("playerProgramme").textContent = "Checking this account and preparing the live stream…";
  $("playerProgrammeTime").textContent = "Live";
  $("playerProgrammeDescription").textContent = "";
  $("guideChannelName").textContent = channel.name;
  $("programmeRail").replaceChildren();
  $("playerStatus").textContent = "Connecting…";
  if (!$("playerDialog").open) $("playerDialog").showModal();
  try {
    const [playback, epg] = await Promise.all([
      api("/api/playback", { method: "POST", body: JSON.stringify({ channelId: channel.id }) }),
      fetchProgrammeGuide(channel.id),
    ]);
    state.programs = epg.programs || [];
    renderProgrammeInfo();
    renderProgrammeGuide();
    const video = $("video");
    video.addEventListener("playing", () => { if (generation === state.playbackGeneration) $("playerStatus").textContent = "LIVE"; }, { once: true });
    video.addEventListener("waiting", () => { if (generation === state.playbackGeneration) $("playerStatus").textContent = "Buffering…"; }, { once: true });
    if (playback.protocol === "dash") {
      $("playerStatus").textContent = playback.drm ? "Opening protected stream…" : "Opening stream…";
      await playDash(video, playback, generation);
    } else if (window.Hls?.isSupported()) {
      state.hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 });
      state.hls.attachMedia(video);
      state.hls.on(Hls.Events.MEDIA_ATTACHED, () => state.hls?.loadSource(playback.url));
      state.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        $("playerStatus").textContent = "Starting video…";
        video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
      });
      state.hls.on(Hls.Events.ERROR, (_, data) => {
        if (!data.fatal || generation !== state.playbackGeneration) return;
        $("playerStatus").textContent = "Unable to play";
        $("playerProgramme").textContent = `Stream error: ${data.details || data.type || "unknown"}`;
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = playback.url;
      $("playerStatus").textContent = "Starting video…";
      await video.play().catch(() => { $("playerStatus").textContent = "Press play to start"; });
    } else throw new Error("This browser does not support HLS playback.");
    startPlayerClock();
  } catch (error) {
    const message = protectedPlaybackMessage(error);
    $("playerProgrammeDescription").textContent = message;
    $("playerStatus").textContent = error.status === 403 ? "Not included for this account" : message.includes("Google Chrome") ? "Open in Chrome" : "Unable to play";
    if (error.status === 401) setConnected(false);
  } finally { state.busy = false; }
}

function stepChannel(delta) {
  if (!state.channels.length || state.busy) return;
  const next = (state.currentIndex + delta + state.channels.length) % state.channels.length;
  playChannel(state.channels[next].id);
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
$("closePlayer").onclick = () => { destroyPlayback(); $("playerDialog").close(); };
$("previousChannel").onclick = () => stepChannel(-1);
$("nextChannel").onclick = () => stepChannel(1);
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
  $("guideButton").textContent = hidden ? "Show programme guide" : "Hide programme guide";
};
$("video").addEventListener("play", updatePlayerClock);
$("video").addEventListener("pause", updatePlayerClock);
$("playerDialog").addEventListener("close", destroyPlayback);
document.addEventListener("keydown", (event) => {
  if (!$("playerDialog").open) return;
  if (event.key === "ArrowLeft") stepChannel(-1);
  if (event.key === "ArrowRight") stepChannel(1);
  if (event.key === "Escape") { destroyPlayback(); $("playerDialog").close(); }
});
loadStatus();
