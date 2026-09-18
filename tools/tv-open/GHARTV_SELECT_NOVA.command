#!/bin/bash
# Same existing Nova installation; explicit AVD/package/checksum; no APK or public change.
set -u
umask 077
export PYTHONDONTWRITEBYTECODE=1
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"
status=0
python3 - <<'GHARTV_NOVA_PY' || status=$?
"""Select the existing verified Nova installation. Never install or downgrade an APK."""
from __future__ import annotations
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

SOURCE = '59c130abc1283e66607315db916973553164064d'
APK_SHA = 'b4f682c7b118c580b7f66e50555f23b58d4549ae194dfe55db57a836cc4b5467'
BASE_HELPER = '26fa8454f70c82412bdd9b95ec90f527346299c73b4ab04219292fc4546d02aa'
PRIOR_FIX = '3769c964d121e911009549c4805076432d14277b83c3394dacb27ff19151a5eb'
FIXED_HELPER = '4cb785c641a392bf1dc3c9d0487d40de84dd30ff949c6d54de0994d3dd1f7da9'
PATCH_SHA = 'd62411b6dd831a92092acd2dd5ce3ddf56fdc511b8f578e06818b647b8830562'
PATCH_URL = 'https://raw.githubusercontent.com/AmritSinghGit/ghartv/ea7791ea3835a7af2c074e1639ae5467a143daae/tools/tv-open/reopen_transport.txt'
AVD = 'GharTV_Nova_Manual_google_tv_API36'
SERIAL = 'emulator-5580'
PACKAGE = 'in.ghartv.nova'
HOME = Path.home()
STATE = HOME / 'Library/Application Support/GharTV/owner-review'
CURRENT = STATE / 'current'
RUNTIME = STATE / 'runtime-current'
RUN_ID = 'GHARTV-NOVA-SELECT-' + dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + str(os.getpid())
RUN = STATE / 'runs' / RUN_ID

class Hold(RuntimeError):
    pass

def digest(data):
    return hashlib.sha256(data).hexdigest()

def checked(path, limit=None):
    path = Path(path).absolute()
    for part in (path, *path.parents):
        if part.is_symlink():
            raise Hold('SYMLINK_PRESERVED')
    if path.exists():
        stat = path.stat()
        if stat.st_uid != os.getuid():
            raise Hold('OTHER_OWNER_FILE_PRESERVED')
        if limit is not None and (not path.is_file() or stat.st_size > limit):
            raise Hold('UNEXPECTED_FILE_PRESERVED')
    return path

def read(path):
    return json.loads(checked(path, 256000).read_text())

def save(path, data, mode=0o600):
    path = checked(path)
    checked(path.parent).mkdir(parents=True, exist_ok=True, mode=0o700)
    content = data if isinstance(data, bytes) else data.encode() if isinstance(data, str) else (json.dumps(data, indent=2) + '\n').encode()
    fd, temporary = tempfile.mkstemp(prefix='.' + path.name + '.', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            os.fchmod(stream.fileno(), mode)
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)

def validate_receipt(receipt, marker):
    if (receipt.get('review_source') != SOURCE or marker.get('source') != SOURCE
            or receipt.get('version_code') != 24 or receipt.get('signed_apk_sha256') != APK_SHA):
        raise Hold('NOVA_CODE24_RECEIPT_MISMATCH_NO_APK_CHANGED')

def build_helper(old, patch):
    if digest(patch) != PATCH_SHA:
        raise Hold('PATCH_CHECKSUM_FAILED')
    identity = digest(old)
    if identity == FIXED_HELPER:
        return old
    if identity == PRIOR_FIX:
        text = old.decode().replace('record("WAIT_EXPIRED_NO_DUPLICATE")', "record('WAIT_EXPIRED_NO_DUPLICATE')")
        text = text.replace('raise Hold("EMULATOR_STILL_CLOSING_OR_UNRESPONSIVE_NO_DUPLICATE_STARTED")', "raise Hold('EMULATOR_STILL_CLOSING_OR_UNRESPONSIVE_NO_DUPLICATE_STARTED')")
    elif identity == BASE_HELPER:
        text = old.decode()
        first = text.index('def attach_or_start(')
        last = text.index('def installed_identity(', first)
        text = text[:first] + patch.decode() + text[last:]
        text = text.replace("REVISION = 'tv-open-1.0.0'", "REVISION = 'tv-open-1.1.0-port-reopen'")
        text = text.replace("run_id='GHARTV-CYAN-12-OPEN-'", "run_id='GHARTV-TV-OPEN-'")
    else:
        raise Hold('UNKNOWN_LOCAL_OPENER_PRESERVED')
    result = text.encode()
    if digest(result) != FIXED_HELPER:
        raise Hold('RESULTING_HELPER_CHECKSUM_FAILED')
    compile(result, 'tv_local.py', 'exec')
    return result

