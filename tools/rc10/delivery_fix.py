"""Idempotent delivery repair; no owner machine or signing state is touched."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[2]
p=R/'tools/run_owner_bundle.command.in';s=p.read_text()
a="07578',a6bfd744";b="07578','a6bfd744"
if a in s:s=s.replace(a,b);p.write_text(s)
# Repair the historical integration recipe as well, without rerunning its one-shot work.
p=R/'tools/rc10/provider_fix.py';s=p.read_text()
a='known={\'fdb079207be8a533d5865f5aa8d283729b1b7d633afd6d947f376b9a06207578\',"'
b='known={\'fdb079207be8a533d5865f5aa8d283729b1b7d633afd6d947f376b9a06207578\',\'"'
if a in s:s=s.replace(a,b);p.write_text(s)
p=R/'tools/package_owner_review.py';s=p.read_text()
if "'.woff2'" not in s:
 a="if not p.is_file() or p.is_symlink():continue"
 b="if not p.is_file() or p.is_symlink():continue\n  if p.suffix.lower() in ('.ttf','.otf','.woff','.woff2'):continue"
 assert s.count(a)==1;s=s.replace(a,b);p.write_text(s)
# Syntax-check the bootstrap's actual embedded Python, not merely its shell wrapper.
s=(R/'tools/run_owner_bundle.command.in').read_text().replace('@ARTIFACT_HASHES_REPR@','{}').replace('@COMMAND_SHA@','0'*64)
body=s.split("<<'SEED'\n",1)[1].split('\nSEED\n',1)[0]
compile(body,'run_owner_bundle.command.in:SEED','exec')
for name in ('tools/rc10/provider_fix.py','tools/package_owner_review.py'):
 compile((R/name).read_text(),name,'exec')
print('RC10_1_BOOTSTRAP_EMBEDDED_PYTHON_VALIDATED')
