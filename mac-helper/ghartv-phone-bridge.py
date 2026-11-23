#!/usr/bin/env python3
"""Mac LAN companion for the Android emulator's local QR phone remote.

No cloud services, no stream interception. Runs until Ctrl-C in Terminal.
Pairs only with the original in.ghartv.nova APK through ADB forwarded localhost.
The phone still needs the app's random one-time pairing key.
"""
import ipaddress
import os
import re
import select
import socket
import socketserver
import subprocess
import sys
import threading

REG_PORT=18672
LAN_PORT=18673
ADB_PORT=18674
ANDROID_SERVER_HOST="127.0.0.1"
LOCK=threading.Lock()
ACTIVE={"port":None,"forward":False}

def mac_lan_ip():
    for iface in ("en0","en1"):
        r=subprocess.run(["ipconfig","getifaddr",iface],capture_output=True,text=True)
        ip=r.stdout.strip()
        try:
            if r.returncode==0 and ipaddress.ip_address(ip).is_private:
                return ip
        except ValueError:
            pass
    raise RuntimeError("No private Wi-Fi/Ethernet address found")

def run_adb(*args):
    return subprocess.run(["adb","-s","emulator-5554",*args],capture_output=True,text=True,timeout=12)

def register(port):
    if port<1024 or port>65535:raise ValueError("invalid Android port")
    with LOCK:
        current=run_adb("forward","--list")
        if current.returncode:raise RuntimeError("ADB forward list unavailable")
        entries=[x.split() for x in current.stdout.splitlines()]
        existing=[x for x in entries if len(x)>=3 and x[1]==f"tcp:{ADB_PORT}"]
        # The emulator may reboot while the Mac companion stays alive.
        # ADB silently drops its forward, invalidating our in-memory state.
        # Reconcile observed state before removing/replacing the forward.
        if ACTIVE["forward"] and not existing:
            ACTIVE.update(port=None,forward=False)
        if existing and not ACTIVE["forward"]:
            raise RuntimeError("ADB port already belongs to a different process")
        if ACTIVE["forward"] and ACTIVE["port"]!=port:
            removed=run_adb("forward","--remove",f"tcp:{ADB_PORT}")
            if removed.returncode:raise RuntimeError("could not replace the prior GharTV forward")
        if ACTIVE["port"]!=port or not ACTIVE["forward"]:
            result=run_adb("forward",f"tcp:{ADB_PORT}",f"tcp:{port}")
            if result.returncode:raise RuntimeError("adb forward failed")
            ACTIVE.update(port=port,forward=True)
    return True

class ReuseServer(socketserver.ThreadingTCPServer):
    allow_reuse_address=True
    daemon_threads=True

class Registration(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(3)
        try:
            line=self.request.recv(120)
            m=re.fullmatch(rb"PAIR ([0-9]{4,5})\n",line)
            if not m:return
            port=int(m.group(1))
            register(port)
            self.request.sendall(f"OK http://{LAN_IP}:{LAN_PORT}\n".encode("ascii"))
        except (ValueError,RuntimeError,OSError,subprocess.TimeoutExpired):
            try:self.request.sendall(b"UNAVAILABLE\n")
            except OSError:pass

def read_header(client):
    buf=b""
    while b"\r\n\r\n" not in buf and len(buf)<16384:
        chunk=client.recv(4096)
        if not chunk:raise OSError("empty")
        buf+=chunk
    if b"\r\n\r\n" not in buf:raise OSError("invalid header")
    head,remain=buf.split(b"\r\n\r\n",1)
    return head,remain

class Proxy(socketserver.BaseRequestHandler):
    def handle(self):
        with LOCK:
            port=ACTIVE["port"]
            connected=ACTIVE["forward"]
        if not connected or not port:return
        try:
            clientip=ipaddress.ip_address(self.client_address[0])
            if not clientip.is_private:return
        except ValueError:return
        sock=self.request
        sock.settimeout(10)
        try:
            head,remainder=read_header(sock)
            parts=head.decode("latin1").split("\r\n")
            if not parts[0].startswith("GET ") or " HTTP/1." not in parts[0]:return
            lines=[parts[0]]
            for entry in parts[1:]:
                if entry.lower().startswith("host:"):
                    lines.append(f"Host: {ANDROID_SERVER_HOST}:{port}")
                elif entry.lower().startswith("origin:"):
                    lines.append(f"Origin: http://{ANDROID_SERVER_HOST}:{port}")
                else:lines.append(entry)
            with socket.create_connection(("127.0.0.1",ADB_PORT),timeout=7) as upstream:
                upstream.sendall(("\r\n".join(lines)+"\r\n\r\n").encode("latin1")+remainder)
                sock.settimeout(None);upstream.settimeout(None)
                channels=(sock,upstream)
                while True:
                    readable,_,_=select.select(channels,[],[],600)
                    if not readable:break
                    for side in readable:
                        data=side.recv(65536)
                        if not data:return
                        (upstream if side is sock else sock).sendall(data)
        except (OSError,ValueError):
            return

if __name__=="__main__":
    LAN_IP=mac_lan_ip()
    control=ReuseServer(("127.0.0.1",REG_PORT),Registration)
    relay=ReuseServer((LAN_IP,LAN_PORT),Proxy)
    print("GharTV phone relay ready on the Mac's private LAN.",flush=True)
    print("Android emulator: open GharTV → FlixMomo → Phone.",flush=True)
    print("The TV will display a QR code; scan it on a phone on the same Wi-Fi.",flush=True)
    print("Neither pairing keys nor phone keystrokes are logged. Press Ctrl-C to stop.",flush=True)
    threading.Thread(target=control.serve_forever,daemon=True).start()
    try:
        relay.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        relay.shutdown();control.shutdown()
        with LOCK:
            if ACTIVE["forward"]:run_adb("forward","--remove",f"tcp:{ADB_PORT}")
