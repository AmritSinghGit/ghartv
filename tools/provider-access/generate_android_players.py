"""Generate Android's exact embedded copy from the existing tested player normalizer."""
from pathlib import Path
import sys,hashlib
root=Path(__file__).resolve().parents[2]
source=(root/'tools/provider-access/player-options.mjs').read_text()
assert 'export function enumeratePlayers' in source
js='const PlayerOptions=(()=>{\n'+source.replace('export function ','function ')+'\nreturn {identifyPlayer,enumeratePlayers,resolvePlayer};})();\n'
java='package in.ghartv.nova;\n\n// GENERATED from tools/provider-access/player-options.mjs; do not edit separately.\nfinal class FilmPlayerOptions {\n    static final String JS="""\n'+''.join('        '+line.replace('\\','\\\\').replace('"""','\\"\\"\\"')+'\n' for line in js.splitlines())+'        """;\n}\n'
path=root/'android-tv/app/src/main/java/in/ghartv/nova/FilmPlayerOptions.java'
if '--check' in sys.argv:assert path.read_text()==java,'Generated Android normalizer is stale'
else:path.write_text(java)
print('Android player-normalizer SHA256',hashlib.sha256(source.encode()).hexdigest())
