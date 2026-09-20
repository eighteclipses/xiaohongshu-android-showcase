require('dotenv').config();
const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const storage = require('./storage');
const { ensureTextPoster } = require('./text-poster');
const { creatorStats, isPublicPost, canReadPost } = require('./creator-stats');
const { mountAiRoutes } = require('./ai-assistant');
const moderation = require('./moderation');

const app = express();
const port = Number(process.env.PORT || 3001);
// 不提供不安全的兜底密钥：JWT_SECRET 缺失时 start() 直接启动失败
const jwtSecret = process.env.JWT_SECRET;
const publicDir = path.join(__dirname, 'public');
const uploadDir = path.join(__dirname, 'uploads');
const dataFile = process.env.DATA_FILE
  ? path.resolve(process.env.DATA_FILE)
  : path.join(__dirname, 'data.json');

// 基础安全响应头
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  next();
});
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.static(publicDir, { index: false }));
fs.mkdirSync(uploadDir, { recursive: true });
app.use('/uploads', express.static(uploadDir));

const UPLOAD_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.mp4', '.webm'];
const UPLOAD_EXTENSIONS_IMAGE = ['.jpg', '.jpeg', '.png', '.gif', '.webp'];
const UPLOAD_EXTENSIONS_VIDEO = ['.mp4', '.webm'];
const upload = multer({
  storage: multer.diskStorage({
    destination: uploadDir,
    filename: (req, file, callback) => {
      const extension = path.extname(file.originalname || '').toLowerCase() || '.jpg';
      callback(null, `${Date.now()}-${crypto.randomUUID()}${extension}`);
    }
  }),
  limits: { fileSize: 50 * 1024 * 1024, files: 9 },
  fileFilter: (req, file, callback) => {
    const extension = path.extname(file.originalname || '').toLowerCase();
    const isImage = /^image\//i.test(file.mimetype) && UPLOAD_EXTENSIONS_IMAGE.includes(extension);
    const isVideo = /^video\//i.test(file.mimetype) && UPLOAD_EXTENSIONS_VIDEO.includes(extension);
    callback(null, isImage || isVideo);
  }
});

// 上传内容魔数校验：MIME/后缀可伪造，落盘后读文件头确认真的是图片/视频
const IMAGE_MAGIC_SIGNATURES = [
  { bytes: [0xff, 0xd8, 0xff], type: 'image/jpeg' },
  { bytes: [0x89, 0x50, 0x4e, 0x47], type: 'image/png' },
  { bytes: [0x47, 0x49, 0x46, 0x38], type: 'image/gif' }
];

function detectMediaType(filePath) {
  try {
    const handle = fs.openSync(filePath, 'r');
    const header = Buffer.alloc(12);
    const read = fs.readSync(handle, header, 0, 12, 0);
    fs.closeSync(handle);
    if (read >= 12 && header.toString('ascii', 0, 4) === 'RIFF' && header.toString('ascii', 8, 12) === 'WEBP') {
      return 'image/webp';
    }
    // MP4：偏移 4-8 为 'ftyp' box 类型
    if (read >= 8 && header.toString('ascii', 4, 8) === 'ftyp') {
      return 'video/mp4';
    }
    // WebM/MKV：EBML 魔数 0x1A45DFA3
    if (read >= 4 && header[0] === 0x1a && header[1] === 0x45 && header[2] === 0xdf && header[3] === 0xa3) {
      return 'video/webm';
    }
    const signature = IMAGE_MAGIC_SIGNATURES.find(sig =>
      sig.bytes.every((byte, index) => header[index] === byte));
    return signature ? signature.type : null;
  } catch {
    return null;
  }
}

// 登录/注册接口的内存滑动窗口限流（无外部依赖）
const rateLimitBuckets = new Map();
function rateLimit(windowMs, max) {
  return (req, res, next) => {
    const key = req.ip || (req.socket && req.socket.remoteAddress) || 'unknown';
    const now = Date.now();
    const timestamps = (rateLimitBuckets.get(key) || []).filter(ts => now - ts < windowMs);
    if (timestamps.length >= max) {
      return res.status(429).json({ code: 429, message: '请求过于频繁，请稍后再试' });
    }
    timestamps.push(now);
    rateLimitBuckets.set(key, timestamps);
    return next();
  };
}

function emptyStore() {
  return { users: [], adminUsers: [], posts: [], comments: [], likes: [], collections: [], follows: [], notifications: [], views: [], reports: [], auditLogs: [], directMessages: [], blocks: [] };
}

function readStore() {
  try {
    const value = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
    return { ...emptyStore(), ...value };
  } catch (error) {
    return emptyStore();
  }
}

const transactions = require('./transaction-store').createStore(readStore(), persistSnapshot);
const store = transactions.store;
moderation.ensure(store);
app.use(transactions.middleware);
if (!Array.isArray(store.adminUsers)) store.adminUsers = [];
if (!Array.isArray(store.views)) store.views = [];
if (!Array.isArray(store.reports)) store.reports = [];
if (!Array.isArray(store.auditLogs)) store.auditLogs = [];
if (!Array.isArray(store.directMessages)) store.directMessages = [];
if (!Array.isArray(store.blocks)) store.blocks = [];

function ensureDefaultAdmin() {
  const username = process.env.ADMIN_USERNAME || 'admin';
  if (store.adminUsers.some(admin => admin.username === username)) return;
  // 未配置 ADMIN_PASSWORD 时生成随机密码并打印一次，不再使用固定弱口令
  const adminPassword = process.env.ADMIN_PASSWORD || crypto.randomBytes(8).toString('hex');
  if (!process.env.ADMIN_PASSWORD) {
    console.log(`[init] 已为管理员 ${username} 生成随机密码: ${adminPassword}`);
  }
  store.adminUsers.push({
    id: `admin-${crypto.randomUUID()}`,
    username,
    passwordHash: bcrypt.hashSync(adminPassword, 10),
    role: 'admin',
    created_at: new Date().toISOString()
  });
}
ensureDefaultAdmin();

function writeStore() { return transactions.write(); }

function persistSnapshot(snapshot) {
  if (storage.mode() === 'mysql') return storage.persist(snapshot);
  const temporaryFile = `${dataFile}.tmp`;
  fs.mkdirSync(path.dirname(dataFile), { recursive: true });
  fs.writeFileSync(temporaryFile, JSON.stringify(snapshot, null, 2), 'utf8');
  fs.renameSync(temporaryFile, dataFile);
  return Promise.resolve();
}

function publicUser(user) {
  const stats = userStats(user.id);
  return {
    id: user.id,
    username: user.username,
    nickname: user.nickname,
    avatar: user.avatar || user.avatarUri || '',
    background: user.background || '',
    bio: user.bio || '',
    status: user.status === 'banned' ? 'banned' : 'active',
    ban_reason: user.ban_reason || '',
    level: creatorLevel(user),
    coins: user.coins ?? 5000,
    created_at: user.created_at,
    follow_count: stats.following,
    fans_count: stats.followers,
    like_count: stats.likes,
    post_count: stats.posts
  };
}

function userStats(userId) {
  return {
    following: store.follows.filter(item => item.followerId === userId).length,
    followers: store.follows.filter(item => item.followingId === userId).length,
    likes: store.likes.filter(item => {
      const post = store.posts.find(candidate => candidate.id === item.postId);
      return post && post.userId === userId;
    }).length,
    posts: store.posts.filter(item => item.userId === userId && !item.deleted_at && !item.isDraft && item.isPublic && item.status === 'approved').length
  };
}

