#!/bin/bash
# Private owner report for GharTV. Reads; does not mutate GitHub, Cloudflare or TV state.
set -Eeuo pipefail

PROJECT="${GHARTV_PROJECT:-$HOME/Downloads/GharTV_Nova_v0.4.2}"
REPOSITORY="AmritSinghGit/ghartv"
PACKAGE="in.ghartv.nova"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${GHARTV_LOG_REPORT_DIR:-$HOME/Desktop/GharTV-Owner-Report-$STAMP}"
COLLECTOR_ENV="$HOME/Library/Application Support/GharTV/telemetry/collector.env"
mkdir -p "$OUT/raw" "$OUT/sanitized"
chmod 700 "$OUT" "$OUT/raw" "$OUT/sanitized" 2>/dev/null || true

say(){ printf '%s\n' "$*"; }
section(){ printf '\n============================================================\n%s\n============================================================\n' "$*"; }

sanitize(){
  local source="$1" destination="$2"
  python3 - "$source" "$destination" <<'PY'
from pathlib import Path
import re,sys
src,dst=map(Path,sys.argv[1:])
text=src.read_text(encoding='utf-8',errors='replace') if src.exists() else ''
patterns=[
 (r'(?i)(authorization|cookie|ssoToken|authToken|refreshToken|accessToken|otp|password|passcode|secret|admin_token|ingest_key)\s*[:=]\s*[^\s,;]+',r'\1=[REDACTED]'),
 (r'(?<!\d)(?:\+?91[- ]?)?[6-9]\d{9}(?!\d)','[PHONE REDACTED]'),
 (r'(?i)https?://[^\s\]\)\>\"\']+','[URL REDACTED]'),
 (r'(?i)Bearer\s+[A-Za-z0-9._~+/-]{12,}','Bearer [REDACTED]'),
 (r'(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{48,}(?:\.[A-Za-z0-9_-]{12,}){0,2}(?![A-Za-z0-9_-])','[LONG VALUE REDACTED]'),
]
for pattern,repl in patterns: text=re.sub(pattern,repl,text)
dst.write_text(text,encoding='utf-8')
PY
  chmod 600 "$destination" 2>/dev/null || true
}

section "GharTV owner logs and health"
say "Project: $PROJECT"
say "Output:  $OUT"

section "Canonical project and GitHub state"
if [ -d "$PROJECT/.git" ]; then
  {
    printf 'project=%s\n' "$PROJECT"
    printf 'branch=%s\n' "$(git -C "$PROJECT" symbolic-ref --short HEAD 2>/dev/null || true)"
    printf 'head=%s\n' "$(git -C "$PROJECT" rev-parse HEAD 2>/dev/null || true)"
    printf 'origin=%s\n' "$(git -C "$PROJECT" remote get-url origin 2>/dev/null || true)"
    printf '\nstatus:\n'; git -C "$PROJECT" status --short || true
    printf '\nrecent commits:\n'; git -C "$PROJECT" log -10 --date=iso --pretty='format:%H %ad %s' || true
  } > "$OUT/raw/git-state.txt"
else
  printf 'Canonical project not found at %s\n' "$PROJECT" > "$OUT/raw/git-state.txt"
fi

if command -v gh >/dev/null 2>&1 && gh auth status -h github.com >/dev/null 2>&1; then
  gh repo view "$REPOSITORY" --json nameWithOwner,url,defaultBranchRef,visibility > "$OUT/raw/github-repository.json" 2>/dev/null || true
  gh release list --repo "$REPOSITORY" --limit 20 > "$OUT/raw/github-releases.txt" 2>/dev/null || true
  gh run list --repo "$REPOSITORY" --limit 20 > "$OUT/raw/github-runs.txt" 2>/dev/null || true
else
  printf 'GitHub CLI unavailable or not authenticated.\n' > "$OUT/raw/github-runs.txt"
fi

