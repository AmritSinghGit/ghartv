import test from 'node:test';
import assert from 'node:assert/strict';
import {identifyPlayer,enumeratePlayers,resolvePlayer} from './player-options.mjs';
const page='https://flixmomo.bet/watch/movie/fixture';
const button=(text,extra={})=>({text,kind:'button',visible:true,disabled:false,...extra});
const texts=['PLAYER #1 OG','PLAYER #2 4K BEST','PLAYER #3 GOOD','PLAYER #4 4K','PLAYER #5','PLAYER #6','PLAYER #7','PLAYER #8','PLAYER #9 NEW 🔥','PLAYER #10','PLAYER #11','PLAYER #12 4K'];
const controls=texts.map(t=>button(t));
test('all twelve numbered controls survive provider badge text',()=>{
 const r=enumeratePlayers(controls,page);assert.equal(r.detectedCount,12);assert.equal(r.choices[1].label,'Player 2');assert.deepEqual(r.choices[1].badges,['4K','BEST']);assert.equal(r.truncated,false);
});
test('ten and thirteen are not hardcoded as six or twelve',()=>{
 assert.equal(enumeratePlayers(controls.slice(0,10),page).detectedCount,10);
 assert.equal(enumeratePlayers([...controls,button('Player #13')],page).choices.length,13);
});
test('nested unknown badges preserve plain direct label without granting quality claims',()=>{
 const p=identifyPlayer(button('Player #2 EXTRA',{directText:'Player #2',badges:['EXTRA']}));assert.equal(p.id,'player:2');assert.deepEqual(p.badges,['EXTRA']);assert.equal(p.playback,'not-tested');
});
test('unrecognized flat trailing actions do not become players',()=>assert.equal(identifyPlayer(button('Player 2 delete account')),null));
test('hidden or disabled controls are not offered',()=>assert.equal(enumeratePlayers([button('Player 1',{visible:false}),button('Player 2',{disabled:true})],page).detectedCount,0));
test('noninteractive text is not a source button',()=>assert.equal(identifyPlayer(button('Player 1',{kind:'text'})),null));
test('malformed zero negative and huge ids rejected',()=>{for(const x of ['Player 0','Player -1','Player 0001','Player 9999','Player 1foo'])assert.equal(identifyPlayer(button(x)),null,x);});
test('duplicate renderings do not inflate total',()=>assert.equal(enumeratePlayers([button('PLAYER #1'),button('Player 1 GOOD')],page).detectedCount,1));
test('bounded output reports actual detected count and truncation',()=>{const r=enumeratePlayers(controls,page,{limit:4});assert.equal(r.choices.length,4);assert.equal(r.detectedCount,12);assert.equal(r.truncated,true);});
test('scan truncation never masquerades as complete observation',()=>assert.equal(enumeratePlayers(controls,page,{scanLimit:4}).truncated,true));
test('invalid and lookalike origins fail closed',()=>{for(const x of ['http://flixmomo.bet/','https://flixmomo2.app/','https://flixmomo.bet.attacker.invalid/','https://user:pass@flixmomo.bet/','https://flixmomo.bet:8443/'])assert.equal(enumeratePlayers(controls,x).state,'origin-rejected');});
test('only same-document provider links qualify',()=>{
 const r=enumeratePlayers([button('Player 1',{kind:'link',href:'#one'}),button('Player 2',{kind:'link',href:'https://other.invalid/'}),button('Player 3',{kind:'link',href:'/different'}),button('Player 4',{kind:'link',href:'javascript:alert(1)'})],page);assert.equal(r.detectedCount,1);
});
test('reordering and changed quality badge retain stable selection',()=>{
 const r=resolvePlayer({pageUrl:page,id:'player:2'},[button('Player 2 HD'),button('Player 1')],page);assert.equal(r.state,'current-choice');assert.equal(r.choice.number,2);
});
test('navigation and removed choice reject stale activation',()=>{
 assert.equal(resolvePlayer({pageUrl:page,id:'player:2'},controls,page+'/other').state,'stale-page');
 assert.equal(resolvePlayer({pageUrl:page,id:'player:99'},controls,page).state,'choice-not-observed');
});
test('no working count or advertised total is invented',()=>{
 const r=enumeratePlayers(controls,page);assert.equal(r.workingSourceCount,null);assert.equal(r.providerAdvertisedTotal,null);assert(r.choices.every(x=>x.quality==='provider-claim-unverified'));
});
test('bounded labels and caller arguments',()=>{
 assert.equal(identifyPlayer(button('Player 1 '+'X'.repeat(250))),null);
 assert.throws(()=>enumeratePlayers(controls,page,{limit:0}),TypeError);
 assert.throws(()=>enumeratePlayers('wrong',page),TypeError);
});