// 创作者等级（与 Web 端成长体系同阈值）：1-6 级，礼物定价以此为基础
function creatorLevel(user) {
  const stats = userStats(user.id);
  let level = 1;
  if (stats.posts >= 3) level = 2;
  if (stats.posts >= 8 && stats.likes >= 20) level = 3;
  if (stats.posts >= 15 && stats.likes >= 100 && stats.followers >= 10) level = 4;
  if (stats.likes >= 500 && stats.followers >= 50) level = 5;
  if (stats.likes >= 2000 && stats.followers >= 200) level = 6;
  return level;
}

function issueToken(user) {
  return jwt.sign({ sub: user.id, username: user.username }, jwtSecret, { expiresIn: '7d' });
}

function issueAdminToken(admin) {
  return jwt.sign({ sub: admin.id, username: admin.username, role: admin.role || 'admin' }, jwtSecret, { expiresIn: '12h' });
}

function auth(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token) return res.status(401).json({ code: 401, message: '请先登录' });
  try {
    const payload = jwt.verify(token, jwtSecret);
    const user = store.users.find(item => item.id === payload.sub);
    if (!user) return res.status(401).json({ code: 401, message: '登录已失效' });
    if (user.status === 'banned') return res.status(403).json({ code: 403, message: `账号已被封禁${user.ban_reason ? '：' + user.ban_reason : ''}` });
    req.user = user;
    next();
  } catch (error) {
    return res.status(401).json({ code: 401, message: '登录已失效' });
  }
}

mountAiRoutes(app, auth);
require('./moderation-routes').mountModeration(app, {store,auth,admin:adminAuth,serializePost,publicUser,normalizePost:normalizedPost,writeAudit,ensurePosterForTextPost});
const moderationWorker = require('./moderation-worker').createWorker(transactions);

function optionalAuth(req, res, next) {
  const header = req.get('authorization') || '';
  if (!header.startsWith('Bearer ')) return next();
  try {
    const payload = jwt.verify(header.slice(7), jwtSecret);
    req.user = store.users.find(item => item.id === payload.sub) || undefined;
  } catch { req.user = undefined; }
  next();
}

function adminAuth(req, res, next) {
  const header = req.get('authorization') || '';
  if (!header.startsWith('Bearer ')) return res.status(401).json({ code: 401, message: '请使用管理员账号登录' });
  try {
    const payload = jwt.verify(header.slice(7), jwtSecret);
    if (payload.role !== 'admin') return res.status(403).json({ code: 403, message: '管理员权限不足' });
    const admin = store.adminUsers.find(item => item.id === payload.sub);
    if (!admin) return res.status(401).json({ code: 401, message: '管理员登录已失效' });
    req.adminUser = admin;
    next();
  } catch { return res.status(401).json({ code: 401, message: '管理员登录已失效' }); }
}

function response(res, data, message = 'success') {
  return res.json({ code: 200, message, data });
}

function validateCredentials(username, password) {
  if (typeof username !== 'string' || username.trim().length < 2 || username.trim().length > 30) {
    return '用户名长度需为 2-30 位';
  }
  if (typeof password !== 'string' || password.length < 6 || password.length > 64) {
    return '密码长度需为 6-64 位';
  }
  return null;
}

app.get('/api/health', (req, res) => response(res, { timestamp: new Date().toISOString(), uptime: process.uptime(), storage: storage.mode() }, 'OK'));

app.get('/', (req, res) => res.sendFile(path.join(publicDir, 'index.html')));
app.get('/login', (req, res) => res.sendFile(path.join(publicDir, 'login.html')));
app.get('/admin', (req, res) => res.sendFile(path.join(publicDir, 'admin.html')));
app.get('/admin/posts/:id', (req, res) => res.sendFile(path.join(publicDir, 'admin-note.html')));

app.post('/api/auth/register', rateLimit(60000, 10), async (req, res) => {
  const username = String(req.body.username || req.body.user_id || '').trim();
  const password = req.body.password;
  const validationError = validateCredentials(username, password);
  if (validationError) return res.status(400).json({ code: 400, message: validationError });
  if (store.users.some(user => user.username.toLowerCase() === username.toLowerCase())) {
    return res.status(409).json({ code: 409, message: '用户名已被注册' });
  }

  const user = {
    id: crypto.randomUUID(),
    username,
    nickname: String(req.body.nickname || username),
    passwordHash: await bcrypt.hash(password, 10),
    avatar: String(req.body.avatar || ''),
    bio: String(req.body.bio || ''),
    created_at: new Date().toISOString()
  };
  store.users.push(user);
  writeStore();
  return response(res, { user: publicUser(user), access_token: issueToken(user) }, '注册成功');
});

app.post('/api/auth/login', rateLimit(60000, 10), async (req, res) => {
  const username = String(req.body.username || req.body.user_id || '').trim();
  const password = req.body.password;
  if (!username || !password) return res.status(400).json({ code: 400, message: '用户名或密码不能为空' });
  const user = store.users.find(item => item.username.toLowerCase() === username.toLowerCase());
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    return res.status(401).json({ code: 401, message: '用户名或密码错误' });
  }
  if (user.status === 'banned') {
    return res.status(403).json({ code: 403, message: `账号已被封禁${user.ban_reason ? '：' + user.ban_reason : ''}` });
  }
  return response(res, { user: publicUser(user), access_token: issueToken(user) }, '登录成功');
});

app.get('/api/auth/me', auth, (req, res) => response(res, publicUser(req.user)));

app.patch('/api/auth/me', auth, (req, res) => {
  if (req.body.nickname !== undefined) req.user.nickname = String(req.body.nickname).trim().slice(0, 30);
  if (req.body.bio !== undefined) req.user.bio = String(req.body.bio).trim().slice(0, 200);
  if (req.body.avatar !== undefined) req.user.avatar = String(req.body.avatar).trim().slice(0, 500);
  if (req.body.background !== undefined) req.user.background = String(req.body.background).trim().slice(0, 800);
  writeStore();
  return response(res, publicUser(req.user), '资料更新成功');
});

app.post('/api/auth/logout', auth, (req, res) => response(res, null, '退出成功'));

app.post('/api/auth/refresh', auth, (req, res) => response(res, { access_token: issueToken(req.user), expires_in: 604800 }, '令牌刷新成功'));

app.post('/api/media/upload', auth, (req, res) => {
  upload.array('files', 9)(req, res, error => {
    if (error) {
      const message = error.code === 'LIMIT_FILE_SIZE'
        ? '单个文件不能超过 50MB'
        : (error.code === 'LIMIT_UNEXPECTED_FILE' ? '文件类型不支持，仅允许图片或 mp4/webm 视频' : '上传失败');
      return res.status(400).json({ code: 400, message });
    }
    const files = [];
    for (const file of (req.files || [])) {
      const realType = detectMediaType(file.path);
      if (!realType) {
        fs.unlink(file.path, () => {});
        return res.status(400).json({ code: 400, message: '文件内容无效或已损坏，请重新选择' });
      }
      // 返回相对路径：浏览器同源可用；Android 端按 backend_base_url 补全，避免 host 漂移
      files.push({
        url: `/uploads/${encodeURIComponent(file.filename)}`,
        name: file.originalname,
        size: file.size,
        type: realType
      });
    }
    return response(res, { files }, '上传成功');
  });
});