section "Private opt-in telemetry"
TELEMETRY_STATUS="collector configuration not found"
if [ -f "$COLLECTOR_ENV" ]; then
  set +u
  # shellcheck disable=SC1090
  source "$COLLECTOR_ENV"
  set -u
  ENDPOINT="${GHARTV_TELEMETRY_ENDPOINT:-}"
  ADMIN_TOKEN="${GHARTV_TELEMETRY_ADMIN_TOKEN:-}"
  if [ -n "$ENDPOINT" ]; then
    if curl -fsS --max-time 20 "${ENDPOINT%/}/health" > "$OUT/raw/collector-health.json" 2> "$OUT/raw/collector-health-error.txt"; then
      TELEMETRY_STATUS="collector reachable"
    else
      TELEMETRY_STATUS="collector health request failed"
    fi
  fi
  if [ -n "$ENDPOINT" ] && [ -n "$ADMIN_TOKEN" ]; then
    AUTH_FILE="$OUT/raw/.collector-auth.conf"
    printf 'header = "Authorization: Bearer %s"\n' "$ADMIN_TOKEN" > "$AUTH_FILE"
    chmod 600 "$AUTH_FILE"
    if curl -fsS --max-time 45 --config "$AUTH_FILE" \
      "${ENDPOINT%/}/v1/admin/summary?days=${GHARTV_TELEMETRY_DAYS:-30}" \
      > "$OUT/raw/telemetry-summary.json" 2> "$OUT/raw/telemetry-summary-error.txt"; then
      TELEMETRY_STATUS="$TELEMETRY_STATUS; summary retrieved"
    fi
    if curl -fsS --max-time 90 --config "$AUTH_FILE" \
      "${ENDPOINT%/}/v1/admin/export?days=${GHARTV_TELEMETRY_DAYS:-30}&limit=5000" \
      > "$OUT/raw/telemetry-events.json" 2> "$OUT/raw/telemetry-export-error.txt"; then
      TELEMETRY_STATUS="$TELEMETRY_STATUS; export retrieved"
    fi
    rm -f "$AUTH_FILE"
  else
    TELEMETRY_STATUS="$TELEMETRY_STATUS; owner token unavailable"
  fi
fi
printf '%s\n' "$TELEMETRY_STATUS" > "$OUT/raw/telemetry-status.txt"

section "Existing Google TV/emulator evidence"
SDK_ROOT=""
for candidate in "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ]; then SDK_ROOT="$candidate"; break; fi
done
if [ -n "$SDK_ROOT" ]; then
  ADB="$SDK_ROOT/platform-tools/adb"
  "$ADB" devices -l > "$OUT/raw/adb-devices.txt" 2>&1 || true
  SERIAL=""
  while read -r candidate state _; do
    [[ "$candidate" == emulator-* && "$state" == device ]] || continue
    avd="$($ADB -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -1 || true)"
    if [[ "$avd" == GharTV_* ]]; then SERIAL="$candidate"; printf '%s\n' "$avd" > "$OUT/raw/emulator-avd.txt"; break; fi
  done < <("$ADB" devices | tail -n +2)
  if [ -n "$SERIAL" ]; then
    "$ADB" -s "$SERIAL" shell dumpsys package "$PACKAGE" > "$OUT/raw/package.txt" 2>&1 || true
    "$ADB" -s "$SERIAL" shell dumpsys activity activities > "$OUT/raw/activities.txt" 2>&1 || true
    "$ADB" -s "$SERIAL" logcat -d -v threadtime -t 20000 > "$OUT/raw/logcat.txt" 2>&1 || true
    "$ADB" -s "$SERIAL" exec-out screencap -p > "$OUT/GharTV-current-screen.png" 2>/dev/null || true
    "$ADB" -s "$SERIAL" shell uiautomator dump /sdcard/ghartv-owner.xml >/dev/null 2>&1 || true
    "$ADB" -s "$SERIAL" pull /sdcard/ghartv-owner.xml "$OUT/raw/emulator-ui.xml" >/dev/null 2>&1 || true
  else
    printf 'No running GharTV emulator found.\n' > "$OUT/raw/emulator-status.txt"
  fi
else
  printf 'Android SDK/adb not found.\n' > "$OUT/raw/adb-devices.txt"
fi

section "Recent sanitized run evidence"
{
  find "$HOME/.local/state/operon-terminal-runs/ghartv" -type f \
    \( -name 'PASTE_TO_CHAT.txt' -o -name 'receipt.json' -o -name '*transcript*.log' \) \
    -print 2>/dev/null || true
  find "$HOME/Desktop" -maxdepth 3 -type f \
    \( -iname 'setup.log' -o -iname '*ghartv*.log' -o -iname '*ghartv*.txt' \) \
    -print 2>/dev/null || true
} | while IFS= read -r file; do
  [ -f "$file" ] || continue
  printf '%s\t%s\n' "$(stat -f '%m' "$file" 2>/dev/null || printf 0)" "$file"
