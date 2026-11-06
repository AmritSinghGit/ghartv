#!/usr/bin/env python3
"""FlixMomo address tracker, approve-first (owner 6 Nov; CTO decision 13:02).

Reads the FlixMomo home page, looks for a "new address" banner, and when it names a FlixMomo
address that the app does not already know, writes a PROPOSAL for the owner to approve on the
board. It never changes the app, the feed or the reviewed host list: the app keeps allowing only
the hosts in FilmProviderPolicy.java until an approved, reviewed update adds one.

    python3 tools/flixmomo_url_tracker.py                      # fetch the live home page
    python3 tools/flixmomo_url_tracker.py --html saved.html     # read a saved page instead
    TRACKER_PROXY=http://127.0.0.1:8118 python3 ...             # fetch through Tor's HTTP tunnel (once Tor is on dev)

Exit 0 = nothing new, 3 = proposal written, 2 = could not read the page.
"""
import argparse, json, re, subprocess, sys, urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POLICY_REF = 'origin/codex/ghartv-remove-auto-preview:android-tv/app/src/main/java/in/ghartv/nova/FilmProviderPolicy.java'
OUT = Path.home() / 'operon-status/lanes/candidates/ghartv-nova/url-proposals'
# Only a plain flixmomo.<tld> name (optionally www.) can be proposed; lookalikes such as
# flixmomo-login.xyz or flixmomo.bet.evil.com never match.
NAME = re.compile(r'(?<![a-z0-9.-])((?:www\.)?flixmomo\.[a-z]{2,10})(?![a-z0-9-]|\.[a-z])', re.I)


def known_hosts(policy_text):
    m = re.search(r'HOSTS=\{([^}]*)\}', policy_text)
    if not m:
        raise ValueError('HOSTS list not found in FilmProviderPolicy.java')
    return set(re.findall(r'"([^"]+)"', m.group(1)))


def home_url(policy_text):
    m = re.search(r'HOME="(https://[^"]+)"', policy_text)
    return m.group(1) if m else None


def candidates(html, known):
    """FlixMomo host names on the page that the app does not know yet, in page order."""
    text = re.sub(r'<[^>]+>', ' ', html)  # banner text, plus hrefs below
    found = NAME.findall(text) + NAME.findall(' '.join(re.findall(r'href="([^"]+)"', html)))
    seen, out = set(), []
    for host in (h.lower() for h in found):
        bare = host[4:] if host.startswith('www.') else host
        if bare in known or host in known or bare in seen:
            continue
        seen.add(bare)
        out.append(bare)
    return out


def fetch(url, proxy=None, timeout=60):
    handlers = []
    if proxy:
        if proxy.startswith('socks'):
            raise SystemExit('SOCKS needs PySocks; use an HTTP proxy (Tor HTTPTunnelPort) such as http://127.0.0.1:8118')
        handlers.append(urllib.request.ProxyHandler({'https': proxy, 'http': proxy}))
    opener = urllib.request.build_opener(*handlers)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (GharTV address check)'})
    with opener.open(req, timeout=timeout) as r:
        return r.read(2_000_000).decode('utf-8', 'replace'), r.geturl()


def write_proposal(hosts, source_url, final_url, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    data = {'schema': 'ghartv.flixmomo-url-proposal.v1', 'status': 'PROPOSED_OWNER_APPROVAL_NEEDED',
            'proposed_hosts': hosts, 'read_from': source_url, 'page_landed_on': final_url, 'found_at': stamp,
            'what_approval_does': 'adds the host to FilmProviderPolicy HOSTS in a reviewed app update; nothing changes before that'}
    path = out_dir / f'{stamp}-PROPOSAL.json'
    path.write_text(json.dumps(data, indent=2) + '\n')
    return path


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument('--html', help='read this saved page instead of fetching')
    ap.add_argument('--policy', help='FilmProviderPolicy.java path (default: PR #1 branch via git)')
    ap.add_argument('--out', default=str(OUT))
    a = ap.parse_args(argv)
    policy = Path(a.policy).read_text() if a.policy else subprocess.check_output(['git', '-C', str(ROOT), 'show', POLICY_REF], text=True)
    known, url = known_hosts(policy), home_url(policy)
    try:
        html, final = (Path(a.html).read_text(), a.html) if a.html else fetch(url, __import__('os').environ.get('TRACKER_PROXY'))
    except Exception as e:  # network, TLS, blocked: say so, propose nothing
        print(f'could not read {url}: {type(e).__name__}: {e}')
        return 2
    new = candidates(html, known)
    # A redirect to another FlixMomo name is the same signal as a banner.
    landed = re.sub(r'^https?://([^/:]+).*$', r'\1', final).lower() if final.startswith('http') else ''
    landed = landed[4:] if landed.startswith('www.') else landed
    if landed and NAME.fullmatch(landed) and landed not in known and landed not in new:
        new.insert(0, landed)
    if not new:
        print(f'no new FlixMomo address on {final} (known: {", ".join(sorted(known))})')
        return 0
    path = write_proposal(new, url, final, Path(a.out))
    print(f'PROPOSAL (owner approves on the board): {", ".join(new)} -> {path}')
    return 3


if __name__ == '__main__':
    sys.exit(main())
