const state = { token: localStorage.getItem('xhs_token') || '', me: null, posts: [], drafts: [], stats: null, filter: 'all', creator: null, notesPage: 1, notesTotalPages: 1, notesLimit: 20, notesTotal: 0, notesKeyword: '', notesRequest: 0, editingPostId: '', editingSource: null, clientNoteId: '', viewOnly: false, saving: false };
const $ = id => document.getElementById(id);

async function api(path, options = {}, retryAuth = true) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(`/api${path}`, { ...options, headers });
  const body = await response.json().catch(() => ({}));
  // token 过期时自动续期一次再重试原请求
  if (response.status === 401 && state.token && retryAuth) {
    const refreshed = await fetch('/api/auth/refresh', { method: 'POST', headers: { Authorization: `Bearer ${state.token}` } });
    const refreshBody = await refreshed.json().catch(() => ({}));
    if (refreshed.ok && refreshBody.code === 200 && refreshBody.data?.access_token) {
      state.token = refreshBody.data.access_token;
      localStorage.setItem('xhs_token', state.token);
      return api(path, options, false);
    }
  }
  if (!response.ok || body.code !== 200) {
    const error = new Error(body.message || '请求失败');
    error.status = response.status;
    throw error;
  }
  return body.data;
}

/* ---------- 本地创作状态（报名/领取/邀约处理结果持久化） ---------- */
function loadCreatorState() {
  const defaults = { joined: [], claimedTasks: [], claimedActivities: [], acceptedInvites: [], dismissedInvites: [], points: 0 };
  try { return { ...defaults, ...JSON.parse(localStorage.getItem('xhs_creator_state') || '{}') }; }
  catch { return defaults; }
}
function saveCreatorState() { localStorage.setItem('xhs_creator_state', JSON.stringify(state.creator)); }

/* ---------- 视图切换 ---------- */
const VIEW_TITLES = { home: '创作者中心', notes: '笔记管理', data: '数据中心', services: '创作服务', activity: '活动中心', growth: '成长中心', invite: '作者邀约' };

function setView(view) {
  document.querySelectorAll('[data-view-panel]').forEach(panel => panel.classList.toggle('hidden', panel.dataset.viewPanel !== view));
  document.querySelectorAll('.nav-item[data-view]').forEach(item => item.classList.toggle('active', item.dataset.view === view));
  $('pageTitle').textContent = VIEW_TITLES[view] || VIEW_TITLES.home;
  if (view === 'notes') renderNotes();
  if (view === 'data' || view === 'home') loadData();
  if (view === 'activity') renderActivities();
  if (view === 'growth') renderGrowth();
  if (view === 'invite') renderInvites();
}

function formatDate(value) {
  if (!value) return '';
  return new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}