done | sort -rn | head -40 | cut -f2- > "$OUT/raw/recent-evidence-files.txt"

index=0
while IFS= read -r file; do
  [ -f "$file" ] || continue
  index=$((index+1))
  raw="$OUT/raw/evidence-$index.txt"
  clean="$OUT/sanitized/evidence-$index.txt"
  { printf 'SOURCE: %s\n\n' "$file"; tail -n 1800 "$file" 2>/dev/null || true; } > "$raw"
  sanitize "$raw" "$clean"
done < "$OUT/raw/recent-evidence-files.txt"

[ ! -f "$OUT/raw/logcat.txt" ] || sanitize "$OUT/raw/logcat.txt" "$OUT/sanitized/emulator-logcat.txt"
[ ! -f "$OUT/raw/git-state.txt" ] || sanitize "$OUT/raw/git-state.txt" "$OUT/sanitized/git-state.txt"

section "Building owner report"
python3 - "$OUT" <<'PY'
from pathlib import Path
from collections import Counter
from datetime import datetime
import html,json,re,sys
out=Path(sys.argv[1]); raw=out/'raw'; clean=out/'sanitized'

def load(name, default):
    p=raw/name
    try:
        if not p.exists() or not p.read_text(errors='replace').strip(): return default
        return json.loads(p.read_text(errors='replace'))
    except Exception: return default

summary=load('telemetry-summary.json',{})
export=load('telemetry-events.json',{})
events=export.get('events') if isinstance(export,dict) else []
if not isinstance(events,list): events=[]
totals=summary.get('totals') if isinstance(summary,dict) else {}
if not isinstance(totals,dict): totals={}
failures=summary.get('failures') if isinstance(summary,dict) else []
if not isinstance(failures,list): failures=[]
feature_events=summary.get('events') if isinstance(summary,dict) else []
if not isinstance(feature_events,list): feature_events=[]

local_text='\n'.join(p.read_text(errors='replace') for p in clean.glob('*.txt'))
patterns={
 'DNS / host resolution':r'unable to resolve host|unknownhost|no address associated',
 'HTTP 403 / forbidden':r'\b403\b|forbidden',
 'Session 401 / 419':r'\b401\b|\b419\b',
 'DRM / Widevine':r'widevine|\bdrm\b|license acquisition',
 'Timeout / connection reset':r'timed? out|timeout|connection reset',
 'Buffering / stalled stream':r'buffering|stream remained|stream stalled',
 'App crash / fatal exception':r'fatal exception|androidruntime|uncaughtexception',
}
local_counts={label:len(re.findall(pattern,local_text,re.I)) for label,pattern in patterns.items()}
local_counts={k:v for k,v in local_counts.items() if v}

telemetry_count=totals.get('events',len(events))
installations=totals.get('installations',0)
health=load('collector-health.json',{})
collector_ok=bool(health.get('ok')) if isinstance(health,dict) else False

def esc(v): return html.escape(str(v if v is not None else ''))
def table(rows, cols):
    if not rows: return '<p class="muted">No records in this period.</p>'
    th=''.join(f'<th>{esc(label)}</th>' for key,label in cols)
    body=[]
    for row in rows:
        if not isinstance(row,dict): continue
        body.append('<tr>'+''.join(f'<td>{esc(row.get(key,""))}</td>' for key,label in cols)+'</tr>')
    return '<div class="scroll"><table><thead><tr>'+th+'</tr></thead><tbody>'+''.join(body)+'</tbody></table></div>'

md=[]
md.append('# GharTV owner logs and health')
md.append('')
md.append(f'- Generated: {datetime.now().isoformat(timespec="seconds")}')
md.append(f'- Collector reachable: {"yes" if collector_ok else "no / not verified"}')
md.append(f'- Opt-in telemetry events: {telemetry_count}')
md.append(f'- Diagnostic installations: {installations}')
md.append(f'- Exported records: {len(events)}')
md.append('')
md.append('## Interpretation')
if telemetry_count in (0,None) and not events:
    md.append('No opt-in television events were available. This is valid when diagnostics were declined, the TV has not yet updated, or the queue has not been sent. Historical delivery/emulator evidence is shown separately and is not presented as household usage.')
else:
    md.append('Telemetry below is privacy-filtered and comes from televisions that explicitly enabled diagnostics.')
md.append('')
md.append('## Historical/local signal counts')
if local_counts:
    for k,v in sorted(local_counts.items(),key=lambda x:-x[1]): md.append(f'- {k}: {v}')
