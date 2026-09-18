from pathlib import Path
J=Path('android-tv/app/src/main/java/in/ghartv/nova')
def change(p,old,new):
    s=p.read_text();assert s.count(old)==1,(str(p),old[:100]);p.write_text(s.replace(old,new))
change(Path('tools/owner_review.command.in'),'GHARTV_CYAN_REVIEW_13_HANDOFF','GHARTV_CYAN_REVIEW_14_HANDOFF')
change(Path('tools/owner_review.command.in'),'saved key differs from public RC9','saved key differs from the published signing reference')
change(J/'HeroPreviewController.java','String key=channel==null?"":channel.number+":"+safe(channel.id);','String key=channel==null?"":safe(channel.id);')
change(J/'MainActivity.java','Channel preferred = selectedChannel==null?null:repository.byNumber(visibleChannels, selectedChannel.number);',
'''Channel preferred=null;
        if(selectedChannel!=null)for(Channel candidate:visibleChannels){
            if(java.util.Objects.equals(selectedChannel.id,candidate.id)){preferred=candidate;break;}
        }''')
p=Path('android-tv/app/src/androidTest/java/in/ghartv/nova/PreviewControllerTest.java')
s=p.read_text();at=s.rfind('\n}');assert at>0
s=s[:at]+'''
 @Test public void catalogueRenumberDoesNotRestartSameChannel(){
  ui(()->a.focus("stable-id"));waitFor(PreviewGate.Phase.PLAYING,6000);
  ui(()->{Channel c=new Channel();c.id="stable-id";c.number=999;c.name="Fixture";a.controller.select(c);});
  SystemClock.sleep(800);assertEquals(1,a.calls.get());assertEquals(PreviewGate.Phase.PLAYING,phase());
 }
'''+s[at:];p.write_text(s)
p=Path('tools/tv-first/check_contract.py')
p.write_text(p.read_text()+'''\nassert 'GHARTV_CYAN_REVIEW_14_HANDOFF' in launcher and 'GHARTV_CYAN_REVIEW_13_HANDOFF' not in launcher
assert 'java.util.Objects.equals(selectedChannel.id,candidate.id)' in m
assert 'channel.number+":"+safe(channel.id)' not in c
print('STABLE_CHANNEL_ID_AND_REVIEW14_LABEL=PASS')
''')
print('STABLE_CHANNEL_RENUMBERING_AND_EXACT_HANDOFF_LABEL_FIXED')
