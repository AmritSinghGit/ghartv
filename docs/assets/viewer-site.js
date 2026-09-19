'use strict';
const pageLink='https://amritsinghgit.github.io/ghartv/';
const byId=id=>document.getElementById(id);
function checkedRelease(value){
 const u=new URL(value.apkUrl);
 if(u.origin!=='https://github.com'||u.username||u.password||u.search||u.hash||!/^\/AmritSinghGit\/ghartv\/releases\/download\/v[\w.-]+\/[\w.-]+\.apk$/.test(u.pathname)||value.channel!=='production'||!Number.isSafeInteger(value.versionCode)||value.versionCode<1||typeof value.versionName!=='string'||value.versionName.length>100||!/^\d[\w.-]+$/.test(value.versionName)||!/^[a-f0-9]{64}$/.test(value.sha256))throw Error('Invalid release');
 return {url:u.href,name:value.versionName};
}
(async()=>{
 const status=byId('download-status');
 try{
  const r=await fetch('https://raw.githubusercontent.com/AmritSinghGit/ghartv/main/update/latest.json',{credentials:'omit',redirect:'error',cache:'no-store',signal:AbortSignal.timeout(10000)});
  if(!r.ok)throw Error('Unavailable');const text=await r.text();if(text.length>32768)throw Error('Too large');
  const release=checkedRelease(JSON.parse(text));byId('download-apk').href=release.url;byId('version-label').textContent='GharTV Nova · '+release.name;
  status.textContent='Latest available download checked. Android will ask you to confirm installation.';
 }catch{status.textContent='The latest download check is unavailable. The button uses the saved release from 18 September 2026.';}
})();
byId('copy-link').addEventListener('click',async()=>{
 const status=byId('share-status');status.hidden=false;
 try{await navigator.clipboard.writeText(pageLink);status.textContent='Link copied. Share it with your family.';}
 catch{status.textContent='Share this page: '+pageLink;}
});
