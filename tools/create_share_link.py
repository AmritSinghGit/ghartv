#!/usr/bin/env python3
"""Read the already-created owner sharing record; never create duplicate aliases."""
import json
from pathlib import Path
record=json.loads(Path('docs/share.json').read_text())
assert record['destination']=='https://amritsinghgit.github.io/ghartv/'
assert record['share_url']=='https://tinyurl.com/2yju9h2t'
print('GHARTV_SHARE_RECORD='+json.dumps(record))
