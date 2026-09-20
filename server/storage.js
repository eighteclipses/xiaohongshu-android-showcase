const mysql = require('mysql2/promise');
const { migrate, TABLES } = require('./migrate-moderation');

let pool = null;
let activeMode = 'json';
let writeQueue = Promise.resolve();
let lastSynced = null;

function wantsMySql() {
  if (process.env.DATA_FILE || String(process.env.STORAGE || '').toLowerCase() === 'json') return false;
  return String(process.env.STORAGE || 'mysql').toLowerCase() === 'mysql';
}

function dateValue(value) {
  const date = value ? new Date(value) : new Date();
  return Number.isNaN(date.getTime()) ? new Date() : date;
}

function parseJson(value, fallback) {
  if (Array.isArray(value) || (value && typeof value === 'object')) return value;
  try { return JSON.parse(value); } catch { return fallback; }
}

async function load(initialStore) {
  if (!wantsMySql()) return initialStore;
  try {
    const config = {
      host: process.env.DB_HOST || process.env.MYSQL_HOST || '127.0.0.1',
      port: Number(process.env.DB_PORT || process.env.MYSQL_PORT || 3306),
      user: process.env.DB_USER || process.env.MYSQL_USER || 'root',
      password: process.env.DB_PASSWORD ?? process.env.MYSQL_PASSWORD ?? '',
      database: process.env.DB_NAME || process.env.MYSQL_DATABASE || 'xiaohongshu',
      waitForConnections: true,
      connectionLimit: Number(process.env.DB_POOL_SIZE || 10),
      charset: 'utf8mb4'
    };
    pool = mysql.createPool(config);
    await pool.query('SELECT 1');
    await migrate(pool);
    const [users] = await pool.query('SELECT * FROM users ORDER BY created_at DESC');
    const [admins] = await pool.query('SELECT id, username, password_hash, role, created_at FROM admin_users ORDER BY created_at DESC');
    const [posts] = await pool.query('SELECT * FROM posts ORDER BY created_at DESC');
    const [comments] = await pool.query('SELECT id, post_id, user_id, content, created_at FROM comments ORDER BY created_at ASC');
    const [likes] = await pool.query('SELECT post_id, user_id, created_at FROM likes');
    const [collections] = await pool.query('SELECT post_id, user_id, created_at FROM collections');
    const [follows] = await pool.query('SELECT follower_id, following_id, created_at FROM follows');
    const [notifications] = await pool.query('SELECT id, user_id, actor_id, type, post_id, read_flag, created_at, moderation_meta FROM notifications ORDER BY created_at DESC');
    const [views] = await pool.query('SELECT id, post_id, user_id, visitor_key, view_date, created_at FROM post_views ORDER BY created_at DESC');
    const [reports] = await pool.query('SELECT id, post_id, user_id, reason, status, note, handled_by, handled_at, created_at, moderation_meta FROM reports ORDER BY created_at DESC');
    const [auditLogs] = await pool.query('SELECT id, admin_id, action, target_type, target_id, detail, created_at FROM audit_logs ORDER BY created_at DESC');
    const [directMessages] = await pool.query('SELECT id, from_id, to_id, payload, created_at FROM direct_messages ORDER BY created_at ASC');
    activeMode = 'mysql';
    const result = {
      users: users.map(row => ({ ...parseJson(row.account_meta, {}), id: row.id, username: row.username, nickname: row.nickname, passwordHash: row.password_hash, avatar: row.avatar, avatarUri: row.avatar_uri || '', background: row.background || '', bio: row.bio, coins: row.coins ?? 5000, created_at: dateValue(row.created_at).toISOString() })),
      adminUsers: admins.map(row => ({ id: row.id, username: row.username, passwordHash: row.password_hash, role: row.role, created_at: dateValue(row.created_at).toISOString() })),
      posts: posts.map(row => ({ ...parseJson(row.moderation_meta, {}), content_version: row.content_version || 1, deleted_at: row.deleted_at ? dateValue(row.deleted_at).toISOString() : null, id: row.id, userId: row.user_id, title: row.title, content: row.content, images: parseJson(row.images, []), topics: parseJson(row.topics, []), location: row.location, isPublic: Boolean(row.is_public), isDraft: Boolean(row.is_draft), status: row.status || 'approved', mediaType: row.media_type === 'video' ? 'video' : 'image', videoUrl: row.video_url || '', created_at: dateValue(row.created_at).toISOString(), updated_at: dateValue(row.updated_at).toISOString() })),
      comments: comments.map(row => ({ id: row.id, postId: row.post_id, userId: row.user_id, content: row.content, created_at: dateValue(row.created_at).toISOString() })),
      likes: likes.map(row => ({ postId: row.post_id, userId: row.user_id, created_at: dateValue(row.created_at).toISOString() })),
      collections: collections.map(row => ({ postId: row.post_id, userId: row.user_id, created_at: dateValue(row.created_at).toISOString() })),
      follows: follows.map(row => ({ followerId: row.follower_id, followingId: row.following_id, created_at: dateValue(row.created_at).toISOString() })),
      notifications: notifications.map(row => ({ ...parseJson(row.moderation_meta, {}), id: row.id, userId: row.user_id, actorId: row.actor_id, type: row.type, postId: row.post_id, read: Boolean(row.read_flag), created_at: dateValue(row.created_at).toISOString() })),
      views: views.map(row => ({ id: row.id, postId: row.post_id, userId: row.user_id, visitorKey: row.visitor_key, viewDate: row.view_date, created_at: dateValue(row.created_at).toISOString() })),
      reports: reports.map(row => ({ ...parseJson(row.moderation_meta, {}), id: row.id, postId: row.post_id, userId: row.user_id, reason: row.reason, status: row.status, note: row.note || '', handled_by: row.handled_by || '', handled_at: row.handled_at ? dateValue(row.handled_at).toISOString() : '', created_at: dateValue(row.created_at).toISOString() })),
      auditLogs: auditLogs.map(row => ({ id: row.id, adminId: row.admin_id, action: row.action, targetType: row.target_type, targetId: row.target_id, detail: parseJson(row.detail, {}), created_at: dateValue(row.created_at).toISOString() })),
      directMessages: directMessages.map(row => ({ ...parseJson(row.payload, {}), id: row.id, fromId: row.from_id, toId: row.to_id, created_at: dateValue(row.created_at).toISOString() }))
    };
    for (const [key, table] of Object.entries(TABLES)) {
      const [rows] = await pool.query(`SELECT payload FROM ${table}`);
      result[key] = rows.map(row => parseJson(row.payload, {}));
    }
    const [metadata] = await pool.query("SELECT payload FROM app_metadata WHERE id='blocks'");
    result.blocks = metadata.length ? parseJson(metadata[0].payload, []) : [];
    lastSynced = JSON.parse(JSON.stringify(result));
    return result;
  } catch (error) {
    activeMode = 'json';
    if (pool) await pool.end().catch(() => {});
    pool = null;
    // Falling back here would lose the active moderation rules and could approve blocked text.
    throw error;
  }
}

