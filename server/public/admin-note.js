let activeVersion=1;
const $ = id => document.getElementById(id);
const noteId = new URLSearchParams(location.search).get('id') || location.pathname.split('/').pop();
const token = localStorage.getItem('xhs_admin_token') || '';
const REVIEW_LABEL = { approved: '已通过', pending: '待审核', rejected: '已驳回', hidden: '已隐藏' };

function esc(value) {
  return String(value || '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

async function api(path, options = {}) {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}`, ...(options.headers || {}) }
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || body.code !== 200) throw new Error(body.message || '请求失败');
  return body.data;
}

function isDraft(note) { return Boolean(note.is_draft ?? note.isDraft); }

function statusLabel(note) {
  if (isDraft(note)) return '草稿';
  return REVIEW_LABEL[note.status || 'approved'] || (note.status || 'approved');
}

/* 操作按钮：草稿只有"提交审核"；已发布笔记提供审核状态切换、下架与删除 */
function renderActions(note) {
  const buttons = [];
  if (note.deleted_at) { $('noteActions').innerHTML='<a class="button button-light" href="/admin">前往回收站管理</a>'; return; }
  if (isDraft(note)) {
    buttons.push('<button class="button button-primary" data-note-publish="published">提交审核</button>');
  } else {
    const status = note.status || 'approved';
    if (status !== 'approved') buttons.push('<button class="button button-primary" data-note-review="approved">通过</button>');
    
    if (status !== 'rejected') buttons.push('<button class="button button-light" data-note-review="rejected">驳回</button>');
    if (status !== 'hidden') buttons.push('<button class="button button-light" data-note-review="hidden">隐藏</button>');
    buttons.push('<button class="button button-light" data-note-publish="draft">下架转草稿</button>');
  }
  buttons.push('<button class="button button-danger" data-note-delete>删除笔记</button>');
  $('noteActions').innerHTML = buttons.join('');
}

function renderNote(note) {
  activeVersion=note.content_version || 1;
  $('noteStatus').textContent = statusLabel(note);
  $('noteTitle').textContent = note.title || '无标题笔记';
  const author = note.user?.nickname || note.user?.username || '未知';
  const createdAt = note.created_at ? new Date(note.created_at).toLocaleString('zh-CN') : '';
  $('noteAuthor').textContent = `作者：${author} · ${createdAt}`;
  $('noteContent').textContent = note.content || '（无正文）';
  $('noteStats').textContent = `点赞 ${note.like_count || 0} · 收藏 ${note.collection_count || 0} · 评论 ${note.comment_count || 0} · 浏览 ${note.view_count || 0}`;
  $('noteImages').innerHTML = (note.images || []).map(src => `<img src="${esc(src)}" alt="笔记图片">`).join('');
  $('noteVideo').innerHTML = note.media_type === 'video' && note.video_url
    ? `<video controls src="${esc(note.video_url)}"></video>`
    : '';
  $('noteComments').innerHTML = (note.comments || []).map(comment => {
    const name = comment.user?.nickname || comment.user?.username || '匿名用户';
    return `<div class="post-row"><div class="post-meta"><b>${esc(name)}</b><small>${esc(comment.content)}</small></div></div>`;
  }).join('') || '<div class="empty-state">暂无评论</div>';
  renderActions(note);
}

async function load() {
  if (!token) { $('noteLogin').classList.remove('hidden'); return; }
  try {
    const me = await api('/admin/auth/me');
    $('adminState').textContent = me.username;
    const note = await api(`/admin/posts/${encodeURIComponent(noteId)}`);
    $('noteLogin').classList.add('hidden');
    $('noteView').classList.remove('hidden');
    renderNote(note);
  } catch (error) {
    $('noteError').textContent = error.message;
  }
}

$('noteActions').addEventListener('click', async event => {
  const review = event.target.closest('[data-note-review]');
  const publish = event.target.closest('[data-note-publish]');
  const remove = event.target.closest('[data-note-delete]');
  try {
    if (review) {
      const reason=prompt('请填写审核理由');if(!reason?.trim())return;
      await api(`/admin/posts/${encodeURIComponent(noteId)}/review`, {
        method: 'PATCH', body: JSON.stringify({ status: review.dataset.noteReview, note:reason, content_version:activeVersion })
      });
    } else if (publish) {
      await api(`/admin/posts/${encodeURIComponent(noteId)}/status`, {
        method: 'PATCH', body: JSON.stringify({ status: publish.dataset.notePublish, content_version:activeVersion })
      });
    } else if (remove) {
      if (!confirm('确定删除这篇笔记吗？')) return;
      await api(`/admin/posts/${encodeURIComponent(noteId)}`, { method: 'DELETE', body:JSON.stringify({content_version:activeVersion}) });
      location.href = '/admin';
      return;
    } else {
      return;
    }
    await load();
  } catch (error) {
    $('noteError').textContent = error.message;
  }
});

load();