app.post('/api/admin/auth/login', rateLimit(60000, 10), async (req, res) => {
  const username = String(req.body.username || '').trim();
  const password = String(req.body.password || '');
  const admin = store.adminUsers.find(item => item.username.toLowerCase() === username.toLowerCase());
  if (!admin || !(await bcrypt.compare(password, admin.passwordHash))) return res.status(401).json({ code: 401, message: '管理员账号或密码错误' });
  return response(res, { admin: { id: admin.id, username: admin.username, role: admin.role || 'admin' }, access_token: issueAdminToken(admin) }, '管理员登录成功');
});

app.get('/api/admin/auth/me', adminAuth, (req, res) => response(res, { id: req.adminUser.id, username: req.adminUser.username, role: req.adminUser.role || 'admin' }));

function resolveUser(value) {
  const direct = store.users.find(user => user.id === value || user.username.toLowerCase() === String(value || '').toLowerCase());
  if (direct) return direct;
  // 兼容 Android 本地 Room 的 sample_user_N id：按序号映射到同序号种子用户的 username（userN）
  const match = /^sample_user_(\d+)$/.exec(String(value || ''));
  if (match) return store.users.find(user => user.username === `user${match[1]}`);
  return undefined;
}

function paged(items, req) {
  const page = Math.max(1, Number(req.query.page || 1));
  const limit = Math.min(50, Math.max(1, Number(req.query.limit || 20)));
  const start = (page - 1) * limit;
  return { page, limit, total: items.length, pages: Math.ceil(items.length / limit), items: items.slice(start, start + limit) };
}

app.get('/api/users/search', (req, res) => {
  const keyword = String(req.query.keyword || '').trim().toLowerCase();
  if (!keyword) return res.status(400).json({ code: 400, message: '请输入搜索关键词' });
  const result = store.users.filter(user => `${user.username} ${user.nickname} ${user.bio}`.toLowerCase().includes(keyword));
  const page = paged(result, req);
  return response(res, { users: page.items.map(publicUser), pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

app.get('/api/users/:id', (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  return response(res, publicUser(user));
});

app.get('/api/users/:id/follow-status', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const isFollowing = store.follows.some(item => item.followerId === req.user.id && item.followingId === user.id);
  return response(res, { isFollowing, buttonType: isFollowing ? 'unfollow' : 'follow' });
});

app.post('/api/users/:id/follow', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  if (user.id === req.user.id) return res.status(400).json({ code: 400, message: '不能关注自己' });
  if (!store.follows.some(item => item.followerId === req.user.id && item.followingId === user.id)) {
    store.follows.push({ followerId: req.user.id, followingId: user.id, created_at: new Date().toISOString() });
    store.notifications.unshift({ id: crypto.randomUUID(), userId: user.id, actorId: req.user.id, type: 'follow', postId: null, read: false, created_at: new Date().toISOString() });
    writeStore();
  }
  return response(res, { isFollowing: true }, '关注成功');
});

app.delete('/api/users/:id/follow', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  store.follows = store.follows.filter(item => !(item.followerId === req.user.id && item.followingId === user.id));
  writeStore();
  return response(res, { isFollowing: false }, '取消关注成功');
});

// ---------- 拉黑（block） ----------
function isBlocked(a, b) {
  return store.blocks.some(item => (item.blockerId === a && item.blockedId === b) || (item.blockerId === b && item.blockedId === a));
}

app.get('/api/users/:id/block-status', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const blocked = store.blocks.some(item => item.blockerId === req.user.id && item.blockedId === user.id);
  return response(res, { blocked });
});

app.post('/api/users/:id/block', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  if (user.id === req.user.id) return res.status(400).json({ code: 400, message: '不能拉黑自己' });
  if (!store.blocks.some(item => item.blockerId === req.user.id && item.blockedId === user.id)) {
    store.blocks.push({ blockerId: req.user.id, blockedId: user.id, created_at: new Date().toISOString() });
    // 拉黑即解除双方关注关系
    store.follows = store.follows.filter(item =>
      !((item.followerId === req.user.id && item.followingId === user.id) || (item.followerId === user.id && item.followingId === req.user.id)));
    writeStore();
  }
  return response(res, { blocked: true }, '已拉黑');
});

app.delete('/api/users/:id/block', auth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  store.blocks = store.blocks.filter(item => !(item.blockerId === req.user.id && item.blockedId === user.id));
  writeStore();
  return response(res, { blocked: false }, '已解除拉黑');
});

