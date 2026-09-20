const fs = require('fs');
const path = require('path');

const dataFile = path.join(__dirname, '.smoke-test-data.json');
process.env.DATA_FILE = dataFile;
process.env.ADMIN_KEY = 'test-admin';
// dotenv 不会覆盖已有环境变量，这里显式指定冒烟专用密钥与管理员凭据，
// 使测试不依赖 .env 的实际配置
process.env.JWT_SECRET = 'smoke-test-secret';
process.env.ADMIN_USERNAME = 'admin';
process.env.ADMIN_PASSWORD = 'admin123456';

const app = require('./server');

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function main() {
  const server = await new Promise(resolve => {
    const instance = app.listen(0, '127.0.0.1', () => resolve(instance));
  });
  const baseUrl = `http://127.0.0.1:${server.address().port}/api`;

  const versions = new Map();
  async function request(pathname, options = {}) {
    if(options.body) {
      const body=JSON.parse(options.body);
      const match=pathname.match(/^\/posts\/([^/]+)$/);
      const id=match?.[1] || body.id;
      if(id && versions.has(id) && body.content_version===undefined)body.content_version=versions.get(id);
      options={...options,body:JSON.stringify(body)};
    }
    const response = await fetch(baseUrl + pathname, {
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const body = await response.json();
    assert(response.ok && body.code === 200, `${options.method || 'GET'} ${pathname} failed: ${body.message}`);
    if(body.data?.id && body.data?.content_version)versions.set(body.data.id,body.data.content_version);
    return body;
  }

  try {
    const suffix = Date.now().toString();
    const alice = await request('/auth/register', { method: 'POST', body: JSON.stringify({ username: `alice_${suffix}`, password: 'pass123456' }) });
    const bob = await request('/auth/register', { method: 'POST', body: JSON.stringify({ username: `bob_${suffix}`, password: 'pass123456' }) });
    const aliceHeaders = { Authorization: `Bearer ${alice.data.access_token}` };
    const bobHeaders = { Authorization: `Bearer ${bob.data.access_token}` };
    const profile = await request('/auth/me', { method: 'PATCH', headers: aliceHeaders, body: JSON.stringify({ nickname: 'Alice Updated', bio: 'test profile' }) });
    assert(profile.data.nickname === 'Alice Updated', 'profile update mismatch');
    const post = await request('/posts', { method: 'POST', headers: aliceHeaders, body: JSON.stringify({ title: 'Backend smoke', content: 'searchable note', topics: ['test'], images: [], is_public: true }) });
    const postId = post.data.id;
    await request(`/users/${alice.data.user.id}/follow`, { method: 'POST', headers: bobHeaders, body: '{}' });
    const liked = await request(`/posts/${postId}/like`, { method: 'POST', headers: bobHeaders, body: '{}' });
    const collected = await request(`/posts/${postId}/collection`, { method: 'POST', headers: bobHeaders, body: '{}' });
    assert(liked.data.like_count === 1, 'like count should increase by exactly one');
    assert(collected.data.collection_count === 1, 'collection count should increase by exactly one');
    const duplicateLike = await request(`/posts/${postId}/like`, { method: 'POST', headers: bobHeaders, body: '{}' });
    const duplicateCollection = await request(`/posts/${postId}/collection`, { method: 'POST', headers: bobHeaders, body: '{}' });
    assert(duplicateLike.data.like_count === 1, 'duplicate like must not increase the count');
    assert(duplicateCollection.data.collection_count === 1, 'duplicate collection must not increase the count');
    const viewHeaders = { ...bobHeaders, 'X-Visitor-Key': `smoke-${suffix}` };
    const firstView = await request(`/posts/${postId}/view`, { method: 'POST', headers: viewHeaders, body: '{}' });
    const secondView = await request(`/posts/${postId}/view`, { method: 'POST', headers: viewHeaders, body: '{}' });
    assert(firstView.data.viewed === true && secondView.data.viewed === false, 'daily view dedup mismatch');
    const report = await request(`/posts/${postId}/report`, { method: 'POST', headers: bobHeaders, body: JSON.stringify({ reason: 'smoke report' }) });
    assert(report.data.submitted === true, 'report submission mismatch');
    await request(`/posts/${postId}/comments`, { method: 'POST', headers: bobHeaders, body: JSON.stringify({ content: 'nice' }) });
    const comments = await request(`/posts/${postId}/comments`);
    assert(Array.isArray(comments.data) && comments.data.length === 1 && comments.data[0].content === 'nice', 'comments list mismatch');
    const search = await request('/search?keyword=searchable');
    const notifications = await request('/notifications', { headers: aliceHeaders });
    const adminStats = await request('/admin/stats', { headers: { 'X-Admin-Key': 'test-admin' } });
    const adminLogin = await request('/admin/auth/login', { method: 'POST', body: JSON.stringify({ username: 'admin', password: 'admin123456' }) });
    const adminMe = await request('/admin/auth/me', { headers: { Authorization: `Bearer ${adminLogin.data.access_token}` } });
    const adminPosts = await request('/admin/posts', { headers: { Authorization: `Bearer ${adminLogin.data.access_token}` } });
    // 服务端上传接口会校验文件头魔数，必须使用真实的最小 PNG（1x1 像素）
    const minimalPng = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64');
    const uploadForm = new FormData();
    uploadForm.append('files', new Blob([minimalPng], { type: 'image/png' }), 'smoke.png');
    const uploadResponse = await fetch(baseUrl + '/media/upload', { method: 'POST', headers: { Authorization: `Bearer ${alice.data.access_token}` }, body: uploadForm });
    const uploadBody = await uploadResponse.json();
    assert(uploadResponse.ok && uploadBody.code === 200 && uploadBody.data.files.length === 1, 'media upload mismatch');
    assert(search.data.pagination.total === 1, 'search result count mismatch');
    assert(notifications.data.unread === 4, 'notification count mismatch');
    assert(adminStats.data.users === 2 && adminStats.data.posts === 1, 'admin stats mismatch');
    assert(adminMe.data.role === 'admin' && adminPosts.data.posts.length === 1, 'admin JWT mismatch');
    // 公开笔记数不能包含私密、草稿、未通过审核或其他账号的内容。
    const createNote = async (fields, headers = aliceHeaders) => {
      const desired=fields.status; const payload={content:'creator stats regression',...fields};delete payload.status;
      if(desired==='pending') {
        payload.title='pending-'+suffix;
        await request('/admin/keyword-rules',{method:'POST',headers:{Authorization:`Bearer ${adminLogin.data.access_token}`},body:JSON.stringify({term:payload.title})});
      }
      const post=await request('/posts',{method:'POST',headers,body:JSON.stringify(payload)});
      if(['rejected','hidden'].includes(desired)) await request(`/admin/posts/${post.data.id}/review`,{method:'PATCH',headers:{Authorization:`Bearer ${adminLogin.data.access_token}`},body:JSON.stringify({status:desired,note:'smoke moderation',content_version:post.data.content_version})});
      return post;
    };
    const privatePost = await createNote({ is_public: false });
    await request('/posts/drafts', { method: 'POST', headers: aliceHeaders, body: JSON.stringify({ content: 'stats draft' }) });
    for (const status of ['pending', 'rejected', 'hidden']) await createNote({ is_public: true, status });
    await createNote({ is_public: true }, bobHeaders);
    const creatorStats = await request('/creators/me/stats', { headers: aliceHeaders });
    assert(creatorStats.data.posts === 1, 'public count must exclude private, draft, moderated and other users posts');
    assert(creatorStats.data.drafts === 1, 'draft count should remain independent');
    assert(creatorStats.data.trend.reduce((sum, day) => sum + day.posts, 0) === 1, 'publish trend must use public count criteria');
    await request(`/posts/${privatePost.data.id}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_public: true }) });
    const afterPublic = await request('/creators/me/stats', { headers: aliceHeaders });
    assert(afterPublic.data.posts === 2, 'making a private note public should increase public count');
    const paged = await request(`/posts?user_id=${alice.data.user.id}&limit=1`, { headers: aliceHeaders });
    assert(paged.data.posts.length === 1 && afterPublic.data.posts === 2, 'public count must not depend on page size');
    await request(`/posts/${privatePost.data.id}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_public: false }) });
    const afterPrivate = await request('/creators/me/stats', { headers: aliceHeaders });
    assert(afterPrivate.data.posts === 1, 'making a note private should reduce public count again');
    for (const [filter, total] of Object.entries({ all: 6, published: 1, private: 1, draft: 1, pending: 1, rejected: 2 })) {
      const list = await request(`/creators/me/posts?filter=${filter}&limit=10`, { headers: aliceHeaders });
      assert(list.data.pagination.total === total && list.data.posts.length === Math.min(total,10), `creator filter ${filter} must run before pagination: total=${list.data.pagination.total}, page length=${list.data.posts.length}, expected=${total}`);
      assert(list.data.posts.every(post => post.user.id === alice.data.user.id), 'creator list leaked another account');
    }
    const readOwn = await request(`/posts/${privatePost.data.id}`, { headers: aliceHeaders });
    assert(readOwn.data.is_public === false, 'author must still be able to read a private note');
    for (const headers of [{}, bobHeaders]) {
      for (const route of [`/posts/${privatePost.data.id}`, `/posts/${privatePost.data.id}/comments`]) {
        const denied = await fetch(baseUrl + route, { headers });
        assert(denied.status === 404, 'private note or comments exposed to another viewer');
      }
    }
    for (const suffix of ['like', 'collection', 'view', 'comments', 'report']) {
      const denied = await fetch(`${baseUrl}/posts/${privatePost.data.id}/${suffix}`, { method: 'POST', headers: { ...bobHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify({ content: 'should not be added', reason: 'private' }) });
      assert(denied.status === 404, `private note allowed unauthorized ${suffix}`);
    }
    await request(`/posts/${postId}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_public: false }) });
    const publicLikes = await request(`/users/${bob.data.user.id}/likes`);
    const publicCollections = await request(`/users/${bob.data.user.id}/collections`);
    assert(publicLikes.data.posts.length === 0 && publicCollections.data.collections.length === 0, 'liked/collected notes must disappear when made private');
    await request(`/posts/${postId}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_public: true }) });
    const retryId = `retry-${suffix}`;
    const firstPublish = await createNote({ id: retryId, is_public: false, topics: ['保留话题'], location: '厦门中山路' });
    const retryPublish = await createNote({ id: retryId });
    assert(retryPublish.data.created_at === firstPublish.data.created_at, 'publish retry changed creation date');
    assert(retryPublish.data.is_public === false && retryPublish.data.location === '厦门中山路' && retryPublish.data.topics[0] === '保留话题', 'publish retry lost metadata');
    const collision = await fetch(`${baseUrl}/posts`, { method: 'POST', headers: { ...bobHeaders, 'Content-Type': 'application/json' }, body: JSON.stringify({ id: retryId, content: 'collision' }) });
    assert(collision.status === 409, 'post id collision must not overwrite another account');
    const imageOnly = await createNote({ title: '', content: '', images: ['/seed/note/1.jpg'] });
    assert(imageOnly.data.images.length === 1, 'image-only publishing should work');
    const video = await createNote({ media_type: 'video', video_url: '/uploads/test.mp4' });
    const image = await request(`/posts/${video.data.id}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ media_type: 'image', video_url: '' }) });
    assert(image.data.media_type === 'image' && image.data.video_url === '', 'switching from video to image retained stale media');
    const asDraft = await request(`/posts/${retryId}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_draft: true }) });
    assert(asDraft.data.is_draft && !asDraft.data.is_public, 'save as draft must preserve privacy');
    const republish = await request(`/posts/${retryId}`, { method: 'PUT', headers: aliceHeaders, body: JSON.stringify({ is_draft: false }) });
    assert(!republish.data.is_draft && !republish.data.is_public, 'publishing draft must preserve privacy');
    console.log('creator-management-and-privacy-ok');
    console.log('creator-stats-regression-ok');
    console.log('backend-smoke-ok');
  } finally {
    await new Promise(resolve => server.close(resolve));
    if (fs.existsSync(dataFile)) fs.unlinkSync(dataFile);
    if (fs.existsSync(`${dataFile}.tmp`)) fs.unlinkSync(`${dataFile}.tmp`);
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