function postTitle(post) { return (post.title || post.content || '无标题笔记').trim().split('\n')[0] || '无标题笔记'; }
function postImage(post) { return post.images && post.images[0] ? post.images[0] : ''; }
function isDraft(post) { return Boolean(post && (post.is_draft ?? post.isDraft)); }
function escapeHtml(value) { return String(value || '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char])); }

/* ---------- 真实统计口径：全部基于本人已发布笔记 ---------- */
function publishedPosts() { return state.posts.filter(post => !isDraft(post)); }
function publicPosts() { return publishedPosts().filter(post => post.is_public && (post.status || 'approved') === 'approved'); }
function statSum(key) { return publishedPosts().reduce((sum, post) => sum + (post[key] || 0), 0); }
function statMax(key) { return publishedPosts().reduce((max, post) => Math.max(max, post[key] || 0), 0); }
function recentPostsCount(days) {
  if (state.stats) return state.stats.trend.slice(-days).reduce((sum, day) => sum + day.posts, 0);
  const threshold = Date.now() - days * 86400000;
  return publishedPosts().filter(post => new Date(post.created_at).getTime() >= threshold).length;
}
function interactionOf(post) { return (post.like_count || 0) + (post.collection_count || 0) + (post.comment_count || 0); }

/* ---------- 成长体系 ---------- */
const CREATOR_LEVELS = [
  { lv: 1, name: '入门创作者', desc: '发布内容、积累互动，开启创作之旅。', match: () => true },
  { lv: 2, name: '进阶创作者', desc: '累计发布 3 篇笔记。', match: s => s.posts >= 3 },
  { lv: 3, name: '优质创作者', desc: '累计发布 8 篇笔记且获得 20 个赞。', match: s => s.posts >= 8 && s.likes >= 20 },
  { lv: 4, name: '人气创作者', desc: '发布 15 篇笔记、获得 100 个赞和 10 位粉丝。', match: s => s.posts >= 15 && s.likes >= 100 && s.fans >= 10 },
  { lv: 5, name: '签约创作者', desc: '获得 500 个赞和 50 位粉丝。', match: s => s.likes >= 500 && s.fans >= 50 },
  { lv: 6, name: '金牌创作者', desc: '获得 2000 个赞和 200 位粉丝。', match: s => s.likes >= 2000 && s.fans >= 200 }
];

function creatorStats() {
  if (state.stats) return { posts: state.stats.posts, likes: state.stats.likes, collections: state.stats.collections,
    comments: state.stats.comments, fans: state.stats.fans, maxLike: state.stats.max_like };
  return {
    posts: publicPosts().length,
    likes: statSum('like_count'),
    collections: statSum('collection_count'),
    comments: statSum('comment_count'),
    fans: state.me?.fans_count || 0,
    maxLike: statMax('like_count')
  };
}

function resolveLevel(stats) {
  let current = CREATOR_LEVELS[0];
  for (const level of CREATOR_LEVELS) if (level.match(stats)) current = level;
  return current;
}

function nextLevel(stats) { return CREATOR_LEVELS.find(level => !level.match(stats)) || null; }

/* ---------- 任务与活动定义（进度全部来自真实数据） ---------- */
const GROWTH_TASKS = [
  { id: 'first-post', name: '发布第一篇笔记', hint: '让更多人认识你', reward: 20, value: s => s.posts, target: 1 },
  { id: 'three-posts', name: '累计发布 3 篇笔记', hint: '保持稳定的创作节奏', reward: 30, value: s => s.posts, target: 3 },
  { id: 'ten-likes', name: '累计获得 10 个赞', hint: '好内容会被看见', reward: 30, value: s => s.likes, target: 10 },
  { id: 'five-comments', name: '累计收到 5 条评论', hint: '和评论区的读者聊聊天', reward: 30, value: s => s.comments, target: 5 },
  { id: 'five-fans', name: '收获 5 位粉丝', hint: '持续更新，吸引同好', reward: 40, value: s => s.fans, target: 5 },
  { id: 'hot-note', name: '单篇笔记获得 50 个赞', hint: '打磨封面和标题', reward: 50, value: s => s.maxLike, target: 50 }
];

const ACTIVITIES = [
  { id: 'newbie', name: '新人首发礼', desc: '发布你的第一篇笔记，瓜分新人流量扶持。', reward: 20, value: s => s.posts, target: 1 },
  { id: 'weekly', name: '周更挑战', desc: '近 7 天内发布 3 篇笔记，赢取首页推荐位。', reward: 50, value: () => recentPostsCount(7), target: 3 },
  { id: 'rising-star', name: '人气新星', desc: '单篇笔记获得 10 个赞，进入新星榜单。', reward: 40, value: s => s.maxLike, target: 10 },
  { id: 'interactive', name: '互动达人', desc: '累计笔记互动（赞+藏+评）达到 30 次。', reward: 60, value: s => s.likes + s.collections + s.comments, target: 30 }
];

const INVITE_BRANDS = ['山茶花洗护', '白屿咖啡', '拾光胶片相机', '轻野露营装备', '柚家家居', '远山旅行工作室'];
const INVITE_BUDGETS = ['稿费 200-500 元', '稿费 500-800 元', '稿费 800-1500 元'];

/* ---------- 列表渲染 ---------- */
function renderPostRow(post) {
  const image = postImage(post);
  return `<div class="post-row"><div class="post-thumb" ${image ? `style="background-image:url('${image}');background-size:cover;background-position:center"` : ''}></div><div class="post-meta"><b>${escapeHtml(postTitle(post))}</b><small>${formatDate(post.created_at || post.updated_at)} · ${isDraft(post) ? '草稿' : '已发布'}</small></div><div class="post-counts">赞 ${post.like_count || 0} · 藏 ${post.collection_count || 0}</div></div>`;
}

/* ---------- 内容管理：服务端分页 + 查看/编辑/删除 ---------- */
const NOTES_PAGE_SIZE = 20;

async function loadNotesPage(page) {
  if (!state.me) return;
  const target = Math.max(1, page || state.notesPage);
  const request = ++state.notesRequest;
  const posts = await api(`/creators/me/posts?page=${target}&limit=${state.notesLimit || NOTES_PAGE_SIZE}&filter=${encodeURIComponent(state.filter)}&keyword=${encodeURIComponent(state.notesKeyword || '')}`);
  if (request !== state.notesRequest) return;
  state.posts = posts.posts || [];
  state.drafts = state.posts.filter(isDraft);
  state.notesPage = posts.pagination?.page || target;
  state.notesTotalPages = posts.pagination?.pages || 1;
  state.notesTotal = posts.pagination?.total || 0;
  window.onCreatorNotesLoaded?.();
}

function renderNotes() {
  const visible = state.posts;
  const cards = visible.map(post => {
    const status = post.status || 'approved';
    const statusLabel = post.deleted_at ? '回收站' : isDraft(post) ? '草稿' : (!post.is_public ? '仅自己可见' : ({ approved: '已发布', pending: '审核中', rejected: '未通过', hidden: '已隐藏' }[status] || '已发布'));
    const statusClass = isDraft(post) ? '' : ({ approved: 'status-good', pending: 'status-warn', rejected: 'status-bad', hidden: 'status-bad' }[status] || '');
    const isVideo = post.media_type === 'video' && post.video_url;
    const cover = isVideo ? (post.images || [])[0] || '' : postImage(post);
    const coverClass = 'text-cover tg-' + ['pink', 'teal', 'amber', 'violet'][Math.abs((post.id || '').split('').reduce((s, ch) => s + ch.charCodeAt(0), 0)) % 4];
    const coverTitle = `<span class="text-cover-title">${escapeHtml(postTitle(post))}</span>`;
    let coverHtml;
    if (isVideo && cover) {
      coverHtml = `<div class="video-cover" style="background-image:url('${cover}')"><span class="video-badge">▶ 视频</span></div>`;
    } else if (isVideo) {
      coverHtml = `<div class="${coverClass}"><span class="video-badge">▶ 视频</span>${coverTitle}</div>`;
    } else if (postImage(post)) {
      coverHtml = `<div class="post-thumb" style="height:140px;width:100%;background-image:url('${postImage(post)}');background-size:cover;background-position:center"></div>`;
    } else {
      coverHtml = `<div class="${coverClass}">${coverTitle}</div>`;
    }
    // 未通过原因：管理员驳回/隐藏时填写，作者本人可见（对齐真实小红书告知创作者机制）
    const rejectNote = !isDraft(post) && ['pending', 'rejected', 'hidden'].includes(status) && post.review_note
      ? `<p class="note-reject-tip">审核说明：${escapeHtml(post.review_note)}</p>`
      : '';
    // 数据栏：真实小红书内容管理页每篇笔记都露出曝光/赞/藏/评论
    const statLine = `曝光 ${post.view_count || 0} · 赞 ${post.like_count || 0} · 藏 ${post.collection_count || 0} · 评 ${post.comment_count || 0}`;
    return `<article class="note-card"><label class="note-select"><input type="checkbox" data-creator-select="${escapeHtml(post.id)}">选择这篇笔记 <span>v${post.content_version || 1}</span></label>${coverHtml}<h3>${escapeHtml(postTitle(post))}</h3><p>${escapeHtml(post.content || '暂无正文')}</p>${rejectNote}<footer><span class="${statusClass}">${statusLabel}</span><span>${statLine}</span></footer><div class="note-actions"><button class="button button-light" data-note-view="${post.id}">查看</button>${post.deleted_at ? `<button class="button button-primary" data-creator-restore="${post.id}">恢复为草稿</button>` : `<button class="button button-light" data-note-edit="${post.id}">编辑</button>${isDraft(post) ? `<button class="button button-primary" data-note-publish="${post.id}">提交公开审核</button>` : `<button class="button button-light" data-note-todraft="${post.id}">转草稿</button>`}<button class="button button-light" data-note-del="${post.id}">移入回收站</button>`}</div></article>`;
  }).join('');
  const pager = `<div class="pager"><button class="button button-light" id="notesPrevPage" ${state.notesPage <= 1 ? 'disabled' : ''}>上一页</button><span>共 ${state.notesTotal} 条 · 第 ${state.notesPage}/${state.notesTotalPages} 页</span><button class="button button-light" id="notesNextPage" ${state.notesPage >= state.notesTotalPages ? 'disabled' : ''}>下一页</button></div>`;
  $('allPosts').innerHTML = (visible.length ? cards : '<div class="empty-state">暂无笔记，点击右上角发布第一篇内容。</div>') + pager;
}

function updateMetrics(me, serverStats) {
  const stats = creatorStats();
  $('followersMetric').textContent = serverStats ? (serverStats.fans || me?.fans_count || 0) : (me?.fans_count || 0);
  $('postsMetric').textContent = serverStats ? serverStats.posts : stats.posts;
  $('interactionsMetric').textContent = serverStats ? (serverStats.likes + serverStats.collections + serverStats.comments) : (stats.likes + stats.collections + stats.comments);
  $('likesMetric').textContent = serverStats ? serverStats.likes : stats.likes;
  $('collectionsMetric').textContent = serverStats ? serverStats.collections : stats.collections;
  $('commentsMetric').textContent = serverStats ? serverStats.comments : stats.comments;
  $('draftsMetric').textContent = serverStats ? serverStats.drafts : state.drafts.length;
  $('recentPosts').innerHTML = (serverStats?.recent_posts || []).map(renderPostRow).join('') || '<div class="empty-state">公开发布的笔记会显示在这里。</div>';
}

/* ---------- 活动中心 ---------- */
function progressCard(item, joined, claimed, extraActions = '') {
  const value = item.value(creatorStats());
  const ratio = Math.min(100, Math.round(value / item.target * 100));
  const done = value >= item.target;
  let action;
  if (!joined) action = `<button class="button button-primary" data-join="${item.id}">立即报名</button>`;
  else if (!done) action = `<span class="done-mark">已报名 · 进行中</span>`;
  else if (!claimed) action = `<button class="button button-primary" data-claim-activity="${item.id}">领取奖励 +${item.reward} 积分</button>`;
  else action = `<span class="done-mark">奖励已领取 ✓</span>`;
  return `<article class="grow-card"><div class="grow-head"><div><h3>${escapeHtml(item.name)}</h3><p class="desc">${escapeHtml(item.desc)}</p></div><span class="reward-tag">奖励 +${item.reward} 积分</span></div><div class="progress-track"><div class="progress-fill" style="width:${ratio}%"></div></div><p class="progress-text">进度 ${value}/${item.target}${done ? ' · 已达成' : ''}</p><div class="grow-actions">${action}${extraActions}</div></article>`;
}

function renderActivities() {
  const list = $('activityList');
  if (!state.token) { list.innerHTML = '<div class="empty-state">登录后即可报名活动并领取奖励。</div>'; return; }
  const joined = state.creator.joined;
  const claimed = state.creator.claimedActivities;
  list.innerHTML = ACTIVITIES.map(item => progressCard(item, joined.includes(item.id), claimed.includes(item.id))).join('')
    || '<div class="empty-state">暂无进行中的活动。</div>';
}

/* ---------- 成长中心 ---------- */
function renderGrowth() {
  if (!state.token) {
    $('levelBadge').textContent = 'LV1'; $('levelName').textContent = '入门创作者';
    $('levelDesc').textContent = '登录后开始你的创作成长之旅。';
    $('levelProgress').style.width = '0%'; $('levelNext').textContent = '';
    $('growthTasks').innerHTML = '<div class="empty-state">登录后查看创作任务。</div>';
    $('pointsValue').textContent = state.creator.points;
    return;
  }
  const stats = creatorStats();
  const level = resolveLevel(stats);
  const next = nextLevel(stats);
  $('levelBadge').textContent = `LV${level.lv}`;
  $('levelName').textContent = level.name;
  $('levelDesc').textContent = level.desc;
  if (next) {
    const ratio = Math.min(95, Math.round(level.lv / next.lv * 100));
    $('levelProgress').style.width = `${Math.max(8, ratio)}%`;
    $('levelNext').textContent = `下一等级：LV${next.lv} ${next.name} · ${next.desc}`;
  } else {
    $('levelProgress').style.width = '100%';
    $('levelNext').textContent = '已达到最高创作者等级。';
  }
  $('pointsValue').textContent = state.creator.points;
  $('growthTasks').innerHTML = GROWTH_TASKS.map(task => {
    const value = task.value(stats);
    const done = value >= task.target;
    const claimed = state.creator.claimedTasks.includes(task.id);
    const ratio = Math.min(100, Math.round(value / task.target * 100));
    let action;
    if (claimed) action = '<span class="done-mark">已领取 ✓</span>';
    else if (done) action = `<button class="button button-primary" data-claim-task="${task.id}">领取 +${task.reward} 积分</button>`;
    else action = `<span class="progress-text">未完成</span>`;
    return `<article class="grow-card"><div class="grow-head"><div><h3>${escapeHtml(task.name)}</h3><p class="desc">${escapeHtml(task.hint)}</p></div><span class="reward-tag">+${task.reward} 积分</span></div><div class="progress-track"><div class="progress-fill" style="width:${ratio}%"></div></div><p class="progress-text">进度 ${value}/${task.target}</p><div class="grow-actions">${action}</div></article>`;
  }).join('');
}

/* ---------- 作者邀约 ---------- */
function buildInvites() {
  return publishedPosts()
    .filter(post => (post.like_count || 0) >= 5)
    .sort((a, b) => (b.like_count || 0) - (a.like_count || 0))
    .slice(0, 3)
    .map(post => {
      const seed = Array.from(postTitle(post)).reduce((sum, ch) => sum + ch.charCodeAt(0), 0);
      return {
        postId: post.id,
        brand: INVITE_BRANDS[seed % INVITE_BRANDS.length],
        budget: INVITE_BUDGETS[seed % INVITE_BUDGETS.length],
        title: postTitle(post),
        likes: post.like_count || 0
      };
    });
}

function renderInvites() {
  const list = $('inviteList');
  if (!state.token) { list.innerHTML = '<div class="empty-state">登录后即可接收品牌合作邀约。</div>'; return; }
  const invites = buildInvites().filter(invite => !state.creator.dismissedInvites.includes(invite.postId));
  if (!invites.length) {
    const best = statMax('like_count');
    list.innerHTML = `<article class="grow-card"><div class="grow-head"><div><h3>暂无新的合作邀约</h3><p class="desc">当你的单篇笔记点赞达到 5 个后，会收到品牌合作邀约。</p></div></div><div class="progress-track"><div class="progress-fill" style="width:${Math.min(100, Math.round(best / 5 * 100))}%"></div></div><p class="progress-text">最高单篇点赞 ${best}/5</p><div class="grow-actions"><button class="button button-outline" data-view-link="notes">去发布笔记</button></div></article>`;
    return;
  }
  list.innerHTML = invites.map(invite => {
    const accepted = state.creator.acceptedInvites.includes(invite.postId);
    const action = accepted
      ? `<span class="done-mark">已接受邀约</span><button class="button button-primary" id="invitePublishButton">发布合作笔记</button>`
      : `<button class="button button-primary" data-accept="${invite.postId}">接受邀约</button><button class="button button-light" data-dismiss="${invite.postId}">暂不考虑</button>`;
    return `<article class="grow-card"><div class="grow-head"><div><h3>${escapeHtml(invite.brand)} · 内容合作邀约</h3><p class="desc">看中你的笔记《${escapeHtml(invite.title)}》（${invite.likes} 个赞），希望开展图文合作。</p></div><span class="reward-tag">${escapeHtml(invite.budget)} + 流量扶持</span></div><div class="grow-actions">${action}</div></article>`;
  }).join('');
}

/* ---------- 数据中心：近 7 天趋势图 + 笔记排行 ---------- */
function drawTrendChart() {
  const canvas = $('trendChart');
  if (!canvas || !canvas.getContext) return;
  const ctx = canvas.getContext('2d');
  const width = canvas.width, height = canvas.height;
  const padding = { top: 24, right: 20, bottom: 34, left: 40 };
  ctx.clearRect(0, 0, width, height);
  ctx.font = '12px Inter, "Microsoft YaHei", sans-serif';

  const days = (state.stats?.trend || []).map(day => ({ ...day, label: day.date.slice(5).replace('-', '/') }));
  if (!days.length) return;

  // 两组数据共用刻度，避免 1 篇笔记的柱子被拉满而看起来像 5 篇。
  const maxValue = Math.max(4, Math.ceil(Math.max(...days.map(day => Math.max(day.posts, day.interactions))) / 4) * 4);
  const maxPublish = maxValue;
  const maxInteraction = maxValue;
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const step = plotWidth / days.length;

  // 横轴网格与 Y 轴刻度（以互动量为主刻度）
  ctx.strokeStyle = '#eef0f3';
  ctx.fillStyle = '#a4aab4';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = padding.top + plotHeight * i / 4;
    ctx.beginPath(); ctx.moveTo(padding.left, y); ctx.lineTo(width - padding.right, y); ctx.stroke();
    ctx.textAlign = 'right';
    ctx.fillText(String(Math.round(maxInteraction * (4 - i) / 4)), padding.left - 8, y + 4);
  }

  // 柱状：每日发布笔记数
  const barWidth = Math.min(26, step * 0.34);
  days.forEach((day, index) => {
    const x = padding.left + step * index + step / 2;
    const barHeight = day.posts / maxPublish * plotHeight;
    if (day.posts > 0) {
      ctx.fillStyle = '#fe2c55';
      const radius = 3;
      const bx = x - barWidth / 2, by = padding.top + plotHeight - barHeight;
      ctx.beginPath();
      ctx.moveTo(bx, padding.top + plotHeight);
      ctx.lineTo(bx, by + radius);
      ctx.quadraticCurveTo(bx, by, bx + radius, by);
      ctx.lineTo(bx + barWidth - radius, by);
      ctx.quadraticCurveTo(bx + barWidth, by, bx + barWidth, by + radius);
      ctx.lineTo(bx + barWidth, padding.top + plotHeight);
      ctx.closePath();
      ctx.fill();
      ctx.textAlign = 'center';
      ctx.fillText(String(day.posts), x, by - 6);
    }
    ctx.fillStyle = '#a4aab4';
    ctx.textAlign = 'center';
    ctx.fillText(day.label, x, height - 12);
  });

  // 折线：每日互动量
  ctx.strokeStyle = '#3d7bff';
  ctx.lineWidth = 2;
  ctx.beginPath();
  days.forEach((day, index) => {
    const x = padding.left + step * index + step / 2;
    const y = padding.top + plotHeight - day.interactions / maxInteraction * plotHeight;
    if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();
  days.forEach((day, index) => {
    const x = padding.left + step * index + step / 2;
    const y = padding.top + plotHeight - day.interactions / maxInteraction * plotHeight;
    ctx.fillStyle = '#3d7bff';
    ctx.beginPath(); ctx.arc(x, y, 3.5, 0, Math.PI * 2); ctx.fill();
  });

  // 空数据提示
  if (!days.some(day => day.posts || day.interactions)) {
    ctx.fillStyle = '#9da2aa';
    ctx.textAlign = 'center';
    ctx.font = '13px Inter, "Microsoft YaHei", sans-serif';
    ctx.fillText('发布笔记后，这里会展示近 7 天的创作与互动趋势', width / 2, height / 2);
  }
}

function renderTrendChart() {
  const days = state.stats?.trend || [];
  $('chartRange').textContent = days.length ? `统计周期 ${days[0].date} - ${days[days.length - 1].date}（北京时间）` : '统计暂未加载';
  $('topPosts').innerHTML = state.stats?.top_posts?.length
    ? state.stats.top_posts.map(renderPostRow).join('')
    : '<div class="empty-state">还没有已发布的笔记，先去发布第一篇吧。</div>';
  requestAnimationFrame(drawTrendChart);
}

/* ---------- 数据加载 ---------- */
async function loadData() {
  if (!state.token) { updateMetrics(null); return; }
  try {
    const me = await api('/auth/me');
    state.me = me;
    const [, serverStats] = await Promise.all([loadNotesPage(1), api('/creators/me/stats')]);
    state.stats = serverStats;
    $('accountState').textContent = me.nickname || me.username;
    $('loginStrip').classList.add('hidden');
    $('loginButton').textContent = '退出登录';
    $('dataError').textContent = '';
    updateMetrics(me, serverStats);
    renderNotes();
    renderTrendChart();
  } catch (error) {
    if (error.status === 401) {
      state.token = ''; state.me = null; state.stats = null; state.posts = []; state.drafts = [];
      localStorage.removeItem('xhs_token');
      $('accountState').textContent = '未登录';
      $('loginStrip').classList.remove('hidden');
      $('loginError').textContent = error.message;
      updateMetrics(null);
      renderNotes();
    } else {
      $('dataError').textContent = `数据加载失败：${error.message}。请检查后台连接后刷新页面。`;
    }
  }
}

/* ---------- 登录已迁移到独立 /login 页 ---------- */

/* ---------- 发布 / 编辑笔记 ---------- */
let noteMediaType = 'image';

function setNoteMediaType(type) {
  noteMediaType = type;
  document.querySelectorAll('[data-media-type]').forEach(tab => tab.classList.toggle('active', tab.dataset.mediaType === type));
  $('imageFields').classList.toggle('hidden', type !== 'image');
  $('videoFields').classList.toggle('hidden', type !== 'video');
}

function openModal(post, viewOnly = false) {
  if (state.saving) return;
  state.editingPostId = post ? post.id : '';
  state.clientNoteId = post ? post.id : crypto.randomUUID();
  state.editingSource = post || null;
  state.viewOnly = Boolean(viewOnly);
  $('modalTitle').textContent = viewOnly ? '查看笔记' : (post ? '编辑笔记' : '发布笔记');
  $('noteTitleInput').value = post ? (post.title || '') : '';
  $('noteContentInput').value = post ? (post.content || '') : '';
  $('noteImagesInput').value = post ? (post.images || []).join('\n') : '';
  $('noteFilesInput').value = '';
  $('noteVideoInput').value = '';
  $('noteVideoUrlInput').value = post?.video_url || '';
  $('noteVisibilityInput').value = post?.is_public === false ? 'private' : 'public';
  const preview = $('noteVideoPreview');
  preview.pause();
  preview.removeAttribute('src');
  preview.load();
  preview.classList.add('hidden');
  setNoteMediaType(post && post.media_type === 'video' ? 'video' : 'image');
  // 编辑/查看视频笔记：预览已有视频
  if (post && post.media_type === 'video' && post.video_url) {
    const preview = $('noteVideoPreview');
    preview.src = post.video_url;
    preview.classList.remove('hidden');
  }
  // 只读查看：禁用全部输入、隐藏上传与保存按钮
  document.querySelectorAll('#noteModal input, #noteModal textarea, #noteModal select').forEach(field => { field.disabled = state.viewOnly; });
  document.querySelectorAll('[data-media-type]').forEach(tab => { tab.disabled = state.viewOnly; });
  $('saveDraftWebButton').classList.toggle('hidden', state.viewOnly);
  $('publishWebButton').classList.toggle('hidden', state.viewOnly);
  $('publishWebButton').textContent = post && !isDraft(post) ? '保存修改' : '发布';
  $('noteError').textContent = '';
  $('noteModal').classList.remove('hidden');
  if (!state.viewOnly) $('noteTitleInput').focus();
}
function closeModal() {
  if (state.saving) return;
  $('noteModal').classList.add('hidden');
  state.editingPostId = '';
  state.editingSource = null;
  state.viewOnly = false;
  document.querySelectorAll('#noteModal input, #noteModal textarea, #noteModal select').forEach(field => { field.disabled = false; });
  document.querySelectorAll('[data-media-type]').forEach(tab => { tab.disabled = false; });
  $('saveDraftWebButton').classList.remove('hidden');
  $('publishWebButton').classList.remove('hidden');
  const preview = $('noteVideoPreview');
  preview.pause();
  preview.removeAttribute('src');
  preview.load();
}

function setSaving(saving) {
  state.saving = saving;
  document.querySelectorAll('#noteModal input, #noteModal textarea, #noteModal select, #noteModal button').forEach(field => { field.disabled = saving || state.viewOnly; });
  $('publishWebButton').textContent = saving ? '保存中…' : (state.editingSource && !isDraft(state.editingSource) ? '保存修改' : '发布');
}

async function saveNote(draft) {
  if (state.saving || state.viewOnly) return;
  const title = $('noteTitleInput').value.trim();
  const content = $('noteContentInput').value.trim();
  let images = $('noteImagesInput').value.split('\n').map(item => item.trim()).filter(Boolean);
  $('noteError').textContent = '';
  if (noteMediaType === 'video') {
    const hasFile = $('noteVideoInput').files.length > 0;
    const hasUrl = $('noteVideoUrlInput').value.trim().length > 0;
    const existing = (state.editingSource || {}).video_url || '';
    if (!hasFile && !hasUrl && !existing) {
      $('noteError').textContent = '视频笔记需要上传视频文件或填写视频地址'; return;
    }
  } else if (!title && !content && !images.length && !$('noteFilesInput').files.length) {
    $('noteError').textContent = '请输入标题、正文，或至少添加一张图片'; return;
  }
  if (noteMediaType === 'image' && images.length + $('noteFilesInput').files.length > 9) { $('noteError').textContent = '图片最多 9 张'; return; }
  if (noteMediaType === 'image' && Array.from($('noteFilesInput').files).some(file => file.size > 10 * 1024 * 1024)) { $('noteError').textContent = '每张图片不能超过 10MB'; return; }
  setSaving(true);
  try {
    let videoUrl = (state.editingSource || {}).video_url || '';
    if (noteMediaType === 'video') {
      if ($('noteVideoUrlInput').value.trim()) {
        videoUrl = $('noteVideoUrlInput').value.trim();
      }
      if ($('noteVideoInput').files.length) {
        const form = new FormData();
        form.append('files', $('noteVideoInput').files[0]);
        const uploadResponse = await fetch('/api/media/upload', { method: 'POST', headers: { Authorization: `Bearer ${state.token}` }, body: form });
        const uploadBody = await uploadResponse.json();
        if (!uploadResponse.ok || uploadBody.code !== 200) throw new Error(uploadBody.message || '视频上传失败');
        const uploaded = (uploadBody.data.files || [])[0] || {};
        videoUrl = uploaded.url || '';
      }
    }
    if (noteMediaType === 'image') {
      const files = Array.from($('noteFilesInput').files || []).slice(0, 9 - images.length);
      if (files.length) {
        const form = new FormData();
        files.forEach(file => form.append('files', file));
        const uploadResponse = await fetch('/api/media/upload', { method: 'POST', headers: { Authorization: `Bearer ${state.token}` }, body: form });
        const uploadBody = await uploadResponse.json();
        if (!uploadResponse.ok || uploadBody.code !== 200) throw new Error(uploadBody.message || '图片上传失败');
        images = images.concat((uploadBody.data.files || []).map(file => file.url));
      }
    }
    const payload = JSON.stringify({
      id: state.clientNoteId, title, content, images, is_draft: draft,
      content_version: state.editingSource?.content_version,
      resubmit: !draft && ['rejected','hidden'].includes(state.editingSource?.status),
      topics: state.editingSource?.topics || [], location: state.editingSource?.location || '',
      is_public: $('noteVisibilityInput').value === 'public',
      media_type: noteMediaType === 'video' ? 'video' : 'image',
      video_url: noteMediaType === 'video' ? videoUrl : ''
    });
    if (state.editingPostId) {
      await api(`/posts/${encodeURIComponent(state.editingPostId)}`, { method: 'PUT', body: payload });
    } else {
      await api(draft ? '/posts/drafts' : '/posts', { method: 'POST', body: payload });
    }
    setSaving(false);
    closeModal();
    $('noteTitleInput').value = ''; $('noteContentInput').value = ''; $('noteImagesInput').value = ''; $('noteFilesInput').value = ''; $('noteVideoInput').value = '';
    await loadData();
    setView('notes');
  } catch (error) { $('noteError').textContent = error.message; }
  finally { setSaving(false); }
}

/* ---------- 内容管理操作 ---------- */
async function deleteNote(postId) {
  if (!confirm('将这篇笔记移入回收站？之后可以恢复为草稿。')) return;
  try {
    await api(`/posts/${encodeURIComponent(postId)}`, { method: 'DELETE', body: JSON.stringify({content_version: state.posts.find(p=>p.id===postId)?.content_version}) });
    await loadData();
    renderNotes();
  } catch (error) { alert(error.message); }
}

async function toggleNoteDraft(postId, toDraft) {
  try {
    await api(`/posts/${encodeURIComponent(postId)}`, { method: 'PUT', body: JSON.stringify({ is_draft: toDraft, ...(!toDraft?{is_public:true,resubmit:true}:{}), content_version: state.posts.find(p=>p.id===postId)?.content_version }) });
    await loadData();
    renderNotes();
  } catch (error) { alert(error.message); }
}

/* ---------- 事件绑定 ---------- */
document.querySelectorAll('.nav-item[data-view]').forEach(item => item.addEventListener('click', () => setView(item.dataset.view)));
document.querySelectorAll('[data-view-link]').forEach(item => item.addEventListener('click', () => setView(item.dataset.viewLink)));
document.querySelectorAll('[data-note-filter]').forEach(item => item.addEventListener('click', async () => {
  state.filter = item.dataset.noteFilter;
  document.querySelectorAll('[data-note-filter]').forEach(tab => tab.classList.toggle('active', tab === item));
  try { await loadNotesPage(1); renderNotes(); $('dataError').textContent = ''; }
  catch (error) { $('dataError').textContent = `笔记加载失败：${error.message}`; }
}));
$('loginButton').addEventListener('click', () => { if (state.token) { state.token = ''; localStorage.removeItem('xhs_token'); location.href = '/login'; } else location.href = '/login'; });
$('createNoteButton').addEventListener('click', () => openModal()); $('createNoteButton2').addEventListener('click', () => openModal());
$('closeModalButton').addEventListener('click', closeModal); $('saveDraftWebButton').addEventListener('click', () => saveNote(true)); $('publishWebButton').addEventListener('click', () => saveNote(false));

/* 媒体类型切换与视频预览 */
document.querySelectorAll('[data-media-type]').forEach(tab => tab.addEventListener('click', () => setNoteMediaType(tab.dataset.mediaType)));
$('noteVideoInput').addEventListener('change', () => {
  const preview = $('noteVideoPreview');
  const file = ($('noteVideoInput').files || [])[0];
  if (!file) { preview.classList.add('hidden'); preview.removeAttribute('src'); preview.load(); return; }
  if (file.size > 50 * 1024 * 1024) {
    $('noteVideoInput').value = '';
    preview.classList.add('hidden');
    $('noteError').textContent = '视频不能超过 50MB';
    return;
  }
  preview.src = URL.createObjectURL(file);
  preview.classList.remove('hidden');
});

/* 内容管理卡片操作与分页 */
$('allPosts').addEventListener('click', async event => {
  const view = event.target.closest('[data-note-view]');
  const edit = event.target.closest('[data-note-edit]');
  const del = event.target.closest('[data-note-del]');
  const toDraft = event.target.closest('[data-note-todraft]');
  const publish = event.target.closest('[data-note-publish]');
  const pagerPrev = event.target.closest('#notesPrevPage');
  const pagerNext = event.target.closest('#notesNextPage');
  if (view || edit || del || toDraft || publish) {
    const postId = (view || edit || del || toDraft || publish).dataset.noteView || (edit || del || toDraft || publish).dataset.noteEdit || (del || toDraft || publish).dataset.noteDel || (toDraft || publish).dataset.noteTodraft || publish.dataset.notePublish;
    const findPost = id => state.posts.find(post => post.id === id) || state.drafts.find(post => post.id === id);
    if (view) openModal(findPost(postId), true);
    if (edit) openModal(findPost(postId));
    if (del) await deleteNote(postId);
    if (toDraft) await toggleNoteDraft(postId, true);
    if (publish) await toggleNoteDraft(postId, false);
    return;
  }
  if (pagerPrev && state.notesPage > 1) { await loadNotesPage(state.notesPage - 1); renderNotes(); }
  if (pagerNext && state.notesPage < state.notesTotalPages) { await loadNotesPage(state.notesPage + 1); renderNotes(); }
});

$('activityList').addEventListener('click', async event => {
  const join = event.target.closest('[data-join]');
  const claim = event.target.closest('[data-claim-activity]');
  if (join) { if (!state.creator.joined.includes(join.dataset.join)) state.creator.joined.push(join.dataset.join); saveCreatorState(); renderActivities(); }
  if (claim) {
    const item = ACTIVITIES.find(candidate => candidate.id === claim.dataset.claimActivity);
    if (!item || state.creator.claimedActivities.includes(item.id)) return;
    state.creator.claimedActivities.push(item.id);
    state.creator.points += item.reward;
    saveCreatorState();
    renderActivities();
  }
});

$('growthTasks').addEventListener('click', event => {
  const claim = event.target.closest('[data-claim-task]');
  if (!claim) return;
  const task = GROWTH_TASKS.find(candidate => candidate.id === claim.dataset.claimTask);
  if (!task || state.creator.claimedTasks.includes(task.id)) return;
  state.creator.claimedTasks.push(task.id);
  state.creator.points += task.reward;
  saveCreatorState();
  renderGrowth();
});

$('inviteList').addEventListener('click', event => {
  const accept = event.target.closest('[data-accept]');
  const dismiss = event.target.closest('[data-dismiss]');
  const publish = event.target.closest('#invitePublishButton');
  if (accept) { if (!state.creator.acceptedInvites.includes(accept.dataset.accept)) state.creator.acceptedInvites.push(accept.dataset.accept); saveCreatorState(); renderInvites(); }
  if (dismiss) { state.creator.dismissedInvites.push(dismiss.dataset.dismiss); saveCreatorState(); renderInvites(); }
  if (publish) openModal();
});

window.addEventListener('resize', () => { if (!document.querySelector('[data-view-panel="data"]').classList.contains('hidden')) drawTrendChart(); });

state.creator = loadCreatorState();
loadData();
