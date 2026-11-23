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
   else System.out.print(FilmPhonePlayback.command(a[0],Long.parseLong(a[1])));
 }
}`);
try {
 execFileSync('javac',['-d',tmp,mediaSource,helper],{stdio:'pipe'});
 const java=(operation,millis=0)=>execFileSync('java',['-cp',tmp,
   'in.ghartv.nova.FilmPhonePlaybackScript',operation,String(millis)],{encoding:'utf8'});
 const snapshot=java('sample'),pause=java('pause'),play=java('play'),
   seek=java('seek',250000);
 const {runInNewContext}=await import('node:vm');
 function video(overrides={}){
   return {paused:false,ended:false,readyState:4,currentTime:42.375,duration:120,
     seekable:{length:1,start:()=>10,end:()=>100},
     getBoundingClientRect:()=>({width:1280,height:720}),
     play(){this.paused=false;return Promise.resolve();},
     pause(){this.paused=true;},...overrides};
 }
 function run(js,videos){
   const context={document:{querySelectorAll:(selector)=>selector==='video'?videos:[]},
     getComputedStyle:()=>({display:'block',visibility:'visible'})};
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
 test('scripts contain no navigation, reload, frame introspection or URL export',()=>{
   for(const js of [snapshot,pause,play,seek]){
     assert.doesNotMatch(js,/\.location\b|window\.open|\.reload\(|scrollIntoView|\.src\b/);
     assert.doesNotMatch(js,/contentWindow|\.href\b|\.currentSrc\b/);
   }
 });
} catch(err){
 rmSync(tmp,{recursive:true,force:true});
 throw err;
}
