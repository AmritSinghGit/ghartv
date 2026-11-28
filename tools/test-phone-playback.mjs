import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const root=resolve(fileURLToPath(new URL('.',import.meta.url)),'..');
const mediaSource=join(root,'android-tv/app/src/main/java/in/ghartv/nova/FilmPhonePlayback.java');
const tmp=mkdtempSync(join(tmpdir(),'ghartv-phone-tests-'));
process.on('exit',()=>rmSync(tmp,{recursive:true,force:true}));
const helper=join(tmp,'FilmPhonePlaybackScript.java');
writeFileSync(helper,`package in.ghartv.nova;
public final class FilmPhonePlaybackScript {
 public static void main(String[] a) {
   if(a[0].equals("sample")) System.out.print(FilmPhonePlayback.sample());
   else if(a[0].equals("bridge"))System.out.print(FilmPhoneFrameBridge.documentStart());
   else System.out.print(FilmPhonePlayback.command(a[0],Long.parseLong(a[1])));
 }
}`);
try {
 execFileSync('javac',['-d',tmp,mediaSource,mediaSource.replace('FilmPhonePlayback.java','FilmPhoneFrameBridge.java'),helper],{stdio:'pipe'});
 const java=(operation,millis=0)=>execFileSync('java',['-cp',tmp,
   'in.ghartv.nova.FilmPhonePlaybackScript',operation,String(millis)],{encoding:'utf8'});
 const snapshot=java('sample'),pause=java('pause'),play=java('play'),
   seek=java('seek',250000),bridge=java('bridge');
 const {runInNewContext}=await import('node:vm');
 function video(overrides={}){
   return {paused:false,ended:false,readyState:4,currentTime:42.375,duration:120,
     seekable:{length:1,start:()=>10,end:()=>100},
     getBoundingClientRect:()=>({width:1280,height:720}),
     play(){this.paused=false;return Promise.resolve();},
     pause(){this.paused=true;},...overrides};
 }
 function run(js,videos,frames=[],window=undefined){
   const context={document:{querySelectorAll:(selector)=>selector==='video'?videos:
     selector==='iframe'?frames:[]},
     getComputedStyle:()=>({display:'block',visibility:'visible'}),window};
   return JSON.parse(runInNewContext(js,context,{timeout:2000}));
 }
 test('one visible media element reports actual timestamp and seek window',()=>{
   const v=video(),s=run(snapshot,[v]);
   assert.equal(s.state,'playing');assert.equal(s.positionMs,42375);
   assert.equal(s.durationMs,120000);assert.equal(s.seekable,true);
   assert.equal(s.seekStartMs,10000);assert.equal(s.seekEndMs,100000);
   assert.equal(JSON.stringify(s).includes('http'),false);
 });
 test('pause then play update actual player; no synthetic success state',()=>{
   const v=video();
   assert.equal(run(pause,[v]).result,'REQUESTED');
   assert.equal(run(snapshot,[v]).state,'paused');
   assert.equal(run(play,[v]).result,'REQUESTED');
   assert.equal(run(snapshot,[v]).state,'playing');
 });
 test('seek clamps out-of-window position to the actual seekable end',()=>{
   const v=video();
   assert.equal(run(seek,[v]).result,'REQUESTED');
   assert.equal(v.currentTime,100);
 });
 test('nonseekable media refuses seek and retains position',()=>{
   const v=video({seekable:{length:0}});
   assert.equal(run(seek,[v]).result,'NOT_SEEKABLE');
   assert.equal(v.currentTime,42.375);
   assert.equal(run(snapshot,[v]).seekable,false);
 });
 test('opaque frames, absent and ambiguous videos are never controlled',()=>{
   assert.equal(run(snapshot,[]).state,'unknown');
   assert.equal(run(play,[]).result,'UNOBSERVABLE');
   assert.equal(run(pause,[video(),video()]).result,'UNOBSERVABLE');
   assert.equal(run(seek,[video(),video()]).result,'UNOBSERVABLE');
 });
 test('matching recent direct iframe media can be sampled, paused and sought',()=>{
   const calls=[],frameWindow={postMessage:(data,target)=>calls.push({data,target})};
   const frame={contentWindow:frameWindow,getBoundingClientRect:()=>({width:800,height:450})};
   const state={state:'playing',observable:true,positionMs:6500,durationMs:50000,
     seekable:true,seekStartMs:0,seekEndMs:50000};
   const window={__ghartvPhoneFrame:{source:frameWindow,at:Date.now(),state}};
   assert.equal(run(snapshot,[],[frame],window).positionMs,6500);
   assert.equal(run(pause,[],[frame],window).result,'REQUESTED');
   assert.equal(run(seek,[],[frame],window).result,'REQUESTED');
   assert.deepEqual(calls.map(c=>c.data.operation),['pause','seek']);
   assert.deepEqual(calls.map(c=>c.target),['*','*']);
   assert.equal(calls[1].data.positionMs,250000);
   assert.equal(JSON.stringify(calls).includes('http'),false);
 });
 test('stale and mismatched iframe reports are never used to control media',()=>{
   const calls=[],source={postMessage:x=>calls.push(x)};
   const frame={contentWindow:source,getBoundingClientRect:()=>({width:800,height:450})};
   const state={state:'playing',observable:true,positionMs:7000,durationMs:70000,
     seekable:true,seekStartMs:0,seekEndMs:70000};
   assert.equal(run(play,[],[frame],{__ghartvPhoneFrame:{
     source,at:Date.now()-4000,state}}).result,'UNOBSERVABLE');
   assert.equal(run(play,[],[frame],{__ghartvPhoneFrame:{
     source:{},at:Date.now(),state}}).result,'UNOBSERVABLE');
   assert.equal(run(play,[],[frame,frame],{__ghartvPhoneFrame:{
     source,at:Date.now(),state}}).result,'UNOBSERVABLE');
   assert.equal(calls.length,0);
 });
 test('document-start media reporter validates direct frame source and bounded payload',()=>{
   const callbacks={},source={};
   const frame={contentWindow:source,getBoundingClientRect:()=>({width:900,height:500})};
   const top={addEventListener:(event,fn)=>{callbacks[event]=fn}};
   top.top=top;
   const doc={querySelectorAll:k=>k==='iframe'?[frame]:[]};
   runInNewContext(bridge,{window:top,document:doc,
     getComputedStyle:()=>({display:'block',visibility:'visible'})},{timeout:2000});
   const state={state:'playing',observable:true,seekable:true,
     positionMs:12000,durationMs:75000,seekStartMs:0,seekEndMs:75000};
   callbacks.message({origin:'https://player.example',source,
     data:{tag:'ghartv-phone-media-v1',kind:'report',state}});
   assert.equal(top.__ghartvPhoneFrame.state.positionMs,12000);
   const old=top.__ghartvPhoneFrame;
   callbacks.message({origin:'https://player.example',source:{},
     data:{tag:'ghartv-phone-media-v1',kind:'report',state}});
   assert.equal(top.__ghartvPhoneFrame,old);
   callbacks.message({origin:'http://player.example',source,
     data:{tag:'ghartv-phone-media-v1',kind:'report',state}});
   assert.equal(top.__ghartvPhoneFrame,old);
   callbacks.message({origin:'https://player.example',source,
     data:{tag:'ghartv-phone-media-v1',kind:'report',state:{...state,positionMs:Infinity}}});
   assert.equal(top.__ghartvPhoneFrame,old);
 });
 test('frame script observes one video and accepts only parent play pause seek actions',()=>{
   const callbacks={};const posts=[];
   const top={postMessage:(data,target)=>posts.push({data,target})};
   const current={addEventListener:(event,fn)=>callbacks[event]=fn,
     parent:top,top,location:{protocol:'https:'}};
   const v=video();
   let tick;
   const document={querySelector:k=>k==='video'?v:null,querySelectorAll:k=>k==='video'?[v]:[],
     addEventListener:()=>{}};
   runInNewContext(bridge,{window:current,document,location:current.location,
     getComputedStyle:()=>({display:'block',visibility:'visible'}),
     setInterval:fn=>{tick=fn},setTimeout:()=>{}},{timeout:2000});
   tick();
   assert.equal(posts[0].data.state.positionMs,42375);
   assert.equal(posts[0].data.state.state,'playing');
   callbacks.message({source:top,data:{tag:'ghartv-phone-media-v1',
     kind:'command',operation:'pause'}});
   assert.equal(v.paused,true);
   callbacks.message({source:top,data:{tag:'ghartv-phone-media-v1',
     kind:'command',operation:'seek',positionMs:65000}});
   assert.equal(v.currentTime,65);
   callbacks.message({source:{},data:{tag:'ghartv-phone-media-v1',
     kind:'command',operation:'play'}});
   assert.equal(v.paused,true);
   assert.equal(posts[0].data.state.url,undefined);
 });
 test('nested HTTPS frames relay only media status and approved controls to their single visible child',()=>{
   const handlers={},sentUp=[],sentDown=[];
   const top={};
   const deep={postMessage:(data,target)=>sentDown.push({data,target})};
   const middle={parent:top,top,
     addEventListener:(name,callback)=>handlers[name]=callback,
     postMessage:(data,target)=>sentUp.push({data,target})};
   top.postMessage=(data,target)=>sentUp.push({data,target});
   const frame={contentWindow:deep,getBoundingClientRect:()=>({width:700,height:430})};
   const doc={querySelectorAll:k=>k==='iframe'?[frame]:[],
     addEventListener:()=>{}};
   runInNewContext(bridge,{window:middle,document:doc,
     location:{protocol:'https:'},
     getComputedStyle:()=>({display:'block',visibility:'visible'}),
     setInterval:()=>{},setTimeout:()=>{}},{timeout:2000});
   const state={state:'playing',observable:true,seekable:true,
     positionMs:10000,durationMs:90000,seekStartMs:0,seekEndMs:90000};
   handlers.message({origin:'https://nested-player.example',source:deep,
     data:{tag:'ghartv-phone-media-v1',kind:'report',state}});
   assert.equal(middle.__ghartvPhoneFrame.state.positionMs,10000);
   assert.equal(sentUp.length,1);
   assert.equal(sentUp[0].data.state.positionMs,10000);
   assert.equal(JSON.stringify(sentUp[0].data).includes('http'),false);
   handlers.message({source:top,
     data:{tag:'ghartv-phone-media-v1',kind:'command',operation:'pause'}});
   assert.equal(sentDown.length,1);
   assert.equal(sentDown[0].data.operation,'pause');
   handlers.message({source:{},
     data:{tag:'ghartv-phone-media-v1',kind:'command',operation:'play'}});
   assert.equal(sentDown.length,1);
 });
 test('scripts contain no navigation, reload, URL export or frame DOM access',()=>{
   for(const js of [snapshot,pause,play,seek]){
     assert.doesNotMatch(js,/\.location\b|window\.open|\.reload\(|scrollIntoView|\.src\b/);
     assert.doesNotMatch(js,/contentDocument|\.contentWindow\.document|\.href\b|\.currentSrc\b|\.cookie\b/);
   }
 });
} catch(err){
 rmSync(tmp,{recursive:true,force:true});
 throw err;
}
