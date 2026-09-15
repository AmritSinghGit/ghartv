// Exact projection and idempotent readback against an in-process filesystem CLI double. No external posts.
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,readFile,rm,chmod} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
const home=await mkdtemp(join(tmpdir(),'ghartv-receipt-check-'));
const dir=join(home,'Library/Application Support/GharTV/owner-review/current');await mkdir(dir,{recursive:true});
const bin=join(home,'bin');await mkdir(bin);const mock=join(bin,'gh');
await writeFile(mock,`#!${process.execPath}
const fs=require('fs'),p=process.env.HOME+'/mock-comment.json',args=process.argv.slice(2);let input='';
process.stdin.on('data',c=>input+=c);process.stdin.on('end',()=>{
 let record=fs.existsSync(p)?JSON.parse(fs.readFileSync(p)):null;
 if(args.includes('POST')||args.includes('PATCH')){record={id:1001,user:{login:'AmritSinghGit'},issue_url:'https://api.github.com/repos/AmritSinghGit/ghartv/issues/1',body:JSON.parse(input).body};fs.writeFileSync(p,JSON.stringify(record));console.log(JSON.stringify(record));}
 else if(args.some(a=>a.includes('/issues/1/comments?')))console.log(JSON.stringify(record?[record]:[]));
 else if(args.some(a=>a.includes('/issues/comments/1001')))console.log(JSON.stringify(record));
 else process.exit(3);
});\n`,{mode:0o700});
const env={...process.env,HOME:home,PATH:bin+':'+process.env.PATH};delete env.GHARTV_DISABLE_KEYCHAIN;
const receipt={run_id:'GHARTV-CYAN-8-20260915T140000Z-1234',review_source:'a'.repeat(40),version:'0.6.0-rc6-family-focus',version_code:22,status:'REVIEW_READY',signed_apk_sha256:'b'.repeat(64),emulator:'RC6_INSTALLED_BYTES_VERIFIED_AND_FOREGROUND',obsidian:'WRITTEN_AND_READBACK_VERIFIED',token:'SECRET_SENTINEL',obsidian_note:'/private/SECRET_SENTINEL',feedback:'PRIVATE_FEEDBACK_SENTINEL',device_id:'PRIVATE_DEVICE_SENTINEL'};
const run=()=>{const r=spawnSync(process.execPath,[resolve('web-player/review-sync.mjs'),'--publish'],{env,encoding:'utf8',timeout:12000});assert.equal(r.status,0,r.stderr);return JSON.parse(r.stdout);};
try{
 await writeFile(join(dir,'receipt.json'),JSON.stringify(receipt),{mode:0o600});let result=run();assert.equal(result.status,'GITHUB_READBACK_VERIFIED');
 const first=await readFile(join(home,'mock-comment.json'),'utf8');assert.ok(!/SECRET_SENTINEL|PRIVATE_FEEDBACK|PRIVATE_DEVICE|\/private/.test(first));
 result=run();assert.equal(result.status,'GITHUB_READBACK_VERIFIED');assert.equal(await readFile(join(home,'mock-comment.json'),'utf8'),first);
 receipt.run_id='GHARTV-CYAN-8-20260915T150000Z-1235';await writeFile(join(dir,'receipt.json'),JSON.stringify(receipt));assert.equal(run().status,'GITHUB_READBACK_VERIFIED');
 const updated=JSON.parse(await readFile(join(home,'mock-comment.json'),'utf8'));assert.equal(updated.id,1001);assert.ok(updated.body.includes(receipt.run_id));
 receipt.run_id='GHARTV-CYAN-8-20260915T130000Z-1233';await writeFile(join(dir,'receipt.json'),JSON.stringify(receipt));assert.equal(run().status,'PENDING_NEWER_MIRROR_PRESERVED');
 assert.equal(JSON.parse(await readFile(join(home,'mock-comment.json'),'utf8')).body,updated.body);
 console.log('SAFE_RECEIPT_ALLOWLIST_READBACK_IDEMPOTENCY_NEWER_RUN_PRESERVATION=PASS');
}finally{await rm(home,{recursive:true,force:true});}
