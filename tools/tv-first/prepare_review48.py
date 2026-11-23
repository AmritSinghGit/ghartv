"""Apply the exact reviewed source delta once on the existing branch, with race guards."""
import base64,gzip,hashlib,json,os,subprocess,tempfile
from pathlib import Path

def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
parts=sorted(Path('tools/tv-first').glob('review48-source.part*'))
if parts:
    assert len(parts)==4
    encoded=''.join(p.read_text() for p in parts)
    assert len(encoded)<=100000
    patch=gzip.decompress(base64.b64decode(encoded,validate=True))
    assert len(patch)==53775 and hashlib.sha256(patch).hexdigest()=='779be2f179deba03b01921db5470ade01daebd095d25d3d4b0607ceab39df6b9'
    manifest=Path('tools/tv-first/review48-files.json');records=json.loads(manifest.read_text());assert len(records)==11
    for item in records:
        name=item['path'];p=Path(name)
        assert not p.is_absolute() and '..' not in p.parts and not any(x.is_symlink() for x in (p,*p.parents))
        assert name.startswith(('android-tv/','tools/tv-first/')) or name=='REVIEW48.md'
        assert (hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None)==item['before'],name+' changed; preserved'
    with tempfile.NamedTemporaryFile() as f:
        f.write(patch);f.flush()
        subprocess.run(['git','apply','--check',f.name],check=True)
        subprocess.run(['git','apply',f.name],check=True)
    for item in records:assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest()==item['after'],item['path']
    paths=[item['path'] for item in records]+[str(p) for p in parts]+[str(manifest)]
    for p in parts:p.unlink()
    manifest.unlink()
    subprocess.run(['python','tools/tv-first/check_contract.py'],check=True)
    subprocess.run(['python','tools/tv-first/test_collect_review_interactions.py'],check=True)
    assert git('ls-remote','origin','refs/heads/codex/ghartv-remove-auto-preview').split()[0]==os.environ['GITHUB_SHA'],'Concurrent work preserved'
    subprocess.run(['git','add','--',*paths],check=True)
    assert set(git('diff','--cached','--name-only').splitlines())==set(paths)
    subprocess.run(['git','config','user.name','GharTV verified source preparation'],check=True)
    subprocess.run(['git','config','user.email','41898282+github-actions[bot]@users.noreply.github.com'],check=True)
    subprocess.run(['git','commit','-m','fix(ghartv): Review48 pointer timing, paired navigation and private interaction journal'],check=True)
    subprocess.run(['git','push','origin','HEAD:refs/heads/codex/ghartv-remove-auto-preview'],check=True)
source=git('rev-parse','HEAD')
with open(os.environ['GITHUB_OUTPUT'],'a') as f:f.write('source_sha='+source+'\n')
print('EXACT_APPLICATION_SOURCE='+source)
