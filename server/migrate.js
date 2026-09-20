require('dotenv').config();
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const mysql = require('mysql2/promise');

function parse(value, fallback) { try { return JSON.parse(value); } catch { return fallback; } }
function date(value) { const d = value ? new Date(value) : new Date(); return Number.isNaN(d.getTime()) ? new Date() : d; }

async function main() {
  const config = {
    host: process.env.DB_HOST || process.env.MYSQL_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || process.env.MYSQL_PORT || 3306),
    user: process.env.DB_USER || process.env.MYSQL_USER || 'root',
    password: process.env.DB_PASSWORD ?? process.env.MYSQL_PASSWORD ?? '',
    database: process.env.DB_NAME || process.env.MYSQL_DATABASE || 'xiaohongshu'
  };
  const databaseName = config.database;
  const adminConnection = await mysql.createConnection({ ...config, database: undefined });
  const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  await adminConnection.query(`CREATE DATABASE IF NOT EXISTS \`${databaseName}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`);
  await adminConnection.end();
  const connection = await mysql.createConnection({ ...config, database: databaseName, multipleStatements: true });
  const schemaWithoutCreate = schema.replace(/CREATE DATABASE[\s\S]*?;\s*USE `?xiaohongshu`?;\s*/i, '');
  await connection.query(schemaWithoutCreate);
  try { await connection.query("ALTER TABLE users ADD COLUMN avatar_uri VARCHAR(500) NOT NULL DEFAULT ''"); } catch (error) {
    if (error.code !== 'ER_DUP_FIELDNAME') throw error;
  }
  // 已有库的幂等列补齐：审核状态、举报处理信息、用户背景与钱包
  const additiveColumns = [
    "ALTER TABLE users ADD COLUMN background VARCHAR(800) NOT NULL DEFAULT ''",
    "ALTER TABLE users ADD COLUMN coins INT NOT NULL DEFAULT 5000",
    "ALTER TABLE posts ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'approved'",
    "ALTER TABLE reports ADD COLUMN note VARCHAR(200) NOT NULL DEFAULT ''",
    "ALTER TABLE reports ADD COLUMN handled_by VARCHAR(64) NOT NULL DEFAULT ''",
    "ALTER TABLE reports ADD COLUMN handled_at DATETIME(3) NULL"
  ];
  for (const statement of additiveColumns) {
    try { await connection.query(statement); } catch (error) {
      if (error.code !== 'ER_DUP_FIELDNAME') throw error;
    }
  }
  const dataFile = path.resolve(process.env.DATA_FILE || path.join(__dirname, 'data.json'));
  const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
  // Idempotent migration: only fill missing rows and never delete current user data.
  for (const user of data.users || []) await connection.execute('INSERT IGNORE INTO users (id,username,nickname,password_hash,avatar,avatar_uri,bio,created_at) VALUES (?,?,?,?,?,?,?,?)', [user.id,user.username,user.nickname || user.username,user.passwordHash,user.avatar || '',user.avatarUri || '',user.bio || '',date(user.created_at)]);
  for (const post of data.posts || []) await connection.execute('INSERT IGNORE INTO posts (id,user_id,title,content,images,topics,location,is_public,is_draft,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)', [post.id,post.userId,post.title || '',post.content || '',JSON.stringify(post.images || []),JSON.stringify(post.topics || []),post.location || '',post.isPublic !== false,post.isDraft === true,date(post.created_at),date(post.updated_at)]);
  for (const item of data.likes || []) await connection.execute('INSERT IGNORE INTO likes (post_id,user_id,created_at) VALUES (?,?,?)', [item.postId,item.userId,date(item.created_at)]);
  for (const item of data.collections || []) await connection.execute('INSERT IGNORE INTO collections (post_id,user_id,created_at) VALUES (?,?,?)', [item.postId,item.userId,date(item.created_at)]);
  for (const item of data.follows || []) await connection.execute('INSERT IGNORE INTO follows (follower_id,following_id,created_at) VALUES (?,?,?)', [item.followerId,item.followingId,date(item.created_at)]);
  for (const item of data.comments || []) await connection.execute('INSERT IGNORE INTO comments (id,post_id,user_id,content,created_at) VALUES (?,?,?,?,?)', [item.id,item.postId,item.userId,item.content,date(item.created_at)]);
  for (const item of data.notifications || []) await connection.execute('INSERT IGNORE INTO notifications (id,user_id,actor_id,type,post_id,read_flag,created_at) VALUES (?,?,?,?,?,?,?)', [item.id,item.userId,item.actorId,item.type,item.postId || null,item.read === true,date(item.created_at)]);
  const adminUsername = process.env.ADMIN_USERNAME || 'admin';
  // 未配置 ADMIN_PASSWORD 时生成随机密码并打印一次，不再使用固定弱口令
  const adminPassword = process.env.ADMIN_PASSWORD || crypto.randomBytes(8).toString('hex');
  if (!process.env.ADMIN_PASSWORD) {
    console.log(`[migrate] 已为管理员 ${adminUsername} 生成随机密码: ${adminPassword}`);
  }
  await connection.execute('INSERT IGNORE INTO admin_users (id,username,password_hash,role,created_at) VALUES (?,?,?,?,?)', ['admin-default',adminUsername,await bcrypt.hash(adminPassword,10),'admin',new Date()]);
  await connection.end();
  console.log(`mysql-migration-ok database=${config.database} admin=${adminUsername}`);
}
main().catch(error => { console.error(`mysql-migration-failed: ${error.message}`); process.exitCode = 1; });
