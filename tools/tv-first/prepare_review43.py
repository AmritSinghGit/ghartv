#!/usr/bin/env python3
"""Idempotent follow-up to the exact Review43 preparation already in Git history.
The original performance expansion is commit dc429095356b56cf25087e990efcfe4b15543c0b.
This preserves the immediate placeholder expected by retained UI tests; artwork
requests still wait for layout/viewport. No native-media or live-TV change.
"""
from pathlib import Path
import hashlib
ROOT=Path(__file__).resolve().parents[2]

def prepare():
    assert 'versionCode = 43' in (ROOT/'android-tv/app/build.gradle.kts').read_text()
    name='android-tv/app/src/main/java/in/ghartv/nova/FilmHomeView.java'
    p=ROOT/name;raw=p.read_bytes();text=raw.decode()
    inserted='image.setImageDrawable(new ColorDrawable(TvUi.SURFACE_2));'
    if inserted in text:return []
    blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
    assert blob=='cf04612c8862940b98cd38e219abfc678fb2186e','Concurrent home source preserved'
    old='ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);'
    assert text.count(old)==1
    text=text.replace(old,'ImageView image=new ImageView(activity);'+inserted+'image.setScaleType(ImageView.ScaleType.CENTER_CROP);')
    p.write_text(text)
    return [name]

if __name__=='__main__':
    import json
    print(json.dumps(prepare()))