function mode() { return activeMode; }

async function persist(store) {
  if (activeMode !== 'mysql' || !pool) return;
  const snapshot = JSON.parse(JSON.stringify(store));
  const run = async () => {
    const previous = lastSynced || {};
    const connection = await pool.getConnection();
    try {
      await connection.beginTransaction();
      await syncTable(connection, 'users', ['id','username','nickname','password_hash','avatar','avatar_uri','background','bio','coins','created_at','account_meta'], [0],
        (snapshot.users || []).map(u => [u.id,u.username,u.nickname || u.username,u.passwordHash,u.avatar || '',u.avatarUri || '',u.background || '',u.bio || '',u.coins ?? 5000,dateValue(u.created_at),JSON.stringify({status:u.status || 'active',ban_reason:u.ban_reason || ''})]),
        (previous.users || []).map(u => [u.id,u.username,u.nickname || u.username,u.passwordHash,u.avatar || '',u.avatarUri || '',u.background || '',u.bio || '',u.coins ?? 5000,dateValue(u.created_at),JSON.stringify({status:u.status || 'active',ban_reason:u.ban_reason || ''})]));
      await syncTable(connection, 'admin_users', ['id','username','password_hash','role','created_at'], [0],
        (snapshot.adminUsers || []).map(u => [u.id,u.username,u.passwordHash,u.role || 'admin',dateValue(u.created_at)]),
        (previous.adminUsers || []).map(u => [u.id,u.username,u.passwordHash,u.role || 'admin',dateValue(u.created_at)]));
      await syncTable(connection, 'posts', ['id','user_id','title','content','images','topics','location','is_public','is_draft','status','media_type','video_url','created_at','updated_at','content_version','deleted_at','moderation_meta'], [0],
        (snapshot.posts || []).map(p => [p.id,p.userId,p.title || '',p.content || '',JSON.stringify(p.images || []),JSON.stringify(p.topics || []),p.location || '',p.isPublic !== false,p.isDraft === true,p.status || 'approved',p.mediaType === 'video' ? 'video' : 'image',p.videoUrl || '',dateValue(p.created_at),dateValue(p.updated_at),p.content_version || 1,p.deleted_at ? dateValue(p.deleted_at) : null,JSON.stringify({review_note:p.review_note || '',review_at:p.review_at || '',requires_manual:!!p.requires_manual,last_review_status:p.last_review_status || '',last_content_hash:p.last_content_hash || ''})]),
        (previous.posts || []).map(p => [p.id,p.userId,p.title || '',p.content || '',JSON.stringify(p.images || []),JSON.stringify(p.topics || []),p.location || '',p.isPublic !== false,p.isDraft === true,p.status || 'approved',p.mediaType === 'video' ? 'video' : 'image',p.videoUrl || '',dateValue(p.created_at),dateValue(p.updated_at),p.content_version || 1,p.deleted_at ? dateValue(p.deleted_at) : null,JSON.stringify({review_note:p.review_note || '',review_at:p.review_at || '',requires_manual:!!p.requires_manual,last_review_status:p.last_review_status || '',last_content_hash:p.last_content_hash || ''})]));
      await syncTable(connection, 'likes', ['post_id','user_id','created_at'], [0,1],
        (snapshot.likes || []).map(l => [l.postId,l.userId,dateValue(l.created_at)]),
        (previous.likes || []).map(l => [l.postId,l.userId,dateValue(l.created_at)]));
      await syncTable(connection, 'collections', ['post_id','user_id','created_at'], [0,1],
        (snapshot.collections || []).map(l => [l.postId,l.userId,dateValue(l.created_at)]),
        (previous.collections || []).map(l => [l.postId,l.userId,dateValue(l.created_at)]));
      await syncTable(connection, 'follows', ['follower_id','following_id','created_at'], [0,1],
        (snapshot.follows || []).map(f => [f.followerId,f.followingId,dateValue(f.created_at)]),
        (previous.follows || []).map(f => [f.followerId,f.followingId,dateValue(f.created_at)]));
      await syncTable(connection, 'comments', ['id','post_id','user_id','content','created_at'], [0],
        (snapshot.comments || []).map(c => [c.id,c.postId,c.userId,c.content,dateValue(c.created_at)]),
        (previous.comments || []).map(c => [c.id,c.postId,c.userId,c.content,dateValue(c.created_at)]));
      await syncTable(connection, 'notifications', ['id','user_id','actor_id','type','post_id','read_flag','created_at','moderation_meta'], [0],
        (snapshot.notifications || []).map(n => [n.id,n.userId,n.actorId,n.type,n.postId || null,n.read === true,dateValue(n.created_at),JSON.stringify({message:n.message || ''})]),
        (previous.notifications || []).map(n => [n.id,n.userId,n.actorId,n.type,n.postId || null,n.read === true,dateValue(n.created_at),JSON.stringify({message:n.message || ''})]));
      await syncTable(connection, 'post_views', ['id','post_id','user_id','visitor_key','view_date','created_at'], [0],
        (snapshot.views || []).filter(v => v.id && v.postId).map(v => [v.id,v.postId,v.userId || null,v.visitorKey,v.viewDate,dateValue(v.created_at)]),
        (previous.views || []).filter(v => v.id && v.postId).map(v => [v.id,v.postId,v.userId || null,v.visitorKey,v.viewDate,dateValue(v.created_at)]));
      await syncTable(connection, 'reports', ['id','post_id','user_id','reason','status','note','handled_by','handled_at','created_at','moderation_meta'], [0],
        (snapshot.reports || []).map(r => [r.id,r.postId,r.userId,r.reason,r.status || 'pending',r.note || '',r.handled_by || '',r.handled_at ? dateValue(r.handled_at) : null,dateValue(r.created_at),JSON.stringify({caseId:r.caseId || '',content_version:r.content_version || 1,previous_case_id:r.previous_case_id || ''})]),
        (previous.reports || []).map(r => [r.id,r.postId,r.userId,r.reason,r.status || 'pending',r.note || '',r.handled_by || '',r.handled_at ? dateValue(r.handled_at) : null,dateValue(r.created_at),JSON.stringify({caseId:r.caseId || '',content_version:r.content_version || 1,previous_case_id:r.previous_case_id || ''})]));
      await syncTable(connection, 'audit_logs', ['id','admin_id','action','target_type','target_id','detail','created_at'], [0],
        (snapshot.auditLogs || []).map(log => [log.id,log.adminId,log.action,log.targetType,log.targetId,JSON.stringify(log.detail || {}),dateValue(log.created_at)]),
        (previous.auditLogs || []).map(log => [log.id,log.adminId,log.action,log.targetType,log.targetId,JSON.stringify(log.detail || {}),dateValue(log.created_at)]));
      await syncTable(connection, 'direct_messages', ['id','from_id','to_id','payload','created_at'], [0],
        (snapshot.directMessages || []).filter(m => m.fromId && m.toId).map(m => [m.id,m.fromId,m.toId,JSON.stringify(m),dateValue(m.created_at)]),
        (previous.directMessages || []).filter(m => m.fromId && m.toId).map(m => [m.id,m.fromId,m.toId,JSON.stringify(m),dateValue(m.created_at)]));
      for (const [key, table] of Object.entries(TABLES)) {
        await syncTable(connection, table, ['id','payload'], [0], (snapshot[key] || []).map(item => [item.id,JSON.stringify(item)]), (previous[key] || []).map(item => [item.id,JSON.stringify(item)]));
      }
      await connection.execute("INSERT INTO app_metadata (id,payload) VALUES ('blocks',?) ON DUPLICATE KEY UPDATE payload=VALUES(payload)", [JSON.stringify(snapshot.blocks || [])]);
      await connection.commit();
      lastSynced = snapshot;
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally { connection.release(); }
  };
  writeQueue = writeQueue.then(run, run);
  return writeQueue;
}

async function syncTable(connection, table, columns, keyIndexes, currentRows, previousRows) {
  const currentMap = new Map((currentRows || []).map(row => [rowKey(row, keyIndexes), row]));
  const previousMap = new Map((previousRows || []).map(row => [rowKey(row, keyIndexes), row]));
  const mutableColumns = columns.filter((column, index) => !keyIndexes.includes(index));
  for (const [key, row] of currentMap) {
    const old = previousMap.get(key);
    if (old && JSON.stringify(old) === JSON.stringify(row)) continue;
    const placeholders = columns.map(() => '?').join(',');
    const updates = mutableColumns.map(column => `${column}=VALUES(${column})`).join(',');
    await connection.execute(
      `INSERT INTO ${table} (${columns.join(',')}) VALUES (${placeholders}) ON DUPLICATE KEY UPDATE ${updates}`,
      row
    );
  }
  for (const [key, row] of previousMap) {
    if (currentMap.has(key)) continue;
    const where = keyIndexes.map(index => `${columns[index]} = ?`).join(' AND ');
    await connection.execute(`DELETE FROM ${table} WHERE ${where}`, keyIndexes.map(index => row[index]));
  }
}

function rowKey(row, keyIndexes) {
  return keyIndexes.map(index => String(row[index] ?? '')).join('\u0001');
}

async function close() { await writeQueue.catch(() => {}); if (pool) await pool.end(); }

module.exports = { load, persist, close, mode };
