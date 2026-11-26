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
     replaceChildren(){},append(){},scrollIntoView(options){this.lastScroll=options},
     focus(){this.focused=true;document.activeElement=this},getBoundingClientRect(){return {width:900,height:600}},
     querySelector(s){return actions.get(s.match(/data-a="([^"]+)"/)?.[1])},
   };
 };
 const ids=['status','mode','tip','typing','track','editor','send','go',
   'mic','sources-panel','sources-list','sources-note','source-progress','close-sources',
   'sources-next','sources-refresh','timeline','seek-position','playback-state',
   'played-time','total-time','video','tools','input-hint','backspace','clear-text',
   ];
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
 const viewportHandlers=new Map();
 const mockWindow={visualViewport:{height:83,addEventListener:(event,fn)=>viewportHandlers.set(event,fn),listeners:viewportHandlers}};
 const context=createContext({document,window:mockWindow,WebSocket:WS,
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
test('unobservable fullscreen keeps controls without pretending to have a seek range',()=>{
 const ui=setup();ui.receive('V FULLSCREEN');
 ui.receive('H '+JSON.stringify({state:'unknown',fullscreen:true,
   seekable:false,positionMs:0,durationMs:0}));
 assert.equal(ui.get('timeline').hidden,true);
 assert.equal(ui.get('playback-state').hidden,false);
 assert.equal(ui.get('seek-position').disabled,true);
 assert.equal(ui.actions.get('FULLSCREEN').disabled,false);
 assert.match(ui.get('playback-state').textContent,/no timeline yet/i);
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

test('phone remote has no owner diagnostics panel or recording buttons',()=>{
 assert.doesNotMatch(html,/id="usage"|id="usage-start"|id="usage-stop"|Usage report/);
 const ui=setup();
 const before=ui.socket.sent.length;
 ui.receive('G '+JSON.stringify({schema:'ghartv.usage-summary.v1',enabled:true,
   total:2,counts:{SEARCH_SENT:2}}));
 assert.equal(ui.socket.sent.length,before);
 assert.ok(!ui.socket.sent.some(x=>x.startsWith('A USAGE_')));
});
test('player discovery does not show fake timeline before actual media',()=>{
 const ui=setup();
 ui.receive('V STARTING');
 ui.receive('H '+JSON.stringify({state:'unknown',positionMs:0,durationMs:0,seekable:false}));
 assert.equal(ui.get('timeline').hidden,true);
 assert.equal(ui.get('playback-state').hidden,false);
 assert.match(ui.get('playback-state').textContent,/no video confirmed/i);
 ui.receive('V VIDEO');
 ui.receive('H '+JSON.stringify({state:'playing',seekable:false,positionMs:5000,durationMs:0}));
 assert.equal(ui.get('timeline').hidden,false);
 assert.equal(ui.get('seek-position').disabled,true);
 assert.equal(ui.get('played-time').textContent,'0:05');
 assert.equal(ui.get('total-time').textContent,'—');
 assert.match(ui.get('playback-state').textContent,/does not expose seeking/);
});
test('typing layout must not reflow away from Search during the button touch',()=>{
 const ui=setup();
 ui.receive('V SEARCH');
 const input=ui.get('typing');
 input.listeners.get('focus')();
 assert.equal(ui.context.document.body.classList.contains('typing-active'),true);
 input.listeners.get('blur')();
 assert.equal(ui.context.document.body.classList.contains('typing-active'),true,
   'Search mode keeps layout compact through pointerup/click');
 ui.receive('V VIDEO');
 assert.equal(ui.context.document.body.classList.contains('typing-active'),false,
   'The normal trackpad layout returns after the mode changes');
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


test('landscape SwiftKey resize centers the actual Search input rather than editor footer',()=>{
 const ui=setup();ui.receive('V SEARCH');
 const field=ui.get('typing');
 assert.equal(ui.context.document.activeElement,field);
 const resize=ui.context.window.visualViewport.listeners.get('resize');
 assert.equal(typeof resize,'function');
 resize();
 assert.equal(field.lastScroll?.block,'center',
   '83px keyboard viewport must show input and adjacent Search, not Backspace');
 assert.equal(field.lastScroll?.behavior,'instant');
 field.lastScroll=undefined;
 ui.receive('V VIDEO');resize();
 assert.equal(field.lastScroll,undefined,
   'Stale input must not be scrolled while video controls are displayed');
});
