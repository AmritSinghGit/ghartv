#!/bin/bash
set -Eeuo pipefail

CONFIG="$HOME/Library/Application Support/GharTV/telemetry/collector.env"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="${GHARTV_TELEMETRY_REPORT_DIR:-$HOME/Desktop/GharTV-Telemetry-Report-$STAMP}"
DAYS="${GHARTV_TELEMETRY_DAYS:-7}"
mkdir -p "$OUT"

fail(){ printf '\nERROR: %s\n' "$1" >&2; exit 1; }
[[ -f "$CONFIG" ]] || fail "Collector configuration is missing: $CONFIG"
# shellcheck disable=SC1090
source "$CONFIG"
[[ -n "${GHARTV_TELEMETRY_ENDPOINT:-}" ]] || fail "Telemetry endpoint is missing from collector.env"
[[ -n "${GHARTV_TELEMETRY_ADMIN_TOKEN:-}" ]] || fail "Admin token is missing from collector.env"

BASE="${GHARTV_TELEMETRY_ENDPOINT%/}"
SUMMARY="$OUT/summary.json"
EXPORT="$OUT/events.json"
AUTH_CONFIG="$OUT/.curl-auth.conf"
printf 'header = "Authorization: Bearer %s"\n' "$GHARTV_TELEMETRY_ADMIN_TOKEN" > "$AUTH_CONFIG"
chmod 600 "$AUTH_CONFIG"
trap 'rm -f "$AUTH_CONFIG"' EXIT

curl -fsS --max-time 45 --config "$AUTH_CONFIG" \
  "$BASE/v1/admin/summary?days=$DAYS" > "$SUMMARY"

curl -fsS --max-time 90 --config "$AUTH_CONFIG" \
  "$BASE/v1/admin/export?days=$DAYS&limit=5000" > "$EXPORT"

python3 - "$SUMMARY" "$EXPORT" "$OUT/report.html" <<'PY'
from pathlib import Path
import json,sys,html
summary=json.loads(Path(sys.argv[1]).read_text())
export=json.loads(Path(sys.argv[2]).read_text())
out=Path(sys.argv[3])

def esc(v): return html.escape(str(v if v is not None else ''))
def table(rows, columns):
    if not rows: return '<p class="muted">No data in this period.</p>'
    head=''.join(f'<th>{esc(label)}</th>' for key,label in columns)
    body=[]
    for row in rows:
        body.append('<tr>'+''.join(f'<td>{esc(row.get(key,""))}</td>' for key,label in columns)+'</tr>')
    return f'<div class="scroll"><table><thead><tr>{head}</tr></thead><tbody>{"".join(body)}</tbody></table></div>'

totals=summary.get('totals') or {}
failures=summary.get('failures') or []
events=summary.get('events') or []
versions=summary.get('versions') or []
devices=summary.get('devices') or []
daily=summary.get('daily') or []
raw=export.get('events') or []
report=f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>GharTV telemetry report</title><style>
:root{{--bg:#02070d;--panel:#071923;--panel2:#0b2a38;--text:#eefaff;--muted:#92adba;--mint:#73f5c2;--cyan:#53e4ff;--red:#ff8e9a}}*{{box-sizing:border-box}}body{{margin:0;background:radial-gradient(circle at 10% 0,#0d4552,transparent 30%),var(--bg);color:var(--text);font:15px/1.5 system-ui,-apple-system,sans-serif}}main{{width:min(1440px,94vw);margin:auto;padding:40px 0 80px}}h1{{font-size:44px;margin:0}}h2{{margin:0 0 16px}}.muted{{color:var(--muted)}}.cards{{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;margin:24px 0}}.card,section{{background:linear-gradient(135deg,var(--panel),var(--panel2));border:1px solid #17475a;border-radius:20px;padding:20px}}.metric{{font-size:38px;font-weight:850;color:var(--mint)}}section{{margin-top:18px}}table{{width:100%;border-collapse:collapse;min-width:680px}}th,td{{padding:10px 12px;text-align:left;border-bottom:1px solid #17404f}}th{{color:var(--cyan);position:sticky;top:0;background:#071923}}.scroll{{overflow:auto;max-height:520px}}code{{color:var(--mint)}}
</style></head><body><main><h1>GharTV diagnostics</h1><p class="muted">Opt-in, privacy-filtered pseudonymous data · {esc(summary.get('window_days',7))}-day window · generated {esc(summary.get('generated_at',''))}</p>
<div class="cards"><div class="card"><div class="metric">{esc(totals.get('events',0))}</div><div>events</div></div><div class="card"><div class="metric">{esc(totals.get('installations',0))}</div><div>diagnostic installations</div></div><div class="card"><div class="metric">{esc(len(failures))}</div><div>failure signatures</div></div><div class="card"><div class="metric">{esc(len(raw))}</div><div>exported records</div></div></div>
<section><h2>Top failures</h2>{table(failures, [('stage','Stage'),('http_status','HTTP'),('failed_channel_id','Failed Jio channel'),('error_type','Error type'),('fingerprint','Fingerprint'),('count','Count')])}</section>
<section><h2>Feature and lifecycle events</h2>{table(events,[('event_name','Event'),('count','Count')])}</section>
<section><h2>Versions</h2>{table(versions,[('app_version','Version'),('version_code','Code'),('installations','Installs'),('events','Events')])}</section>
<section><h2>TV devices</h2>{table(devices,[('manufacturer','Manufacturer'),('model','Model'),('android_api','API'),('installations','Installs')])}</section>
<section><h2>Daily activity</h2>{table(daily,[('day','Day'),('installations','Installs'),('events','Events')])}</section>
<section><h2>Recent privacy-filtered records</h2>{table(raw[:500],[('received_at','Received epoch'),('reference','Reference'),('event_name','Event'),('screen','Screen'),('app_version','Version'),('model','TV model'),('network','Network'),('attributes','Attributes')])}</section>
<p class="muted">The collector does not store mobile numbers, OTPs, Jio credentials/tokens/cookies, stream or licence URLs, IP addresses, Wi-Fi names, successful channel viewing history, Android IDs or TV serial numbers.</p></main></body></html>'''
out.write_text(report,encoding='utf-8')
PY

chmod 600 "$SUMMARY" "$EXPORT" 2>/dev/null || true
printf 'GharTV telemetry report: %s\n' "$OUT/report.html"
open "$OUT/report.html"
