from pathlib import Path
import hashlib,json,subprocess,sys,zipfile
R=Path(__file__).resolve().parents[2];out=Path(sys.argv[1]);source=sys.argv[2];out.mkdir(parents=True,exist_ok=True)
root=out/'GHARTV_TV_WINDOW_R1';root.mkdir(exist_ok=True)
for name in ('open_tv.py','DisplayProbe'):(root/name).write_bytes((R/'tools/tv-window-r1'/name).read_bytes())
sync=(R/'web-player/review-sync.mjs').read_text()
needle="    web_source:/^[a-f0-9]{40}$/"
assert sync.count(needle)==1
sync=sync.replace(needle,"    tv_window_transport:['EMULATOR_UI','SCRCPY_EXISTING_NOVA','NOT_OPENED'].includes(r.tv_window_transport)?r.tv_window_transport:'NOT_RECORDED',\n    display_frame_received:Array.isArray(r.display_frame_dimensions)&&r.display_frame_dimensions.length===2,\n    apk_changed:r.apk_changed===true,\n    emulator_restarted:r.emulator_restarted===true,\n    recovery_source:/^[a-f0-9]{40}$/.test(r.recovery_source||'')?r.recovery_source:null,\n"+needle)
(root/'window-receipt-sync.mjs').write_text(sync)
(root/'README.md').write_text('''# GharTV TV-window recovery R1

This opens the SAME already-installed RC10.3.1/code30 candidate. No signing, APK installation, application rebuild, production publication or other-lane shutdown.

The prior AppKit probe returned MAC_WINDOW_UNCONFIRMED before querying windows when QEMU had no registered NSRunningApplication. The new probe queries window metadata independently of registration. If the real Nova GUI is not frontmost/visible, the recovery opens one local scrcpy view of emulator-5580's existing display, with keyboard controls. It is not a second Android VM, web reproduction or new virtual display.

The official portable scrcpy4.1 Apple Silicon archive is downloaded directly from Genymobile/scrcpy and checked against its pinned upstream SHA256. No Homebrew/global installation or macOS protection bypass. A temporary scrcpy server helper runs on the selected Android device and cleans up when the view closes. Display/keyboard and device playback audio are local. No microphone capture, recording, automatic clipboard sync, provider credentials or screenshot uploads. DRM-protected surfaces may be black; no protection is removed.

Use arrow keys/Enter; right-click or Option+B is Back. Close the window to stop only the display connection and leave Nova running. Avoid dragging files to the mirror, which is an optional scrcpy feature, not part of this recovery.

An already-running exact Nova is reused even under memory pressure. If it stopped since the last receipt, only the existing named AVD may be started and only after normal resource admission. Unknown processes, ports, sources, signatures or changed helper files are preserved and cause a hold. No reset, uninstall, data clear, forced restart, port substitution or other-lane cleanup.

Source and signed APK are tied to the owner's successful code30 run. Installation receipts are preserved in the new recovery run before a new observation replaces current state. The same private Obsidian note/light handoff and safe GitHub receipt are updated; the public receipt explicitly distinguishes native emulator UI from a local mirrored review. No claim of owner acceptance, physical-TV playback, provider-stream playback or verified memory-replica sync.

Read VALIDATION.json for actual cloud-test scope. Apple Silicon executable/probe tests and Linux real-Android mirror tests are not an owner-Mac execution. No film code, approved public homepage, analytics lane or household update feed is changed.
''')
files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in root.iterdir() if p.is_file()}
(root/'PACKAGE.json').write_text(json.dumps({'schema':'ghartv.tv-window-recovery.v1','source':source,'application_source':'ae4c84578e26e707d1a11c5ff078f6395cf36dde','version_code':30,'files':files},indent=2)+'\n')
zipout=out/'GHARTV_TV_WINDOW_R1.zip'
with zipfile.ZipFile(zipout,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(root.iterdir()):z.write(p,'GHARTV_TV_WINDOW_R1/'+p.name)
h=hashlib.sha256(zipout.read_bytes()).hexdigest()
entry='''#!/bin/bash
set -euo pipefail
umask 077
d="$(mktemp -d "${TMPDIR:-/tmp}/ghartv-tv-window.XXXXXX")"
cleanup(){ cd /; rm -rf -- "$d"; }
trap cleanup EXIT
printf '\\nGharTV: open the existing code30 TV display. No reinstall.\\n'
curl --proto '=https' --proto-redir '=https' -fL --show-error --connect-timeout 20 --max-time 180 --retry 2 'https://github.com/AmritSinghGit/ghartv/releases/download/v0.6.0-rc10.3.1-tv-window-r1/GHARTV_TV_WINDOW_R1.zip' -o "$d/review.zip"
printf '%s  %s\\n' '''+"'"+h+"'"+''' "$d/review.zip" | shasum -a 256 -c -
unzip -q "$d/review.zip" -d "$d"
python3 "$d/GHARTV_TV_WINDOW_R1/open_tv.py"
'''
cmd=out/'OPEN_GHARTV_TV_WINDOW.command';cmd.write_text(entry);subprocess.run(['bash','-n',str(cmd)],check=True)
(out/'DELIVERY.json').write_text(json.dumps({'schema':'ghartv.tv-window-delivery.v1','source':source,'application_source':'ae4c84578e26e707d1a11c5ff078f6395cf36dde','android_code':30,'apk_rebuilt':False,'apk_installed_by_recovery':False,'bundle_sha256':h,'bundle_bytes':zipout.stat().st_size,'entry_sha256':hashlib.sha256(cmd.read_bytes()).hexdigest(),'upstream_scrcpy':'v4.1','upstream_archive_sha256':'20fd47c9014dd5e0fa77091f3cb7adbda8445a360c4584aeaa0150b5b3988ff3'},indent=2)+'\n')
print((out/'DELIVERY.json').read_text())
