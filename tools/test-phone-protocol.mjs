import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const root=resolve(fileURLToPath(new URL('.',import.meta.url)),'..');
const javaSource=join(root,'android-tv/app/src/main/java/in/ghartv/nova/PhoneLinkServer.java');
const tmp=mkdtempSync(join(tmpdir(),'ghartv-remote-protocol-'));
process.on('exit',()=>rmSync(tmp,{recursive:true,force:true}));
const helper=join(tmp,'PhoneSeekProtocolTest.java');
writeFileSync(helper,`package in.ghartv.nova;
public final class PhoneSeekProtocolTest {
 public static void main(String[] args){
  final long[] seek={-1};final int[] calls={0};
  PhoneLinkServer.Sink sink=new PhoneLinkServer.Sink(){
   public void move(int x,int y){} public void click(){} public void down(){}
   public void up(){} public void key(int n){} public void text(String value){}
   public void connected(boolean value){}
   @Override public void seek(long n){seek[0]=n;calls[0]++;}
  };
  PhoneLinkServer server=new PhoneLinkServer(sink,new byte[0]);
  server.dispatch("Z 87500");
  if(calls[0]!=1||seek[0]!=87500)throw new AssertionError("valid seek");
  for(String bad:new String[]{"Z -1","Z 1.2","Z NaN","Z 86400001","Z 999999999",
      "Z 1\\nA PAUSE","Z 00","Z ","Z 3 trailing","Z 0;reboot"}){
     server.dispatch(bad);
     if(calls[0]!=1)throw new AssertionError("invalid seek accepted");
  }
  server.dispatch("Z 0");
  if(calls[0]!=2||seek[0]!=0)throw new AssertionError("seek to beginning");
  server.dispatch("Z 86400000");
  if(calls[0]!=3||seek[0]!=86400000)throw new AssertionError("seek bound");
  System.out.print("PASS: valid 0/87500/86400000; 10 malformed/out-of-range rejected");
 }
}`);
try {
 execFileSync('javac',['-d',tmp,javaSource,helper],{stdio:'pipe'});
 test('phone seek protocol validates milliseconds and rejects untrusted frames',()=>{
   const output=execFileSync('java',['-cp',tmp,'in.ghartv.nova.PhoneSeekProtocolTest'],{encoding:'utf8'});
   assert.match(output,/PASS/);
 });
} catch(err){
 rmSync(tmp,{recursive:true,force:true});
 throw err;
}