def collect_emulator_names(helper):
    rows = []
    try:
        adb = helper.sdk() / 'platform-tools/adb'
        devices = helper.devices(helper.call([adb, 'devices'], 5).stdout)
        for serial, status in list(devices.items())[:8]:
            row = {'serial': serial, 'transport': status, 'selected_for_nova': serial == SERIAL}
            if status == 'device':
                try:
                    name = helper.call([adb, '-s', serial, 'emu', 'avd', 'name'], 3).stdout.splitlines()
                    row['avd'] = name[0][:120] if name else 'NOT_CONFIRMED'
                except Exception:
                    row['avd'] = 'NOT_CONFIRMED'
            rows.append(row)
    except Exception:
        rows.append({'inventory': 'UNAVAILABLE'})
    return rows

def patch_and_open(outcome):
    target = RUNTIME / 'tools/tv_local.py'
    receipt = read(CURRENT / 'receipt.json')
    marker = read(RUNTIME / '.ghartv-managed.json')
    validate_receipt(receipt, marker)
    checked(RUN).mkdir(parents=True, exist_ok=False, mode=0o700)
    patchfile = RUN / 'verified-reopen-transport.txt'
    # This is fixed, public source. No tokens, profiles or provider credentials are sent.
    download = subprocess.run(['/usr/bin/curl', '--proto', '=https', '--proto-redir', '=https', '-fLsS',
        '--connect-timeout', '10', '--max-time', '30', '--retry', '1', '--retry-delay', '1',
        PATCH_URL, '-o', str(patchfile)], stdin=subprocess.DEVNULL, capture_output=True, timeout=70)
    if download.returncode:
        raise Hold('PATCH_DOWNLOAD_FAILED_NO_LOCAL_CODE_CHANGED')
    patch = checked(patchfile, 20000).read_bytes()
    if digest(patch) != PATCH_SHA:
        raise Hold('PATCH_CHECKSUM_FAILED')
    with checked(STATE / 'owner-run.lock').open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Hold('ANOTHER_OWNER_ACTION_RUNNING') from None
        validate_receipt(read(CURRENT / 'receipt.json'), read(RUNTIME / '.ghartv-managed.json'))
        old = checked(target, 100000).read_bytes()
        patched = build_helper(old, patch)
        shortcuts = [HOME / '.local/share/ghartv-launcher/current/GHARTV_OPEN_TV.command']
        if (HOME / 'Desktop').is_dir():
            shortcuts.append(HOME / 'Desktop/GharTV TV.command')
        for shortcut in shortcuts:
            if shortcut.exists() and 'GHARTV_PERSISTENT_TV_OPEN_V1' not in checked(shortcut, 16384).read_text():
                raise Hold('UNKNOWN_SHORTCUT_PRESERVED')
        save(RUN / 'tv_local.before.py', old)
        if (RUN / 'tv_local.before.py').read_bytes() != old:
            raise Hold('BACKUP_NOT_VERIFIED')
        save(target, patched)
        if checked(target).read_bytes() != patched:
            save(target, old)
            raise Hold('PATCH_READBACK_FAILED_PRIOR_HELPER_RESTORED')
        shortcut_text = '#!/bin/bash\n# GHARTV_PERSISTENT_TV_OPEN_V1\nset -euo pipefail\numask 077\nexport PYTHONDONTWRITEBYTECODE=1\nexport PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:$PATH"\nprintf "\\nOpening GharTV Nova on emulator-5580, not One Guide on 5554.\\n"\nexec python3 "$HOME/Library/Application Support/GharTV/owner-review/runtime-current/tools/tv_local.py" open\n'
        for shortcut in shortcuts:
            save(shortcut, shortcut_text, 0o700)
        outcome['opener_patch'] = 'EXACT_HELPER_WRITTEN_AND_READBACK_VERIFIED'
        outcome['helper_sha256'] = digest(patched)
    spec = importlib.util.spec_from_file_location('ghartv_selected_nova', target)
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    if (helper.AVD, helper.SERIAL, helper.PACKAGE) != (AVD, SERIAL, PACKAGE):
        raise Hold('OPENER_TARGET_MISMATCH')
    outcome['emulators_observed_before'] = collect_emulator_names(helper)
    print('Opening the existing Nova TV. Other emulators will not be stopped or changed.', flush=True)
    result = helper.main_action('open', SOURCE)
    outcome['open_result'] = result
    valid = (result.get('ok') is True and result.get('foreground') is True
        and result.get('apk_sha256') == APK_SHA and result.get('source') == SOURCE and result.get('version_code') == 24)
    outcome['result'] = 'EXACT_NOVA_CODE24_FOREGROUND_VERIFIED' if valid else 'NOVA_SELECTION_ACTION_REQUIRED'
    return 0 if valid else 1