function listRelatedUsers(req, res, following) {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const ids = store.follows
    .filter(item => (following ? item.followerId === user.id : item.followingId === user.id))
    .map(item => following ? item.followingId : item.followerId);
  const page = paged(store.users.filter(item => ids.includes(item.id)), req);
  const key = following ? 'following' : 'followers';
  return response(res, { [key]: page.items.map(publicUser), pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
}

app.get('/api/users/:id/following', (req, res) => listRelatedUsers(req, res, true));
app.get('/api/users/:id/followers', (req, res) => listRelatedUsers(req, res, false));

// 统一媒体地址：站内媒体（/uploads、/seed）一律输出相对路径，由各端按自身环境补全。
// 历史数据里的 http://10.0.2.2:3001/... 等绝对地址在这里剥掉 origin，浏览器与模拟器都能显示。
function normalizeMediaUrl(value) {
  const raw = String(value || '');
  try {
    const parsed = new URL(raw, 'http://localhost');
    if (parsed.pathname.startsWith('/uploads/') || parsed.pathname.startsWith('/seed/')) {
      return parsed.pathname + parsed.search;
    }
  } catch { /* 保留原值 */ }
  return raw;
}

// 允许写入的图片：公网 http(s) 地址，或本服务的站内相对路径
const isStorableImage = item => /^https?:\/\//i.test(item) || item.startsWith('/uploads/') || item.startsWith('/seed/');

/**
 * 纯文字笔记自动生成文字海报封面（与 App 端 TextToImageConverter 同款样式），
 * 使 App / 网页 / 管理后台三端看到完全一致的封面。按 内容+作者 哈希落盘复用。
 */
function ensurePosterForTextPost(post, authorUsername) {
  if (!post || post.mediaType === 'video') return;
  if (Array.isArray(post.images) && post.images.length > 0) return;
  const text = [post.title, post.content].filter(s => s && String(s).trim()).join(' ').trim();
  if (!text) return;
  try {
    const url = ensureTextPoster(post.title, post.content, authorUsername);
    if (url) post.images = [url];
  } catch (error) {
    console.warn('text poster generate failed:', error.message);
  }
}

// 审核状态独立于 isDraft：approved 公开可见，pending/rejected/hidden 仅作者和管理员可见
const REVIEW_STATUSES = ['approved', 'pending', 'rejected', 'hidden'];

function serializePost(post, currentUserId) {
  const author = store.users.find(user => user.id === post.userId);
  const topics = Array.isArray(post.topics) ? post.topics : [];
  const images = Array.isArray(post.images) ? post.images : [];
  return {
    id: post.id,
    title: post.title,
    content: post.content,
    images: images.map(normalizeMediaUrl),
    media_type: post.mediaType === 'video' ? 'video' : 'image',
    video_url: normalizeMediaUrl(post.videoUrl || ''),
    status: REVIEW_STATUSES.includes(post.status) ? post.status : 'approved',
    content_version: post.content_version || 1,
    deleted_at: post.deleted_at || null,
    review_at: post.review_at || null,
    topics,
    location: post.location,
    is_public: post.isPublic,
    is_draft: post.isDraft,
    like_count: store.likes.filter(item => item.postId === post.id).length,
    collection_count: store.collections.filter(item => item.postId === post.id).length,
    comment_count: store.comments.filter(item => item.postId === post.id).length,
    view_count: store.views.filter(item => item.postId === post.id).length,
    // 审核备注（驳回原因等）只对作者本人下发：对齐真实小红书"未通过"会告知创作者原因
    review_note: currentUserId && post.userId === currentUserId ? (post.review_note || '') : '',
    liked: Boolean(currentUserId && store.likes.some(item => item.postId === post.id && item.userId === currentUserId)),
    collected: Boolean(currentUserId && store.collections.some(item => item.postId === post.id && item.userId === currentUserId)),
    created_at: post.created_at,
    updated_at: post.updated_at,
    user: author ? publicUser(author) : null
  };
}

function normalizedPost(body, userId, previous = {}) {
  const title = String(body.title ?? previous.title ?? '').trim();
  const content = String(body.content ?? previous.content ?? '').trim();
  if(title.length>200 || content.length>20000) moderation.fail(400,'标题最多200字，正文最多20000字');
  // 只保留可跨端访问的图片地址：http(s) 或本服务的 /uploads、/seed 相对路径
  const rawImages = Array.isArray(body.images) ? body.images.map(String) : (previous.images || []);
  const images = rawImages.filter(isStorableImage).slice(0, 9);
  const topics = Array.isArray(body.topics) ? body.topics.map(String).slice(0, 20) : (previous.topics || []);
  const mediaType = body.media_type !== undefined ? (body.media_type === 'video' ? 'video' : 'image') : (previous.mediaType || 'image');
  return {
    id: previous.id || body.id || crypto.randomUUID(),
    userId,
    title,
    content,
    images,
    topics,
    location: String(body.location ?? previous.location ?? ''),
    isPublic: body.is_public !== undefined ? Boolean(body.is_public) : (previous.isPublic !== false),
    isDraft: body.is_draft !== undefined ? Boolean(body.is_draft) : (previous.isDraft || false),
    mediaType,
    videoUrl: mediaType === 'video' && isStorableImage(String(body.video_url ?? previous.videoUrl ?? '')) ? String(body.video_url ?? previous.videoUrl ?? '') : '',
    status: REVIEW_STATUSES.includes(previous.status) ? previous.status : 'approved',
    created_at: previous.created_at || new Date().toISOString(),
    updated_at: new Date().toISOString()
  };
}

function listPosts(req, res) {
  const page = Math.max(1, Number(req.query.page || 1));
  const limit = Math.min(50, Math.max(1, Number(req.query.limit || 20)));
  const keyword = String(req.query.keyword || req.query.searchKeyword || '').trim().toLowerCase();
  const userId = req.query.user_id || req.query.userId;
  // 公开列表只露出通过审核的已发布笔记；作者本人查看自己的列表时可看到全部审核状态（草稿仍走独立接口）
  const isOwner = Boolean(userId && req.user && req.user.id === userId);
  let posts = store.posts.filter(post => !post.deleted_at && !post.isDraft && post.isPublic
    && (isOwner || (post.status || 'approved') === 'approved'));
  if (userId) posts = posts.filter(post => post.userId === userId);
  if (keyword) posts = posts.filter(post => {
    const topics = Array.isArray(post.topics) ? post.topics : [];
    return `${post.title} ${post.content} ${topics.join(' ')}`.toLowerCase().includes(keyword);
  });
  posts.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  const start = (page - 1) * limit;
  const data = posts.slice(start, start + limit).map(post => serializePost(post, req.user?.id));
  return response(res, { posts: data, pagination: { page, limit, total: posts.length, pages: Math.ceil(posts.length / limit) } });
}

app.get('/api/posts', optionalAuth, (req, res) => listPosts(req, res));

app.get('/api/posts/drafts', auth, (req, res) => {
  const page = Math.max(1, Number(req.query.page || 1));
  const limit = Math.min(50, Math.max(1, Number(req.query.limit || 20)));
  const drafts = store.posts
    .filter(post => post.userId === req.user.id && post.isDraft)
    .sort((a, b) => new Date(b.updated_at) - new Date(a.updated_at));
  const start = (page - 1) * limit;
  return response(res, {
    posts: drafts.slice(start, start + limit).map(post => serializePost(post, req.user.id)),
    pagination: { page, limit, total: drafts.length, pages: Math.ceil(drafts.length / limit) }
  });
});

app.get('/api/posts/:id', optionalAuth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && canReadPost(item, req.user?.id));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  // 非 approved 状态（待审/驳回/隐藏）对非作者不可见
  const status = post.status || 'approved';
  if (!post.isDraft && status !== 'approved' && post.userId !== req.user?.id) {
    return res.status(404).json({ code: 404, message: '笔记不存在' });
  }
  return response(res, serializePost(post, req.user?.id));
});

app.post('/api/posts/:id/view', optionalAuth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && isPublicPost(item));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  const visitorKey = String(req.body.visitor_key || req.get('x-visitor-key') || req.user?.id || req.ip || 'anonymous').slice(0, 128);
  const viewDate = new Date().toISOString().slice(0, 10);
  const exists = store.views.some(item => item.postId === post.id && item.visitorKey === visitorKey && item.viewDate === viewDate);
  if (!exists) {
    store.views.push({ id: Date.now(), postId: post.id, userId: req.user?.id || null, visitorKey, viewDate, created_at: new Date().toISOString() });
    writeStore();
  }
  return response(res, { viewed: !exists, view_count: store.views.filter(item => item.postId === post.id).length }, '浏览已记录');
});




app.get('/api/users/:userId/posts', optionalAuth, (req, res) => {
  const target = resolveUser(req.params.userId);
  req.query.user_id = target ? target.id : req.params.userId;
  return listPosts(req, res);
});



app.post('/api/posts/:id/like', auth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && !item.isDraft && canReadPost(item, req.user.id));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  if (!store.likes.some(item => item.postId === post.id && item.userId === req.user.id)) {
    store.likes.push({ postId: post.id, userId: req.user.id, created_at: new Date().toISOString() });
    if (post.userId !== req.user.id) {
      store.notifications.unshift({ id: crypto.randomUUID(), userId: post.userId, actorId: req.user.id, type: 'like', postId: post.id, read: false, created_at: new Date().toISOString() });
    }
    writeStore();
  }
  return response(res, serializePost(post, req.user.id), '点赞成功');
});

app.delete('/api/posts/:id/like', auth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && !item.isDraft && canReadPost(item, req.user.id));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  store.likes = store.likes.filter(item => !(item.postId === req.params.id && item.userId === req.user.id));
  writeStore();
  return response(res, serializePost(post, req.user.id), '取消点赞成功');
});

