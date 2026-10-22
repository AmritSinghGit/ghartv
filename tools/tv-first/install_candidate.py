#!/usr/bin/env python3
"""Install the GitHub-built GharTV review candidate on the Nova emulator.

    python3 tools/tv-first/install_candidate.py            # GitHub build (default)
    python3 tools/tv-first/install_candidate.py --local    # build on this Mac instead (when GitHub can't hand over the APK)

1. Finds the reviewNN-candidate GitHub Actions run for the current commit (waits while it runs).
2. Downloads its owner-review artifact and checks it was built from exactly this commit and
   that the APK matches the SHA-256 recorded by CI.
3. Signs it with your EXISTING local GharTV key (~/Library/Application Support/GharTV/signing),
   checks the certificate is the GharTV release certificate, so the update keeps your app data
   and Jio login.
4. Starts the Nova emulator if it is not running, installs the update, opens GharTV.

It never creates a key, never uninstalls, never publishes, and never prints passwords.
"""
import hashlib, json, os, re, shlex, shutil, subprocess, sys, tempfile, time
from pathlib import Path

REPO = 'AmritSinghGit/ghartv'
PACKAGE = 'in.ghartv.nova'
CERT = '40a9d8bf6b1c557b3d6fd02acef075368dd13e28691f207a297202d0d5ec233c'
AVD = 'GharTV_Nova_Manual_google_tv_API36'
SIGNING = Path.home() / 'Library/Application Support/GharTV/signing'
KEEP = Path.home() / 'Library/Application Support/GharTV/owner-review/candidates'
ROOT = Path(__file__).resolve().parents[2]
LANE = 'ghartv-nova'
CI_STATE = Path.home() / 'operon-tools/state/ci' / LANE
KEYS = ('GHARTV_SIGNING_STORE', 'GHARTV_SIGNING_STORE_PASSWORD', 'GHARTV_SIGNING_KEY_ALIAS', 'GHARTV_SIGNING_KEY_PASSWORD')


def die(msg):
    sys.exit('\nSTOP: ' + msg)


def run(args, env=None, timeout=600):
    return subprocess.run([str(a) for a in args], text=True, capture_output=True, env=env, timeout=timeout)


def must(args, what, env=None, timeout=600):
    p = run(args, env, timeout)
    if p.returncode:
        die(f'{what} failed: {(p.stderr or p.stdout).strip()[-800:]}')
    return p.stdout


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def version_code():
    m = re.search(r'versionCode = (\d+)', (ROOT / 'android-tv/app/build.gradle.kts').read_text())
    if not m:
        die('cannot read versionCode from android-tv/app/build.gradle.kts')
    return int(m.group(1))


def find_sdk():
    for c in (os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), Path.home() / 'Library/Android/sdk'):
        if c and (Path(c) / 'platform-tools/adb').exists():
            return Path(c)
    die('Android SDK not found - set ANDROID_HOME to your SDK folder')


def build_tools(sdk):
    roots = [p for p in (sdk / 'build-tools').glob('*') if (p / 'apksigner').exists() and (p / 'zipalign').exists()]
    if not roots:
        die(f'no build-tools with apksigner and zipalign under {sdk}/build-tools')
    return sorted(roots, key=lambda p: [int(x) for x in re.findall(r'\d+', p.name)])[-1]


def wait_for_ci(sha, code):
    wf = f'review{code}-candidate.yml'
    name = f'GharTV Review{code} candidate'
    told = False
    for _ in range(240):
        # Not filtered with --workflow: GitHub only resolves workflow names that exist on the default branch.
        out = must(['gh', 'run', 'list', '--repo', REPO, '--commit', sha,
                    '--json', 'databaseId,status,conclusion,url,workflowName', '-L', '50'], 'reading GitHub Actions')
        runs = [r for r in json.loads(out or '[]') if r.get('workflowName') == name]
        if runs:
            r = runs[0]
            if r['status'] == 'completed':
                if r['conclusion'] != 'success':
                    report_failure(r, sha, code)
                    die(f"GitHub's build/tests did not pass ({r['conclusion']}). Nothing was installed. Details: {r['url']}")
                print(f"GitHub build and tests passed: {r['url']}")
                record(sha, code, r, 'PASSED', 'GitHub build and deterministic tests passed.')
                return r
            if not told:
                print(f"GitHub is building and testing code{code} ({r['url']}). Waiting - usually 25-40 minutes...", flush=True)
                told = True
        elif not told:
            print(f'Waiting for GitHub to start {wf} for {sha[:12]} (did `git push` finish?)...', flush=True)
            told = True
        time.sleep(30)
    die('gave up after 2 hours of waiting for GitHub')


