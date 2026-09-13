const $ = (id) => document.getElementById(id);
const state = { connected: false, otpSent: false, channels: [], filtered: [], category: "All", currentIndex: -1, hls: null, busy: false };

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
  $("heroNote").textContent = connected ? "Choose a channel below. Playback permission is checked when you press play." : "Use your own Jio number and OTP. Nothing is saved when the local server stops.";
  $("browse").classList.toggle("hidden", !connected);
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
  $("resultCount").textContent = `${state.filtered.length.toLocaleString()} of ${state.channels.length.toLocaleString()} channels · JioTV experimental local connector`;
}

function destroyPlayback() {
  if (state.hls) { state.hls.destroy(); state.hls = null; }
  const video = $("video");
  video.pause();
  video.removeAttribute("src");
  video.load();
}

async function playChannel(channelId) {
  if (state.busy) return;
  const channel = state.channels.find((item) => item.id === String(channelId));
  if (!channel) return;
  state.currentIndex = state.channels.indexOf(channel);
  state.busy = true;
  destroyPlayback();
  $("playerNumber").textContent = `CHANNEL ${String(channel.number).padStart(3, "0")} · ${channel.language} · ${channel.category}`;
  $("playerTitle").textContent = channel.name;
  $("playerProgramme").textContent = "Checking this account and preparing the live stream…";
  $("playerStatus").textContent = "Connecting…";
  if (!$("playerDialog").open) $("playerDialog").showModal();
  try {
    const [playback, epg] = await Promise.all([
      api("/api/playback", { method: "POST", body: JSON.stringify({ channelId: channel.id }) }),
      api(`/api/epg?channel_id=${encodeURIComponent(channel.id)}`).catch(() => ({ programs: [] })),
    ]);
    const current = (epg.programs || []).find((program) => {
      const start = Number(program.startEpoch || program.startTime || program.start || 0) * (String(program.startEpoch || program.startTime || program.start || "").length <= 10 ? 1000 : 1);
      const end = Number(program.endEpoch || program.endTime || program.end || 0) * (String(program.endEpoch || program.endTime || program.end || "").length <= 10 ? 1000 : 1);
      return start <= Date.now() && end >= Date.now();
    });
    $("playerProgramme").textContent = current?.showname || current?.title || "Live now";
    const video = $("video");
    if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = playback.url;
      await video.play().catch(() => {});
    } else if (window.Hls?.isSupported()) {
      state.hls = new Hls({ enableWorker: true, lowLatencyMode: true, backBufferLength: 30 });
      state.hls.loadSource(playback.url);
      state.hls.attachMedia(video);
      state.hls.on(Hls.Events.MANIFEST_PARSED, () => video.play().catch(() => {}));
      state.hls.on(Hls.Events.ERROR, (_, data) => { if (data.fatal) $("playerStatus").textContent = "Playback needs attention"; });
    } else throw new Error("This browser does not support HLS playback.");
    $("playerStatus").textContent = "LIVE · Local session";
  } catch (error) {
    $("playerProgramme").textContent = error.message;
    $("playerStatus").textContent = error.status === 403 ? "Not included for this account" : "Unable to play";
    if (error.status === 401) setConnected(false);
  } finally { state.busy = false; }
}

function stepChannel(delta) {
  if (!state.channels.length || state.busy) return;
  const next = (state.currentIndex + delta + state.channels.length) % state.channels.length;
  playChannel(state.channels[next].id);
}

$("accountButton").onclick = () => { setStatus(""); $("loginDialog").showModal(); };
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
      $("otp").focus();
      action.textContent = "Connect and open guide";
      setStatus(`OTP sent to ${result.destination}.`);
    } else {
      const result = await api("/api/auth/otp/verify", { method: "POST", body: JSON.stringify({ otp: $("otp").value }) });
      setConnected(true, result.mobile);
      state.otpSent = false;
      action.textContent = "Send OTP";
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
$("playerDialog").addEventListener("close", destroyPlayback);
document.addEventListener("keydown", (event) => {
  if (!$("playerDialog").open) return;
  if (event.key === "ArrowLeft") stepChannel(-1);
  if (event.key === "ArrowRight") stepChannel(1);
  if (event.key === "Escape") { destroyPlayback(); $("playerDialog").close(); }
});
loadStatus();
