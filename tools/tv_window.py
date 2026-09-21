"""Bring forward the named, already-running Nova window. Never start or stop a VM.
Only bounded window metadata is read; no screenshots, window titles or prompts.
"""
from __future__ import annotations
import ctypes
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
AVD = 'GharTV_Nova_Manual_google_tv_API36'


def classify_processes(rows):
    targets = []
    for pid, command in rows:
        try:
            words = shlex.split(command)
        except ValueError:
            continue
        if not words or not isinstance(pid, int) or pid <= 0:
            continue
        executable = Path(words[0]).name
        if not executable.startswith('qemu-system-'):
            continue
        avds = [words[i + 1] for i, word in enumerate(words[:-1]) if word == '-avd']
        avds += [word[1:] for word in words if word.startswith('@')]
        if avds != [AVD]:
            continue
        targets.append((pid, words))
    if len(targets) != 1:
        return {'status': 'MAC_TARGET_NOT_UNIQUE', 'window_observed': False}
    pid, words = targets[0]
    if '-qt-hide-window' in words:
        return {'status': 'ANDROID_STUDIO_EMBEDDED_WINDOW', 'window_observed': False}
    if '-no-window' in words or ('-qt-platform' in words and any(x in words for x in ('offscreen', 'minimal'))):
        return {'status': 'HEADLESS_EMULATOR_PRESERVED', 'window_observed': False}
    return {'status': 'TARGET_SELECTED', 'pid': pid, 'window_observed': False}


def restore_minimized_if_authorized(pid):
    """Use Accessibility only when already granted; never trigger a permission prompt."""
    result = {'accessibility_already_authorized': False, 'minimized_restore_requests': 0}
    if sys.platform != 'darwin':
        return result
    ax = cf = None
    refs = []
    try:
        ax = ctypes.CDLL('/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices')
        cf = ctypes.CDLL('/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation')
        ax.AXIsProcessTrusted.restype = ctypes.c_bool
        if not ax.AXIsProcessTrusted():
            return result
        result['accessibility_already_authorized'] = True
        ax.AXUIElementCreateApplication.argtypes = [ctypes.c_int]
        ax.AXUIElementCreateApplication.restype = ctypes.c_void_p
        ax.AXUIElementSetMessagingTimeout.argtypes = [ctypes.c_void_p, ctypes.c_float]
        ax.AXUIElementCopyAttributeValue.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p)]
        ax.AXUIElementSetAttributeValue.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
        ax.AXUIElementPerformAction.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
        cf.CFStringCreateWithCString.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_uint32]
        cf.CFStringCreateWithCString.restype = ctypes.c_void_p
        cf.CFArrayGetCount.argtypes = [ctypes.c_void_p]
        cf.CFArrayGetCount.restype = ctypes.c_long
        cf.CFArrayGetValueAtIndex.argtypes = [ctypes.c_void_p, ctypes.c_long]
        cf.CFArrayGetValueAtIndex.restype = ctypes.c_void_p
        cf.CFRelease.argtypes = [ctypes.c_void_p]
        app = ax.AXUIElementCreateApplication(pid)
        if not app:
            return result
        refs.append(app)
        ax.AXUIElementSetMessagingTimeout(app, 0.25)
        def key(text):
            value = cf.CFStringCreateWithCString(None, text.encode(), 0x08000100)
            refs.append(value)
            return value
        true_value=ctypes.c_void_p.in_dll(cf,'kCFBooleanTrue')
        result['frontmost_request_accepted']=ax.AXUIElementSetAttributeValue(app,key('AXFrontmost'),true_value)==0
        windows_key, minimized_key, raise_key = key('AXWindows'), key('AXMinimized'), key('AXRaise')
        windows = ctypes.c_void_p()
        if ax.AXUIElementCopyAttributeValue(app, windows_key, ctypes.byref(windows)) != 0 or not windows.value:
            return result
        refs.append(windows.value)
        false_value = ctypes.c_void_p.in_dll(cf, 'kCFBooleanFalse')
        for i in range(min(cf.CFArrayGetCount(windows), 4)):
            window = cf.CFArrayGetValueAtIndex(windows, i)
            if ax.AXUIElementSetAttributeValue(window, minimized_key, false_value) == 0:
                result['minimized_restore_requests'] += 1
            ax.AXUIElementPerformAction(window, raise_key)
    except (OSError, AttributeError, ValueError):
        result['accessibility_operation'] = 'UNAVAILABLE_NO_PERMISSION_CHANGE'
    finally:
        if cf is not None:
            for ref in reversed(refs):
                if ref:
                    cf.CFRelease(ref)
    return result


def verified_probe():
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


def request_window_details(rows, run=subprocess.run):
    chosen = classify_processes(rows)
    if chosen['status'] != 'TARGET_SELECTED':
        return chosen
    pid = chosen['pid']
    try:
        check = run(['/bin/ps', '-p', str(pid), '-o', 'uid=,command='],
                    capture_output=True, text=True, timeout=3)
        parts = check.stdout.strip().split(None, 1)
        import os
        if check.returncode or len(parts) != 2 or parts[0] != str(os.getuid()) or classify_processes([(pid,parts[1])])['status'] != 'TARGET_SELECTED':
            return {'status': 'MAC_TARGET_CHANGED_PRESERVED', 'window_observed': False}
        result = native_window(pid,run)
        return result
    except (OSError,subprocess.TimeoutExpired):
        return {'status': 'MAC_TARGET_UNCONFIRMED', 'window_observed': False}