def main():
    if sys.platform != 'darwin':
        print('MAC_ONLY_NO_ACTION')
        return 1
    os.umask(0o077)
    outcome = {'schema': 'ghartv.nova-selection.v1', 'run_id': RUN_ID,
        'target_avd': AVD, 'target_serial': SERIAL, 'target_package': PACKAGE,
        'expected_version_code': 24, 'expected_source': SOURCE, 'expected_apk_sha256': APK_SHA,
        'legacy_screen_decision': 'REJECTED_ONE_GUIDE_NOT_NOVA_BASELINE',
        'apk_changed': False, 'production_changed': False, 'rollback_activated': False,
        'other_emulators_stopped': False, 'application_data_cleared': False,
        'github_sync': 'NOT_ATTEMPTED_PRIVATE_LOCAL_RESULT', 'playback_verified': False}
    print('\033[36mGharTV Nova · restore the correct local review target\033[0m', flush=True)
    print('Expected: GharTV_Nova_Manual_google_tv_API36:5580 / code 24 / in.ghartv.nova', flush=True)
    status = 1
    try:
        status = patch_and_open(outcome)
    except Exception as error:
        outcome.update(result='NOVA_SELECTION_ACTION_REQUIRED', error=str(error) if isinstance(error, Hold) else type(error).__name__)
    try:
        note_dir = HOME / 'Documents/Amrit Executive Memory/90 System/Operon Portfolio/Handoffs/Terminal Runs/ghartv'
        if checked(note_dir).is_dir():
            note = '# GharTV Nova local selection\n\n```json\n' + json.dumps(outcome, indent=2) + '\n```\nThe rejected One Guide screen is not the Nova development baseline. No household rollback was performed.\n'
            save(note_dir / (RUN_ID + '.md'), note)
            outcome['obsidian'] = 'RUN_NOTE_WRITTEN_AND_READBACK_VERIFIED' if (note_dir / (RUN_ID + '.md')).read_text() == note else 'UNVERIFIED'
        else:
            outcome['obsidian'] = 'EXISTING_NOTE_DIRECTORY_NOT_FOUND'
        save(RUN / 'NOVA_SELECTION.json', outcome)
        save(CURRENT / 'nova-selection.json', outcome)
        text = 'GHARTV_NOVA_SELECTION_HANDOFF\n' + json.dumps(outcome, indent=2) + '\n'
        save(RUN / 'handoff.txt', text)
        save(CURRENT / 'nova-selection-handoff.txt', text)
    except Exception as error:
        outcome['record_error'] = type(error).__name__
    print('GHARTV_NOVA_SELECTION_HANDOFF\n' + json.dumps(outcome, indent=2), flush=True)
    return status

if __name__ == '__main__':
    sys.exit(main())

GHARTV_NOVA_PY
if [[ -t 0 ]]; then
 printf '\nPress Enter to copy this handoff: '
 read -r _ || true
 receipt="$HOME/Library/Application Support/GharTV/owner-review/current/nova-selection-handoff.txt"
 [[ -f "$receipt" ]] && /usr/bin/pbcopy < "$receipt"
 printf 'Press Enter to finish (Nova stays open): '
 read -r _ || true
fi
exit "$status"
