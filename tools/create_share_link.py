#!/usr/bin/env python3
"""Owner-requested public alias. No tokens, tracking option, or private URL."""
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
DESTINATION = 'https://amritsinghgit.github.io/ghartv/'
record = Path('docs/share.json')
if record.exists():
    saved = json.loads(record.read_text())
    if saved.get('destination') == DESTINATION and re.fullmatch(r'https://is\.gd/[A-Za-z0-9_]{5,30}', saved.get('share_url', '')):
        print('GHARTV_SHARE_RECORD=' + json.dumps(saved)); raise SystemExit(0)
for alias in ['GharTVNova', None]:
    params = {'format': 'json', 'url': DESTINATION}
    if alias: params['shorturl'] = alias
    request = urllib.request.Request('https://is.gd/create.php?' + urllib.parse.urlencode(params), headers={'User-Agent': 'GharTV-owner-request/1'})
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            result = json.loads(response.read(8192))
    except urllib.error.HTTPError as error:
        result = json.loads(error.read(8192))
    if result.get('errorcode') == 2 and alias: continue
    if result.get('errorcode'): raise SystemExit('SHORT_LINK_NOT_CREATED: service error ' + str(result['errorcode']))
    short = result.get('shorturl', '')
    if not re.fullmatch(r'https://is\.gd/[A-Za-z0-9_]{5,30}', short): raise SystemExit('SHORT_LINK_RESPONSE_INVALID')
    with urllib.request.urlopen(urllib.request.Request(short, method='HEAD'), timeout=15) as response:
        final = response.geturl()
    if final.rstrip('/') != DESTINATION.rstrip('/'): raise SystemExit('SHORT_LINK_DESTINATION_NOT_VERIFIED')
    print('GHARTV_SHARE_RECORD=' + json.dumps({'share_url': short, 'destination': DESTINATION, 'verified_destination': True, 'statistics_requested': False}))
    break
else:
    raise SystemExit('SHORT_LINK_UNAVAILABLE')
