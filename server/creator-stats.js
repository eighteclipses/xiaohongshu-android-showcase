const timeZone = 'Asia/Shanghai';
const dateFormatter = new Intl.DateTimeFormat('en-CA', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' });

function dayKey(value) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return null;
  const parts = Object.fromEntries(dateFormatter.formatToParts(date).map(part => [part.type, part.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}

function isPublicPost(post) {
  return Boolean(post && !post.deleted_at && !post.isDraft && post.isPublic && (post.status || 'approved') === 'approved');
}

function canReadPost(post, userId) {
  return Boolean(post && !post.deleted_at && ((userId && post.userId === userId) || isPublicPost(post)));
}

function creatorStats(store, userId, now = new Date()) {
  const mine = store.posts.filter(post => post.userId === userId && !post.deleted_at && !post.isDraft);
  const publicPosts = mine.filter(isPublicPost);
  const ids = new Set(mine.map(post => post.id));
  const ownRows = rows => rows.filter(row => ids.has(row.postId));
  const likes = ownRows(store.likes);
  const collections = ownRows(store.collections);
  const comments = ownRows(store.comments);
  const interactions = [...likes, ...collections, ...comments];
  const trend = Array.from({ length: 7 }, (_, index) => {
    const date = dayKey(new Date(now.getTime() - (6 - index) * 86400000));
    return {
      date,
      posts: publicPosts.filter(post => dayKey(post.created_at) === date).length,
      // 互动按发生日期统计，不能把累计值重复放入每一天。
      interactions: interactions.filter(row => dayKey(row.created_at) === date).length
    };
  });
  return {
    posts: publicPosts.length,
    private_posts: mine.filter(post => !post.isPublic).length,
    drafts: store.posts.filter(post => post.userId === userId && !post.deleted_at && post.isDraft).length,
    likes: likes.length, collections: collections.length, comments: comments.length,
    views: ownRows(store.views).length,
    fans: store.follows.filter(row => row.followingId === userId).length,
    max_like: Math.max(0, ...mine.map(post => likes.filter(row => row.postId === post.id).length)),
    time_zone: timeZone,
    trend
  };
}

module.exports = { creatorStats, isPublicPost, canReadPost, dayKey };
