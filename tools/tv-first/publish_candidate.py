#!/usr/bin/env python3
"""Publish the exact GharTV candidate you tested on the emulator to the household update channel - or roll back.

    python3 tools/tv-first/publish_candidate.py              # dry run: checks everything, changes nothing
    python3 tools/tv-first/publish_candidate.py --go         # publish
    python3 tools/tv-first/publish_candidate.py --rollback   # point the update channel back to the previous version

Publishing = (1) a GitHub release holding the exact signed APK you installed and tested (no rebuild),
(2) update/latest.json on main pointing TVs at it. TVs offer the update; nothing installs silently.

Gates: the APK must be the one install_candidate.py signed for this commit and carry the GharTV certificate;
GitHub's Review tests must have passed for the same app source (override only with --without-ci);
the live channel must still be on an older version. Before changing the channel, the current channel is saved
(locally and inside the release notes) so --rollback can restore it.

Rollback restores the previous channel immediately, so no more TVs take this update. Android never
downgrades an installed app: TVs that already updated keep it until a newer fixed version is published.
"""
import base64, json, re, sys, tempfile, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import install_candidate as ic  # same folder: shared checks (SDK, signing certificate, logging)

REPO, ROOT = ic.REPO, ic.ROOT
CONTROL = Path.home() / 'Library/Application Support/GharTV/owner-review/release-control'
FEED = 'update/latest.json'


def gh_json(args, what):
    return json.loads(ic.must(['gh'] + args, what) or 'null')


def live_feed():
    j = gh_json(['api', f'repos/{REPO}/contents/{FEED}?ref=main'], 'reading the live update channel')
    return j['sha'], json.loads(base64.b64decode(j['content']).decode())


def put_feed(feed, blob, message):
    body = {'message': message, 'branch': 'main', 'sha': blob,
            'content': base64.b64encode((json.dumps(feed, indent=2) + '\n').encode()).decode()}
    with tempfile.NamedTemporaryFile('w', suffix='.json', delete=False) as f:
        json.dump(body, f)
    out = gh_json(['api', '-X', 'PUT', f'repos/{REPO}/contents/{FEED}', '--input', f.name], 'updating the update channel')
    Path(f.name).unlink(missing_ok=True)
    _, seen = live_feed()
    if any(seen.get(k) != feed[k] for k in ('versionCode', 'sha256', 'apkUrl', 'sourceCommit')):
        ic.die('the update channel did not read back as written - check update/latest.json on GitHub')
    return out.get('commit', {}).get('sha', '')


def pr_comment(text):
    ic.run(['gh', 'pr', 'comment', '1', '--repo', REPO, '--body', text], timeout=60)


def version_name():
    m = re.search(r'versionName = "([^"]+)"', (ROOT / 'android-tv/app/build.gradle.kts').read_text())
    return m.group(1)


def ci_passed(sha, code):
    """A Review<code> run whose app source (android-tv tree) equals this commit's, with the test step green."""
    tree = ic.must(['git', '-C', ROOT, 'rev-parse', f'{sha}:android-tv'], 'git').strip()
    runs = gh_json(['run', 'list', '--repo', REPO, '--branch', ic.run(['git', '-C', ROOT, 'branch', '--show-current']).stdout.strip(),
                    '--json', 'databaseId,headSha,conclusion,status,url,workflowName', '-L', '40'], 'reading GitHub Actions')
    for r in runs:
        if r['workflowName'] != f'GharTV Review{code} candidate' or r['status'] != 'completed':
            continue
        if ic.run(['git', '-C', ROOT, 'rev-parse', f"{r['headSha']}:android-tv"]).stdout.strip() != tree:
            continue
        jobs = gh_json(['run', 'view', str(r['databaseId']), '--repo', REPO, '--json', 'jobs'], 'reading the test run')
        steps = {s['name']: s.get('conclusion') for j in jobs['jobs'] for s in j.get('steps', [])}
        ok = steps.get('Run focused and complete deterministic suites') == 'success'
        if not ok:  # keep the evidence where Claude/operon can read it - no copy-pasting
            ic.CI_STATE.mkdir(parents=True, exist_ok=True)
            log = ic.CI_STATE / f"run-{r['databaseId']}-failed.log"
            log.write_text(ic.run(['gh', 'run', 'view', str(r['databaseId']), '--repo', REPO, '--log-failed'], timeout=300).stdout)
            failed = [n for n, c in steps.items() if c == 'failure']
            ic.remember(f"publish check: tests not passed in run {r['databaseId']} (failed steps: {failed}); log {log}")
            return False, r['url'] + f" (failed: {', '.join(failed) or 'unknown'}; log saved for Claude)"
        return ok, r['url']
    return False, 'no completed GitHub test run for this app source yet'


