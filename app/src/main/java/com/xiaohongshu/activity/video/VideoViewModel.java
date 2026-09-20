package com.xiaohongshu.activity.video;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.CollectionEntity;
import com.xiaohongshu.database.entity.LikeEntity;
import com.xiaohongshu.database.entity.MessageEntity;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.network.PendingSyncStore;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.ui.home.HomeDataRepository;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 刷视频页 ViewModel
 * 负责加载视频列表，并提供基于本地数据库/远程后台的真实点赞、收藏、评论数据
 */
public class VideoViewModel extends AndroidViewModel {
    private String id = "";
    private final MutableLiveData<GraphicCardBean> graphicCardBean = new MutableLiveData<>();
    // 无初始值：避免 observer 注册时回放空列表导致误报"暂无视频"
    private final MutableLiveData<List<GraphicCardBean>> videoList = new MutableLiveData<>();

    private final MutableLiveData<Map<String, Integer>> likeCounts = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Map<String, Integer>> collectionCounts = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Map<String, Integer>> commentCounts = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Set<String>> likedIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Set<String>> collectedIds = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<List<com.xiaohongshu.activity.graphic.CommentBean>> comments = new MutableLiveData<>(new ArrayList<>());

    private final AppDatabase database;
    private final RemoteApiClient remoteApiClient;
    private final HomeDataRepository repository = HomeDataRepository.getInstance();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public VideoViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
        remoteApiClient = new RemoteApiClient(application.getApplicationContext());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public LiveData<List<GraphicCardBean>> getVideoList() {
        return videoList;
    }

    public LiveData<GraphicCardBean> getGraphicCardBean() {
        return graphicCardBean;
    }

    public LiveData<Map<String, Integer>> getLikeCounts() {
        return likeCounts;
    }

    public LiveData<Map<String, Integer>> getCollectionCounts() {
        return collectionCounts;
    }

    public LiveData<Map<String, Integer>> getCommentCounts() {
        return commentCounts;
    }

    public LiveData<Set<String>> getLikedIds() {
        return likedIds;
    }

    public LiveData<List<com.xiaohongshu.activity.graphic.CommentBean>> getComments() {
        return comments;
    }

    public LiveData<Set<String>> getCollectedIds() {
        return collectedIds;
    }

