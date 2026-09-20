const crypto = require('node:crypto');
const M = require('./moderation');
const { parseCsv } = require('./csv');
function paginate(items, query) {
  const limit = [10,20,50].includes(Number(query.limit)) ? Number(query.limit) : 20;
  const pages = Math.max(1, Math.ceil(items.length / limit));
  const page = Math.min(pages, Math.max(1, Math.floor(Number(query.page)) || 1));
  return { items: items.slice((page-1)*limit,page*limit), pagination: {page,limit,total:items.length,pages} };
}
function mountModeration(app, { store, auth, admin, serializePost, publicUser, normalizePost, writeAudit, ensurePosterForTextPost }) {
  M.ensure(store);
  const wrap = fn => (req,res,next) => { try { fn(req,res); } catch(error) { if(error instanceof M.DomainError) res.status(error.status).json({code:error.status,message:error.message}); else next(error); } };
  const ok = (res,data,message='success') => res.json({code:200,message,data});
  const list = (res,items,query,key='items') => { const result=paginate(items,query);ok(res,{...result,[key]:result.items}); };
  const actor = req => req.adminUser?.username || req.user.id;
  const findPost = (id, userId, includeDeleted=false) => {
    const post=store.posts.find(post=>post.id===id && (!userId || post.userId===userId));
    if(!post || (!includeDeleted && post.deleted_at)) M.fail(404,'笔记不存在');return post;
  };
  const audit = (req, action, type, id, detail) => writeAudit(req,action,type,id,detail);
  const filtered = (items,req) => {
    const keyword=M.normalize(req.query.keyword);
    return items.filter(item=>!keyword || M.normalize(JSON.stringify(item)).includes(keyword));
  };
  const checkItems = req => {
    if(!Array.isArray(req.body.items) || !req.body.items.length || req.body.items.length>100) M.fail(400,'请选择 1～100 条记录');
    if(new Set(req.body.items.map(item=>item.id)).size!==req.body.items.length) M.fail(400,'记录不能重复');
    return req.body.items;
  };
  const executeBatch = (req,res,fn) => {
    const items=checkItems(req);const results=[];
    for(const item of items) {
      const before=JSON.parse(JSON.stringify(store));
      try { const data=fn(item);results.push({id:item.id,success:true,data}); }
      catch(error) {
        // Per-record validation failures are isolated; infrastructure errors abort the entire transaction.
        if(!(error instanceof M.DomainError)) throw error;
        for(const key of Object.keys(before)) store[key]=before[key];
        results.push({id:item.id,success:false,code:error.status,message:error.message});
      }
    }
    ok(res,{results,succeeded:results.filter(r=>r.success).length,failed:results.filter(r=>!r.success).length});
  };
  function importCsv(req,res,kind) {
    if(typeof req.body.request_id!=='string' || !/^[\w-]{8,80}$/.test(req.body.request_id)) M.fail(400,'请提供有效的导入请求编号');
    let csv;try{csv=parseCsv(req.body.csv);}catch(error){M.fail(400,error.message);}
    const aliases=kind==='rules'? {关键词:'term',分类:'category',启用:'enabled'} : {标题:'title',正文:'content',话题:'topics',地点:'location'};
    const headers=csv.headers.map(key=>aliases[key]||key);
    const required=kind==='rules'?['term']:['title','content','topics','location'];
    if(!required.every(key=>headers.includes(key)) || new Set(headers).size!==headers.length) M.fail(400,'CSV 表头不符合模板');
    const id=M.hash([kind,req.user?.id||req.adminUser.id,req.body.request_id]);
    const fingerprint=M.hash(req.body.csv);
    const old=store.importReceipts.find(item=>item.id===id);
    if(old){if(old.fingerprint!==fingerprint)M.fail(409,'同一请求编号不能导入不同内容');return ok(res,old.result);}
    const results=csv.rows.map(row=>{
      try {
        if(row.cells.length!==headers.length) M.fail(400,'列数与表头不一致');
        const data=Object.fromEntries(headers.map((key,index)=>[key,row.cells[index]]));
        if(kind==='rules') {
          if(data.enabled && !['true','false','1','0','是','否'].includes(data.enabled)) M.fail(400,'启用列需为 true/false');
          const rule=M.rule(store,{...data,enabled:!['false','0','否'].includes(data.enabled)});
          audit(req,'keyword_import','keyword',rule.id,{term:rule.term});return {line:row.line,id:rule.id,success:true};
        }
        if(!(data.title||data.content).trim())M.fail(400,'标题与正文不能同时为空');
        const post=normalizePost({...data,id:'import_'+M.hash([id,row.line]).slice(0,32),topics:(data.topics||'').split('|').map(x=>x.trim()).filter(Boolean),is_public:false,is_draft:true},req.user.id);
        M.submit(store,post);store.posts.unshift(post);return {line:row.line,id:post.id,success:true};
      } catch(error) {if(!(error instanceof M.DomainError))throw error;return {line:row.line,success:false,code:error.status,message:error.message};}
    });
    const result={results,succeeded:results.filter(r=>r.success).length,failed:results.filter(r=>!r.success).length};
    store.importReceipts.push({id,fingerprint,result,created_at:new Date().toISOString()});ok(res,result);
  }

  app.get('/api/admin/keyword-rules',admin,wrap((req,res)=>list(res,filtered(store.keywordRules,req).filter(r=>req.query.enabled===undefined||String(r.enabled)===req.query.enabled),req.query)));
  app.post('/api/admin/keyword-rules',admin,wrap((req,res)=>{const r=M.rule(store,req.body);audit(req,'keyword_create','keyword',r.id,r);ok(res,r);}));
  app.post('/api/admin/keyword-rules/import',admin,wrap((req,res)=>importCsv(req,res,'rules')));
  app.post('/api/admin/keyword-rules/batch',admin,wrap((req,res)=>{
    if(!['enable','disable','delete'].includes(req.body.action))M.fail(400,'无效操作');
    executeBatch(req,res,item=>{const r=store.keywordRules.find(r=>r.id===item.id);if(!r)M.fail(404,'关键词不存在');
      if(req.body.action==='delete')store.keywordRules=store.keywordRules.filter(r=>r.id!==item.id);else r.enabled=req.body.action==='enable';
      audit(req,'keyword_'+req.body.action,'keyword',r.id,{rule:r});return r;});
  }));
  app.patch('/api/admin/keyword-rules/:id',admin,wrap((req,res)=>{const r=store.keywordRules.find(r=>r.id===req.params.id);if(!r)M.fail(404,'关键词不存在');M.rule(store,req.body,r);audit(req,'keyword_update','keyword',r.id,r);ok(res,r);}));
  app.delete('/api/admin/keyword-rules/:id',admin,wrap((req,res)=>{const r=store.keywordRules.find(r=>r.id===req.params.id);if(!r)M.fail(404,'关键词不存在');store.keywordRules=store.keywordRules.filter(r=>r.id!==req.params.id);audit(req,'keyword_delete','keyword',r.id,r);ok(res,null);}));

  function caseDetail(item) {
    const post=store.posts.find(p=>p.id===item.postId);
    return {...item,post:post?serializePost(post,post.userId):null,
      reports:store.reports.filter(r=>r.caseId===item.id).map(r=>({...r,reporter:publicUser(store.users.find(u=>u.id===r.userId)||{id:r.userId,username:'未知'})})),
      jobs:store.aiJobs.filter(job=>job.caseId===item.id),events:store.moderationEvents.filter(e=>e.postId===item.postId)};
  }
  app.get('/api/admin/moderation-cases',admin,wrap((req,res)=>{
    let items=store.moderationCases.filter(c=>(!req.query.state||req.query.state==='all'||c.state===req.query.state)&&(!req.query.kind||req.query.kind==='all'||c.kind===req.query.kind)
      &&(!req.query.ai_state||req.query.ai_state==='all'||c.ai_state===req.query.ai_state));
    items=filtered(items,req).sort((a,b)=>b.created_at.localeCompare(a.created_at));list(res,items,req.query);
  }));
  app.get('/api/admin/moderation-cases/:id',admin,wrap((req,res)=>{const c=store.moderationCases.find(c=>c.id===req.params.id);if(!c)M.fail(404,'案件不存在');ok(res,caseDetail(c));}));
  app.post('/api/admin/moderation-cases/:id/decision',admin,wrap((req,res)=>{const c=store.moderationCases.find(c=>c.id===req.params.id);if(!c)M.fail(404,'案件不存在');const p=findPost(c.postId);M.decide(store,p,req.body.decision,req.body.reason,actor(req),req.body.content_version,c.id);audit(req,'case_decision','case',c.id,req.body);ok(res,caseDetail(c));}));
  app.post('/api/admin/moderation-cases/:id/retry-ai',admin,wrap((req,res)=>{
    const c=store.moderationCases.find(c=>c.id===req.params.id);if(!c)M.fail(404,'案件不存在');const p=findPost(c.postId);M.expectVersion(p,req.body.content_version);
    if(c.state!=='open'||c.content_version!==M.version(p))M.fail(409,'案件已变化，请重新扫描当前内容');M.enqueue(store,c);audit(req,'ai_retry','case',c.id,{});ok(res,c);
  }));
  app.post('/api/admin/moderation-cases/:id/rebase',admin,wrap((req,res)=>{
    const old=store.moderationCases.find(c=>c.id===req.params.id);if(!old)M.fail(404,'案件不存在');
    const p=findPost(old.postId);M.expectVersion(p,req.body.content_version);
    if(old.state!=='stale'||p.isDraft)M.fail(409,'仅内容变化的案件可重新确认当前版本');
    const next=M.openCase(store,p,old.kind,M.hits(store,p),old.reason);
    if(old.kind==='publication')p.status='pending';
    for(const r of store.reports.filter(r=>r.caseId===old.id&&r.status==='pending')){r.previous_case_id=old.id;r.caseId=next.id;}
    M.event(store,p,'case_rebased',actor(req),{previous_case_id:old.id,caseId:next.id});audit(req,'case_rebase','case',next.id,{from:old.id});ok(res,caseDetail(next));
  }));
  app.get('/api/admin/posts',admin,wrap((req,res)=>{
    const items=store.posts.filter(p=>req.query.status==='trash'?p.deleted_at:!p.deleted_at).filter(p=>!req.query.status||['all','trash'].includes(req.query.status)|| (req.query.status==='draft'?p.isDraft:!p.isDraft&&p.status===req.query.status))
      .map(p=>serializePost(p,p.userId));const rows=filtered(items,req).sort((a,b)=>(req.query.sort==='likes'?b.like_count-a.like_count:0)||String(b.updated_at||b.created_at).localeCompare(String(a.updated_at||a.created_at))||String(a.id).localeCompare(String(b.id)));list(res,rows,req.query,'posts');
  }));
  app.get('/api/admin/posts/:id',admin,wrap((req,res)=>{
    const p=findPost(req.params.id,null,true);ok(res,{...serializePost(p,p.userId),cases:store.moderationCases.filter(c=>c.postId===p.id),events:store.moderationEvents.filter(e=>e.postId===p.id),reports:store.reports.filter(r=>r.postId===p.id),comments:store.comments.filter(c=>c.postId===p.id)});
  }));
  function batchPost(req,item,isAdmin) {
    const post=findPost(item.id,isAdmin?null:req.user.id,['restore','delete'].includes(req.body.action));
    M.expectVersion(post,item.content_version);
    const action=req.body.action;
    if(['delete','restore'].includes(action))M.recycle(store,post,actor(req),item.content_version,action==='restore');
    else if(isAdmin&&['approve','reject','hide'].includes(action))M.decide(store,post,{approve:'approved',reject:'rejected',hide:'hidden'}[action],req.body.reason,actor(req),item.content_version);
    else if(isAdmin&&action==='retry_ai') {const c=store.moderationCases.find(c=>c.postId===post.id&&c.content_version===M.version(post)&&c.state==='open');if(!c)M.fail(409,'无待审案件，请先重新扫描');M.enqueue(store,c);}
    else if(['submit','public','private','draft','rescan'].includes(action)) {
      const previous=structuredClone(post);
      if(action==='draft')post.isDraft=true;
      if(action==='submit'){post.isDraft=false;post.isPublic=true;}
      if(action==='public')post.isPublic=true;
      if(action==='private')post.isPublic=false;
      M.submit(store,post,previous,['submit','rescan'].includes(action));
    } else M.fail(400,'不支持的操作');
    if(isAdmin)audit(req,'post_'+action,'post',post.id,{version:M.version(post),reason:req.body.reason});
    return serializePost(post,post.userId);
  }
  for(const isAdmin of [true,false]) {
    const route=isAdmin?'/api/admin/posts/batch':'/api/creators/me/posts/batch';
    app.post(route,isAdmin?admin:auth,wrap((req,res)=>{
      const allowed=isAdmin?['approve','reject','hide','rescan','retry_ai','delete','restore']:['submit','public','private','draft','delete','restore'];
      if(!allowed.includes(req.body.action))M.fail(400,'不支持的批量操作');executeBatch(req,res,item=>batchPost(req,item,isAdmin));
    }));
  }
  app.post('/api/creators/me/posts/import',auth,wrap((req,res)=>importCsv(req,res,'posts')));
  app.get('/api/posts/sync',auth,wrap((req,res)=>{
    const ids=String(req.query.ids||'').split(',').filter(Boolean);if(!ids.length||ids.length>100)M.fail(400,'需提供 1～100 个笔记编号');
    ok(res,{posts:store.posts.filter(p=>ids.includes(p.id)).map(p=>p.userId===req.user.id?serializePost(p,req.user.id):{id:p.id,status:p.status,content_version:M.version(p),is_public:p.isPublic,is_draft:p.isDraft,deleted_at:p.deleted_at||null,updated_at:p.updated_at}),missing_ids:ids.filter(id=>!store.posts.some(p=>p.id===id))});
  }));
  app.get('/api/creators/me/posts',auth,wrap((req,res)=>{
    const filter=req.query.filter||'all';const checks={all:p=>!p.deleted_at,published:p=>!p.deleted_at&&!p.isDraft&&p.isPublic&&p.status==='approved',private:p=>!p.deleted_at&&!p.isDraft&&!p.isPublic,draft:p=>!p.deleted_at&&p.isDraft,pending:p=>!p.deleted_at&&!p.isDraft&&p.status==='pending',rejected:p=>!p.deleted_at&&!p.isDraft&&['rejected','hidden'].includes(p.status),trash:p=>!!p.deleted_at};
    if(!Object.hasOwn(checks,filter))M.fail(400,'无效筛选');list(res,filtered(store.posts.filter(p=>p.userId===req.user.id&&checks[filter](p)).map(p=>serializePost(p,req.user.id)),req).sort((a,b)=>String(b.created_at).localeCompare(String(a.created_at))),req.query,'posts');
  }));
  function savePost(req,res,draft,update=false) {
    const id=update?req.params.id:req.body.id;
    const previous=id?store.posts.find(p=>p.id===id):null;
    if(update&&!previous)M.fail(404,'笔记不存在');
    if(previous){if(previous.userId!==req.user.id)M.fail(409,'笔记编号已存在');if(previous.deleted_at)M.fail(409,'请先从回收站恢复');M.expectVersion(previous,req.body.content_version);}
    const body={...req.body};delete body.status;
    for(const key of ['is_public','is_draft'])if(body[key]!==undefined&&typeof body[key]!=='boolean')M.fail(400,'可见范围和草稿标记必须为布尔值');
    if(!update)body.is_draft=draft;
    const post=normalizePost(body,req.user.id,previous||{});
    if(!post.isDraft&&!post.title&&!post.content&&!post.images.length&&!post.videoUrl)M.fail(400,'请输入内容或添加媒体');
    ensurePosterForTextPost(post,req.user.username);
    M.submit(store,post,previous,req.body.resubmit===true);
    if(previous)store.posts[store.posts.indexOf(previous)]=post;else store.posts.unshift(post);
    ok(res,serializePost(post,req.user.id),post.isDraft?'草稿已保存':!post.isPublic?'私密笔记已保存':post.status==='pending'?'已提交，等待审核':'发布成功');
  }
  app.post('/api/posts',auth,wrap((req,res)=>savePost(req,res,false)));
  app.post('/api/posts/drafts',auth,wrap((req,res)=>savePost(req,res,true)));
  app.put('/api/posts/:id',auth,wrap((req,res)=>savePost(req,res,false,true)));
  app.delete('/api/posts/:id',auth,wrap((req,res)=>{const p=findPost(req.params.id,req.user.id);M.recycle(store,p,actor(req),req.body.content_version);ok(res,null,'已移入回收站');}));
  app.delete('/api/admin/posts/:id',admin,wrap((req,res)=>{const p=findPost(req.params.id);M.recycle(store,p,actor(req),req.body.content_version);audit(req,'post_delete','post',p.id,{});ok(res,null,'已移入回收站');}));
  app.patch('/api/admin/posts/:id/review',admin,wrap((req,res)=>{const p=findPost(req.params.id);M.decide(store,p,req.body.status,req.body.note,actor(req),req.body.content_version);audit(req,'post_review','post',p.id,req.body);ok(res,serializePost(p,p.userId));}));
  app.patch('/api/admin/posts/:id/status',admin,wrap((req,res)=>{const p=findPost(req.params.id);M.expectVersion(p,req.body.content_version);const previous=structuredClone(p);p.isDraft=req.body.status!=='published';M.submit(store,p,previous,true);audit(req,'post_status','post',p.id,req.body);ok(res,serializePost(p,p.userId));}));
  app.post('/api/posts/:id/report',auth,wrap((req,res)=>{
    const p=findPost(req.params.id);if(p.isDraft||!p.isPublic||p.status!=='approved')M.fail(404,'笔记不存在');
    const reason=String(req.body.reason||'').trim();if(!reason||reason.length>500)M.fail(400,'请填写 1～500 字举报理由');ok(res,M.report(store,p,req.user.id,reason),'举报已进入人工再审');
  }));
  app.patch('/api/admin/reports/:id/status',admin,wrap((req,res)=>{
    const r=store.reports.find(r=>r.id===req.params.id);if(!r)M.fail(404,'举报不存在');
    if(!['resolved','dismissed'].includes(req.body.status))M.fail(400,'请选择处理结论');
    const p=findPost(r.postId);let c=store.moderationCases.find(c=>c.id===r.caseId);
    if(!c){c=M.openCase(store,p,'report',M.hits(store,p),r.reason);r.caseId=c.id;}
    M.decide(store,p,req.body.status==='resolved'?'hidden':'keep',req.body.note,actor(req),req.body.content_version,c.id);audit(req,'report_handle','report',r.id,req.body);ok(res,r);
  }));
  app.get('/api/admin/reports',admin,wrap((req,res)=>list(res,filtered(store.reports,req).filter(r=>!req.query.status||req.query.status==='all'||r.status===req.query.status),req.query,'reports')));
  app.get('/api/admin/users',admin,wrap((req,res)=>list(res,filtered(store.users.map(publicUser),req).filter(u=>!req.query.status||req.query.status==='all'||u.status===req.query.status),req.query,'users')));
  app.get('/api/admin/comments',admin,wrap((req,res)=>list(res,filtered(store.comments.slice().reverse().map(c=>({...c,user:publicUser(store.users.find(u=>u.id===c.userId)||{id:c.userId}),post_title:store.posts.find(p=>p.id===c.postId)?.title||''})),req),req.query,'comments')));
  app.get('/api/admin/audit',admin,wrap((req,res)=>list(res,filtered(store.auditLogs,req),req.query,'logs')));
}
module.exports={mountModeration,paginate};
