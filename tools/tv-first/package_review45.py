"""Reuse the checked Review44 packager for the current45 compiled artefacts.

Packaging only: never runs on the owner Mac and never signs or publishes an APK.
The original signing/runtime functions remain independently AST-checked there.
"""
from pathlib import Path
import re

report=Path('/tmp/ghartv-candidate/proof/android-tests.txt').read_text()
assert re.search(r'OK \(130 tests\)',report), 'Exact130 Android acceptance required'
source=Path(__file__).with_name('package_review44.py').read_text()
assert "'actual_android_tests_passed':122" in source
assert '0.6.0-rc12.5-loading-mouse-review' in source
source=source.replace('44','45').replace('0.6.0-rc12.5-loading-mouse-review','0.6.0-rc12.6-play-control-evidence-review')
source=source.replace("'actual_android_tests_passed':122","'actual_android_tests_passed':130")
source=source.replace("'retained_review43_tests':110","'retained_review44_tests':122")
source=source.replace("'new_review45_tests':12","'new_review45_tests':8")
assert "'new_review45_tests':8" in source
exec(compile(source,'package_review45_expanded.py','exec'),{'__name__':'__main__'})