else: md.append('- No matching product-error strings found in the inspected sanitized evidence.')
md.append('')
md.append('## Top collector failures')
for row in failures[:25]:
    md.append(f"- {row.get('stage','unknown')} · HTTP {row.get('http_status','')} · channel {row.get('failed_channel_id','')} · {row.get('error_type','')} · count {row.get('count','')}")
if not failures: md.append('- None reported.')
(out/'GHARTV_OWNER_REPORT.md').write_text('\n'.join(md)+'\n')

local_rows=[{'signal':k,'count':v} for k,v in sorted(local_counts.items(),key=lambda x:-x[1])]
report=f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GharTV owner report</title><style>
:root{{--bg:#02070d;--panel:#071923;--panel2:#0b2a38;--text:#eefaff;--muted:#92adba;--mint:#73f5c2;--cyan:#53e4ff;--red:#ff8e9a;--amber:#ffc857}}*{{box-sizing:border-box}}body{{margin:0;background:radial-gradient(circle at 10% 0,#0d4552,transparent 30%),var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,sans-serif}}main{{max-width:1400px;margin:auto;padding:32px}}h1{{font-size:38px;margin:0 0 8px}}h2{{margin-top:30px}}.muted{{color:var(--muted)}}.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:14px;margin:24px 0}}.card,section{{background:linear-gradient(145deg,var(--panel),var(--panel2));border:1px solid #174555;border-radius:20px;padding:18px}}.metric{{font-size:32px;font-weight:800;color:var(--mint)}}table{{width:100%;border-collapse:collapse}}th,td{{text-align:left;padding:9px;border-bottom:1px solid #143544;vertical-align:top}}th{{color:var(--cyan)}}.scroll{{overflow:auto}}code{{color:var(--mint)}}.ok{{color:var(--mint)}}.bad{{color:var(--red)}}</style></head><body><main>
<h1>GharTV owner report</h1><p class="muted">Private product, collector, emulator and delivery evidence · {esc(datetime.now().isoformat(timespec='seconds'))}</p>
<div class="cards"><div class="card"><div class="metric">{esc(telemetry_count or 0)}</div><div>opt-in events</div></div><div class="card"><div class="metric">{esc(installations or 0)}</div><div>diagnostic installations</div></div><div class="card"><div class="metric">{esc(len(failures))}</div><div>failure signatures</div></div><div class="card"><div class="metric {'ok' if collector_ok else 'bad'}">{'ONLINE' if collector_ok else 'UNKNOWN'}</div><div>collector health</div></div></div>
<section><h2>Evidence boundary</h2><p>{'Telemetry was retrieved from opted-in televisions.' if telemetry_count or events else 'No opt-in television events were available. Historical build, emulator and terminal evidence is kept separate and is not represented as user behaviour.'}</p></section>
<section><h2>Top telemetry failures</h2>{table(failures[:50],[('stage','Stage'),('http_status','HTTP'),('failed_channel_id','Failed Jio channel'),('error_type','Error type'),('fingerprint','Fingerprint'),('count','Count')])}</section>
<section><h2>Feature and lifecycle events</h2>{table(feature_events[:100],[('event_name','Event'),('count','Count')])}</section>
<section><h2>Sanitized local/historical signals</h2>{table(local_rows,[('signal','Signal'),('count','Matches')])}</section>
<section><h2>Recent privacy-filtered events</h2>{table(events[:500],[('received_at','Received'),('reference','Reference'),('event_name','Event'),('screen','Screen'),('app_version','Version'),('model','TV model'),('network','Network'),('attributes','Attributes')])}</section>
<section><h2>Files</h2><p><code>GHARTV_OWNER_REPORT.md</code>, raw owner-only JSON, sanitized evidence, emulator screenshot/UI/logcat, GitHub state and release/build summaries are in this folder.</p></section>
<p class="muted">Never publish this report folder. Collector credentials are not copied into it.</p></main></body></html>'''
(out/'GHARTV_OWNER_REPORT.html').write_text(report,encoding='utf-8')
PY

chmod 600 "$OUT"/*.md "$OUT"/*.html "$OUT"/raw/* "$OUT"/sanitized/* 2>/dev/null || true
section "Report ready"
say "$OUT/GHARTV_OWNER_REPORT.html"
command -v open >/dev/null 2>&1 && open "$OUT/GHARTV_OWNER_REPORT.html" >/dev/null 2>&1 || true
