const crypto = require('node:crypto');
const { TABLES } = require('./migrate-moderation');
class DomainError extends Error { constructor(status, message) { super(message); this.status = status; } }
const fail = (status, message) => { throw new DomainError(status, message); };
const now = () => new Date().toISOString();
const normalize = text => String(text || '').normalize('NFKC').toLowerCase().replace(/[\u200b-\u200f\u2060\ufeff]/g, '');
const hash = value => crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
const version = post => post.content_version || 1;
const snapshot = post => ({ title: post.title || '', content: post.content || '', topics: post.topics || [], location: post.location || '', images: post.images || [], mediaType: post.mediaType, videoUrl: post.videoUrl });
function ensure(store) {
  for (const key of Object.keys(TABLES)) if (!Array.isArray(store[key])) store[key] = [];
  for(const post of store.posts) {
    if(post.status==null||post.status==='')post.status='approved';
    if(!post.content_version)post.content_version=1;
  }
}
function expectVersion(post, expected) {
  if (!Number.isInteger(Number(expected)) || Number(expected) !== version(post)) fail(409, '内容已变化，请刷新后重新操作');
}
function event(store, post, action, actor, detail = {}) {
  store.moderationEvents.unshift({ id: crypto.randomUUID(), postId: post?.id || null, content_version: post ? version(post) : null, action, actor, detail, created_at: now() });
}
function hits(store, post) {
  const found = [];
  for (const rule of store.keywordRules.filter(rule => rule.enabled)) {
    for (const field of ['title', 'content', 'topics']) {
      const value = normalize(field === 'topics' ? (post.topics || []).join(' ') : post[field]);
      const index = value.indexOf(rule.normalized);
      if (index >= 0) found.push({ ruleId: rule.id, keyword: rule.term, category: rule.category, field,
        snippet: value.slice(Math.max(0, index - 40), index + rule.normalized.length + 40), normalized_match: rule.normalized });
    }
  }
  return found;
}
function staleCases(store, post) {
  for (const item of store.moderationCases.filter(item => item.postId === post.id && item.state === 'open' && item.content_version !== version(post))) {
    item.state = 'stale'; item.updated_at = now();
    event(store, post, 'case_stale', 'system', { caseId: item.id, previous_version: item.content_version });
  }
}
function enqueue(store, item) {
  if (store.aiJobs.some(job => job.caseId === item.id && ['queued', 'running'].includes(job.state))) return;
  const job = { id: crypto.randomUUID(), caseId: item.id, postId: item.postId, content_version: item.content_version,
    snapshot: item.snapshot, hits: item.hits, reason: item.reason, state: 'queued', attempts: 0, next_at: now(), created_at: now() };
  store.aiJobs.push(job); item.ai_state = 'queued'; item.updated_at = now();
}
function openCase(store, post, kind, matches = [], reason = '') {
  let item = store.moderationCases.find(item => item.postId === post.id && item.content_version === version(post) && item.kind === kind && item.state === 'open');
  if (item) return item;
  item = { id: crypto.randomUUID(), postId: post.id, content_version: version(post), kind, state: 'open',
    snapshot: snapshot(post), hits: matches, reason, ai_state: 'queued', ai_result: null, created_at: now(), updated_at: now() };
  store.moderationCases.unshift(item); enqueue(store, item);
  event(store, post, 'case_opened', 'system', { caseId: item.id, kind, hits: matches });
  return item;
}
function submit(store, post, previous, force = false) {
  ensure(store);
  const changed = !previous || hash(snapshot(post)) !== hash(snapshot(previous));
  const changedVisibility = previous && (post.isPublic !== previous.isPublic || post.isDraft !== previous.isDraft);
  if (previous && !changed && !changedVisibility && !force) {
    Object.assign(post, { status: previous.status, content_version: version(previous), review_note: previous.review_note,
      review_at: previous.review_at, requires_manual: previous.requires_manual, deleted_at: previous.deleted_at });
    return post;
  }
  post.content_version = previous ? version(previous) + 1 : 1;
  post.deleted_at = previous?.deleted_at || null;
  post.requires_manual = Boolean(previous?.requires_manual || ['rejected', 'hidden'].includes(previous?.status));
  post.review_note = ''; post.review_at = ''; post.updated_at = now();
  staleCases(store, post);
  if (post.isDraft || !post.isPublic) {
    post.status = post.requires_manual ? 'rejected' : 'approved';
  } else {
    const matches = hits(store, post);
    post.status = matches.length || post.requires_manual ? 'pending' : 'approved';
    if (post.status === 'pending') {
      post.review_note = matches.length ? '命中平台关键词，等待人工审核' : '重新提交，等待人工审核';
      openCase(store, post, 'publication', matches, post.review_note);
    }
  }
  event(store, post, 'submitted', post.userId, { status: post.status, isDraft: post.isDraft, isPublic: post.isPublic });
  return post;
}
function notify(store, post, decision, reason) {
  store.notifications.unshift({ id: crypto.randomUUID(), userId: post.userId, actorId: post.userId, type: 'moderation', postId: post.id, read: false,
    message: `笔记${decision === 'approved' ? '审核通过' : decision === 'hidden' ? '已隐藏' : '未通过审核'}：${reason}`, created_at: now() });
}
function decide(store, post, decision, reason, actor, expected, caseId) {
  expectVersion(post, expected);
  if (post.deleted_at || post.isDraft) fail(409, '草稿或回收站内容不能直接审核发布');
  if (!['approved', 'rejected', 'hidden', 'keep'].includes(decision)) fail(400, '无效的审核结论');
  if (typeof reason !== 'string' || !reason.trim() || reason.length > 1000) fail(400, '请填写 1～1000 字审核理由');
  const item = caseId ? store.moderationCases.find(item => item.id === caseId) : null;
  if (caseId && (!item || item.postId !== post.id || item.content_version !== version(post) || item.state !== 'open')) fail(409, '审核案件已变化，请刷新');
  if (decision === 'keep' && item?.kind !== 'report') fail(400, '只有举报再审支持保留结论');
  const before = post.status;
  if (decision !== 'keep') {
    post.status = decision; post.requires_manual = decision !== 'approved';
    post.review_note = reason.trim(); post.review_at = now(); post.updated_at = now();
    notify(store, post, decision, reason.trim());
  }
  const cases = item && decision === 'keep' ? [item] : store.moderationCases.filter(c => c.postId === post.id && c.content_version === version(post) && c.state === 'open');
  for (const c of cases) {
    c.state = 'closed'; if(!c.ai_result)c.ai_state='cancelled'; c.decision = decision; c.note = reason.trim(); c.handled_by = actor; c.handled_at = now(); c.updated_at = now();
    for (const report of store.reports.filter(r => r.caseId === c.id && r.status === 'pending')) {
      Object.assign(report, { status: ['hidden', 'rejected'].includes(decision) ? 'resolved' : 'dismissed', note: reason.trim(), handled_by: actor, handled_at: now() });
    }
  }
  event(store, post, 'human_review', actor, { before, after: post.status, decision, reason: reason.trim(), caseId: item?.id });
  return post;
}
function recycle(store, post, actor, expected, restore = false) {
  expectVersion(post, expected);
  if (restore ? !post.deleted_at : !!post.deleted_at) fail(409, restore ? '笔记不在回收站' : '笔记已在回收站');
  post.deleted_at = restore ? null : now(); post.content_version = version(post) + 1;
  if (restore) { post.isDraft = true; post.isPublic = false; }
  post.updated_at = now(); staleCases(store, post);
  if (!restore) for (const r of store.reports.filter(r=>r.postId===post.id && r.status==='pending')) {
    Object.assign(r,{status:'resolved',note:'笔记已移入回收站，举报记录保留',handled_by:actor,handled_at:now()});
  }
  event(store, post, restore ? 'restored_as_draft' : 'recycled', actor);
  return post;
}
function report(store, post, userId, reason) {
  const duplicate = store.reports.find(r => r.postId === post.id && r.userId === userId && r.content_version === version(post) && r.status === 'pending');
  if (duplicate) return { submitted: false, case_id: duplicate.caseId };
  const item = openCase(store, post, 'report', hits(store, post), reason);
  store.reports.push({ id: crypto.randomUUID(), postId: post.id, content_version: version(post), caseId: item.id,
    userId, reason, status: 'pending', created_at: now() });
  event(store, post, 'reported', userId, { caseId: item.id });
  return { submitted: true, case_id: item.id };
}
function rule(store, body, existing) {
  const term = String(body.term ?? existing?.term ?? '').trim();
  const normalized = normalize(term);
  if (!normalized.trim() || term.length > 80) fail(400, '关键词需为 1～80 个字符');
  if (store.keywordRules.some(r => r.normalized === normalized && r.id !== existing?.id)) fail(409, '关键词已存在');
  const result = { ...existing, id: existing?.id || crypto.randomUUID(), term, normalized,
    category: String(body.category ?? existing?.category ?? '自定义').slice(0, 40),
    enabled: body.enabled === undefined ? (existing?.enabled ?? true) : body.enabled === true,
    created_at: existing?.created_at || now(), updated_at: now() };
  if (existing) Object.assign(existing, result); else store.keywordRules.push(result);
  return result;
}
module.exports = { ensure, normalize, hits, snapshot, version, expectVersion, submit, decide, recycle, report, rule, event, enqueue, openCase, hash, fail, DomainError };
