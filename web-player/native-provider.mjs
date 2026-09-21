/** Normal user-browser navigation, not an automated provider session. */
import {access,lstat} from 'node:fs/promises';
import {constants} from 'node:fs';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {homedir} from 'node:os';
import {join} from 'node:path';
const execute=promisify(execFile);
export const PROVIDER='https://flixmomo.app';
export function providerTarget(action,query=''){
 if(action==='browse')return PROVIDER+'/';
 if(action!=='search')throw Error('ACTION_REJECTED');
 if(typeof query!=='string')throw Error('QUERY_LENGTH_REJECTED');
 const q=query.trim();
 if(q.length<2||q.length>120||/[\x00-\x1f\x7f]/.test(q))throw Error('QUERY_LENGTH_REJECTED');
 const url=new URL('/search',PROVIDER);url.searchParams.set('q',q);return url.href;
}
export async function findTorBrowser(){
 if(process.platform!=='darwin')return null;
 for(const root of ['/Applications',join(homedir(),'Applications')]){
  const app=join(root,'Tor Browser.app');
  try{const info=await lstat(app);if(!info.isDirectory()||info.isSymbolicLink())continue;
   await access(join(app,'Contents/MacOS/firefox'),constants.X_OK);return app;
  }catch{}
 }
 return null;
}
export async function openInTorBrowser(action,query,{find=findTorBrowser,run=execute}={}){
 const target=providerTarget(action,query),app=await find();
 if(!app)throw Error('TOR_BROWSER_NOT_FOUND');
 // LaunchServices opens a normal browser. No WebDriver, debug port, script injection,
 // existing-profile extraction, arbitrary URL, direct fallback or process termination.
 const result=await run('/usr/bin/open',['-a',app,'--',target],{
  timeout:8000,maxBuffer:8192,env:{PATH:'/usr/bin:/bin',LANG:'en_US.UTF-8'}
 });
 if(result?.code)throw Error('TOR_BROWSER_OPEN_FAILED');
 return {ok:true,status:'TOR_BROWSER_OPEN_REQUESTED',route:'tor-browser',torVerified:false,
   playbackVerified:false,automaticSearch:false};
}
