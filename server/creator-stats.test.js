const test = require('node:test');
const assert = require('node:assert/strict');
const { creatorStats, dayKey } = require('./creator-stats');

test('daily analytics uses interaction dates and Beijing midnight, across every post', () => {
  const store = { posts: [], likes: [], collections: [], comments: [], follows: [], views: [] };
  for (let index = 0; index < 25; index++) store.posts.push({ id: `post${index}`, userId: 'alice', isPublic: true, created_at: '2026-09-08T16:10:00Z' });
  store.posts.push({ id: 'old', userId: 'alice', isPublic: true, created_at: '2026-08-01T00:00:00Z' });
  store.posts.push({ id: 'private', userId: 'alice', isPublic: false, created_at: '2026-09-08T16:10:00Z' });
  store.likes.push({ postId: 'old', created_at: '2026-09-08T15:59:59Z' });
  store.collections.push({ postId: 'old', created_at: '2026-09-08T16:00:00Z' });
  store.comments.push({ postId: 'post24', created_at: '2026-09-09T01:00:00Z' });
  store.likes.push({ postId: 'someone-else', created_at: '2026-09-09T01:00:00Z' });
  const result = creatorStats(store, 'alice', new Date('2026-09-09T02:00:00Z'));
  assert.equal(result.posts, 26);
  assert.equal(result.private_posts, 1);
  assert.deepEqual(result.trend.at(-1), { date: '2026-09-09', posts: 25, interactions: 2 });
  assert.deepEqual(result.trend.at(-2), { date: '2026-09-08', posts: 0, interactions: 1 });
  assert.equal(result.trend.reduce((sum, day) => sum + day.interactions, 0), 3);
  assert.equal(result.likes, 1);
  assert.equal(result.time_zone, 'Asia/Shanghai');
});

test('missing historic timestamps are not invented as today', () => {
  assert.equal(dayKey(undefined), null);
  assert.equal(dayKey('invalid'), null);
  const result = creatorStats({ posts: [{ id: 'p', userId: 'alice', isPublic: true }], likes: [{ postId: 'p' }], collections: [], comments: [], follows: [], views: [] }, 'alice');
  assert.equal(result.likes, 1);
  assert.equal(result.trend.reduce((sum, day) => sum + day.interactions, 0), 0);
});
