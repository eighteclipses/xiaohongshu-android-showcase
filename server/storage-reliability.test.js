const test=require('node:test');const assert=require('node:assert/strict');const mysql=require('mysql2/promise');
const storage=require('./storage');
function mysqlEnvironment(){const old={DATA_FILE:process.env.DATA_FILE,STORAGE:process.env.STORAGE};delete process.env.DATA_FILE;process.env.STORAGE='mysql';return()=>{for(const [key,value]of Object.entries(old))if(value===undefined)delete process.env[key];else process.env[key]=value;};}
test('configured MySQL failure never silently switches to empty-rule JSON',async()=>{
 const restore=mysqlEnvironment(),original=mysql.createPool;
 mysql.createPool=()=>({query:async()=>{throw Object.assign(new Error('unavailable'),{code:'ECONNREFUSED'});},end:async()=>{}});
 try{await assert.rejects(storage.load({posts:[],keywordRules:[]}),e=>e.code==='ECONNREFUSED');}finally{mysql.createPool=original;restore();}
});
test('queued snapshots compare against the last committed state, including delete then restore',async()=>{
 const restore=mysqlEnvironment(),original=mysql.createPool;let exists=true;const commands=[];
 const row={id:'p1',user_id:'u1',title:'original',content:'body',images:[],topics:[],location:'',is_public:1,is_draft:0,status:'approved',content_version:1,created_at:new Date(),updated_at:new Date()};
 const connection={beginTransaction:async()=>{},commit:async()=>{},rollback:async()=>{},release:()=>{},execute:async(sql,values)=>{commands.push(sql);if(sql.startsWith('DELETE FROM posts '))exists=false;if(sql.startsWith('INSERT INTO posts '))exists=true;return[[]];}};
 mysql.createPool=()=>({query:async sql=>{
   if(sql.startsWith('SHOW COLUMNS'))return[[...['content_version','deleted_at','moderation_meta','account_meta'].map(Field=>({Field}))]];
   if(sql==='SELECT * FROM posts ORDER BY created_at DESC')return[[row]];
   return[[]];
 },getConnection:async()=>connection,end:async()=>{}});
 try {
   const initial=await storage.load({});
   await Promise.all([storage.persist({...initial,posts:[]}),storage.persist(initial)]);
   assert.equal(exists,true,'second snapshot must restore the deleted row');
   assert.ok(commands.some(sql=>sql.startsWith('INSERT INTO posts ')));
 }finally{await storage.close();mysql.createPool=original;restore();}
});
