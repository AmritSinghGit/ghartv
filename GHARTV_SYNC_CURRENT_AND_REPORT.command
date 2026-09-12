#!/bin/bash
# GharTV canonical owner continuation. No build, signing, TV reset or provider write.
# Source/app identity and delivery/control identity are deliberately separate.
set -u
umask 077
STATE="$HOME/Library/Application Support/GharTV/owner-review"
mkdir -p "$STATE" || exit 1
export GHARTV_OWNER_STATE="$STATE"
SELF="$0"
printf '\033[38;5;51m\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n GHARTV · CYAN · Production handoff and owner report\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\033[0m\n'
if ! command -v python3 >/dev/null 2>&1; then
  printf 'RESULT=BLOCKED\nREASON=python3 is required; no checkout or television was changed.\n'
  [ ! -t 0 ] || { printf 'Press Enter to finish: '; IFS= read -r _; }
  exit 1
fi
python3 - "$SELF" "$@" <<'PY'
from __future__ import annotations
import argparse, datetime as dt, hashlib, html, json, math, os, re, shlex
import shutil, socket, subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request
from collections import Counter, defaultdict
from pathlib import Path

REPO = 'AmritSinghGit/ghartv'
SOURCE = 'b46b2cd607c309d364d531b5fd9da618cd007f6c'
PUBLICATION = 'e91fa93d89e2a36872da19dcceb3fcfa0d48bf96'
APK_SHA = '6f60d18a78e4b1692d6591d04bcef6e1cfda4252dba976d160a12cef03930199'
TAG = 'v0.5.4-rc5'
VERSION = '0.5.4-rc5-family-photo'
PACKAGE = 'in.ghartv.nova'
AVD = 'GharTV_Nova_Manual_google_tv_API36'
APK_URL = f'https://github.com/{REPO}/releases/download/{TAG}/GharTV-Jio-Live-v0.5.4-rc5.apk'
MANIFEST_URL = f'https://raw.githubusercontent.com/{REPO}/main/update/latest.json'
COLLECTOR = 'https://ghartv-telemetry.ghartv-47d9a0.workers.dev'
HOME = Path.home()
SELF = Path(sys.argv[1]).resolve()
parser = argparse.ArgumentParser(description='Reuse the canonical checkout/AVD; produce a private report.')
parser.add_argument('--delivery-sha', default=os.environ.get('GHARTV_DELIVERY_SHA', ''))
parser.add_argument('--no-emulator', action='store_true')
parser.add_argument('--noninteractive', action='store_true')
args = parser.parse_args(sys.argv[2:])
STATE = Path(os.environ['GHARTV_OWNER_STATE'])
CURRENT = STATE / 'current'
RUN_ID = 'GHARTV-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + str(os.getpid())
RUN = STATE / 'runs' / RUN_ID
PROJECT = Path(os.environ.get('GHARTV_PROJECT', str(HOME / 'Downloads/GharTV_Nova_v0.4.2'))).expanduser()
for p in (CURRENT, RUN):
    if p.is_symlink(): raise SystemExit('Refusing symlinked private output directory')
    p.mkdir(parents=True, exist_ok=True); p.chmod(0o700)
receipt = dict(schema='ghartv.owner-run.v1', run_id=RUN_ID, lane='ghartv', repository=REPO,
    source_sha=SOURCE, publication_sha=PUBLICATION, apk_sha256=APK_SHA, artifact_tag=TAG,
    version_name=VERSION, version_code=14, owner_decision='APPROVED_FOR_PRODUCTION_DISTRIBUTION',
    delivery_sha='UNVERIFIED', local_sha='UNVERIFIED', checkout='NOT_CHECKED',
    production_manifest='NOT_CHECKED', artifact='NOT_CHECKED', emulator='NOT_CHECKED', physical_tv_install='NOT_VERIFIED',
    collector='NOT_CHECKED', analytics='NOT_CHECKED', obsidian='NOT_ATTEMPTED',
    memory_bridge='NOT_ATTEMPTED', cleanup_removed=0, cleanup_bytes=0,
    global_session=os.environ.get('OPERON_SESSION_ID', 'UNBOUND'), warnings=[])