def publish(go, without_ci):
    code, name = ic.version_code(), version_name()
    sha = ic.must(['git', '-C', ROOT, 'log', '-1', '--format=%H', '--', 'android-tv',
                   f'.github/workflows/review{code}-candidate.yml'], 'git').strip()
    apk = ic.KEEP / f'GharTV-code{code}-{sha[:12]}-signed.apk'
    print(f'GharTV code{code} ({name}) from commit {sha[:12]}')
    if not apk.is_file():
        ic.die(f'no tested signed APK for this commit at {apk} - run install_candidate.py first and test it')
    tools = ic.build_tools(ic.find_sdk())
    certs = ic.must([tools / 'apksigner', 'verify', '--print-certs', apk], 'verifying the APK signature')
    if ic.CERT not in certs:
        ic.die('the APK does not carry the GharTV release certificate')
    digest = ic.sha256(apk)
    print(f'  APK: {apk.name} sha256 {digest[:16]}... GharTV certificate OK')
    ok, where = ci_passed(sha, code)
    print(f"  GitHub tests: {'passed' if ok else 'NOT passed'} - {where}")
    if not ok and not without_ci:
        ic.die('GitHub tests have not passed for this app source. Wait for the run, or use --without-ci if you accept the risk.')
    blob, cur = live_feed()
    print(f"  Live channel now: code{cur['versionCode']} ({cur['versionName']})")
    if cur['versionCode'] >= code:
        ic.die(f"the live channel is already at code{cur['versionCode']}; nothing to do")
    tag = f'v{name}'
    asset = f'GharTV-code{code}-household-update.apk'
    if not go:
        print(f'\nDry run only. --go would: create release {tag} with {asset} (exact tested APK, no rebuild),\n'
              f"then point the update channel from code{cur['versionCode']} to code{code}. Rollback: --rollback")
        return
    CONTROL.mkdir(parents=True, exist_ok=True)
    saved = CONTROL / f"channel-before-code{code}.json"
    saved.write_text(json.dumps(cur, indent=2) + '\n')
    notes = (f'GharTV code{code} ({name}) for household update. Exact APK tested on the Nova emulator; no rebuild.\n'
             f'Source {sha}. SHA-256 {digest}. GitHub tests: {where}\n\n'
             f'Fixes: Jio live/preview timeouts, DNS recovery budget, Discover green pointer.\n\n'
             f'Previous update channel (used by publish_candidate.py --rollback):\n```json\n{json.dumps(cur, indent=2)}\n```\n')
    with tempfile.TemporaryDirectory() as td:
        staged = Path(td) / asset
        staged.write_bytes(apk.read_bytes())
        nf = Path(td) / 'notes.md'
        nf.write_text(notes)
        exists = ic.run(['gh', 'release', 'view', tag, '--repo', REPO, '--json', 'assets'])
        if exists.returncode:
            ic.must(['gh', 'release', 'create', tag, staged, '--repo', REPO, '--target', sha, '--title', f'GharTV code{code}',
                     '--notes-file', nf, '--prerelease'], 'creating the GitHub release')
        else:
            names = [a['name'] for a in json.loads(exists.stdout)['assets']]
            if asset not in names:
                ic.must(['gh', 'release', 'upload', tag, staged, '--repo', REPO], 'uploading the APK')
        back = Path(td) / 'readback'
        ic.must(['gh', 'release', 'download', tag, '--repo', REPO, '--pattern', asset, '--dir', back], 'reading the APK back')
        if ic.sha256(back / asset) != digest:
            ic.die('the APK on GitHub differs from the tested one; the update channel was NOT changed')
    print('  Release uploaded and read back: identical to the tested APK.')
    feed = {'versionCode': code, 'versionName': name,
            'apkUrl': f'https://github.com/{REPO}/releases/download/{tag}/{asset}', 'sha256': digest,
            'sourceCommit': sha, 'artifactTag': tag, 'channel': 'production',
            'publishedAt': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'ownerDecision': f'OWNER_TESTED_ON_EMULATOR_AND_APPROVED_CODE{code}', 'distributionMode': 'exact-tested-signed-apk-no-rebuild',
            'notes': 'Jio live and preview loading fixed; Discover pointer is back. Install over the existing app to keep your settings.',
            'mandatoryUpdateSupported': False, 'silentInstallSupported': False,
            'previousVersionCode': cur['versionCode'], 'previousArtifactSha256': cur['sha256']}
    commit = put_feed(feed, blob, f'release: publish owner-tested exact code{code} APK; no rebuild')
    (CONTROL / f'published-code{code}.json').write_text(json.dumps({'feed': feed, 'commit': commit}, indent=2) + '\n')
    ic.run(['gh', 'release', 'edit', tag, '--repo', REPO, '--prerelease=false', '--latest'])
    pr_comment(f'Published GharTV code{code} to the household update channel (was code{cur["versionCode"]}). '
               f'Exact tested APK sha256 `{digest}`, source `{sha}`, feed commit `{commit[:12]}`. '
               f'Rollback: `python3 tools/tv-first/publish_candidate.py --rollback`.')
    ic.remember(f'published code{code} sha256 {digest} feed commit {commit}')
    print(f"\nPublished. TVs on code{cur['versionCode']} will offer code{code} at their next update check.")
    print('If anything goes wrong: python3 tools/tv-first/publish_candidate.py --rollback')