app.post('/api/posts/:id/collection', auth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && !item.isDraft && canReadPost(item, req.user.id));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  if (!store.collections.some(item => item.postId === post.id && item.userId === req.user.id)) {
    store.collections.push({ postId: post.id, userId: req.user.id, created_at: new Date().toISOString() });
    if (post.userId !== req.user.id) {
      store.notifications.unshift({ id: crypto.randomUUID(), userId: post.userId, actorId: req.user.id, type: 'collection', postId: post.id, read: false, created_at: new Date().toISOString() });
    }
    writeStore();
  }
  return response(res, serializePost(post, req.user.id), '收藏成功');
});

app.delete('/api/posts/:id/collection', auth, (req, res) => {
  const post = store.posts.find(item => item.id === req.params.id && !item.isDraft && canReadPost(item, req.user.id));
  if (!post) return res.status(404).json({ code: 404, message: '笔记不存在' });
  store.collections = store.collections.filter(item => !(item.postId === req.params.id && item.userId === req.user.id));
  writeStore();
  return response(res, serializePost(post, req.user.id), '取消收藏成功');
});

app.get('/api/users/:id/collections', optionalAuth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const ids = store.collections.filter(item => item.userId === user.id).map(item => item.postId);
  const posts = store.posts.filter(item => ids.includes(item.id) && !item.isDraft && canReadPost(item, req.user?.id));
  const page = paged(posts, req);
  return response(res, { collections: page.items.map(item => serializePost(item, req.user?.id)), pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

app.get('/api/users/:id/likes', optionalAuth, (req, res) => {
  const user = resolveUser(req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const ids = store.likes.filter(item => item.userId === user.id).map(item => item.postId);
  const posts = store.posts.filter(item => ids.includes(item.id) && !item.isDraft && canReadPost(item, req.user?.id));
  const page = paged(posts, req);
  return response(res, { posts: page.items.map(item => serializePost(item, req.user?.id)), pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

app.get('/api/posts/:id/comments', optionalAuth, (req, res) => {
  if (!store.posts.some(post => post.id === req.params.id && canReadPost(post, req.user?.id))) {
    return res.status(404).json({ code: 404, message: '笔记不存在' });
  }
  const comments = store.comments.filter(item => item.postId === req.params.id).map(comment => ({
    ...comment,
    user: publicUser(store.users.find(user => user.id === comment.userId) || { id: '', username: 'unknown', nickname: '匿名用户' })
  }));
  return response(res, comments);
});

app.post('/api/posts/:id/comments', auth, (req, res) => {
  const content = String(req.body.content || '').trim();
  if (!content) return res.status(400).json({ code: 400, message: '评论不能为空' });
  if (!store.posts.some(item => item.id === req.params.id && !item.isDraft && canReadPost(item, req.user.id))) {
    return res.status(404).json({ code: 404, message: '笔记不存在' });
  }
  const comment = { id: crypto.randomUUID(), postId: req.params.id, userId: req.user.id, content, created_at: new Date().toISOString() };
  store.comments.push(comment);
  const post = store.posts.find(item => item.id === req.params.id);
  if (post && post.userId !== req.user.id) {
    store.notifications.unshift({ id: crypto.randomUUID(), userId: post.userId, actorId: req.user.id, type: 'comment', postId: post.id, read: false, created_at: new Date().toISOString() });
  }
  writeStore();
  return response(res, { ...comment, user: publicUser(req.user) }, '评论成功');
});

app.get('/api/notifications', auth, (req, res) => {
  const page = paged(store.notifications.filter(item => item.userId === req.user.id && (!req.query.type || item.type === req.query.type)), req);
  const notifications = page.items.map(item => ({
    ...item,
    actor: publicUser(store.users.find(user => user.id === item.actorId) || { id: '', username: 'unknown', nickname: '匿名用户' })
  }));
  // 未读数统计该用户的全部未读，而非仅当前页
  const unread = store.notifications.filter(item => item.userId === req.user.id && !item.read).length;
  return response(res, { notifications, unread, pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

app.patch('/api/notifications/read', auth, (req, res) => {
  store.notifications.forEach(item => {
    if (item.userId === req.user.id) item.read = true;
  });
  writeStore();
  return response(res, null, '通知已读');
});

app.get('/api/search', optionalAuth, (req, res) => {
  const keyword = String(req.query.keyword || '').trim();
  if (!keyword) return res.status(400).json({ code: 400, message: '请输入搜索关键词' });
  const posts = store.posts.filter(post => !post.deleted_at && !post.isDraft && post.isPublic
    && (post.status || 'approved') === 'approved'
    && `${post.title} ${post.content} ${(post.topics || []).join(' ')}`.toLowerCase().includes(keyword.toLowerCase()));
  const page = paged(posts, req);
  return response(res, { posts: page.items.map(post => serializePost(post, req.user?.id)), pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

function admin(req, res, next) {
  const header = req.get('authorization') || '';
  if (header.startsWith('Bearer ')) return adminAuth(req, res, next);
  // 未配置 ADMIN_KEY 时禁用 legacy key 认证，仅允许管理员 JWT
  const expected = process.env.ADMIN_KEY;
  if (!expected || (req.get('x-admin-key') || '') !== expected) return res.status(403).json({ code: 403, message: '管理员权限不足' });
  next();
}

// 概览统计：在原有扁平计数基础上补充「近 N 日发布趋势」「审核状态分布」「互动总量」，
// 供管理后台首页做可视化。天粒度按服务器本地时区归并，避免 UTC 偏移把跨日数据算错。
function statDayKey(value) {
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return null;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function buildDailySeries(list, days, dateField = 'created_at') {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const buckets = new Map();
  const series = [];
  for (let i = days - 1; i >= 0; i -= 1) {
    const d = new Date(today.getTime() - i * 86400000);
    const key = statDayKey(d);
    buckets.set(key, 0);
    series.push({ date: key, label: `${d.getMonth() + 1}/${d.getDate()}`, count: 0 });
  }
  list.forEach(item => {
    const key = statDayKey(item && item[dateField]);
    if (key && buckets.has(key)) buckets.set(key, buckets.get(key) + 1);
  });
  series.forEach(item => { item.count = buckets.get(item.date) || 0; });
  return series;
}

app.get('/api/admin/stats', admin, (req, res) => {
  const days = Math.min(90, Math.max(7, Number(req.query.days) || 30));
  const counted = store.posts.filter(item => !item.deleted_at && !item.isDraft);
  const statusOf = item => item.status || 'approved';
  const countStatus = status => counted.filter(item => statusOf(item) === status).length;
  const drafts = store.posts.filter(item => !item.deleted_at && item.isDraft).length;

  return response(res, {
    users: store.users.length,
    banned_users: store.users.filter(item => item.status === 'banned').length,
    posts: counted.filter(item => item.isPublic && statusOf(item) === 'approved').length,
    pending_review: countStatus('pending'),
    hidden_posts: countStatus('rejected') + countStatus('hidden'),
    drafts,
    comments: store.comments.length,
    likes: store.likes.length,
    collections: store.collections.length,
    views: store.views.length,
    reports: store.reports.filter(item => item.status === 'pending').length,
    ai_pending: store.aiJobs.filter(job => ['queued', 'running'].includes(job.state)).length,
    manual_pending: store.moderationCases.filter(c => c.state === 'open').length,
    trash: store.posts.filter(p => p.deleted_at).length,
    keyword_rules: store.keywordRules.length,
    // 近 N 日笔记提交趋势（按创建时间，含草稿/待审，反映投稿活跃度）
    trend_days: days,
    posts_by_date: buildDailySeries(counted, days, 'created_at'),
    // 审核状态分布
    status_breakdown: [
      { key: 'approved', label: '已通过', count: countStatus('approved') },
      { key: 'pending', label: '待审核', count: countStatus('pending') },
      { key: 'rejected', label: '已驳回', count: countStatus('rejected') },
      { key: 'hidden', label: '已隐藏', count: countStatus('hidden') },
      { key: 'draft', label: '草稿', count: drafts }
    ],
    // 互动总量，供概览页做占比分析
    engagement: {
      likes: store.likes.length,
      comments: store.comments.length,
      collections: store.collections.length
    }
  });
});







// audit_logs.id 为 BIGINT 自增语义：时间戳毫秒 ×1000 + 序号，避免同毫秒冲突
let auditIdSeq = 0;
function writeAudit(req, action, targetType, targetId, detail) {
  store.auditLogs.unshift({
    id: Date.now() * 1000 + (auditIdSeq++ % 1000),
    adminId: req.adminUser?.username || req.adminUser?.id || 'legacy-key',
    action,
    targetType,
    targetId,
    detail: detail || {},
    created_at: new Date().toISOString()
  });

}

// 笔记审核：独立设置审核状态（approved/pending/rejected/hidden），与上下架（isDraft）解耦

// 举报处理：支持处理备注、处理人、处理时间，并写入审计日志

// 评论管理：分页列表 + 管理员删除

app.delete('/api/admin/comments/:id', admin, (req, res) => {
  const index = store.comments.findIndex(item => item.id === req.params.id);
  if (index < 0) return res.status(404).json({ code: 404, message: '评论不存在' });
  const [removed] = store.comments.splice(index, 1);
  writeAudit(req, 'comment_delete', 'comment', removed.id, { postId: removed.postId, content: removed.content });
  writeStore();
  return response(res, null, '评论已删除');
});

// 用户管理：封禁/解封（封禁后登录与已签发 token 一并失效）
app.patch('/api/admin/users/:id/status', admin, (req, res) => {
  const user = store.users.find(item => item.id === req.params.id || item.username === req.params.id);
  if (!user) return res.status(404).json({ code: 404, message: '用户不存在' });
  const status = String(req.body.status || '');
  if (!['active', 'banned'].includes(status)) return res.status(400).json({ code: 400, message: '非法账号状态' });
  user.status = status;
  user.ban_reason = status === 'banned' ? String(req.body.reason || '违规内容').slice(0, 200) : '';
  writeAudit(req, status === 'banned' ? 'user_ban' : 'user_unban', 'user', user.id, { reason: user.ban_reason });
  writeStore();
  return response(res, publicUser(user), status === 'banned' ? '账号已封禁' : '账号已解封');
});

// 媒体管理：列出上传文件、引用计数，删除被引用文件时拒绝
function mediaReferences(filename) {
  const marker = `/uploads/${filename}`;
  return store.posts.filter(post => ((post.images || []).some(image => String(image).endsWith(marker)) || String(post.videoUrl||'').endsWith(marker)));
}

app.get('/api/admin/media', admin, (req, res) => {
  let files = [];
  try {
    files = fs.readdirSync(uploadDir).filter(name => UPLOAD_EXTENSIONS.includes(path.extname(name).toLowerCase()));
  } catch { files = []; }
  const media = files.map(name => {
    let size = 0;
    try { size = fs.statSync(path.join(uploadDir, name)).size; } catch { size = 0; }
    const referenced = mediaReferences(name);
    return {
      name,
      url: `/uploads/${encodeURIComponent(name)}`,
      size,
      referenced_by: referenced.map(post => ({ id: post.id, title: post.title || '无标题笔记' })),
      referenced: referenced.length > 0
    };
  }).sort((a, b) => b.size - a.size);
  const keyword=String(req.query.keyword||'').trim().toLowerCase();
  const result=require('./moderation-routes').paginate(media.filter(m=>!keyword||m.name.toLowerCase().includes(keyword)),req.query);
  return response(res,{...result,media:result.items});
});

app.delete('/api/admin/media/:filename', admin, (req, res) => {
  const filename = path.basename(req.params.filename);
  if (!UPLOAD_EXTENSIONS.includes(path.extname(filename).toLowerCase())) {
    return res.status(400).json({ code: 400, message: '非法文件名' });
  }
  const referenced = mediaReferences(filename);
  if (referenced.length > 0) {
    return res.status(409).json({ code: 409, message: `文件仍被 ${referenced.length} 篇笔记引用，先删除或编辑相关笔记` });
  }
  const filePath = path.join(uploadDir, filename);
  if (!fs.existsSync(filePath)) return res.status(404).json({ code: 404, message: '文件不存在' });
  fs.unlinkSync(filePath);
  writeAudit(req, 'media_delete', 'media', filename, {});
  writeStore();
  return response(res, null, '文件已删除');
});

// 审计日志查询
app.get('/api/admin/audit-logs', admin, (req, res) => {
  const page = paged(store.auditLogs, req);
  return response(res, { logs: page.items, pagination: { page: page.page, limit: page.limit, total: page.total, pages: page.pages } });
});

// 作者管理列表包含私密笔记与草稿，筛选发生在分页之前。

// 统计、最近发布和排行榜均覆盖全部笔记，不受内容管理当前页影响。
app.get('/api/creators/me/stats', auth, (req, res) => {
  const publicPosts = store.posts.filter(post => post.userId === req.user.id && isPublicPost(post));
  const recent = publicPosts.slice().sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  const ranked = publicPosts.map(post => serializePost(post, req.user.id)).sort((a, b) => b.like_count - a.like_count);
  return response(res, {
    ...creatorStats(store, req.user.id),
    recent_posts: recent.slice(0, 5).map(post => serializePost(post, req.user.id)),
    top_posts: ranked.slice(0, 5)
  });
});

// ---------- 私信（direct messages） ----------
// 规则：对方关注我（含互关）→ 无限私信；对方未关注我 → 只能发一条；
// 送礼物（按对方创作者等级定价）可解锁无限私信。
const GIFT_CATALOG = [
  { id: 'rose', name: '玫瑰', emoji: '🌹', base: 9 },
  { id: 'coffee', name: '咖啡', emoji: '☕', base: 19 },
  { id: 'game', name: '游戏机', emoji: '🎮', base: 39 },
  { id: 'crown', name: '皇冠', emoji: '👑', base: 69 },
  { id: 'diamond', name: '钻石', emoji: '💎', base: 129 },
  { id: 'rocket', name: '火箭', emoji: '🚀', base: 199 },
  { id: 'milktea', name: '奶茶', emoji: '🧋', base: 5 },
  { id: 'cake', name: '蛋糕', emoji: '🍰', base: 12 },
  { id: 'bear', name: '小熊', emoji: '🧸', base: 29 },
  { id: 'ring', name: '戒指', emoji: '💍', base: 99 },
  { id: 'sportscar', name: '跑车', emoji: '🏎️', base: 299 },
  { id: 'castle', name: '城堡', emoji: '🏰', base: 520 }
];

function dmAllowed(from, to) {
  if (from.id === to.id) return { ok: false, message: '不能给自己发私信' };
  if (isBlocked(from.id, to.id)) return { ok: false, message: '对方已开启隐私保护，无法发送私信' };
  const theyFollowMe = store.follows.some(f => f.followerId === to.id && f.followingId === from.id);
  if (theyFollowMe) return { ok: true };
  const gifted = store.directMessages.some(m => m.fromId === from.id && m.toId === to.id && m.gift);
  if (gifted) return { ok: true };
  const sent = store.directMessages.some(m => m.fromId === from.id && m.toId === to.id);
  if (sent) return { ok: false, message: '对方关注你之前只能发送一条私信，送一份礼物解锁无限私信吧' };
  return { ok: true };
}

// 私信会话列表：按对方聚合，返回每个会话的对方用户与最后一条消息
app.get('/api/dm/conversations', auth, (req, res) => {
  const lastByPeer = new Map();
  for (const m of store.directMessages) {
    const peerId = m.fromId === req.user.id ? m.toId : (m.toId === req.user.id ? m.fromId : null);
    if (!peerId) continue;
    const existing = lastByPeer.get(peerId);
    if (!existing || new Date(m.created_at) > new Date(existing.created_at)) lastByPeer.set(peerId, m);
  }
  const conversations = [];
  for (const [peerId, last] of lastByPeer) {
    const peer = store.users.find(user => user.id === peerId);
    if (!peer) continue;
    conversations.push({
      user: publicUser(peer),
      last_message: last.content || (last.gift ? `送出礼物 ${last.gift.emoji} ${last.gift.name}` : ''),
      created_at: last.created_at
    });
  }
  conversations.sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  return response(res, { conversations });
});

app.get('/api/dm/with/:toId', auth, (req, res) => {
  const to = resolveUser(req.params.toId);
  if (!to) return res.status(404).json({ code: 404, message: '用户不存在' });
  const thread = store.directMessages
    .filter(m => (m.fromId === req.user.id && m.toId === to.id) || (m.fromId === to.id && m.toId === req.user.id))
    .sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
  return response(res, {
    user: publicUser(to),
    can_send: dmAllowed(req.user, to).ok,
    messages: thread
  });
});

app.post('/api/dm/:toId', auth, (req, res) => {
  const to = resolveUser(req.params.toId);
  if (!to) return res.status(404).json({ code: 404, message: '用户不存在' });
  const content = String(req.body.content || '').trim().slice(0, 500);
  if (!content) return res.status(400).json({ code: 400, message: '私信内容不能为空' });
  const check = dmAllowed(req.user, to);
  if (!check.ok) return res.status(403).json({ code: 403, message: check.message });
  const message = {
    id: crypto.randomUUID(),
    fromId: req.user.id,
    toId: to.id,
    content,
    gift: null,
    created_at: new Date().toISOString()
  };
  store.directMessages.push(message);
  writeStore();
  return response(res, message, '私信已发送');
});

// 礼物目录：固定价格，不随等级变化；支付走钱包概念，无余额校验
app.get('/api/dm/:toId/gifts', auth, (req, res) => {
  const to = resolveUser(req.params.toId);
  if (!to) return res.status(404).json({ code: 404, message: '用户不存在' });
  const gifts = GIFT_CATALOG.map(gift => ({ ...gift, price: gift.base }));
  return response(res, { gifts });
});

// 钱包：薯币余额（用户对象 coins 字段为权威源，种子/存量用户默认 5000）
app.get('/api/wallet', auth, (req, res) => {
  return response(res, { coins: req.user.coins ?? 0 });
});

// 充值：演示支付，金额白名单，直接累加薯币（不产生真实扣款）
const RECHARGE_PRESETS = [6, 30, 68, 128, 328, 648];
app.post('/api/wallet/recharge', auth, (req, res) => {
  const amount = Number(req.body.amount || 0);
  if (!RECHARGE_PRESETS.includes(amount)) {
    return res.status(400).json({ code: 400, message: '不支持的充值金额' });
  }
  req.user.coins = (req.user.coins ?? 0) + amount;
  writeStore();
  return response(res, { coins: req.user.coins, amount }, '充值成功');
});

// 送礼物：校验薯币余额、扣费、对方按 1:1 获得收益，写入对话（金色礼物消息）、解锁无限私信、通知对方
app.post('/api/dm/:toId/gift', auth, (req, res) => {
  const to = resolveUser(req.params.toId);
  if (!to) return res.status(404).json({ code: 404, message: '用户不存在' });
  if (to.id === req.user.id) return res.status(400).json({ code: 400, message: '不能给自己送礼物' });
  const gift = GIFT_CATALOG.find(item => item.id === String(req.body.giftId || ''));
  if (!gift) return res.status(400).json({ code: 400, message: '礼物不存在' });
  const price = gift.base;
  const balance = req.user.coins ?? 0;
  if (balance < price) {
    return res.status(403).json({ code: 403, message: `薯币余额不足，还差 ${price - balance} 薯币，请先充值` });
  }
  req.user.coins = balance - price;
  if (to.status !== 'banned') {
    to.coins = (to.coins ?? 0) + price; // 收礼方按 1:1 获得薯币收益
  }
  const message = {
    id: crypto.randomUUID(),
    fromId: req.user.id,
    toId: to.id,
    content: `送出礼物 ${gift.emoji} ${gift.name}（${price} 薯币）`,
    gift: { ...gift, price },
    created_at: new Date().toISOString()
  };
  store.directMessages.push(message);
  if (to.status !== 'banned') {
    store.notifications.unshift({ id: crypto.randomUUID(), userId: to.id, actorId: req.user.id, type: 'gift', postId: null, read: false, created_at: new Date().toISOString() });
  }
  writeStore();
  return response(res, { message, unlocked: true, balance: req.user.coins, earned: price }, `已送出 ${gift.name}，解锁无限私信`);
});

app.use((error, req, res, next) => {
  console.error(error);
  return res.status(500).json({ code: 500, message: '服务器内部错误' });
});

app.use((req, res) => res.status(404).json({ code: 404, message: '接口不存在' }));

// 与 Android 端 DatabaseInitializer 对齐的种子数据：素材图在 public/seed/note/，
// 存相对路径，浏览器同源直接可用，Android 端按 backend_base_url 补全。
// 所有 seed_post_* / seed_user_* 均按 id 幂等，可安全地每次启动执行。
const SEED_POST_TITLES = [
  '春日穿搭分享，温柔又时尚',
  '今天做了超好吃的抹茶蛋糕',
  '云南旅行vlog，风景太美了',
  '摄影技巧分享：如何拍出好照片',
  '我的房间改造计划',
  '日常护肤routine分享',
  '健身房打卡第30天',
  '最近在读的几本好书推荐',
  '周末音乐会，现场太棒了',
  '手绘插画作品集',
  '编程学习心得分享',
  '教师节礼物推荐',
  '健康饮食小贴士',
  '医学生的日常',
  '大学生活vlog'
];

const SEED_POST_CONTENTS = [
  '春天来了，分享几套温柔又时尚的穿搭，希望大家喜欢～',
  '第一次做抹茶蛋糕，虽然有点小瑕疵，但味道还不错！',
  '云南真的太美了，每一帧都是风景，强烈推荐大家去！',
  '分享一些摄影小技巧，希望能帮助到喜欢拍照的朋友',
  '终于把房间改造完成了，满满的成就感！',
  '坚持护肤一个月，皮肤真的变好了很多',
  '健身30天，虽然累但很充实，继续加油！',
  '最近读了几本很棒的书，推荐给大家',
  '周末去听了音乐会，现场氛围太棒了',
  '最近画的一些插画，希望大家喜欢',
  '学习编程的心得体会，分享给同样在路上的朋友',
  '教师节快到了，推荐一些适合送老师的礼物',
  '健康饮食真的很重要，分享一些小心得',
  '医学生的日常，虽然累但很充实',
  '记录一下大学生活的点点滴滴'
];

const SEED_POST_TOPICS = [
  '穿搭', '美食', '旅行', '摄影', '家居',
  '护肤', '健身', '读书', '音乐', '插画',
  '编程', '教师节', '健康饮食', '医学生', '校园'
];

const SEED_POST_LOCATIONS = [
  '北京', '上海', '广州', '深圳', '杭州',
  '成都', '重庆', '西安', '南京', '武汉',
  '长沙', '厦门', '青岛', '大连', '苏州'
];

const SEED_USER_NICKNAMES = [
  '时尚达人', '美食家', '旅行者', '摄影师', '设计师',
  '生活家', '美妆师', '健身教练', '读书人', '音乐人',
  '艺术家', '程序员', '教师', '医生', '学生'
];

// 视频种子笔记：素材在 public/seed/video/，封面复用 note 图
const SEED_VIDEO_POSTS = [
  { title: '周末vlog：记录轻松的一天', content: '随手拍的日常片段，节奏很慢，适合放松的时候看～', video: 1, cover: 2, topic: 'vlog', location: '杭州' },
  { title: '健身打卡第 30 天', content: '坚持一个月的动作合集，最后一个动作是终结者。', video: 2, cover: 7, topic: '健身', location: '上海' },
  { title: '治愈系风景混剪', content: '把最近拍的风景剪成了短视频，每一帧都是壁纸。', video: 3, cover: 12, topic: '风景', location: '大理' },
  { title: '咖啡拉花慢动作', content: '第一次尝试拉花，慢动作回放看细节。', video: 4, cover: 13, topic: '咖啡', location: '上海' },
  { title: '夜间城市漫步', content: '晚风、灯光和街道，散步十分钟的随手记录。', video: 5, cover: 8, topic: '夜游', location: '重庆' }
];

function seedPosts() {
  const existingSeedUsers = [];
  for (let i = 0; i < SEED_USER_NICKNAMES.length; i++) {
    const username = `user${i + 1}`;
    let user = store.users.find(item => item.username === username);
    if (!user) {
      user = {
        id: `seed_user_${i + 1}`,
        username,
        nickname: SEED_USER_NICKNAMES[i],
        passwordHash: bcrypt.hashSync('123456', 10),
        avatar: '',
        bio: '分享生活中的美好',
        created_at: new Date().toISOString()
      };
      store.users.push(user);
    }
    existingSeedUsers.push(user);
  }

  let created = 0;
  for (let i = 0; i < SEED_POST_TITLES.length; i++) {
    const id = `seed_post_${i + 1}`;
    // 纯文字种子（i>=12）也要有海报封面，三端一致
    const seedPost = store.posts.find(post => post.id === id);
    if (seedPost) {
      if (i >= 12) ensurePosterForTextPost(seedPost, existingSeedUsers[i].username);
      continue;
    }
    // 前 12 篇为图文笔记（1~3 张真实素材图），后 3 篇保持纯文字，与 Android 端一致
    const images = [];
    if (i < 12) {
      const count = (i % 3) + 1;
      for (let k = 0; k < count; k++) {
        images.push(`/seed/note/image_${((i + k * 5) % 15) + 1}.jpg`);
      }
    }
    const baseTime = new Date(Date.now() - (15 - i) * 3600000).toISOString();
    const newPost = {
      id,
      userId: existingSeedUsers[i].id,
      title: SEED_POST_TITLES[i],
      content: SEED_POST_CONTENTS[i],
      images,
      topics: [SEED_POST_TOPICS[i]],
      location: SEED_POST_LOCATIONS[i],
      isPublic: true,
      isDraft: false,
      status: 'approved',
      mediaType: 'image',
      videoUrl: '',
      seed: true,
      created_at: baseTime,
      updated_at: baseTime
    };
    if (i >= 12) ensurePosterForTextPost(newPost, existingSeedUsers[i].username);
    store.posts.unshift(newPost);
    created++;
  }

  // 视频种子笔记：seed_post_16..20，封面 + 服务端视频地址
  for (let i = 0; i < SEED_VIDEO_POSTS.length; i++) {
    const spec = SEED_VIDEO_POSTS[i];
    const id = `seed_post_${SEED_POST_TITLES.length + i + 1}`;
    const existing = store.posts.find(post => post.id === id);
    if (existing) {
      // 自愈：旧版本持久化曾丢失视频字段（media_type/video_url 列缺失时期），补写
      if (existing.mediaType !== 'video' || !existing.videoUrl) {
        existing.mediaType = 'video';
        existing.videoUrl = `/seed/video/video_${spec.video}.mp4`;
        created++;
      }
      continue;
    }
    const baseTime = new Date(Date.now() - (20 - i) * 3600000).toISOString();
    store.posts.unshift({
      id,
      userId: existingSeedUsers[(i * 3) % existingSeedUsers.length].id,
      title: spec.title,
      content: spec.content,
      images: [`/seed/note/image_${spec.cover}.jpg`],
      topics: [spec.topic],
      location: spec.location,
      isPublic: true,
      isDraft: false,
      status: 'approved',
      mediaType: 'video',
      videoUrl: `/seed/video/video_${spec.video}.mp4`,
      seed: true,
      created_at: baseTime,
      updated_at: baseTime
    });
    created++;
  }

  if (created > 0) {
    writeStore();
    console.log(`[init] 已注入 ${created} 篇种子笔记（含 ${SEED_VIDEO_POSTS.length} 篇视频）`);
  }
}

async function start() {
  if (!jwtSecret) {
    console.error('Backend startup failed: 缺少 JWT_SECRET 环境变量（参考 .env.example 配置）');
    process.exitCode = 1;
    return;
  }
  transactions.replace(await storage.load(store));
  moderation.ensure(store);
  // MySQL 模式的 load 结果不含 blocks，兜底为内存数组（JSON 模式则持久化到 data.json）
  if (!Array.isArray(store.blocks)) store.blocks = [];
  const hadAdmin = store.adminUsers.length;
  ensureDefaultAdmin();
  if (storage.mode() === 'mysql' && store.adminUsers.length !== hadAdmin) await storage.persist(store);
  await transactions.run(() => seedPosts());
  await moderationWorker.start();
  return app.listen(port, '0.0.0.0', () => console.log(`Xiaohongshu backend listening on http://0.0.0.0:${port} storage=${storage.mode()}`));
}

app.use('/api', (req, res) => res.status(404).json({code:404,message:'接口不存在'}));
app.use((error, req, res, next) => res.status(error.status || 500).json({code:error.status || 500,message:error.status ? error.message : '请求处理失败'}));

if (require.main === module) {
  start().catch(error => {
    console.error(`Backend startup failed: ${error.message}`);
    process.exitCode = 1;
  });
}

module.exports = app;
module.exports.start = start;

module.exports.transactions = transactions;
module.exports.moderationWorker = moderationWorker;