class Stop(RuntimeError): pass
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, hdrs, newurl):
        return None  # Never forward the private admin token to another origin.

OPENER = urllib.request.build_opener(NoRedirect())
def now(): return dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds')
def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(131072), b''): h.update(b)
    return h.hexdigest()
def write(path, text):
    if path.is_symlink(): raise Stop('Refusing to overwrite a symlink')
    tmp = path.with_name(path.name + '.new-' + str(os.getpid()))
    with open(tmp, 'x', encoding='utf-8') as f: f.write(text)
    tmp.chmod(0o600); os.replace(tmp, path)
def run(argv, timeout=30, required=True):
    try: p = subprocess.run([str(x) for x in argv], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        if required: raise Stop('Timed out: ' + str(argv[0]))
        return ''
    if p.returncode and required:
        # Command output can contain private values; do not embed it in public-safe receipts.
        raise Stop('Command failed: ' + Path(str(argv[0])).name + ' (exit ' + str(p.returncode) + ')')
    return p.stdout.strip() if p.returncode == 0 else ''
def git(*items, required=True): return run(['git', '-C', PROJECT, *items], 45, required)
def request(url, token='', timeout=15):
    headers = {'User-Agent': 'GharTV-owner-review/1.0', 'Cache-Control': 'no-cache'}
    if token: headers['Authorization'] = 'Bearer ' + token
    with OPENER.open(urllib.request.Request(url, headers=headers), timeout=timeout) as r:
        body = r.read(8 * 1024 * 1024 + 1)
        if len(body) > 8 * 1024 * 1024: raise Stop('Response exceeds bounded report size')
        return json.loads(body.decode('utf-8'))
def warn(stage, exc):
    detail = 'HTTP_' + str(exc.code) if isinstance(exc, urllib.error.HTTPError) else type(exc).__name__
    receipt['warnings'].append(stage + ': ' + detail)
def stamp(ms):
    try: return dt.datetime.fromtimestamp(float(ms) / 1000, dt.timezone.utc).isoformat(timespec='seconds')
    except (ValueError, TypeError, OverflowError, OSError): return 'Unknown'
def esc(value): return html.escape(str(value if value is not None else 'Unknown'))
def finite(value): return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
def percentile(values, q):
    xs = sorted(values); pos = (len(xs)-1)*q; lo = math.floor(pos); hi = math.ceil(pos)
    return xs[lo] + (xs[hi]-xs[lo])*(pos-lo)

def sync_checkout():
    if not shutil.which('git'): raise Stop('git is required')
    if not (PROJECT / '.git').exists(): raise Stop('Canonical checkout missing; no clone created')
    origin = git('remote', 'get-url', 'origin').removesuffix('.git')
    if origin not in ('https://github.com/' + REPO, 'git@github.com:' + REPO, 'ssh://git@github.com/' + REPO):
        raise Stop('Unexpected repository origin; preserved')
    if git('symbolic-ref', '--short', 'HEAD', required=False) != 'main':
        raise Stop('Canonical checkout is not on main; preserved')
    receipt['local_sha'] = git('rev-parse', 'HEAD')
    if git('status', '--porcelain', '--untracked-files=normal'):
        raise Stop('Local changes/untracked files require reconciliation; preserved, not blindly pushed')
    git('fetch', '--no-tags', 'origin', 'main')
    remote = git('rev-parse', 'origin/main')
    desired = args.delivery_sha or remote
    if not re.fullmatch('[0-9a-f]{40}', desired): raise Stop('Invalid delivery SHA')
    if desired != remote: raise Stop('Pinned delivery differs from current main; refresh handoff, no rewind')
    if not args.delivery_sha:
        committed = subprocess.check_output(['git', '-C', str(PROJECT), 'show', desired + ':GHARTV_SYNC_CURRENT_AND_REPORT.command'])
        if hashlib.sha256(committed).hexdigest() != sha(SELF):
            raise Stop('This launcher differs from the current committed launcher')
    receipt['delivery_sha'] = desired
    git('merge-base', '--is-ancestor', PUBLICATION, desired)
    git('merge-base', '--is-ancestor', receipt['local_sha'], desired)
    if git('diff', '--name-only', SOURCE, desired, '--', 'android-tv'):
        raise Stop('App source changed since the approved binary; new review required')
    git('merge', '--ff-only', desired)
    receipt['local_sha'] = git('rev-parse', 'HEAD')
    receipt['checkout'] = 'EXACT_DELIVERY_FAST_FORWARD_VERIFIED'


def verify_manifest():
    m = request(MANIFEST_URL + '?owner_check=' + str(int(time.time())))
    expected = {'versionCode': 14, 'versionName': VERSION, 'sha256': APK_SHA,
                'apkUrl': APK_URL, 'sourceCommit': SOURCE}
    if any(m.get(k) != v for k, v in expected.items()):
        raise Stop('Live update channel changed; refusing to install an obsolete candidate')
    receipt['production_manifest'] = 'EXACT_REVIEWED_APK_ADVERTISED'


def ensure_apk():
    target = CURRENT / 'GharTV-Jio-Live-current.apk'
    if target.is_symlink(): raise Stop('Refusing symlinked APK target')
    if target.exists() and sha(target) == APK_SHA:
        receipt['artifact'] = 'SHA256_VERIFIED'; return target
    with tempfile.TemporaryDirectory(prefix='.ghartv-download-', dir=CURRENT) as td:
        part = Path(td) / 'candidate.apk'
        run(['curl', '--proto', '=https', '--proto-redir', '=https', '-fLsS',
             '--connect-timeout', '12', '--max-time', '120', APK_URL, '-o', part], 130)
        if sha(part) != APK_SHA: raise Stop('APK digest mismatch; previous candidate retained')
        part.chmod(0o600); os.replace(part, target)
    receipt['artifact'] = 'SHA256_VERIFIED'
    return target


def inspect_emulator():
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT', str(HOME / 'Library/Android/sdk')))
    adb = sdk / 'platform-tools/adb'
    emulator = sdk / 'emulator/emulator'
    if not adb.exists(): receipt['emulator'] = 'SDK_UNAVAILABLE'; return
    serials = []
    for line in run([adb, 'devices']).splitlines():
        fields = line.split()
        if len(fields) >= 2 and fields[0].startswith('emulator-') and fields[1] == 'device':
            name = run([adb, '-s', fields[0], 'emu', 'avd', 'name'], 8, False).splitlines()
            if name and name[0] == AVD: serials.append(fields[0])
    if len(serials) > 1: raise Stop('Multiple canonical AVD instances found; none stopped')
    if not serials:
        if not emulator.exists() or AVD not in run([emulator, '-list-avds']).splitlines():
            receipt['emulator'] = 'EXISTING_AVD_UNAVAILABLE_NO_DUPLICATE_CREATED'; return
        for port in (5580, 5581):
            with socket.socket() as sock:
                if sock.connect_ex(('127.0.0.1', port)) == 0:
                    raise Stop('Canonical emulator port occupied; no competing runtime started')
        with open(CURRENT / 'emulator-start.log', 'wb') as log:
            subprocess.Popen([str(emulator), '-avd', AVD, '-port', '5580'],
                             stdout=log, stderr=log, start_new_session=True)
        serials = ['emulator-5580']
        ready = False
        for _ in range(60):
            if run([adb, '-s', serials[0], 'shell', 'getprop', 'sys.boot_completed'], 3, False) == '1':
                ready = True; break
            time.sleep(2)
        if not ready: raise Stop('Existing AVD is still booting; preserved, no second launch')
    serial = serials[0]
    data = run([adb, '-s', serial, 'shell', 'dumpsys', 'package', PACKAGE], 15)
    found = re.search(r'versionCode=(\d+)', data)
    code = int(found.group(1)) if found else 0
    if code > 14: raise Stop('Emulator has a newer app; downgrade refused')
    if code < 14:
        apk = ensure_apk()
        output = run([adb, '-s', serial, 'install', '-r', apk], 90)
        if 'Success' not in output: raise Stop('Emulator did not confirm package update')
    paths = run([adb, '-s', serial, 'shell', 'pm', 'path', PACKAGE], 15).splitlines()
    if len(paths) != 1 or not paths[0].startswith('package:/'):
        raise Stop('Installed APK layout cannot be verified; preserved')
    with tempfile.TemporaryDirectory(prefix='.ghartv-verify-', dir=CURRENT) as td:
        pulled = Path(td) / 'installed.apk'
        run([adb, '-s', serial, 'pull', paths[0][8:], pulled], 40)
        if sha(pulled) != APK_SHA: raise Stop('Installed APK is not the reviewed binary; preserved')
    opened = run([adb, '-s', serial, 'shell', 'am', 'start', '-W', '-n', PACKAGE + '/.SplashActivity',
                 '--es', 'ghartv_theme_preview', 'auto'], 20)
    if 'Error:' in opened: raise Stop('Android did not launch the reviewed activity')
    receipt['emulator'] = 'EXACT_INSTALLED_APK_VERIFIED_AND_OPENED'
    receipt['emulator_serial'] = serial


def cleanup_known_downloads():
    known = {
      'GHARTV_V054_RC5R1_VALIDATION_REPAIR_AND_CONTINUE.command':
        '3926275b708788ce3765b7fbb8ccb3fb72b31226fe7ac6fa720b83b5e8ac9916',
      'GHARTV_V054_RC5_CI_REPAIR_PHOTO_AUTOPREVIEW_CONTINUE.command':
        '669cdd4ced27efaa67baf3b5643b4f0094f37822f3f83fb4159da360362c49f8'}
    downloads = HOME / 'Downloads'
    if not downloads.is_dir(): return
    for p in downloads.iterdir():
        for name, digest in known.items():
            stem, ext = name.rsplit('.', 1)
            if not re.fullmatch(re.escape(stem) + r'(?: \(\d+\))?\.' + re.escape(ext), p.name): continue
            if p.is_symlink() or not p.is_file() or p.resolve() == SELF: continue
            if sha(p) == digest:
                size = p.stat().st_size; p.unlink()
                receipt['cleanup_removed'] += 1; receipt['cleanup_bytes'] += size
    # Only exact known, checksum-matched redundant installers. No directory sweep or Git pruning.


def collector_report():
    config = HOME / 'Library/Application Support/GharTV/telemetry/collector.env'
    values = {}
    if config.is_file():
        for line in config.read_text().splitlines():
            line = line.strip()
            if line.startswith('export '): line = line[7:]
            k, sep, value = line.partition('=')
            if sep and k in ('GHARTV_TELEMETRY_ENDPOINT', 'GHARTV_TELEMETRY_ADMIN_TOKEN'):
                try:
                    bits = shlex.split(value, comments=True)
                    if len(bits) == 1: values[k] = bits[0]
                except ValueError: pass
    endpoint = values.get('GHARTV_TELEMETRY_ENDPOINT', COLLECTOR).rstrip('/')
    if endpoint != COLLECTOR:
        receipt['collector'] = 'CONFIG_ORIGIN_MISMATCH_TOKEN_NOT_SENT'; return None, None
    try:
        h = request(endpoint + '/health')
        receipt['collector_health'] = 'REACHABLE' if h.get('ok') else 'UNVERIFIED'
    except Exception as e:
        receipt['collector_health'] = 'UNAVAILABLE'; warn('collector_health', e)
    token = values.get('GHARTV_TELEMETRY_ADMIN_TOKEN', '')
    if not token:
        receipt['collector'] = 'ADMIN_CREDENTIAL_NOT_AVAILABLE'; return None, None
    summary = export = None
    for name, path in [('summary', '/v1/admin/summary?days=30'), ('export', '/v1/admin/export?days=30&limit=5000')]:
        try:
            body = request(endpoint + path, token, 25)
            if not isinstance(body, dict) or body.get('ok') is not True: raise Stop('Invalid collector response')
            if name == 'summary':
                totals = body.get('totals')
                if not isinstance(totals, dict) or not all(isinstance(totals.get(k), int) and totals[k] >= 0 for k in ('events','installations')):
                    raise Stop('Invalid summary totals')
                summary = body
            else:
                if not isinstance(body.get('events'), list): raise Stop('Invalid export records')
                export = body
        except Exception as e: warn('collector_' + name, e)
    receipt['collector'] = 'SUMMARY_AND_BOUNDED_EXPORT_READ' if summary is not None and export is not None else 'PARTIAL_OR_UNAVAILABLE'
    return summary, export


def existing_analytics():
    marker = HOME / 'Library/Application Support/Operon/operon-command-market-v0.2.0/runtime/server.url'
    if not marker.is_file(): receipt['analytics'] = 'EXISTING_RUNTIME_NOT_MATERIALIZED'; return ''
    url = marker.read_text().strip().rstrip('/')
    p = urllib.parse.urlsplit(url)
    if p.scheme not in ('http','https') or p.hostname not in ('127.0.0.1','localhost','::1') or p.username or p.password or p.query or p.fragment:
        receipt['analytics'] = 'UNTRUSTED_RUNTIME_URL_NOT_OPENED'; return ''
    try:
        request(url + '/api/v1/health', timeout=3)
        with OPENER.open(url + '/ghartv-analytics.html', timeout=3) as r:
            page = r.read(256000).decode('utf-8', errors='replace')
            if 'ghartv' not in page.lower(): raise Stop('Tenant surface not identified')
        receipt['analytics'] = 'EXISTING_TENANT_SURFACE_REACHABLE_SHA_NOT_ATTESTED'
        return url + '/ghartv-analytics.html'
    except Exception as e:
        receipt['analytics'] = 'EXISTING_TENANT_SURFACE_UNAVAILABLE'; warn('analytics', e); return ''


def render_report(summary, export, analytics):
    events = [x for x in (export or {}).get('events', []) if isinstance(x, dict)]
    devices = {}; latency = defaultdict(list)
    for e in events:
        ident = e.get('install_hash')
        if isinstance(ident, str) and re.fullmatch('[a-fA-F0-9]{16,64}', ident):
            old = devices.get(ident)
            if old is None or (finite(e.get('received_at')) and e['received_at'] > (old.get('received_at') if finite(old.get('received_at')) else 0)):
                devices[ident] = e
        attrs = e.get('attributes')
        if isinstance(attrs, dict):
            for k, value in attrs.items():
                if isinstance(k, str) and k.endswith('_ms') and finite(value) and 0 <= value <= 3600000:
                    latency[(str(e.get('event_name','unknown')), str(attrs.get('stage','')), k)].append(value)
    totals = (summary or {}).get('totals', {})
    def table(rows, cols):
        if not rows: return '<p class="muted">No records returned for this table; see access and sample status above.</p>'
        return '<div class="scroll"><table><thead><tr>' + ''.join('<th>'+esc(label)+'</th>' for key,label in cols) + '</tr></thead><tbody>' + ''.join('<tr>'+''.join('<td>'+esc(row.get(key))+'</td>' for key,label in cols)+'</tr>' for row in rows if isinstance(row,dict)) + '</tbody></table></div>'
    device_rows = []
    for ident,e in devices.items():
        device_rows.append(dict(identity=ident, model=str(e.get('manufacturer',''))+' '+str(e.get('model','')),
            version=e.get('app_version'), code=e.get('version_code'), network=e.get('network'), last=stamp(e.get('received_at'))))
    timing_rows = [dict(event=k[0], stage=k[1], metric=k[2], n=len(v), p50=round(percentile(v,.5),1), p95=round(percentile(v,.95),1))
                   for k,v in sorted(latency.items())]
    source_time = (summary or {}).get('generated_at','Unavailable')
    latest = stamp(max((e['received_at'] for e in events if finite(e.get('received_at'))), default=None))
    sample_note = 'Export unavailable: device/timing coverage is unknown.' if export is None else f'Latest {len(events)} returned events; limit 5,000 in a 30-day received-time window. Device and timing tables describe this sample only.'
    if export is not None and len(events) >= 5000: sample_note += ' Export cap reached: older records are omitted.'
    feedback = '\n'.join([f'RUN_ID={RUN_ID}',f'APP_SOURCE_SHA={SOURCE}',f'DELIVERY_SHA={receipt["delivery_sha"]}',f'APK_SHA256={APK_SHA}',
        'PHYSICAL_TV_INSTALL=NOT_VERIFIED','SCREEN_OR_ACTION=','EXPECTED=','OBSERVED=','REPRODUCTION_STEPS=','SEVERITY='])
    document = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"><title>GharTV · Owner report</title><style>
:root{color-scheme:dark;--bg:#071018;--panel:#102430;--line:#234756;--text:#edf8ff;--muted:#a2bdc8;--accent:#77e8df}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.55 system-ui,-apple-system,sans-serif}main{max-width:1380px;margin:auto;padding:36px}h1{font-size:38px;margin:4px 0}h2{font-size:22px;margin:0 0 12px}p{max-width:1100px}.eyebrow{color:var(--accent);letter-spacing:.14em;font-size:12px}.muted{color:var(--muted)}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;margin:28px 0}.card,section{background:var(--panel);padding:22px;border:1px solid var(--line);border-radius:14px}section{margin:18px 0}.value{font-size:28px;color:var(--accent);font-weight:700}.scroll{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{padding:11px;text-align:left;border-bottom:1px solid var(--line);vertical-align:top;overflow-wrap:anywhere}th{color:var(--accent)}code{overflow-wrap:anywhere}a{color:var(--accent)}textarea{width:100%;min-height:240px;background:var(--bg);color:var(--text);padding:16px;border:1px solid var(--line);font:13px/1.5 monospace}button{background:var(--accent);color:#071018;padding:12px 18px;border:0;border-radius:8px;font-weight:700;cursor:pointer}.badge{display:inline-block;padding:5px 12px;border:1px solid var(--accent);border-radius:30px}details{margin:12px 0}@media(max-width:700px){main{padding:18px}h1{font-size:29px}}
</style></head><body><main><div class="eyebrow">GHAR TV · PRIVATE OWNER REPORT · EXISTING GHARTV LANE</div><h1>Release &amp; television health</h1><p class="muted">Private snapshot. Run the same command again to refresh. No public report server, no invented live events, no automatic uploads of this report.</p>'''
    document += '<div class="cards">' + ''.join('<div class="card"><div class="muted">'+esc(label)+'</div><div class="value">'+esc(value)+'</div></div>' for label,value in [
        ('Approved APK · this handoff',VERSION),('Collector events · 30 days',totals.get('events','Unavailable')),
        ('Opt-in installations · 30 days',totals.get('installations','Unavailable')),('TV installation confirmation','Not yet verified')]) + '</div>'
    document += '<section><h2>Evidence and freshness</h2>' + table([
        {'check':'Generated locally','status':now()},{'check':'Collector summary generated','status':source_time},
        {'check':'Latest received event in sample','status':latest},
        *[{'check':k,'status':receipt[k]} for k in ('production_manifest','artifact','checkout','emulator','collector','analytics')]], [('check','Check'),('status','Observed result')]) + '<p>'+esc(sample_note)+'</p><p>Zero means the collector successfully returned zero. Missing credentials, timeouts and failed requests are shown as unavailable. A reported version is last observed activity, not a present online-status guarantee.</p>'
    if analytics: document += '<p><a href="'+esc(analytics)+'">Open the existing Operon GharTV analytics surface</a> — no new runtime was started.</p>'
    document += '</section><section><h2>Observed installations</h2><p class="muted">Pseudonymous diagnostic IDs, not Jio account IDs or physical TV serial numbers. Emulators can also send opted-in events. Location and owner-friendly labels are not collected by this release.</p>' + table(device_rows,[('identity','Diagnostic ID'),('model','Reported device'),('version','Last reported build'),('code','Code'),('network','Network type'),('last','Last received · UTC')]) + '</section>'
    document += '<section><h2>Action and playback timings</h2><p class="muted">Only numeric *_ms fields actually received are aggregated. P50/P95 and sample counts are per event, stage and metric. Missing instrumentation is not zero latency.</p>' + table(timing_rows,[('event','Event'),('stage','Stage'),('metric','Metric · ms'),('n','Samples'),('p50','P50'),('p95','P95')]) + '</section>'
    document += '<section><h2>Reported failures</h2><p class="muted">HTTP/session/DNS/decoder signals are evidence for investigation, not proof that a particular ISP, API or the app is at fault. Successful viewing history is deliberately not uploaded.</p>' + table((summary or {}).get('failures',[]),[('stage','Stage'),('http_status','HTTP'),('failed_channel_id','Failed channel only'),('error_type','Error'),('count','Events')]) + '</section>'
    document += '<section><h2>Feature events and version adoption</h2>' + table((summary or {}).get('events',[]),[('event_name','Event'),('count','Count')]) + table((summary or {}).get('versions',[]),[('app_version','Version'),('version_code','Code'),('installations','Installations'),('events','Events')]) + '</section>'
    document += '<section><h2>Review feedback tied to this candidate</h2><p>Describe the screen/action and the change needed. Do not paste account details, tokens, private diagnostic IDs or household locations into GitHub. Copying below does not send anything.</p><textarea id="feedback">'+esc(feedback)+'</textarea><p><button onclick="const t=document.getElementById(\'feedback\');t.select();if(navigator.clipboard){navigator.clipboard.writeText(t.value).then(()=>this.textContent=\'Copied\').catch(()=>this.textContent=\'Selected — copy manually\')}else{this.textContent=\'Selected — copy manually\'}">Copy feedback</button></p></section>'
    document += '<section><h2>Release lineage</h2><p>Reviewed app source: <code>'+SOURCE+'</code><br>Update publication: <code>'+PUBLICATION+'</code><br>Delivery/control source: <code>'+esc(receipt['delivery_sha'])+'</code><br>Reviewed APK SHA-256: <code>'+APK_SHA+'</code></p><p>The APK retains its RC5 label; production distribution does not rebuild it or certify physical-TV installation. Mandatory update gating, silent installation, location, detailed bitrate/decoder telemetry and the microphone fix remain follow-up work.</p></section></main></body></html>'
    write(CURRENT/'GHARTV_OWNER_REPORT.html', document)
    receipt['report'] = str(CURRENT/'GHARTV_OWNER_REPORT.html')


def finish():
    receipt['finished_at'] = now()
    receipt['result'] = 'READY' if receipt['checkout'] == 'EXACT_DELIVERY_FAST_FORWARD_VERIFIED' and receipt['production_manifest'] == 'EXACT_REVIEWED_APK_ADVERTISED' and receipt['artifact'] == 'SHA256_VERIFIED' and (args.no_emulator or receipt['emulator'] == 'EXACT_INSTALLED_APK_VERIFIED_AND_OPENED') else 'ACTION_REQUIRED_PRIOR_STATE_PRESERVED'
    vault = HOME/'Documents/Amrit Executive Memory'
    note = vault/'90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'/('ghartv-'+RUN_ID+'.md')
    def text():
        keys = ['run_id','global_session','result','lane','repository','source_sha','delivery_sha','publication_sha','apk_sha256',
            'version_name','version_code','checkout','local_sha','production_manifest','artifact','emulator','physical_tv_install',
            'collector','analytics','obsidian','memory_bridge','cleanup_removed','cleanup_bytes']
        return '\n'.join(k.upper()+'='+str(receipt[k]) for k in keys) + '\nWARNINGS=' + '; '.join(receipt['warnings']) + '\nNEXT_ACTION=Confirm the physical TV update and review the same candidate; no account or diagnostic data belongs in GitHub.\n'
    if vault.is_dir():
        try:
            note.parent.mkdir(parents=True, exist_ok=True)
            receipt['obsidian'] = 'LOCAL_VAULT_NOTE_WRITTEN_SYNC_NOT_ATTESTED'
            write(note, '# GharTV canonical continuation\n\n```text\n'+text()+'```\n')
            bridge = shutil.which('amrit-context')
            if bridge:
                p = subprocess.run([bridge,'handoff','--file',str(note)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=25)
                receipt['memory_bridge'] = 'COMMAND_EXIT_0_REMOTE_SYNC_NOT_ATTESTED' if p.returncode == 0 else 'COMMAND_FAILED'
            else: receipt['memory_bridge'] = 'COMMAND_UNAVAILABLE'
            write(note, '# GharTV canonical continuation\n\n```text\n'+text()+'```\n')
        except Exception as e: receipt['obsidian'] = 'WRITE_OR_BRIDGE_FAILED'; warn('continuity',e)
    else: receipt['obsidian'] = 'EXISTING_VAULT_NOT_FOUND_NO_REPLACEMENT_CREATED'
    encoded = json.dumps(receipt, indent=2)+'\n'
    write(RUN/'receipt.json',encoded); write(RUN/'PASTE_TO_CHAT.txt',text())
    write(STATE/'latest-receipt.json',encoded); write(STATE/'PASTE_TO_CHAT.txt',text())
    print('\n'+text())
    print('PRIVATE_REPORT='+receipt.get('report','Unavailable'))
    if sys.platform == 'darwin' and receipt.get('report'):
        subprocess.run(['open',receipt['report']], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

if __name__ == '__main__':
    lock = STATE/'owner-run.lock'
    try: lock.mkdir()
    except FileExistsError:
        receipt['warnings'].append('Another run or stale owner-run.lock exists. No concurrent mutation attempted.')
        write(RUN/'PASTE_TO_CHAT.txt', 'RUN_ID='+RUN_ID+'\nRESULT=BLOCKED_CONCURRENT_RUN\nAPP_SOURCE_SHA='+SOURCE+'\nNO_MUTATION_ATTEMPTED=true\n')
        print((RUN/'PASTE_TO_CHAT.txt').read_text())
        raise SystemExit(3)
    write(lock/'pid.txt',str(os.getpid())+'\n')
    try:
        if sys.platform != 'darwin': raise Stop('Run this owner command on the existing Mac, not on a replacement machine')
        print('Checking the existing checkout, the live update offer and the approved binary…')
        try:
            sync_checkout(); verify_manifest(); ensure_apk()
            if not args.no_emulator: inspect_emulator()
            else: receipt['emulator'] = 'SKIPPED_BY_OWNER'
            cleanup_known_downloads()
        except Exception as e:
            if isinstance(e, Stop): receipt['warnings'].append(str(e))
            else: warn('owner_run',e)
        summary, export = collector_report()
        analytics = existing_analytics()
        render_report(summary, export, analytics)
    except Exception as e:
        if isinstance(e, Stop): receipt['warnings'].append(str(e))
        else: warn('report',e)
    finally:
        try: finish()
        finally:
            (lock/'pid.txt').unlink(missing_ok=True)
            lock.rmdir()
    sys.exit(0 if receipt['result'] == 'READY' else 1)
PY
RESULT=$?
if [ "$RESULT" -eq 3 ]; then
  printf 'Concurrent-run guard: no prior handoff will be copied. Resolve the other run first.\n'
  exit 3
fi
if [ -t 0 ] && [[ " $* " != *" --noninteractive "* ]]; then
  printf '\nPress Enter to copy the handoff: '; IFS= read -r _
  if command -v pbcopy >/dev/null 2>&1 && [ -f "$STATE/PASTE_TO_CHAT.txt" ]; then
    pbcopy < "$STATE/PASTE_TO_CHAT.txt" && printf 'Copied.\n'
  fi
  printf 'Press Enter to finish/close this dedicated terminal; the app remains open: '; IFS= read -r _
  # Close only the dedicated launcher window with this exact TTY and one tab.
  # Shared terminals and unsupported terminal applications remain open.
  if [ "${TERM_PROGRAM:-}" = Apple_Terminal ] && [ "$SELF" = "$HOME/.local/share/ghartv-launcher/current/GHARTV_SYNC_CURRENT_AND_REPORT.command" ]; then
    THIS_TTY="$(tty)"
    (sleep 1; osascript - "$THIS_TTY" <<'APPLESCRIPT' >/dev/null 2>&1
on run argv
  tell application "Terminal"
    repeat with w in windows
      if (count of tabs of w) is 1 then
        if tty of selected tab of w is item 1 of argv then
          close w saving no
          exit repeat
        end if
      end if
    end repeat
  end tell
end run
APPLESCRIPT
    ) </dev/null >/dev/null 2>&1 &
  fi
fi
exit "$RESULT"