    /**
     * 加载刷视频列表；带 id 时把点击的视频放到第一位，否则从第一个视频开始刷。
     * 缓存组不出列表时强制刷新一次（mock 卡 id 或缓存可能失配）。
     */
    public void init() {
        repository.getGraphicCardList(false, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                List<GraphicCardBean> videos = buildVideoFeed(data);
                if (videos.isEmpty()) {
                    repository.getGraphicCardList(true, new HomeDataRepository.DataCallback<List<GraphicCardBean>>() {
                        @Override
                        public void onSuccess(List<GraphicCardBean> fresh) {
                            List<GraphicCardBean> retry = buildVideoFeed(fresh);
                            videoList.postValue(retry);
                            loadInteractions(retry);
                        }

                        @Override
                        public void onError(Exception error) {
                            videoList.postValue(new ArrayList<>());
                        }
                    });
                    return;
                }
                videoList.postValue(videos);
                loadInteractions(videos);
            }

            @Override
            public void onError(Exception error) {
                // 列表加载失败时保持空列表
            }
        });
    }

    /** 从内容列表组装视频 feed：被点击的视频置顶，其余视频卡跟随 */
    private List<GraphicCardBean> buildVideoFeed(List<GraphicCardBean> data) {
        List<GraphicCardBean> videos = new ArrayList<>();
        GraphicCardBean foundCard = null;
        for (GraphicCardBean card : data) {
            if (id.equals(card.getId())) {
                foundCard = card;
                break;
            }
        }
        if (foundCard != null) {
            videos.add(foundCard);
            graphicCardBean.postValue(foundCard);
        }
        for (GraphicCardBean card : data) {
            if (card.getType() == GraphicCardType.Video && !card.getId().equals(id)) {
                videos.add(card);
            }
        }
        return videos;
    }

    /**
     * 一次性加载所有视频的真实互动数据
     */
    private void loadInteractions(List<GraphicCardBean> videos) {
        executorService.execute(() -> {
            // 浏览上报：用户点进来的那个视频计一次曝光（服务端 optionalAuth，匿名也计）
            if (id != null && !id.isEmpty()) {
                try {
                    String deviceId = android.provider.Settings.Secure.getString(
                            getApplication().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                    remoteApiClient.recordView(id, "android-" + (deviceId == null ? "unknown" : deviceId),
                            currentAccessToken());
                } catch (Exception ignored) {
                    // 后台不可达时跳过，不影响刷视频
                }
            }
            Map<String, Integer> likes = new HashMap<>();
            Map<String, Integer> collections = new HashMap<>();
            Map<String, Integer> comments = new HashMap<>();
            Set<String> liked = new HashSet<>();
            Set<String> collected = new HashSet<>();
            String userId = resolveCurrentUserId();
            for (GraphicCardBean card : videos) {
                String noteId = card.getId();
                if (noteId == null || noteId.isEmpty()) continue;
                likes.put(noteId, database.likeDao().getLikeCountSync(noteId));
                collections.put(noteId, database.collectionDao().getCollectionCountSync(noteId));
                comments.put(noteId, database.commentDao().getCommentCountSync(noteId));
                if (!userId.isEmpty()) {
                    if (database.likeDao().checkLike(noteId, userId) != null) liked.add(noteId);
                    if (database.collectionDao().checkCollection(noteId, userId) != null) collected.add(noteId);
                }
            }
            likeCounts.postValue(likes);
            collectionCounts.postValue(collections);
            commentCounts.postValue(comments);
            likedIds.postValue(liked);
            collectedIds.postValue(collected);
        });
    }

    /**
     * 点赞/取消点赞
     */
    public void toggleLike(GraphicCardBean card) {
        if (card == null) return;
        executorService.execute(() -> {
            String userId = resolveCurrentUserId();
            if (userId.isEmpty()) return;
            String noteId = card.getId();
            boolean liked = database.likeDao().checkLike(noteId, userId) != null;
            if (liked) {
                database.likeDao().deleteLike(noteId, userId);
                syncRemote("unlike", noteId);
            } else {
                database.likeDao().insert(new LikeEntity(noteId, userId, System.currentTimeMillis()));
                createMessage(noteId, userId, 0, "点赞了你的笔记");
                syncRemote("like", noteId);
            }

            Map<String, Integer> current = value(likeCounts);
            current.put(noteId, database.likeDao().getLikeCountSync(noteId));
            likeCounts.postValue(new HashMap<>(current));
            Set<String> ids = new HashSet<>(value(likedIds));
            if (liked) ids.remove(noteId); else ids.add(noteId);
            likedIds.postValue(ids);
        });
    }

    /**
     * 收藏/取消收藏
     */
    public void toggleCollect(GraphicCardBean card) {
        if (card == null) return;
        executorService.execute(() -> {
            String userId = resolveCurrentUserId();
            if (userId.isEmpty()) return;
            String noteId = card.getId();
            boolean collected = database.collectionDao().checkCollection(noteId, userId) != null;
            if (collected) {
                database.collectionDao().deleteCollection(noteId, userId);
                syncRemote("uncollection", noteId);
            } else {
                database.collectionDao().insert(new CollectionEntity(noteId, userId, System.currentTimeMillis()));
                createMessage(noteId, userId, 1, "收藏了你的笔记");
                syncRemote("collection", noteId);
            }

            Map<String, Integer> current = value(collectionCounts);
            current.put(noteId, database.collectionDao().getCollectionCountSync(noteId));
            collectionCounts.postValue(new HashMap<>(current));
            Set<String> ids = new HashSet<>(value(collectedIds));
            if (collected) ids.remove(noteId); else ids.add(noteId);
            collectedIds.postValue(ids);
        });
    }

    private String resolveCurrentUserId() {
        com.xiaohongshu.bean.UserBean currentUser =
                LoginDataRepository.getInstance(getApplication()).getCurrentUser();
        if (currentUser == null) return "";
        UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
        return userEntity == null ? "" : userEntity.id;
    }

    /**
     * 加载指定视频的评论：先拉后台评论缓存到本地，再读本地展示（与图文详情一致）。
     */
    public void loadComments(String noteId) {
        if (noteId == null || noteId.isEmpty()) return;
        executorService.execute(() -> {
            String token = currentAccessToken();
            if (!token.isEmpty()) {
                try {
                    List<com.xiaohongshu.network.RemoteComment> remote =
                            remoteApiClient.getComments(noteId);
                    if (remote != null && !remote.isEmpty()) {
                        List<com.xiaohongshu.database.entity.CommentEntity> entities = new ArrayList<>();
                        for (com.xiaohongshu.network.RemoteComment comment : remote) {
                            if (comment.getId().isEmpty()) continue;
                            entities.add(new com.xiaohongshu.database.entity.CommentEntity(
                                    comment.getId(),
                                    noteId,
                                    comment.getUserId(),
                                    comment.getContent(),
                                    null,
                                    parseRemoteTime(comment.getCreatedAt()),
                                    comment.getUserName(),
                                    comment.getAvatarUrl()
                            ));
                        }
                        if (!entities.isEmpty()) database.commentDao().insertAll(entities);
                    }
                } catch (Exception ignored) {
                    // 后台不可达时用本地缓存
                }
            }
            List<com.xiaohongshu.database.entity.CommentEntity> entities =
                    database.commentDao().getCommentsByNoteIdSync(noteId);
            List<com.xiaohongshu.activity.graphic.CommentBean> beans = new ArrayList<>();
            if (entities != null) {
                for (com.xiaohongshu.database.entity.CommentEntity entity : entities) {
                    beans.add(convertToCommentBean(entity));
                }
            }
            comments.postValue(beans);
        });
    }

    /**
     * 在视频页内发表评论：先写本地，再同步后台，失败入待同步队列。
     */
    public void addComment(String noteId, String content) {
        if (noteId == null || noteId.isEmpty() || content == null || content.trim().isEmpty()) return;
        executorService.execute(() -> {
            String userId = resolveCurrentUserId();
            if (userId.isEmpty()) return;
            com.xiaohongshu.database.entity.CommentEntity comment =
                    new com.xiaohongshu.database.entity.CommentEntity(
                            String.valueOf(System.currentTimeMillis()),
                            noteId,
                            userId,
                            content.trim(),
                            null,
                            System.currentTimeMillis()
                    );
            database.commentDao().insert(comment);
            createMessage(noteId, userId, 2, "评论了你的笔记：" + content.trim());
            String token = currentAccessToken();
            if (!token.isEmpty()) {
                try {
                    com.xiaohongshu.network.RemoteComment remoteComment =
                            remoteApiClient.addComment(noteId, content.trim(), token);
                    if (remoteComment != null && !remoteComment.getId().isEmpty()) {
                        database.commentDao().delete(comment);
                        database.commentDao().insert(new com.xiaohongshu.database.entity.CommentEntity(
                                remoteComment.getId(),
                                noteId,
                                remoteComment.getUserId().isEmpty() ? userId : remoteComment.getUserId(),
                                remoteComment.getContent(),
                                null,
                                parseRemoteTime(remoteComment.getCreatedAt()),
                                remoteComment.getUserName(),
                                remoteComment.getAvatarUrl()
                        ));
                    }
                } catch (Exception ignored) {
                    PendingSyncStore.enqueue(getApplication(), "comment", noteId, content.trim());
                }
            }
            // 刷新评论列表与计数
            Map<String, Integer> current = value(commentCounts);
            current.put(noteId, database.commentDao().getCommentCountSync(noteId));
            commentCounts.postValue(new HashMap<>(current));
            loadComments(noteId);
        });
    }

    private com.xiaohongshu.activity.graphic.CommentBean convertToCommentBean(
            com.xiaohongshu.database.entity.CommentEntity entity) {
        UserBean userBean;
        if (entity.authorName != null && !entity.authorName.isEmpty()) {
            // 远程同步的评论：作者不在本地用户表，使用冗余的作者信息
            userBean = new UserBean(entity.userId, entity.authorName, 0, null);
            if (entity.authorAvatar != null && !entity.authorAvatar.isEmpty()) {
                userBean.setImageUri(entity.authorAvatar);
            }
        } else {
            UserEntity userEntity = database.userDao().getUserById(entity.userId);
            if (userEntity != null) {
                userBean = new UserBean(
                        userEntity.id,
                        userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                        userEntity.avatar > 0 ? userEntity.avatar : com.xiaohongshu.R.drawable.p1,
                        null);
            } else {
                userBean = new UserBean(entity.userId, "用户", com.xiaohongshu.R.drawable.p1, null);
            }
        }
        return new com.xiaohongshu.activity.graphic.CommentBean(
                entity.id,
                entity.noteId,
                userBean,
                entity.content,
                entity.parentId,
                entity.createTime,
                0
        );
    }

    private long parseRemoteTime(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return System.currentTimeMillis();
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = format.parse(createdAt.length() > 19 ? createdAt.substring(0, 19) : createdAt);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private void createMessage(String noteId, String userId, int type, String content) {
        NoteEntity note = database.noteDao().getNoteById(noteId);
        if (note != null && !note.userId.equals(userId)) {
            database.messageDao().insert(new MessageEntity(
                    String.valueOf(System.currentTimeMillis()),
                    userId,
                    note.userId,
                    type,
                    content,
                    noteId,
                    false,
                    System.currentTimeMillis()
            ));
        }
    }

    private void syncRemote(String type, String noteId) {
        String token = currentAccessToken();
        if (token.isEmpty()) return;
        try {
            switch (type) {
                case "like": remoteApiClient.likePost(noteId, token); break;
                case "unlike": remoteApiClient.unlikePost(noteId, token); break;
                case "collection": remoteApiClient.collectPost(noteId, token); break;
                case "uncollection": remoteApiClient.uncollectPost(noteId, token); break;
            }
        } catch (Exception ignored) {
            PendingSyncStore.enqueue(getApplication(), type, noteId, null);
        }
    }

    private String currentAccessToken() {
        com.xiaohongshu.bean.UserBean user = LoginDataRepository.getInstance(getApplication()).getCurrentUser();
        return user == null || user.getToken() == null ? "" : user.getToken();
    }

    private <T> T value(LiveData<T> liveData) {
        T value = liveData.getValue();
        return value == null ? null : value;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