KEY_LINES = re.compile(r'FAILURES!!!|Tests run:|OK \(\d+ tests?\)|There (?:was|were) \d+ failure|^\d+\) \w+\(in\.ghartv|AssertionError|'
                       r'Exception:|error:|FAILED|BUILD FAILED|What went wrong|Process completed with exit code|Traceback|assert ', re.I)


def record(sha, code, r, state, summary):
    """Latest CI result for this lane, read by operon's packets so every chat/AI sees it."""
    CI_STATE.mkdir(parents=True, exist_ok=True)
    (CI_STATE / 'latest.md').write_text(
        f"CI {state} · code{code} · commit {sha[:12]} · {time.strftime('%Y-%m-%d %H:%M %Z')}\n"
        f"Run: {r['url']}\n{summary.strip()}\n")


def report_failure(r, sha, code):
    """Save the failure evidence locally and post a short report on the lane's PR (once per run)."""
    run_id = str(r['databaseId'])
    folder = CI_STATE / f'run-{run_id}'
    folder.mkdir(parents=True, exist_ok=True)
    log = run(['gh', 'run', 'view', run_id, '--repo', REPO, '--log-failed'], timeout=300).stdout
    (folder / 'failed.log').write_text(log)
    run(['gh', 'run', 'download', run_id, '--repo', REPO, '-n', f'ghartv-code{code}-owner-review', '-D', folder / 'artifact'], timeout=600)
    jobs = json.loads(run(['gh', 'run', 'view', run_id, '--repo', REPO, '--json', 'jobs']).stdout or '{"jobs":[]}')
    steps = [f"{j['name']} -> {s['name']}" for j in jobs.get('jobs', []) for s in j.get('steps', []) if s.get('conclusion') == 'failure']
    lines = [l.split('\t')[-1].strip() for l in log.splitlines() if KEY_LINES.search(l)]
    proof = folder / 'artifact' / 'proof'
    for name in ('focused.txt', 'full.txt'):
        f = proof / name
        if f.is_file():
            lines += [f'[{name}] ' + l.strip() for l in f.read_text(errors='replace').splitlines() if KEY_LINES.search(l)]
    key = '\n'.join(dict.fromkeys(lines[-40:])) or '(no recognisable failure lines - see the run log)'
    summary = ('Failed step: ' + ('; '.join(steps) or 'unknown') + '\n```\n' + key[-3500:] + '\n```\n'
               f'Evidence saved on the owner\'s Mac: {folder}')
    record(sha, code, r, 'FAILED', summary)
    # Operon event (same format as `operon`'s ~/operon-tools/state/events.jsonl) so every chat/agent sees it.
    events = Path.home() / 'operon-tools/state/events.jsonl'
    events.parent.mkdir(parents=True, exist_ok=True)
    with events.open('a') as f:
        f.write(json.dumps({'ts': time.time(), 'at': time.strftime('%Y-%m-%d %H:%M %Z'), 'kind': 'ci_failed', 'lane': LANE,
                            'repo': REPO, 'run_id': run_id, 'run_url': r['url'], 'commit': sha, 'version_code': code,
                            'failed_steps': steps, 'evidence': str(folder), 'worktree': str(ROOT)}) + '\n')
    marker = f'<!-- operon-ci-failure run {run_id} -->'
    pr = run(['gh', 'pr', 'view', '--repo', REPO, '--json', 'number', '-q', '.number'], timeout=60).stdout.strip() or '1'
    existing = run(['gh', 'api', f'repos/{REPO}/issues/{pr}/comments', '--paginate', '-q', '.[].body'], timeout=120).stdout
    if marker not in existing:
        body = (f'{marker}\nOperon CI report · lane {LANE} · code{code} · commit `{sha[:12]}` · **FAILED** · nothing installed\n'
                f'Run: {r["url"]}\n' + summary)
        subprocess.run(['gh', 'pr', 'comment', pr, '--repo', REPO, '--body-file', '-'], input=body, text=True, capture_output=True)
        print(f'Failure report posted on PR #{pr} and saved in {folder}')
    print('\n' + summary)


def java_env(sdk):
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
    def major(home):
        out = run([Path(home) / 'bin/java', '-version']).stderr if home else ''
        m = re.search(r'version "(\d+)', out)
        return int(m.group(1)) if m else 0
    candidates = [os.environ.get('JAVA_HOME'),
                  run(['/usr/libexec/java_home', '-v', '17+']).stdout.strip() if Path('/usr/libexec/java_home').exists() else '',
                  '/Applications/Android Studio.app/Contents/jbr/Contents/Home']
    for home in candidates:
        if home and Path(home, 'bin/java').exists() and major(home) >= 17:
            env['JAVA_HOME'] = home
            return env
    die('no Java 17+ found (install Android Studio or a JDK 17+)')


