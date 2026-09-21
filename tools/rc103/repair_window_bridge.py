from pathlib import Path
R=Path(__file__).resolve().parents[2]
p=R/'tools/tv_window.py';s=p.read_text()
a=s.index('def native_window(');b=s.index('\ndef request_window_details',a)
s=s[:a]+'''def verified_probe():
    import hashlib
    root=Path(__file__).resolve().parent/'native'
    executable=root/'GharTVWindowProbe';manifest=root/'window-manifest.json'
    if executable.is_symlink() or manifest.is_symlink() or not executable.is_file() or not manifest.is_file():
        raise ValueError('WINDOW_PROBE_UNAVAILABLE')
    meta=json.loads(manifest.read_text())
    if executable.stat().st_size>4000000 or hashlib.sha256(executable.read_bytes()).hexdigest()!=meta.get('sha256'):
        raise ValueError('WINDOW_PROBE_CHECKSUM_FAILED')
    executable.chmod(0o700)
    return str(executable)


def native_window(pid, run=subprocess.run):
    if sys.platform != 'darwin':
        return {'status':'MAC_ONLY','window_observed':False}
    if not isinstance(pid,int) or pid<=0:
        return {'status':'INVALID_PID','window_observed':False}
    restoration={}
    try:
        executable=verified_probe()
        restoration=restore_minimized_if_authorized(pid)
        checked=run([executable,str(pid)],stdin=subprocess.DEVNULL,capture_output=True,
                    text=True,timeout=5,env={'PATH':'/usr/bin:/bin','LANG':'en_US.UTF-8'})
        value=json.loads(checked.stdout) if checked.returncode==0 else {}
        if value.get('status') not in {'MAC_WINDOW_FRONTMOST_OBSERVED','MAC_WINDOW_ONSCREEN_OBSERVED','MAC_WINDOW_NOT_ONSCREEN','MAC_WINDOW_UNCONFIRMED'}:
            raise ValueError('UNRECOGNIZED_RESULT')
        return {**value,**restoration}
    except (ValueError,OSError,subprocess.TimeoutExpired):
        return {'status':'MAC_WINDOW_QUERY_UNAVAILABLE','window_observed':False,**restoration}

'''+s[b:];p.write_text(s)
p=R/'tools/rc103/test_tv_window.py';s=p.read_text()
s=s.replace("patch.object(m,'restore_minimized_if_authorized',return_value={}):", "patch.object(m,'restore_minimized_if_authorized',return_value={}),patch.object(m,'verified_probe',return_value='/test/probe'):")
p.write_text(s)
p=R/'tools/rc103/package.py';s=p.read_text();s=s.replace("'tools/tv_window.py',","'tools/tv_window.py','tools/native/GharTVWindowProbe','tools/native/window-manifest.json',");p.write_text(s)
p=R/'tools/films/GharTVFilmView.swift';s=p.read_text()
s=s.replace('query.placeholderString = "Search FlixMomo inside GharTV";', 'query.sendsWholeSearchString = true; query.placeholderString = "Search FlixMomo inside GharTV";')
p.write_text(s)
print('NATIVE_WINDOW_PROBE_INTEGRATED')
