require('dotenv').config();
const TABLES = { keywordRules: 'moderation_rules', moderationCases: 'moderation_cases', aiJobs: 'moderation_jobs', moderationEvents: 'moderation_events', importReceipts: 'import_receipts' };
async function migrate(connection) {
  const columns = {
    posts: { content_version: 'INT NOT NULL DEFAULT 1', deleted_at: 'DATETIME(3) NULL', moderation_meta: 'JSON NULL' },
    reports: { moderation_meta: 'JSON NULL' }, notifications: { moderation_meta: 'JSON NULL' }, users: { account_meta: 'JSON NULL' }
  };
  for (const [table, fields] of Object.entries(columns)) {
    const [existing] = await connection.query('SHOW COLUMNS FROM `' + table + '`');
    for (const [column, definition] of Object.entries(fields)) {
      if (!existing.some(item => item.Field === column)) await connection.query(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
    }
  }
  for (const table of Object.values(TABLES)) {
    await connection.query(`CREATE TABLE IF NOT EXISTS ${table} (id VARCHAR(100) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB`);
  }
  await connection.query('CREATE TABLE IF NOT EXISTS app_metadata (id VARCHAR(64) PRIMARY KEY, payload JSON NOT NULL) ENGINE=InnoDB');
}
if (require.main === module) (async () => {
  const mysql = require('mysql2/promise');
  const connection = await mysql.createConnection({ host: process.env.DB_HOST || '127.0.0.1', port: Number(process.env.DB_PORT || 3306), user: process.env.DB_USER || 'root', password: process.env.DB_PASSWORD || '', database: process.env.DB_NAME || 'xiaohongshu' });
  try { await migrate(connection); console.log('moderation-schema-ready'); } finally { await connection.end(); }
})().catch(error => { console.error(error.code || error.message); process.exitCode = 1; });
module.exports = { migrate, TABLES };