def build_local(sdk, sha, code, out_dir):
    if run(['git', '-C', ROOT, 'status', '--porcelain', '--', 'android-tv']).stdout.strip():
        die('android-tv has uncommitted changes; a local candidate must match a commit exactly')
    env = java_env(sdk)
    assets = ROOT / 'android-tv/app/src/main/assets'
    clip = assets / 'ghartv-native-check.mp4'
    if not clip.exists() and shutil.which('ffmpeg'):
        assets.mkdir(parents=True, exist_ok=True)
        run(['ffmpeg', '-nostdin', '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=320x180:rate=24',
             '-t', '30', '-an', '-c:v', 'libx264', '-preset', 'fast', '-crf', '32', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', clip])
    print(f'Building code{code} on this Mac (first build downloads Gradle and libraries; 3-10 minutes)...', flush=True)
    p = subprocess.run(['./gradlew', '--no-daemon', 'assembleRelease'], cwd=ROOT / 'android-tv', env=env, text=True,
                       capture_output=True, timeout=3600)
    if p.returncode:
        CI_STATE.mkdir(parents=True, exist_ok=True)
        (CI_STATE / f'local-build-{sha[:12]}.log').write_text(p.stdout + p.stderr)
        die('local build failed; log saved in ' + str(CI_STATE / f'local-build-{sha[:12]}.log') + '\n' + (p.stdout + p.stderr)[-1500:])
    apk = ROOT / 'android-tv/app/build/outputs/apk/release/app-release-unsigned.apk'
    dest = out_dir / f'GharTV-code{code}-review-unsigned.apk'
    dest.write_bytes(apk.read_bytes())
    return dest


def signing_values():
    env_file, key = SIGNING / 'signing.env', SIGNING / 'ghartv-release.jks'
    if not env_file.is_file() or not key.is_file():
        die('your existing GharTV signing key was not found; no new key is created')
    if env_file.stat().st_mode & 0o077:
        die('signing.env must be private (chmod 600)')
    values = {}
    for line in env_file.read_text().splitlines():
        line = line.strip()
        if line.startswith('export '):
            line = line[7:].strip()
        name, sep, value = line.partition('=')
        name = name.strip()
        if sep and name in KEYS:
            words = shlex.split(value, comments=True, posix=True)
            values[name] = words[0] if words else ''
    if any(not values.get(k) for k in KEYS):
        die('signing.env is missing one of ' + ', '.join(KEYS))
    return values


def sign(unsigned, tools, out_dir):
    v = signing_values()
    env = {k: x for k, x in os.environ.items() if not k.startswith('GHARTV_SIGNING_')}
    env['_GHARTV_STORE_PASSWORD'] = v['GHARTV_SIGNING_STORE_PASSWORD']
    env['_GHARTV_KEY_PASSWORD'] = v['GHARTV_SIGNING_KEY_PASSWORD']
    aligned, signed = out_dir / 'aligned.apk', out_dir / 'signed.apk'
    must([tools / 'zipalign', '-f', '4', unsigned, aligned], 'zipalign')
    p = run([tools / 'apksigner', 'sign', '--ks', v['GHARTV_SIGNING_STORE'], '--ks-key-alias', v['GHARTV_SIGNING_KEY_ALIAS'],
             '--ks-pass', 'env:_GHARTV_STORE_PASSWORD', '--key-pass', 'env:_GHARTV_KEY_PASSWORD',
             '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true',
             '--v4-signing-enabled', 'false', '--out', signed, aligned], env)
    v.clear(); env.clear()
    if p.returncode:
        die('signing with your existing key was rejected (no key was replaced)')
    certs = must([tools / 'apksigner', 'verify', '--print-certs', signed], 'verifying the signature')
    digests = re.findall(r'certificate SHA-256 digest: ([0-9a-f]{64})', certs)
    if CERT not in digests:
        die('the signed APK does not carry the GharTV release certificate; not installing')
    return signed


def emulator(sdk):
    adb = sdk / 'platform-tools/adb'

    def serials():
        found = []
        for line in must([adb, 'devices'], 'adb devices').splitlines()[1:]:
            parts = line.split()
            if len(parts) == 2 and parts[0].startswith('emulator-') and parts[1] == 'device':
                name = run([adb, '-s', parts[0], 'emu', 'avd', 'name']).stdout.splitlines()
                found.append((parts[0], name[0].strip() if name else ''))
        return found

    found = serials()
    if not found:
        print(f'Starting the {AVD} emulator...', flush=True)
        subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', AVD], stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, start_new_session=True)
        for _ in range(90):
            time.sleep(4)
            found = serials()
            if found and run([adb, '-s', found[0][0], 'shell', 'getprop', 'sys.boot_completed']).stdout.strip() == '1':
                break
        else:
            die('the emulator did not finish booting in 6 minutes')
    named = [s for s, n in found if n == AVD]
    if named:
        return adb, named[0]
    if len(found) == 1:
        return adb, found[0][0]
    die('several emulators are running and none is ' + AVD + '; close the extra ones and rerun')


def main():
    if run(['git', '-C', ROOT, 'status', '--porcelain']).stdout.strip():
        print('note: you have uncommitted changes; the candidate is built from the last pushed commit only.')
    code = version_code()
    # The candidate is built from the newest commit that changed the app or its workflow;
    # later commits that only touch tools/docs do not trigger (or need) a new build.
    sha = must(['git', '-C', ROOT, 'log', '-1', '--format=%H', '--', 'android-tv',
                f'.github/workflows/review{code}-candidate.yml'], 'git').strip()
    local = '--local' in sys.argv[1:]
    print(f'GharTV code{code} candidate for commit {sha[:12]}' + (' (local build)' if local else ''), flush=True)
    sdk = find_sdk()
    tools = build_tools(sdk)
    if local:
        print('NOTE: built on this Mac; GitHub tests are not required for this install - test carefully.', flush=True)
        r = {'url': 'local build on ' + os.uname().nodename, 'databaseId': 0}
    else:
        r = wait_for_ci(sha, code)
    with tempfile.TemporaryDirectory(prefix='ghartv-candidate-') as tmp:
        tmp = Path(tmp)
        if local:
            unsigned = build_local(sdk, sha, code, tmp)
        else:
            got = run(['gh', 'run', 'download', str(r['databaseId']), '--repo', REPO, '-n', f'ghartv-code{code}-owner-review', '-D', tmp], timeout=600)
            if got.returncode or not (tmp / 'CANDIDATE.json').exists():
                print('GitHub tests passed but the APK could not be downloaded (artifact storage full?) - building the same commit here.', flush=True)
                unsigned = build_local(sdk, sha, code, tmp)
            else:
                meta = json.loads((tmp / 'CANDIDATE.json').read_text())
                unsigned = tmp / f'GharTV-code{code}-review-unsigned.apk'
                if meta.get('source') != sha or meta.get('version_code') != code or meta.get('package') != PACKAGE:
                    die('the downloaded build is not from this exact commit/version; nothing installed')
                if sha256(unsigned) != meta.get('unsigned_apk_sha256'):
                    die('the downloaded APK does not match the checksum CI recorded; nothing installed')
                print('Download verified: built from this exact commit, checksum matches.', flush=True)
        signed = sign(unsigned, tools, tmp)
        KEEP.mkdir(parents=True, exist_ok=True)
        kept = KEEP / f'GharTV-code{code}-{sha[:12]}-signed.apk'
        kept.write_bytes(signed.read_bytes())
        print(f'Signed with your existing GharTV key: {kept}', flush=True)
    record(sha, code, r, 'INSTALLED-LOCAL-BUILD (GitHub tests not required)' if local else 'INSTALLED', f'Installed on the emulator as {kept.name}; owner testing pending.')
    adb, serial = emulator(sdk)
    p = run([adb, '-s', serial, 'install', '-r', kept], timeout=300)
    if p.returncode or 'Success' not in p.stdout:
        msg = (p.stdout + p.stderr).strip()[-600:]
        if 'UPDATE_INCOMPATIBLE' in msg:
            die('the installed GharTV uses a different signature. Not uninstalling (that would erase your Jio login). Ask Claude.')
        die('install failed: ' + msg)
    run([adb, '-s', serial, 'shell', 'input', 'keyevent', 'KEYCODE_WAKEUP'])
    run([adb, '-s', serial, 'shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity'])
    print(f'\nInstalled GharTV code{code} on {serial} and opened it. Please test:')
    print('  1. Jio guide refreshes; preview starts on a focused channel; Watch Live plays (try 2 channels)')
    print('  2. Discover: press Down from the search bar into the page - the green pointer appears')
    print('  3. Menu -> "Return to page / pointer" brings the pointer back')
    print('If something fails: Menu -> Connection details, take a photo/screenshot and share it with Claude.')


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        sys.exit('\ncancelled - nothing else was changed')
