import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const root=resolve(fileURLToPath(new URL('.',import.meta.url)),'..');
const javaSource=join(root,'android-tv/app/src/main/java/in/ghartv/nova/FilmSourceCycle.java');
const tmp=mkdtempSync(join(tmpdir(),'ghartv-source-cycle-'));
process.on('exit',()=>rmSync(tmp,{recursive:true,force:true}));
const helper=join(tmp,'FilmSourceCycleTest.java');
writeFileSync(helper,`package in.ghartv.nova;
import java.util.*;
public class FilmSourceCycleTest {
 static void expect(boolean yes,String detail){if(!yes)throw new AssertionError(detail);}
 public static void main(String[] args){
  List<String> labels=new ArrayList<>();
  for(int i=1;i<=12;i++)labels.add("Player "+i);
  Set<String> tried=new HashSet<>();
  for(int i=0;i<12;i++){
    int at=FilmSourceCycle.next(labels,tried,true);
    expect(at==i,"sequence "+(i+1)+" was index "+at);
    tried.add(labels.get(at));
  }
  expect(FilmSourceCycle.next(labels,tried,false)==-1,"auto must not wrap");
  expect(tried.size()==12,"auto attempt list must remain unchanged");
  expect(FilmSourceCycle.next(labels,tried,true)==0,"manual wraps to first");
  expect(tried.isEmpty(),"manual wrap resets attempt epoch");
  expect(FilmSourceCycle.next(List.of("ad","Server #12","Site 13"),tried,false)==1,
    "only observed valid source number can be selected");
  expect(FilmSourceCycle.next(List.of("ad","stream 1","Player 13"),tried,true)==-1,
    "invalid source choices rejected");
  expect(FilmSourceCycle.next(List.of("Player 1","Player 2"),Set.of("Player 1"),false)==1,
    "auto advances only untried");
  System.out.print("PASS 12 ordered, no automatic wrap, manual wrap first, invalid rejected");
 }
}`);
try {
 execFileSync('javac',['-d',tmp,javaSource,helper],{stdio:'pipe'});
 test('all 12 observed players cycle, only manual wraps and source labels are validated',()=>{
   const output=execFileSync('java',['-cp',tmp,'in.ghartv.nova.FilmSourceCycleTest'],{encoding:'utf8'});
   assert.match(output,/PASS 12 ordered/);
 });
} catch(err){
 throw err;
}