def rollback():
    blob, cur = live_feed()
    saved = CONTROL / f"channel-before-code{cur['versionCode']}.json"
    if saved.is_file():
        prev = json.loads(saved.read_text())
    else:
        body = gh_json(['release', 'view', cur['artifactTag'], '--repo', REPO, '--json', 'body'], 'reading the release notes')['body']
        m = re.search(r'Previous update channel.*?```json\n(.*?)\n```', body, re.S)
        if not m:
            ic.die('no saved previous channel found for code%s' % cur['versionCode'])
        prev = json.loads(m.group(1))
    print(f"Rolling the update channel back: code{cur['versionCode']} -> code{prev['versionCode']} ({prev['versionName']})")
    prev = dict(prev, publishedAt=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
                ownerDecision=f"OWNER_ROLLBACK_FROM_CODE{cur['versionCode']}")
    commit = put_feed(prev, blob, f"release: roll back update channel to code{prev['versionCode']} (owner request)")
    pr_comment(f"Rolled the household update channel back from code{cur['versionCode']} to code{prev['versionCode']} "
               f"(feed commit `{commit[:12]}`). The code{cur['versionCode']} release is kept. TVs that already updated keep "
               f"code{cur['versionCode']} until a newer fixed version is published.")
    ic.remember(f"rolled back channel code{cur['versionCode']} -> code{prev['versionCode']} feed commit {commit}")
    print('Done. No more TVs will take the rolled-back version. TVs that already updated keep it until a newer fix is published.')


if __name__ == '__main__':
    args = sys.argv[1:]
    if args and args[0] == '--rollback':
        rollback()
    else:
        publish('--go' in args, '--without-ci' in args)
