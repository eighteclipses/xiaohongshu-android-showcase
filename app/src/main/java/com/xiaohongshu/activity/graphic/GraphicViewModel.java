package com.xiaohongshu.activity.graphic;

import android.app.Application;
import android.content.Context;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.dao.*;
import com.xiaohongshu.database.entity.*;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.network.PendingSyncStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 笔记详情页ViewModel
 * 管理笔记详情、点赞、收藏、评论、关注等功能
 */
public class GraphicViewModel extends AndroidViewModel {
    private String noteId = "";
    private String currentUserId = "";
    // 远程笔记作者昵称（来自后台响应，用于把消息接收方映射到本地账号）
    private volatile String remoteAuthorName = "";

    private final AppDatabase database;
    private final RemoteApiClient remoteApiClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // LiveData
    private final MutableLiveData<GraphicCardBean> graphicCardBean = new MutableLiveData<>();
    private final MutableLiveData<List<CommentBean>> commentList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> likeCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> collectionCount = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isLiked = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isCollected = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isFollowed = new MutableLiveData<>(false);
    private final MutableLiveData<String> authorId = new MutableLiveData<>("");
    private final MutableLiveData<String> editInfo = new MutableLiveData<>("");
    /** 详情页底部"相关推荐"：与当前笔记同作者或内容相似的公开非草稿笔记，最多 6 条 */
    private final MutableLiveData<List<GraphicCardBean>> relatedNotes = new MutableLiveData<>(new ArrayList<>());
    /** 举报提交结果：成功 / 需登录 / 后台不可达 */
    public static final int REPORT_OK = 0, REPORT_NEED_LOGIN = 1, REPORT_OFFLINE = 2;
    private final MutableLiveData<Integer> reportResult = new MutableLiveData<>();

    public GraphicViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
        remoteApiClient = new RemoteApiClient(application.getApplicationContext());

