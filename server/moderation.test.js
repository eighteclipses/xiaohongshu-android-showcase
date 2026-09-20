const test=require('node:test');const assert=require('node:assert/strict');
const M=require('./moderation');const {createStore}=require('./transaction-store');const {createWorker,reviewWithAi}=require('./moderation-worker');const {parseCsv}=require('./csv');
const empty=()=>({posts:[],reports:[],notifications:[],keywordRules:[],moderationCases:[],aiJobs:[],moderationEvents:[],importReceipts:[]});
const note=(id='p')=>({id,userId:'alice',title:'Test',content:'normal',topics:[],images:[],isPublic:true,isDraft:false,status:'approved'});
test('keyword normalization, fields, manual decisions and old versions',()=>{
 const s=empty();M.rule(s,{term:'ＴＥＳＴ',category:'演示'});const p=note();M.submit(s,p);s.posts.push(p);
 assert.equal(p.status,'pending');assert.equal(s.aiJobs.length,1);assert.equal(s.moderationCases[0].hits[0].field,'title');
 assert.equal(M.normalize('T\u200besＴ'),'test');
 M.decide(s,p,'approved','正常语境','admin',1,s.moderationCases[0].id);assert.equal(p.status,'approved');
 const old=structuredClone(p);p.content='changed';M.submit(s,p,old);assert.equal(p.content_version,2);assert.equal(p.status,'pending');
 assert.throws(()=>M.decide(s,p,'approved','old','admin',1),e=>e.status===409);
 M.decide(s,p,'rejected','需修改','admin',2);const rejected=structuredClone(p);p.title='clean';p.content='clean';M.submit(s,p,rejected);assert.equal(p.status,'pending');
});
test('reports preserve visibility, group by version, and hide only on manual decision',()=>{
 const s=empty();const p=note();M.submit(s,p);s.posts.push(p);
 M.report(s,p,'bob','不实内容');M.report(s,p,'carol','重复广告');assert.equal(s.moderationCases.length,1);assert.equal(s.aiJobs.length,1);assert.equal(p.status,'approved');
 assert.equal(M.report(s,p,'bob','again').submitted,false);assert.equal(s.reports.length,2);
 M.decide(s,p,'keep','无违规','admin',1,s.moderationCases[0].id);assert.equal(p.status,'approved');assert.ok(s.reports.every(r=>r.status==='dismissed'));
 M.report(s,p,'dave','new report');M.decide(s,p,'hidden','确认违规','admin',1,s.moderationCases[0].id);assert.equal(p.status,'hidden');
});
test('recycle restores as private draft and invalidates jobs',()=>{
 const s=empty();const p=note();M.rule(s,{term:'test'});M.submit(s,p);s.posts.push(p);M.recycle(s,p,'alice',1);assert.ok(p.deleted_at);assert.equal(s.moderationCases[0].state,'stale');M.recycle(s,p,'alice',2,true);assert.equal(p.deleted_at,null);assert.equal(p.isDraft,true);assert.equal(p.isPublic,false);
});
test('transaction rollback, concurrent writes and committed readers',async()=>{
 let fail=true;const persisted=[];const t=createStore({posts:[]},async s=>{if(fail)throw Error('disk');persisted.push(structuredClone(s));});
 await assert.rejects(t.run(s=>s.posts.push('lost')));assert.deepEqual(t.store.posts,[]);fail=false;
 await Promise.all([t.run(async s=>{s.posts.push('a');await new Promise(r=>setTimeout(r,10));}),t.run(s=>s.posts.push('b'))]);
 assert.deepEqual(t.store.posts,['a','b']);assert.deepEqual(persisted.at(-1).posts,['a','b']);
});
const wait=async condition=>{for(let i=0;i<100;i++){if(condition())return;await new Promise(r=>setTimeout(r,5));}throw Error('timeout');};
test('AI result cannot overwrite manual decision; failed tasks retry at 1 and 5 minutes',async()=>{
 let time=Date.now();const s=empty();M.rule(s,{term:'test'});const p=note();M.submit(s,p);s.posts.push(p);const t=createStore(s,async()=>{});
 let release;const worker=createWorker(t,()=>new Promise(r=>{release=r;}),()=>time);await worker.start();await wait(()=>!!release);
 await t.run(s=>M.decide(s,s.posts[0],'rejected','人工决定','admin',1));release({recommendation:'approve'});await wait(()=>worker.idle());worker.stop();assert.equal(t.store.posts[0].status,'rejected');assert.equal(t.store.aiJobs[0].state,'stale');
 const t2=createStore(structuredClone(s),async()=>{});const failing=createWorker(t2,async()=>{throw Error('network');},()=>time);await failing.start();await wait(()=>failing.idle());assert.equal(t2.store.aiJobs[0].attempts,1);assert.equal(new Date(t2.store.aiJobs[0].next_at).getTime(),time+60000);
 time+=60000;await failing.tick();await wait(()=>failing.idle());assert.equal(new Date(t2.store.aiJobs[0].next_at).getTime(),time+300000);
 time+=300000;await failing.tick();await wait(()=>failing.idle());failing.stop();assert.equal(t2.store.aiJobs[0].state,'failed');assert.equal(t2.store.posts[0].status,'pending');
});
test('review parser accepts only structured suggestions; CSV handles quoted commas/newlines',async()=>{
 for(const recommendation of ['approve','reject','uncertain']){const result=await reviewWithAi({snapshot:{title:'test'},hits:[],reason:''},{apiKey:'fake',fetchImpl:async()=>({ok:true,json:async()=>({choices:[{message:{content:JSON.stringify({recommendation,category:'normal',evidence:[],explanation:'context'})}}]})})});assert.equal(result.recommendation,recommendation);}
 assert.equal(parseCsv('\uFEFFtitle,content\r\n"hello, world","line1\nline2"').rows[0].data.content,'line1\nline2');assert.throws(()=>parseCsv('a\n"broken'));assert.throws(()=>parseCsv('a\n'+Array(101).fill('x').join('\n')));
});

test('worker recovers durable running jobs and never exceeds two concurrent reviews',async()=>{
 const s=empty();M.rule(s,{term:'test'});for(let i=0;i<3;i++){const p=note('job-'+i);M.submit(s,p);s.posts.push(p);}
 s.aiJobs[0].state='running';s.aiJobs[0].attempts=1;
 const t=createStore(s,async()=>{});const releases=[];let outstanding=0,max=0;
 const worker=createWorker(t,()=>{outstanding++;max=Math.max(max,outstanding);return new Promise(resolve=>releases.push(()=>{outstanding--;resolve({recommendation:'uncertain',category:'test',evidence:[],explanation:'test'});}));});
 try{await worker.start();await wait(()=>releases.length===2);assert.equal(t.store.aiJobs[0].attempts,2);releases[0]();await wait(()=>t.store.aiJobs[0].state==='completed');await worker.tick();await wait(()=>releases.length===3);assert.equal(max,2);releases[1]();releases[2]();await wait(()=>worker.idle());assert.ok(t.store.posts.every(p=>p.status==='pending'));}finally{worker.stop();}
});
