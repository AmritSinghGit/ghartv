import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createContext,runInContext} from 'node:vm';
import {test} from 'node:test';

const root=resolve(fileURLToPath(new URL('.',import.meta.url)),'..');
const html=readFileSync(resolve(root,'android-tv/app/src/main/assets/phone/index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
assert.ok(script,'Phone remote script exists');

function setup(){
 const elements=new Map(),actions=new Map(),keys=new Map();
 const make=id=>{
   const listeners=new Map();
   const flags=new Set();
   return {id,hidden:false,disabled:false,value:'',textContent:'',placeholder:'',
     dataset:{},style:{},listeners,
     classList:{add:x=>flags.add(x),remove:x=>flags.delete(x),
       toggle:(x,on)=>{if(on)flags.add(x);else flags.delete(x)},
       contains:x=>flags.has(x)},
     addEventListener:(name,fn)=>listeners.set(name,fn),
     setAttribute(k,v){this[k]=v},
     replaceChildren(){},append(){},scrollIntoView(){},
     focus(){this.focused=true},getBoundingClientRect(){return {width:900,height:600}},
     querySelector(s){return actions.get(s.match(/data-a="([^"]+)"/)?.[1])},
   };
 };
 const ids=['status','mode','tip','typing','track','editor','send','go',
   'mic','sources-panel','sources-list','sources-note','source-progress','close-sources',
   'sources-next','sources-refresh','timeline','seek-position',
   'played-time','total-time','video','tools','input-hint','backspace','clear-text',
   'usage','usage-start','usage-stop','usage-view','usage-clear','usage-copy','usage-output'];
 for(const id of ids)elements.set(id,make(id));
 for(const action of ['PLAY','PAUSE','FULLSCREEN','SOURCES','NEXT','BACK']){
   const el=make('button-'+action);el.dataset.a=action;actions.set(action,el);
 }
 for(const code of ['19','20','21','22','23','85']){
   const el=make('key-'+code);el.dataset.k=code;keys.set(code,el);
 }
 const allButtons=[...actions.values(),...keys.values()];
 const document={body:make('body'),
   getElementById:id=>{assert.ok(elements.has(id),id);return elements.get(id)},
   querySelectorAll:selector=>selector==='button'?allButtons:
     selector==='[data-a]'?[...actions.values()]:
     selector==='[data-k]'?[...keys.values()]:[],
   createElement:id=>make('new-'+id)
 };
 let socket;
 class WS {
   constructor(){socket=this;this.readyState=1;this.sent=[]}
   send(message){this.sent.push(message)}
 }
 const context=createContext({document,window:{},WebSocket:WS,
   location:{search:'?k=review-key',host:'192.168.0.40:1945'},URLSearchParams,
   requestAnimationFrame:()=>{},setInterval:()=>{},setTimeout:()=>0,
   clearTimeout:()=>{},Date,console});
 runInContext(script,context,{timeout:2000});
 const get=id=>elements.get(id);
 const click=el=>{assert.ok(el.listeners.has('click'),el.id);el.listeners.get('click')()};
 const receive=s=>socket.onmessage({data:s});
 socket.onopen();
 return {get,socket,click,receive,actions,context};
}
test('phone video buttons send play, pause and enter/exit fullscreen even before fullscreen',()=>{
 const ui=setup();
 ui.receive('V VIDEO');
 const playback={state:'paused',fullscreen:false,canFullscreen:true,
   positionMs:52000,durationMs:90000,seekable:true,seekStartMs:0,seekEndMs:90000};
 ui.receive('H '+JSON.stringify(playback));
 assert.equal(ui.get('video').hidden,false);
 assert.equal(ui.get('timeline').hidden,false);
 assert.equal(ui.get('seek-position').disabled,false);
 assert.equal(ui.get('total-time').textContent,'1:30');
 assert.equal(ui.actions.get('FULLSCREEN').disabled,false);
 assert.match(ui.actions.get('FULLSCREEN').textContent,/Fullscreen/);
 ui.click(ui.actions.get('PLAY'));
 ui.click(ui.actions.get('FULLSCREEN'));
 assert.deepEqual(ui.socket.sent.slice(-2),['A PLAY','A FULLSCREEN']);
 ui.receive('V FULLSCREEN');
 ui.receive('H '+JSON.stringify({...playback,state:'playing',fullscreen:true}));
 assert.match(ui.actions.get('FULLSCREEN').textContent,/Exit fullscreen/);
 assert.equal(ui.actions.get('PAUSE').disabled,false);
 ui.click(ui.actions.get('PAUSE'));
 ui.click(ui.actions.get('FULLSCREEN'));
 assert.deepEqual(ui.socket.sent.slice(-2),['A PAUSE','A FULLSCREEN']);
});
test('scrubbing sends millisecond position and follows observed progress',()=>{
 const ui=setup();ui.receive('V VIDEO');
 ui.receive('H '+JSON.stringify({state:'playing',fullscreen:false,
   positionMs:15000,durationMs:180000,seekable:true,seekStartMs:0,seekEndMs:180000}));
 assert.equal(ui.get('played-time').textContent,'0:15');
 const seek=ui.get('seek-position');
 seek.value='85000';seek.listeners.get('input')();
 assert.equal(ui.get('played-time').textContent,'1:25');
 seek.listeners.get('change')();
 assert.equal(ui.socket.sent.at(-1),'Z 85000');
});
test('unobservable video keeps timeline visible but blocks fake seek',()=>{
 const ui=setup();ui.receive('V FULLSCREEN');
 ui.receive('H '+JSON.stringify({state:'unknown',fullscreen:true,
   seekable:false,positionMs:0,durationMs:0}));
 assert.equal(ui.get('timeline').hidden,false);
 assert.equal(ui.get('seek-position').disabled,true);
 assert.equal(ui.actions.get('FULLSCREEN').disabled,false);
 assert.equal(ui.get('total-time').textContent,'--:--');
 ui.click(ui.actions.get('FULLSCREEN'));
 assert.equal(ui.socket.sent.at(-1),'A FULLSCREEN');
});
test('late pairing gets immediate SEARCH mode and phone input focus',()=>{
 const ui=setup();
 ui.receive('V SEARCH');
 assert.equal(ui.get('typing').focused,true);
 assert.equal(ui.get('video').hidden,true);
 assert.match(ui.get('mode').textContent,/SEARCH/);
 ui.get('typing').focused=false;
 ui.receive('V EDITOR');
 assert.equal(ui.get('typing').focused,true);
});
test('source choices remain open until the TV confirms selected source and show batch status',()=>{
 const ui=setup();ui.receive('V VIDEO');
 const choices=Array.from({length:12},(_,i)=>({number:i+1,label:'Player '+(i+1),
   badges:'',selected:false,tried:false}));
 ui.receive('O '+JSON.stringify({choices,observed:12,batch:1,batchStart:1,
   batchEnd:3,phase:'checking'}));
 assert.match(ui.get('source-progress').textContent,/Batch 1/);
 assert.equal(ui.get('sources-panel').hidden,false);
 ui.click(ui.actions.get('SOURCES'));
 const buttons=ui.get('sources-list');
 assert.ok(buttons.id);
 // Requesting Sources never triggers a TV-native modal command.
 assert.equal(ui.socket.sent.at(-1),'A SOURCES');
 ui.receive('O '+JSON.stringify({choices:choices.map(x=>({...x,
   selected:x.number===4,tried:x.number===1})),observed:12,
   batch:2,batchStart:4,batchEnd:6,phase:'verified'}));
 assert.match(ui.get('source-progress').textContent,/Player 4/);
 assert.match(ui.get('source-progress').textContent,/Batch 2/);
 assert.match(ui.get('source-progress').textContent,/verified/);
});
test('phone microphone safely suggests native keyboard dictation when browser speech is unsupported',()=>{
 const ui=setup();
 ui.click(ui.get('mic'));
 assert.match(ui.get('input-hint').textContent,/phone's own keyboard/);
 assert.equal(ui.get('typing').focused,true);
});

test('technical usage report is explicit opt-in and can be stopped, viewed and deleted',()=>{
 const ui=setup();
 const empty={schema:'ghartv.usage-summary.v1',enabled:false,retention_days:7,
   uploaded:false,total:0,counts:{},steps:[]};
 ui.receive('G '+JSON.stringify(empty));
 assert.equal(ui.get('usage-start').hidden,false,'Recording must begin OFF');
 assert.equal(ui.get('usage-stop').hidden,true);
 ui.click(ui.get('usage-start'));
 assert.equal(ui.socket.sent.at(-1),'A USAGE_ON');
 const opted={...empty,enabled:true,total:3,
   counts:{CONSENT_ENABLED:1,SEARCH_SENT:1,SOURCE_UNAVAILABLE:1},
   steps:[{at_s:0,event:'CONSENT_ENABLED'},{at_s:8,event:'SEARCH_SENT'},
     {at_s:10,event:'SOURCE_UNAVAILABLE'}]};
 ui.receive('G '+JSON.stringify(opted));
 assert.equal(ui.get('usage-start').hidden,true);
 assert.equal(ui.get('usage-stop').hidden,false);
 assert.match(ui.get('usage-output').textContent,/source unavailable: 1/i);
 assert.match(ui.get('usage-output').textContent,/no automatic upload/i);
 ui.click(ui.get('usage-view'));
 assert.equal(ui.socket.sent.at(-1),'A USAGE_VIEW');
 ui.click(ui.get('usage-stop'));
 assert.equal(ui.socket.sent.at(-1),'A USAGE_OFF');
 ui.receive('G '+JSON.stringify({...opted,enabled:false}));
 assert.equal(ui.get('usage-start').hidden,false);
 ui.click(ui.get('usage-clear'));
 assert.notEqual(ui.socket.sent.at(-1),'A USAGE_CLEAR',
   'Deletion must require a second deliberate tap');
 ui.click(ui.get('usage-clear'));
 assert.equal(ui.socket.sent.at(-1),'A USAGE_CLEAR');
 ui.receive('G '+JSON.stringify(empty));
 assert.match(ui.get('usage-output').textContent,/Technical actions: 0/);
});
test('usage report displays only validated event labels and never HTML',()=>{
 const ui=setup();
 ui.receive('G '+JSON.stringify({schema:'ghartv.usage-summary.v1',enabled:true,
   retention_days:7,uploaded:false,total:1,
   counts:{SEARCH_SENT:1,'<script>evil</script>':999},steps:[
     {at_s:4,event:'SEARCH_SENT'},{at_s:7,event:'password=secret'}]}));
 const out=ui.get('usage-output').textContent;
 assert.match(out,/search sent: 1/);
 assert.doesNotMatch(out,/script|password|secret/);
});
test('stale player updates cannot replace live Search or editor typing guidance',()=>{
 const ui=setup();
 ui.receive('V VIDEO');
 ui.receive('H '+JSON.stringify({state:'playing',fullscreen:false,positionMs:5000,
   seekable:true,seekStartMs:0,seekEndMs:60000}));
 assert.match(ui.get('tip').textContent,/TV player playing/);
 ui.receive('V SEARCH');
 assert.equal(ui.get('typing').focused,true);
 assert.match(ui.get('tip').textContent,/updates the TV search live/);
 ui.receive('H '+JSON.stringify({state:'playing',fullscreen:false,positionMs:9000,
   seekable:true,seekStartMs:0,seekEndMs:60000}));
 assert.match(ui.get('tip').textContent,/updates the TV search live/);
 assert.equal(ui.get('timeline').hidden,true);
 ui.receive('V EDITOR');
 assert.match(ui.get('tip').textContent,/Type below on your phone/);
 ui.receive('H '+JSON.stringify({state:'paused',fullscreen:false,positionMs:9000,
   seekable:false}));
 assert.match(ui.get('tip').textContent,/Type below on your phone/);
});