        // 获取当前登录用户ID
        com.xiaohongshu.bean.UserBean currentUser =
            LoginDataRepository.getInstance(application).getCurrentUser();
        if (currentUser != null) {
            // 从数据库查找用户ID
            executorService.execute(() -> {
                UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                }
            });
        }
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    /**
     * 举报当前笔记：走服务端 POST /api/posts/:id/report（服务端按用户+笔记去重）。
     * 断网/未登录由调用方先行拦截；结果通过 reportResult 通知 UI。
     */
    public void reportNote(String reason) {
        executorService.execute(() -> {
            int result;
            try {
                com.xiaohongshu.bean.UserBean user =
                        LoginDataRepository.getInstance(getApplication()).getCurrentUser();
                if (user == null || user.getToken() == null || user.getToken().isEmpty()) {
                    result = REPORT_NEED_LOGIN;
                } else {
                    remoteApiClient.reportPost(noteId, reason, user.getToken());
                    result = REPORT_OK;
                }
            } catch (IOException e) {
                // 服务端对重复举报返回 submitted=false 而非错误，这里只处理网络不可达
                result = REPORT_OFFLINE;
            }
            reportResult.postValue(result);
        });
    }

    public LiveData<Integer> getReportResult() {
        return reportResult;
    }

    public LiveData<GraphicCardBean> getGraphicCardBean() {
        return graphicCardBean;
    }

    public LiveData<List<CommentBean>> getCommentList() {
        return commentList;
    }

    public LiveData<Integer> getLikeCount() {
        return likeCount;
    }

    public LiveData<Integer> getCollectionCount() {
        return collectionCount;
    }

    public LiveData<Boolean> getIsLiked() {
        return isLiked;
    }

    public LiveData<Boolean> getIsCollected() {
        return isCollected;
    }

    public LiveData<Boolean> getIsFollowed() {
        return isFollowed;
    }

    public LiveData<String> getAuthorId() {
        return authorId;
    }

    public LiveData<String> getEditInfo() {
        return editInfo;
    }

    public LiveData<List<GraphicCardBean>> getRelatedNotes() {
        return relatedNotes;
    }

    /**
     * 初始化笔记数据
     */
    public void init() {
        if (noteId == null || noteId.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            // 等待 DatabaseInitializer 完成示例数据/文字海报准备，避免冷启动首开详情页读到空数据。
            com.xiaohongshu.database.DatabaseInitializer.awaitReady(5000);
            syncRemotePostState();
            retryPendingSync();
            // 浏览上报：对齐真实小红书口径，游客浏览同样计入曝光。
            // 服务端 /api/posts/:id/view 为 optionalAuth，匿名时以 visitor_key 去重即可。
            try {
                com.xiaohongshu.bean.UserBean loggedInUser =
                        LoginDataRepository.getInstance(getApplication()).getCurrentUser();
                String token = (loggedInUser == null || loggedInUser.getToken() == null)
                        ? null : loggedInUser.getToken();
                String deviceId = android.provider.Settings.Secure.getString(
                        getApplication().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                remoteApiClient.recordView(noteId, "android-" + (deviceId == null ? "unknown" : deviceId), token);
            } catch (Exception ignored) {
                // Local browsing remains available when the server is offline.
            }
            com.xiaohongshu.network.ModerationSync.refresh(getApplication());
            // 从数据库加载笔记
            NoteEntity noteEntity = database.noteDao().getNoteById(noteId);
            if (noteEntity != null && !com.xiaohongshu.network.ModerationSync.publiclyVisible(noteEntity)
                    && (noteEntity.deleted || !java.util.Objects.equals(noteEntity.userId,currentUserId))) {
                graphicCardBean.postValue(null); return;
            }
            if (noteEntity != null) {
                // 转换为GraphicCardBean
                GraphicCardBean card = convertToGraphicCardBean(noteEntity);
                graphicCardBean.postValue(card);
                authorId.postValue(noteEntity.userId);

                // 格式化编辑信息
                String editInfoText = formatEditInfo(noteEntity.createTime, noteEntity.location);
                editInfo.postValue(editInfoText);

                // 加载点赞数
                int likes = database.likeDao().getLikeCountSync(noteId);
                likeCount.postValue(likes);

                // 加载收藏数
                int collections = database.collectionDao().getCollectionCountSync(noteId);
                collectionCount.postValue(collections);

                // 检查是否已点赞
                if (!currentUserId.isEmpty()) {
                    LikeEntity like = database.likeDao().checkLike(noteId, currentUserId);
                    isLiked.postValue(like != null);

                    // 检查是否已收藏
                    CollectionEntity collection = database.collectionDao().checkCollection(noteId, currentUserId);
                    isCollected.postValue(collection != null);

                    // 检查是否已关注作者
                    if (!noteEntity.userId.isEmpty()) {
                        FollowEntity follow = database.followDao().checkFollow(currentUserId, noteEntity.userId);
                        isFollowed.postValue(follow != null);
                    }
                }

                // 加载评论
                loadComments();

                // 加载相关推荐（同作者 + 标题/正文相似，去重取 Top 6）
                loadRelatedNotes(noteEntity);
            } else {
                // 如果数据库中没有，尝试从HomeDataRepository加载（兼容旧数据）
                loadFromRepository();
            }
        });
    }

    /**
     * 加载相关推荐：从同作者笔记与关键词命中笔记中合并去重，按时间倒序取 Top 6。
     * 真实小红书"相关推荐"主信号是内容相似度；本项目无 FTS，按标题/正文 LIKE 实现近似，
     * 与作者信号合并确保冷启动数据稀疏时也有兜底结果。
     */
    private void loadRelatedNotes(NoteEntity current) {
        if (current == null) return;
        try {
            java.util.LinkedHashMap<String, NoteEntity> merged = new java.util.LinkedHashMap<>();            // 作者信号
            if (current.userId != null && !current.userId.isEmpty()) {
                java.util.List<NoteEntity> byAuthor = database.noteDao()
                        .getRelatedByAuthorSync(current.userId, current.id, 8);
                if (byAuthor != null) {
                    for (NoteEntity n : byAuthor) {
                        if (n != null && n.id != null) merged.put(n.id, n);
                    }
                }
            }
            // 话题信号：topics 精确匹配（JSON 引号边界，避免子串误匹配），与真实小红书"同话题"推荐最接近
            if (current.topics != null) {
                for (String topic : current.topics) {
                    if (topic == null || topic.trim().isEmpty()) continue;
                    java.util.List<NoteEntity> byTopic = database.noteDao()
                            .getRelatedByTopicSync(topic.trim(), current.id, 6);
                    if (byTopic != null) {
                        for (NoteEntity n : byTopic) {
                            if (n != null && n.id != null) merged.putIfAbsent(n.id, n);
                        }
                    }
                }
            }
            // 关键词信号：标题截取较短片段提高命中率（12 字过长几乎不会与其他笔记重复）
            String keyword = extractKeyword(current.title, current.content);
            if (keyword != null) {
                java.util.List<NoteEntity> byKeyword = database.noteDao()
                        .getRelatedByKeywordSync(keyword, current.id, 8);
                if (byKeyword != null) {
                    for (NoteEntity n : byKeyword) {
                        if (n != null && n.id != null) merged.putIfAbsent(n.id, n);
                    }
                }
            }
            if (merged.isEmpty()) {
                relatedNotes.postValue(new java.util.ArrayList<>());
                return;
            }
            java.util.List<NoteEntity> sorted = new java.util.ArrayList<>(merged.values());
            java.util.Collections.sort(sorted, (a, b) -> Long.compare(b.createTime, a.createTime));
            if (sorted.size() > 6) sorted = sorted.subList(0, 6);
            java.util.List<GraphicCardBean> cards = new java.util.ArrayList<>();
            for (NoteEntity n : sorted) cards.add(convertToGraphicCardBean(n));
            relatedNotes.postValue(cards);
        } catch (Exception error) {
            // 相关推荐是体验增强：失败时回退到空列表，详情页正常展示
            relatedNotes.postValue(new java.util.ArrayList<>());
        }
    }

    /**
     * 提取用于相关推荐的关键词：取标题前 4 字（足够区分又保留命中率）；
     * 标题为空时退回到正文首行前 6 字。截取越短命中率越高，但噪声也越大，4~6 字是平衡点。
     */
    private String extractKeyword(String title, String content) {
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.length() >= 2) return trimmed.substring(0, Math.min(trimmed.length(), 4));
        }
        if (content != null) {
            String firstLine = content.trim().split("\\R", 2)[0].trim();
            if (firstLine.length() >= 2) return firstLine.substring(0, Math.min(firstLine.length(), 6));
        }
        return null;
    }

    private void syncRemotePostState() {
        String token = currentAccessToken();
        if (token.isEmpty() || noteId.isEmpty()) return;
        try {
            com.xiaohongshu.network.RemotePost remote = remoteApiClient.getPost(noteId, token);
            if (remote == null) return;
            likeCount.postValue(remote.getLikeCount());
            collectionCount.postValue(remote.getCollectionCount());
            isLiked.postValue(remote.isLiked());
            isCollected.postValue(remote.isCollected());
            authorId.postValue(remote.getAuthorId());
            remoteAuthorName = remote.getAuthorName();
            if (graphicCardBean.getValue() == null) {
                com.xiaohongshu.ui.home.bean.UserBean user = new com.xiaohongshu.ui.home.bean.UserBean(
                        remote.getAuthorId(), remote.getAuthorName(), com.xiaohongshu.R.drawable.p1, null);
                com.xiaohongshu.ui.home.bean.GraphicCardBean card = new com.xiaohongshu.ui.home.bean.GraphicCardBean(
                        remote.getId(), remote.getTitle().isEmpty() ? firstContentLine(remote.getContent()) : remote.getTitle(),
                        0, 0, user, remote.getLikeCount(), com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic);
                card.setContent(remote.getContent());
                if (!remote.getImages().isEmpty()) {
                    card.setImageUris(remote.getImages());
                } else {
                    // 远程纯文字笔记：生成/复用仿真文字海报
                    android.net.Uri poster = com.xiaohongshu.ui.publish.TextToImageConverter.createPosterForNote(
                            getApplication(), remote.getId(),
                            remote.getTitle().isEmpty() ? null : remote.getTitle(), remote.getContent());
                    if (poster != null) card.setImageUri(poster.toString());
                }
                graphicCardBean.postValue(card);
                editInfo.postValue("编辑于 刚刚");
            }
        } catch (Exception ignored) {
            // Local Room data remains the offline source of truth.
        }
    }

    /**
     * 从Repository加载数据（兼容旧数据）
     */
    /**
     * 远程笔记的相关推荐兜底：服务端 ID（seed_post_N）与本地 ID（sample_note_N）是两套空间，
     * 首页/详情以远程 ID 打开时 getNoteById 查不到本地实体。
     * 按标题把远程笔记映射回本地笔记（本地种子与远程种子一一对应），再复用本地推荐查询。
     */
    private void loadRelatedByTitle(String title) {
        if (title == null || title.trim().isEmpty()) return;
        try {
            NoteEntity local = database.noteDao().getNoteByTitleSync(title.trim());
            if (local != null) {
                loadRelatedNotes(local);
            }
        } catch (Exception error) {
            // 标题映射失败仅影响相关推荐，静默回退
        }
    }

    private void loadFromRepository() {
        executorService.execute(() -> {
            // 从HomeDataRepository查找对应的GraphicCardBean
            com.xiaohongshu.ui.home.HomeDataRepository repository =
                com.xiaohongshu.ui.home.HomeDataRepository.getInstance();

            // 尝试从首页列表查找
            repository.getGraphicCardList(false, new com.xiaohongshu.ui.home.HomeDataRepository.DataCallback<List<com.xiaohongshu.ui.home.bean.GraphicCardBean>>() {
                @Override
                public void onSuccess(List<com.xiaohongshu.ui.home.bean.GraphicCardBean> data) {
                    if (data != null) {
                        for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : data) {
                            if (card != null && card.getId() != null && card.getId().equals(noteId)) {
                                // 找到对应的卡片，使用它来显示
                                graphicCardBean.postValue(card);

                                // 设置基本信息
                                if (card.getUser() != null) {
                                    authorId.postValue(card.getUser().getId());
                                }

                                // 设置点赞数
                                likeCount.postValue(card.getLikes());

                                // 设置编辑信息（使用当前时间）
                                String editInfoText = formatEditInfo(System.currentTimeMillis(), "");
                                editInfo.postValue(editInfoText);

                                // 加载评论（可能为空）
                                loadComments();

                                // 相关推荐：远程 ID 在本地库查不到，按标题映射回本地笔记再查
                                loadRelatedByTitle(card.getTitle());
                                return;
                            }
                        }
                    }

                    // 如果首页列表中没有，尝试从同城列表查找
                    repository.getCityGraphicCardList(false, new com.xiaohongshu.ui.home.HomeDataRepository.DataCallback<List<com.xiaohongshu.ui.home.bean.GraphicCardBean>>() {
                        @Override
                        public void onSuccess(List<com.xiaohongshu.ui.home.bean.GraphicCardBean> cityData) {
                            if (cityData != null) {
                                for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : cityData) {
                                    if (card != null && card.getId() != null && card.getId().equals(noteId)) {
                                        // 找到对应的卡片，使用它来显示
                                        graphicCardBean.postValue(card);

                                        // 设置基本信息
                                        if (card.getUser() != null) {
                                            authorId.postValue(card.getUser().getId());
                                        }

                                        // 设置点赞数
                                        likeCount.postValue(card.getLikes());

                                        // 设置编辑信息（使用当前时间）
                                        String editInfoText = formatEditInfo(System.currentTimeMillis(), "");
                                        editInfo.postValue(editInfoText);

                                        // 加载评论（可能为空）
                                        loadComments();

                                        // 相关推荐：远程 ID 在本地库查不到，按标题映射回本地笔记再查
                                        loadRelatedByTitle(card.getTitle());
                                        return;
                                    }
                                }
                            }
                        }

                        @Override
                        public void onError(Exception error) {
                            // 如果都找不到，创建一个基本的卡片
                            createBasicCard();
                        }
                    });
                }

                @Override
                public void onError(Exception error) {
                    // 如果都找不到，创建一个基本的卡片
                    createBasicCard();
                }
            });
        });
    }

    /**
     * 创建一个基本的卡片（当找不到数据时）
     */
    private void createBasicCard() {
        com.xiaohongshu.ui.home.bean.UserBean defaultUser = new com.xiaohongshu.ui.home.bean.UserBean(
            "unknown",
            "未知用户",
            com.xiaohongshu.R.drawable.p1,
            null
        );

        com.xiaohongshu.ui.home.bean.GraphicCardBean basicCard = new com.xiaohongshu.ui.home.bean.GraphicCardBean(
            noteId,
            "笔记详情",
            com.xiaohongshu.R.drawable.image_1,
            0,
            defaultUser,
            0,
            com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic
        );

        graphicCardBean.postValue(basicCard);
        authorId.postValue("unknown");
        likeCount.postValue(0);
        collectionCount.postValue(0);
        editInfo.postValue("编辑于 刚刚");
        commentList.postValue(new ArrayList<>());
    }

    /**
     * 加载评论列表：先尝试拉取后台评论缓存到本地，再读本地展示。
     * 断网或未登录时跳过远程，本地数据仍是展示来源。
     */
    private void loadComments() {
        executorService.execute(() -> {
            refreshRemoteComments();
            List<CommentEntity> commentEntities = database.commentDao().getCommentsByNoteIdSync(noteId);
            if (commentEntities == null) {
                commentEntities = new ArrayList<>();
            }

            // 根评论统计回复数；标记当前用户已点赞的评论
            java.util.Set<String> liked = likedCommentIds();
            java.util.Map<String, Integer> replyCounts = new java.util.HashMap<>();
            for (CommentEntity entity : commentEntities) {
                if (entity.parentId != null && !entity.parentId.isEmpty()) {
                    Integer count = replyCounts.get(entity.parentId);
                    replyCounts.put(entity.parentId, count == null ? 1 : count + 1);
                }
            }

            List<CommentBean> comments = new ArrayList<>();
            for (CommentEntity entity : commentEntities) {
                CommentBean bean = convertToCommentBean(entity);
                bean.setLikedByMe(liked.contains(entity.id));
                Integer replies = replyCounts.get(entity.id);
                bean.setReplyCount(replies == null ? 0 : replies);
                comments.add(bean);
            }
            commentList.postValue(comments);
        });
    }

    /**
     * 拉取后台评论并以远程UUID为主键幂等写入本地缓存。
     * 服务端没有 parentId/likes 字段：已存在的本地记录保留其回复关系与点赞数，避免被 REPLACE 清掉。
     */
    private void refreshRemoteComments() {
        String token = currentAccessToken();
        if (token.isEmpty() || noteId.isEmpty()) return;
        try {
            List<com.xiaohongshu.network.RemoteComment> remote = remoteApiClient.getComments(noteId);
            if (remote == null || remote.isEmpty()) return;
            List<CommentEntity> entities = new ArrayList<>();
            for (com.xiaohongshu.network.RemoteComment comment : remote) {
                if (comment.getId().isEmpty()) continue;
                CommentEntity existing = database.commentDao().getCommentById(comment.getId());
                entities.add(new CommentEntity(
                    comment.getId(),
                    noteId,
                    comment.getUserId(),
                    comment.getContent(),
                    existing != null ? existing.parentId : null,
                    existing != null && existing.createTime > 0 ? existing.createTime : parseRemoteTime(comment.getCreatedAt()),
                    existing != null ? existing.likes : 0,
                    comment.getUserName(),
                    comment.getAvatarUrl()
                ));
            }
            if (!entities.isEmpty()) {
                database.commentDao().insertAll(entities);
            }
        } catch (Exception ignored) {
            // 后台不可达时静默回退本地缓存
        }
    }

    /** 解析后台 ISO8601 时间为毫秒，失败时退回当前时间。 */
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

    /**
     * 点赞/取消点赞
     */
    public void toggleLike() {
        if (currentUserId.isEmpty() || noteId.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            LikeEntity existingLike = database.likeDao().checkLike(noteId, currentUserId);
            if (existingLike != null) {
                // 取消点赞
                database.likeDao().deleteLike(noteId, currentUserId);
                isLiked.postValue(false);

                // 更新点赞数
                int count = database.likeDao().getLikeCountSync(noteId);
                // 删除完成后 count 已经是最新值，不能再次减一。
                likeCount.postValue(count);
                syncRemoteLike(false);
            } else {
                // 点赞
                LikeEntity like = new LikeEntity(noteId, currentUserId, System.currentTimeMillis());
                database.likeDao().insert(like);
                isLiked.postValue(true);

                // 更新点赞数
                int count = database.likeDao().getLikeCountSync(noteId);
                // 插入完成后 count 已经是最新值，不能再次加一。
                likeCount.postValue(count);
                syncRemoteLike(true);

                // 生成点赞消息
                createLikeMessage();
            }
        });
    }

    /**
     * 解析消息接收方（笔记作者）：优先本地笔记表；远程笔记把服务器作者映射到
     * 本地同名账号（学习版默认昵称即用户名），保证消息写入与查询使用同一套本地 userId。
     * 作者为本人或无法确定时返回 null。
     */
    private String resolveMessageTarget() {
        NoteEntity note = database.noteDao().getNoteById(noteId);
        String targetUserId = note != null ? note.userId : null;
        if (targetUserId == null || targetUserId.isEmpty()) {
            String remote = authorId.getValue();
            if (remote != null && !remote.isEmpty()) {
                UserEntity localById = database.userDao().getUserById(remote);
                if (localById != null) {
                    targetUserId = localById.id;
                } else {
                    // 远程作者不在本地：按用户名匹配本地账号
                    UserEntity localByName = remoteAuthorName.isEmpty() ? null
                            : database.userDao().getUserByUsername(remoteAuthorName);
                    targetUserId = localByName != null ? localByName.id : remote;
                }
            }
        }
        if (targetUserId == null || targetUserId.equals(currentUserId)) return null;
        return targetUserId;
    }

    /**
     * 创建点赞消息
     */
    private void createLikeMessage() {
        String targetUserId = resolveMessageTarget();
        if (targetUserId != null) {
            MessageEntity message = new MessageEntity(
                String.valueOf(System.currentTimeMillis()),
                currentUserId,
                targetUserId,
                0, // 点赞消息
                "点赞了你的笔记",
                noteId,
                false,
                System.currentTimeMillis()
            );
            database.messageDao().insert(message);
        }
    }

    /**
     * 收藏/取消收藏
     */
    public void toggleCollection() {
        if (currentUserId.isEmpty() || noteId.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            CollectionEntity existingCollection = database.collectionDao().checkCollection(noteId, currentUserId);
            if (existingCollection != null) {
                // 取消收藏
                database.collectionDao().deleteCollection(noteId, currentUserId);
                isCollected.postValue(false);

                // 更新收藏数
                int count = database.collectionDao().getCollectionCountSync(noteId);
                // 删除完成后 count 已经是最新值，不能再次减一。
                collectionCount.postValue(count);
                syncRemoteCollection(false);
            } else {
                // 收藏
                CollectionEntity collection = new CollectionEntity(noteId, currentUserId, System.currentTimeMillis());
                database.collectionDao().insert(collection);
                isCollected.postValue(true);

                // 更新收藏数
                int count = database.collectionDao().getCollectionCountSync(noteId);
                // 插入完成后 count 已经是最新值，不能再次加一。
                collectionCount.postValue(count);
                syncRemoteCollection(true);

                // 生成收藏消息
                createCollectionMessage();
            }
        });
    }

    /**
     * 创建收藏消息
     */
    private void createCollectionMessage() {
        String targetUserId = resolveMessageTarget();
        if (targetUserId != null) {
            MessageEntity message = new MessageEntity(
                String.valueOf(System.currentTimeMillis()),
                currentUserId,
                targetUserId,
                1, // 收藏消息
                "收藏了你的笔记",
                noteId,
                false,
                System.currentTimeMillis()
            );
            database.messageDao().insert(message);
        }
    }

    private String currentAccessToken() {
        com.xiaohongshu.bean.UserBean user = LoginDataRepository.getInstance(getApplication()).getCurrentUser();
        return user == null || user.getToken() == null ? "" : user.getToken();
    }

    private void syncRemoteLike(boolean liked) {
        String token = currentAccessToken();
        if (token.isEmpty()) return;
        try {
            com.xiaohongshu.network.RemotePost remote = liked
                    ? remoteApiClient.likePost(noteId, token)
                    : remoteApiClient.unlikePost(noteId, token);
            if (remote != null) likeCount.postValue(remote.getLikeCount());
        } catch (Exception ignored) {
            PendingSyncStore.enqueue(getApplication(), liked ? "like" : "unlike", noteId, null);
        }
    }

    private void syncRemoteCollection(boolean collected) {
        String token = currentAccessToken();
        if (token.isEmpty()) return;
        try {
            com.xiaohongshu.network.RemotePost remote = collected
                    ? remoteApiClient.collectPost(noteId, token)
                    : remoteApiClient.uncollectPost(noteId, token);
            if (remote != null) collectionCount.postValue(remote.getCollectionCount());
        } catch (Exception ignored) {
            PendingSyncStore.enqueue(getApplication(), collected ? "collection" : "uncollection", noteId, null);
        }
    }

    private void retryPendingSync() {
        String token = currentAccessToken();
        if (token.isEmpty()) return;
        List<PendingSyncStore.Action> failed = new ArrayList<>();
        for (PendingSyncStore.Action action : PendingSyncStore.drain(getApplication())) {
            try {
                if ("like".equals(action.type)) remoteApiClient.likePost(action.id, token);
                else if ("unlike".equals(action.type)) remoteApiClient.unlikePost(action.id, token);
                else if ("collection".equals(action.type)) remoteApiClient.collectPost(action.id, token);
                else if ("uncollection".equals(action.type)) remoteApiClient.uncollectPost(action.id, token);
                else if ("follow".equals(action.type)) remoteApiClient.followUser(action.id, token);
                else if ("unfollow".equals(action.type)) remoteApiClient.unfollowUser(action.id, token);
                else if ("comment".equals(action.type)) remoteApiClient.addComment(action.id, action.value, token);
                else if ("publish".equals(action.type)) retryPendingPublish(action.id, token, false);
                else if ("draft".equals(action.type)) retryPendingPublish(action.id, token, true);
            } catch (Exception error) { failed.add(action); }
        }
        PendingSyncStore.restore(getApplication(), failed);
    }

    /**
     * 重试此前失败的远程发布/存草稿：从 Room 取最新内容，先完成图片上传，
     * 全部成功才提交；仍失败则抛出交由调用方重新入队。
     */
    private void retryPendingPublish(String noteId, String token, boolean asDraft) throws IOException {
        NoteEntity entity = database.noteDao().getNoteById(noteId);
        if (entity == null || !"pending".equals(entity.syncState)) return;
        com.xiaohongshu.ui.publish.model.NoteModel note = new com.xiaohongshu.ui.publish.model.NoteModel();
        note.setId(entity.id);
        note.setContentVersion(entity.contentVersion);
        note.setModerationStatus(entity.moderationStatus);
        note.setTitle(entity.title);
        note.setContent(entity.content);
        note.setImageUris(entity.imageUris != null ? new ArrayList<>(entity.imageUris) : new ArrayList<>());
        note.setTopics(entity.topics != null ? new ArrayList<>(entity.topics) : new ArrayList<>());
        note.setLocation(entity.location);
        note.setPublic(entity.isPublic);
        note.setDraft(asDraft);
        note.setCreateTime(entity.createTime);
        note.setUpdateTime(System.currentTimeMillis());
        boolean replaced = uploadPendingImages(note, token);
        if (replaced) {
            entity.imageUris = new ArrayList<>(note.getImageUris());
            entity.updateTime = System.currentTimeMillis();
            database.noteDao().update(entity);
        }
        if (asDraft) remoteApiClient.saveDraft(note, token);
        else remoteApiClient.publishNote(note, token);
    }

    /** 仅当还有本地 URI（file:///content:///android.resource://）时尝试上传；纯 http 列表原样返回。 */
    private boolean uploadPendingImages(com.xiaohongshu.ui.publish.model.NoteModel note, String token) throws IOException {
        boolean hasLocalImage = false;
        for (String uri : note.getImageUris()) {
            if (uri != null && !uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("/")) {
                hasLocalImage = true;
                break;
            }
        }
        if (!hasLocalImage) return false;
        List<String> uploaded = remoteApiClient.uploadImages(getApplication(), note.getImageUris(), token);
        if (!uploaded.isEmpty() && !uploaded.equals(note.getImageUris())) {
            note.setImageUris(uploaded);
            return true;
        }
        return false;
    }

    /**
     * 添加评论（普通评论）
     */
    public void addComment(String content) {
        addComment(content, null);
    }

    /**
     * 添加评论或回复：replyTo 非空时记录 parentId，并在内容前拼上 @被回复人 前缀
     * （内容前缀随评论正文存储与同步，展示层无需区分处理）。
     */
    public void addComment(String content, CommentBean replyTo) {
        if (currentUserId.isEmpty() || noteId.isEmpty() || content == null || content.trim().isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            String parentId = null;
            String storedContent = content.trim();
            if (replyTo != null && replyTo.getId() != null && !replyTo.getId().isEmpty()) {
                parentId = replyTo.getId();
                String replyName = replyTo.getUser() == null || replyTo.getUser().getName() == null
                        ? "用户" : replyTo.getUser().getName();
                storedContent = "回复 @" + replyName + "：" + storedContent;
            }
            String finalContent = storedContent;
            String finalParentId = parentId;
            String commentId = String.valueOf(System.currentTimeMillis());
            CommentEntity comment = new CommentEntity(
                commentId,
                noteId,
                currentUserId,
                finalContent,
                finalParentId, // 回复关系；普通评论为 null
                System.currentTimeMillis()
            );
            database.commentDao().insert(comment);

            // 重新加载评论列表
            loadComments();

            // 回复通知被回复者（而非笔记作者）；普通评论通知作者
            if (replyTo != null) {
                createReplyMessage(finalContent, replyTo);
            } else {
                createCommentMessage(finalContent);
            }
            // 解析正文中的 @提及，为被提及用户生成「@我」消息（type 4）
            createMentionMessages(finalContent);
            String token = currentAccessToken();
            if (!token.isEmpty()) {
                try {
                    com.xiaohongshu.network.RemoteComment remoteComment =
                            remoteApiClient.addComment(noteId, finalContent, token);
                    if (remoteComment != null && !remoteComment.getId().isEmpty()) {
                        // 用远程记录（UUID主键）替换本地毫秒ID占位记录，避免刷新后同一评论显示两次
                        database.commentDao().delete(comment);
                        CommentEntity synced = new CommentEntity(
                            remoteComment.getId(),
                            noteId,
                            remoteComment.getUserId().isEmpty() ? currentUserId : remoteComment.getUserId(),
                            remoteComment.getContent(),
                            finalParentId,
                            parseRemoteTime(remoteComment.getCreatedAt()),
                            remoteComment.getUserName(),
                            remoteComment.getAvatarUrl()
                        );
                        database.commentDao().insert(synced);
                        loadComments();
                    }
                } catch (Exception ignored) {
                    PendingSyncStore.enqueue(getApplication(), "comment", noteId, finalContent);
                }
            }
        });
    }

    /**
     * 创建评论消息
     */
    private void createCommentMessage(String content) {
        String targetUserId = resolveMessageTarget();
        if (targetUserId != null) {
            MessageEntity message = new MessageEntity(
                String.valueOf(System.currentTimeMillis()),
                currentUserId,
                targetUserId,
                2, // 评论消息
                "评论了你的笔记：" + content,
                noteId, // 关联笔记，点击消息可跳转详情
                false,
                System.currentTimeMillis()
            );
            database.messageDao().insert(message);
        }
    }

    /**
     * 创建回复消息：通知被回复评论的作者（映射到本地账号），而非笔记作者。
     */
    private void createReplyMessage(String content, CommentBean replyTo) {
        if (replyTo == null || replyTo.getId() == null) return;
        String targetUserId = null;
        if (replyTo.getUser() != null && replyTo.getUser().getId() != null) {
            String rawId = replyTo.getUser().getId();
            UserEntity localById = database.userDao().getUserById(rawId);
            if (localById != null) {
                targetUserId = localById.id;
            } else {
                UserEntity localByName = database.userDao().getUserByUsername(rawId);
                if (localByName != null) {
                    targetUserId = localByName.id;
                } else if (replyTo.getUser().getName() != null) {
                    UserEntity byName = database.userDao().getUserByUsername(replyTo.getUser().getName());
                    targetUserId = byName != null ? byName.id : rawId;
                }
            }
        }
        if (targetUserId == null || targetUserId.equals(currentUserId)) return;
        MessageEntity message = new MessageEntity(
            String.valueOf(System.currentTimeMillis()) + "r",
            currentUserId,
            targetUserId,
            2, // 评论消息（回复）
            "回复了你的评论：" + content,
            noteId,
            false,
            System.currentTimeMillis()
        );
        database.messageDao().insert(message);
    }

    /**
     * 解析评论正文中的 @昵称，为每个被提及用户生成 type 4「@我」消息。
     * 昵称按本地用户的 nickname / username 精确匹配，@自己 不产生消息。
     */
    private void createMentionMessages(String content) {
        if (content == null || !content.contains("@")) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("@([^@\\s：:，,。]+)").matcher(content);
        java.util.Set<String> mentioned = new java.util.HashSet<>();
        while (matcher.find()) {
            mentioned.add(matcher.group(1));
        }
        long now = System.currentTimeMillis();
        int seq = 0;
        for (String name : mentioned) {
            UserEntity target = database.userDao().getUserByUsername(name);
            if (target == null) {
                target = database.userDao().getUserByNickname(name);
            }
            if (target == null || target.id.equals(currentUserId)) continue;
            MessageEntity message = new MessageEntity(
                String.valueOf(now) + "m" + (seq++),
                currentUserId,
                target.id,
                4, // @我消息
                "在评论中提到了你：" + content,
                noteId,
                false,
                now
            );
            database.messageDao().insert(message);
        }
    }

    /**
     * 关注/取消关注
     */
    public void toggleFollow(String authorId) {
        if (currentUserId.isEmpty() || authorId == null || authorId.isEmpty() || currentUserId.equals(authorId)) {
            return;
        }

        executorService.execute(() -> {
            FollowEntity existingFollow = database.followDao().checkFollow(currentUserId, authorId);
            if (existingFollow != null) {
                // 取消关注
                database.followDao().unfollow(currentUserId, authorId);
                isFollowed.postValue(false);
                String token = currentAccessToken();
                if (!token.isEmpty()) {
                    try { remoteApiClient.unfollowUser(authorId, token); } catch (Exception ignored) {
                        PendingSyncStore.enqueue(getApplication(), "unfollow", authorId, null);
                    }
                }
            } else {
                // 关注
                FollowEntity follow = new FollowEntity(currentUserId, authorId, System.currentTimeMillis());
                database.followDao().insert(follow);
                isFollowed.postValue(true);
                String token = currentAccessToken();
                if (!token.isEmpty()) {
                    try { remoteApiClient.followUser(authorId, token); } catch (Exception ignored) {
                        PendingSyncStore.enqueue(getApplication(), "follow", authorId, null);
                    }
                }

                // 生成关注消息
                createFollowMessage(authorId);
            }
        });
    }

    /**
     * 创建关注消息
     */
    private void createFollowMessage(String authorId) {
        MessageEntity message = new MessageEntity(
            String.valueOf(System.currentTimeMillis()),
            currentUserId,
            authorId,
            3, // 关注消息
            "关注了你",
            null,
            false,
            System.currentTimeMillis()
        );
        database.messageDao().insert(message);
    }

    /**
     * 将NoteEntity转换为GraphicCardBean
     */
    private GraphicCardBean convertToGraphicCardBean(NoteEntity entity) {
        // 加载用户信息
        UserEntity userEntity = database.userDao().getUserById(entity.userId);
        UserBean userBean = null;
        if (userEntity != null) {
            // 如果userEntity.avatar是p1-p11中的一个，就使用它；否则根据用户ID生成
            int avatarRes;
            if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                avatarRes = userEntity.avatar;
            } else {
                String userId = userEntity.id;
                if (userId != null && !userId.isEmpty()) {
                    // 根据用户ID生成固定的头像索引，确保同一用户总是显示相同的头像
                    int avatarIndex = userId.hashCode();
                    avatarRes = getAvatarResource(avatarIndex);
                } else {
                    avatarRes = com.xiaohongshu.R.drawable.p1;
                }
            }
            userBean = new UserBean(
                userEntity.id,
                userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                avatarRes,
                null
            );
            if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()) userBean.setImageUri(userEntity.avatarUri);
        }

        // 获取图片资源：优先使用保存的imageUris，否则根据noteId生成
        int imageRes = getNoteImageResource(entity);

        String displayTitle = entity.title != null && !entity.title.trim().isEmpty()
            ? entity.title
            : firstContentLine(entity.content);
        GraphicCardBean card = new GraphicCardBean(
            entity.id,
            displayTitle,
            imageRes,
            0,
            userBean,
            0, // likes会在后续更新
            com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic
        );
        card.setContent(entity.content);
        if (entity.imageUris != null && !entity.imageUris.isEmpty()) {
            card.setImageUris(entity.imageUris);
        }

        return card;
    }

    private String firstContentLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String firstLine = content.trim().split("\\R", 2)[0].trim();
        return firstLine.length() > 36 ? firstLine.substring(0, 36) + "…" : firstLine;
    }

    /**
     * 获取笔记图片资源：优先使用保存的imageUris，否则根据noteId生成
     */
    private int getNoteImageResource(NoteEntity entity) {
        // 优先使用保存的imageUris中的第一张图片
        if (entity.imageUris != null && !entity.imageUris.isEmpty()) {
            String firstImageUri = entity.imageUris.get(0);
            int resourceId = extractResourceIdFromUri(firstImageUri);
            if (resourceId != 0) {
                return resourceId;
            }
        }

        // 纯文字笔记不应被自动补成随机图片。
        return 0;
    }

    /**
     * 从URI中提取资源ID
     * URI格式：android.resource://包名/资源ID
     */
    private int extractResourceIdFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return 0;
        }

        try {
            // 解析android.resource://格式的URI
            if (uri.startsWith("android.resource://")) {
                // 使用Uri类解析
                android.net.Uri parsedUri = android.net.Uri.parse(uri);
                String path = parsedUri.getPath();
                if (path != null && !path.isEmpty()) {
                    // 路径格式：/资源ID，需要去掉开头的"/"
                    String resourceIdStr = path.startsWith("/") ? path.substring(1) : path;
                    return Integer.parseInt(resourceIdStr);
                }

                // 如果Uri解析失败，尝试直接分割字符串
                String[] parts = uri.split("/");
                if (parts.length > 0) {
                    String resourceIdStr = parts[parts.length - 1];
                    // 确保是纯数字
                    if (resourceIdStr.matches("\\d+")) {
                        return Integer.parseInt(resourceIdStr);
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回0
            android.util.Log.e("GraphicViewModel", "Failed to extract resource ID from URI: " + uri, e);
        }

        return 0;
    }

    /**
     * 将CommentEntity转换为CommentBean
     */
    private CommentBean convertToCommentBean(CommentEntity entity) {
        UserBean userBean = null;
        if (entity.authorName != null && !entity.authorName.isEmpty()) {
            // 远程同步的评论：作者不在本地用户表，使用冗余的作者信息
            userBean = new UserBean(entity.userId, entity.authorName, 0, null);
            if (entity.authorAvatar != null && !entity.authorAvatar.isEmpty()) {
                userBean.setImageUri(entity.authorAvatar);
            }
        } else {
            // 加载用户信息
            UserEntity userEntity = database.userDao().getUserById(entity.userId);
            if (userEntity != null) {
                // 优先使用UserEntity的avatar字段，如果无效则使用后备方案
                int avatarRes;
                if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                    // 使用用户设置的头像（最新的头像）
                    avatarRes = userEntity.avatar;
                } else if (userEntity.id != null) {
                    // 如果头像无效，根据用户ID生成
                    int avatarIndex = userEntity.id.hashCode();
                    avatarRes = getAvatarResource(avatarIndex);
                } else {
                    avatarRes = com.xiaohongshu.R.drawable.p1;
                }
                userBean = new UserBean(
                    userEntity.id,
                    userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                    avatarRes,
                    null
                );
            }
        }

        return new CommentBean(
            entity.id,
            entity.noteId,
            userBean,
            entity.content,
            entity.parentId,
            entity.createTime,
            entity.likes
        );
    }

    private static final String PREF_COMMENT_LIKES = "comment_likes";
    private static final String KEY_LIKED_COMMENT_IDS = "liked_comment_ids";

    private java.util.Set<String> likedCommentIds() {
        android.content.SharedPreferences sp = getApplication()
                .getSharedPreferences(PREF_COMMENT_LIKES, Context.MODE_PRIVATE);
        return new java.util.HashSet<>(sp.getStringSet(KEY_LIKED_COMMENT_IDS, new java.util.HashSet<>()));
    }

    /**
     * 评论点赞/取消点赞：计数写入 Room，点赞集合持久化到 SharedPreferences，
     * 服务端暂无评论点赞接口，仅本地生效。
     */
    public void toggleCommentLike(CommentBean comment) {
        if (comment == null || comment.getId() == null || comment.getId().isEmpty()) return;
        executorService.execute(() -> {
            java.util.Set<String> liked = likedCommentIds();
            boolean nowLiked;
            CommentEntity entity = database.commentDao().getCommentById(comment.getId());
            if (entity == null) return;
            if (liked.contains(comment.getId())) {
                liked.remove(comment.getId());
                entity.likes = Math.max(0, entity.likes - 1);
                nowLiked = false;
            } else {
                liked.add(comment.getId());
                entity.likes = entity.likes + 1;
                nowLiked = true;
            }
            database.commentDao().update(entity);
            android.content.SharedPreferences sp = getApplication()
                    .getSharedPreferences(PREF_COMMENT_LIKES, Context.MODE_PRIVATE);
            sp.edit().putStringSet(KEY_LIKED_COMMENT_IDS, liked).apply();
            loadComments();
        });
    }

    /**
     * 格式化编辑信息
     */
    private String formatEditInfo(long createTime, String location) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - createTime;

        String timeStr;
        if (diff < 60000) { // 1分钟内
            timeStr = "刚刚";
        } else if (diff < 3600000) { // 1小时内
            timeStr = (diff / 60000) + "分钟前";
        } else if (diff < 86400000) { // 1天内
            timeStr = (diff / 3600000) + "小时前";
        } else if (diff < 86400000 * 2) { // 2天内
            timeStr = "昨天";
        } else if (diff < 86400000 * 7) { // 7天内
            timeStr = (diff / 86400000) + "天前";
        } else {
            // 格式化日期
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault());
            timeStr = sdf.format(new java.util.Date(createTime));
        }

        String locationStr = location != null && !location.isEmpty() ? location : "";
        if (!locationStr.isEmpty()) {
            return "编辑于 " + timeStr + " " + locationStr;
        } else {
            return "编辑于 " + timeStr;
        }
    }

    /**
     * 根据索引获取头像资源
     * @param index 索引（可以是任意整数）
     * @return 对应的头像资源ID
     */
    private int getAvatarResource(int index) {
        int[] avatars = {
            com.xiaohongshu.R.drawable.p1, com.xiaohongshu.R.drawable.p2,
            com.xiaohongshu.R.drawable.p3, com.xiaohongshu.R.drawable.p4,
            com.xiaohongshu.R.drawable.p5, com.xiaohongshu.R.drawable.p6,
            com.xiaohongshu.R.drawable.p7, com.xiaohongshu.R.drawable.p8,
com.xiaohongshu.R.drawable.p9, com.xiaohongshu.R.drawable.p10,
            com.xiaohongshu.R.drawable.p11,
            com.xiaohongshu.R.drawable.p12, com.xiaohongshu.R.drawable.p13, com.xiaohongshu.R.drawable.p14,
            com.xiaohongshu.R.drawable.p15, com.xiaohongshu.R.drawable.p16
        };
        return avatars[Math.abs(index) % avatars.length];
    }

    /**
     * 检查资源ID是否是p1-p11头像之一
     * @param resourceId 资源ID
     * @return 如果是p1-p11之一返回true，否则返回false
     */
    private boolean isP1ToP11Avatar(int resourceId) {
        int[] avatars = {
            com.xiaohongshu.R.drawable.p1, com.xiaohongshu.R.drawable.p2,
            com.xiaohongshu.R.drawable.p3, com.xiaohongshu.R.drawable.p4,
            com.xiaohongshu.R.drawable.p5, com.xiaohongshu.R.drawable.p6,
            com.xiaohongshu.R.drawable.p7, com.xiaohongshu.R.drawable.p8,
com.xiaohongshu.R.drawable.p9, com.xiaohongshu.R.drawable.p10,
            com.xiaohongshu.R.drawable.p11,
            com.xiaohongshu.R.drawable.p12, com.xiaohongshu.R.drawable.p13, com.xiaohongshu.R.drawable.p14,
            com.xiaohongshu.R.drawable.p15, com.xiaohongshu.R.drawable.p16
        };
        for (int avatar : avatars) {
            if (avatar == resourceId) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
