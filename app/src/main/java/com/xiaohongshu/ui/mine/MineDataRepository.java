package com.xiaohongshu.ui.mine;

import android.content.Context;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.*;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 我的页面数据仓库
 * 从数据库加载用户发布的笔记、收藏的笔记、点赞的笔记等
 */
public class MineDataRepository {
    private static MineDataRepository instance;
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final com.xiaohongshu.network.RemoteApiClient remoteApiClient;
    // 多线程读写字段，volatile 保证可见性
    private volatile String currentUserId = "";

    private MineDataRepository(Context context) {
        this.database = AppApplication.getDatabase();
        this.executorService = Executors.newSingleThreadExecutor();
        this.remoteApiClient = new com.xiaohongshu.network.RemoteApiClient(context.getApplicationContext());
        // currentUserId 不在此处异步初始化：getCurrentUserIdSync 会在首次使用时同步解析，
        // 异步写入存在读到过期空串的竞态
    }
    
    public static synchronized MineDataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MineDataRepository(context);
        }
        return instance;
    }
    
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception error);
    }
    
    /**
     * 刷新当前用户ID
     * 如果currentUserId为空或用户切换，重新从数据库获取
     * 总是从当前登录用户获取userId，确保数据隔离
     */
    private void refreshCurrentUserId(Context context) {
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            // 总是从当前登录用户的username获取UserEntity，确保获取正确的userId
            UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                // 验证获取的userId对应的username与当前登录用户一致（双重验证）
                if (currentUser.getUsername().equals(userEntity.username)) {
                    currentUserId = userEntity.id;
                } else {
                    // 数据不一致，清空userId
                    currentUserId = "";
                    android.util.Log.w("MineDataRepository", "UserEntity username mismatch: expected " + 
                        currentUser.getUsername() + ", got " + userEntity.username);
                }
            } else {
                // 如果UserEntity不存在，清空userId（由getCurrentUserIdSync处理创建逻辑）
                currentUserId = "";
            }
        } else {
            // 没有登录用户，清空userId
            currentUserId = "";
        }
    }
    
    /**
     * 获取当前用户ID（同步方法，确保能获取到正确的用户ID）
     */
    private String getCurrentUserIdSync(Context context) {
        // 获取当前登录用户
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(context).getCurrentUser();
        
        if (currentUser == null) {
            currentUserId = "";
            return "";
        }
        
        // 如果currentUserId不为空，验证它是否属于当前登录用户
        if (currentUserId != null && !currentUserId.isEmpty()) {
            UserEntity cachedUserEntity = database.userDao().getUserById(currentUserId);
            if (cachedUserEntity != null) {
                // 验证缓存的userId对应的username是否与当前登录用户的username一致
                if (currentUser.getUsername().equals(cachedUserEntity.username)) {
                    // 验证通过，返回缓存的userId
                    return currentUserId;
                } else {
                    // 用户已切换，清空缓存并重新获取
                    currentUserId = "";
                }
            } else {
                // 缓存的userId不存在，清空并重新获取
                currentUserId = "";
            }
        }
        
        // 重新获取当前用户的userId
        refreshCurrentUserId(context);
        return currentUserId != null ? currentUserId : "";
    }
    
    /**
     * 获取用户的笔记列表（根据类型：0-全部，1-公开，2-私密，3-草稿）
     */
    public void getNotesByType(int type, DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                // 刷新用户ID，确保使用正确的用户ID
                Context context = AppApplication.getAppContext();
                String userId = getCurrentUserIdSync(context);
                
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<NoteEntity> notes = new ArrayList<>();
                
                if (userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                com.xiaohongshu.network.ModerationSync.refresh(context);
                switch (type) {
                    case 1: // 公开
                        notes = database.noteDao().getOwnSubmittedNotesSync(userId);
                        break;
                    case 2: // 私密
                        notes = database.noteDao().getPrivateNotesByUserIdSync(userId);
                        break;
                    case 3: // 草稿
                        notes = database.noteDao().getDraftsByUserIdSync(userId);
                        break;
                    default: // 全部（公开+私密）
                        List<NoteEntity> publicNotes = database.noteDao().getOwnSubmittedNotesSync(userId);
                        List<NoteEntity> privateNotes = database.noteDao().getPrivateNotesByUserIdSync(userId);
                        notes = new ArrayList<>();
                        if (publicNotes != null) notes.addAll(publicNotes);
                        if (privateNotes != null) notes.addAll(privateNotes);
                        break;
                }
                
                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);

                // 笔记 tab（全部/公开）合并服务端笔记：网页端发布的笔记只存在于服务器，
                // 服务器是同步目标，远程数据在前、本地独有（待同步/离线发布）的在后
                // Own server records (including pending ones) were synchronized above.
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 合并服务端当前用户的公开笔记（按 username 查询，与本地按 id 去重）。
     * 断网或未登录时静默跳过，仅展示本地数据。
     */
    private void mergeRemoteNotes(List<GraphicCardBean> cards) {
        List<GraphicCardBean> remoteCards = new ArrayList<>();
        try {
            com.xiaohongshu.bean.UserBean me =
                    LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
            if (me == null || me.getToken() == null || me.getToken().isEmpty()) return;
            List<com.xiaohongshu.network.RemotePost> remotePosts =
                    remoteApiClient.getUserPosts(me.getUsername(), 1, 50, me.getToken());
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (GraphicCardBean card : cards) seen.add(card.getId());
            for (com.xiaohongshu.network.RemotePost post : remotePosts) {
                if (seen.contains(post.getId())) continue;
                remoteCards.add(convertRemoteToCard(post));
                seen.add(post.getId());
            }
        } catch (Exception ignored) {
            // 后台不可达：仅展示本地笔记
        }
        if (!remoteCards.isEmpty()) {
            cards.addAll(0, remoteCards);
        }
    }

    /** 服务端笔记转卡片：支持视频类型与纯文字海报兜底 */
    private GraphicCardBean convertRemoteToCard(com.xiaohongshu.network.RemotePost post) {
        boolean isVideo = "video".equals(post.getMediaType()) && !post.getVideoUrl().isEmpty();
        GraphicCardBean card = new GraphicCardBean(
                post.getId(),
                post.getTitle().isEmpty() ? firstLine(post.getContent()) : post.getTitle(),
                0, 0,
                new UserBean(post.getAuthorId(), post.getAuthorName(),
                        com.xiaohongshu.R.drawable.p1, null),
                post.getLikeCount(),
                isVideo ? com.xiaohongshu.ui.home.bean.GraphicCardType.Video
                        : com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic);
        card.setContent(post.getContent());
        if (!post.getImages().isEmpty()) {
            card.setImageUris(post.getImages());
        } else if (!isVideo) {
            android.net.Uri poster = com.xiaohongshu.ui.publish.TextToImageConverter.createPosterForNote(
                    AppApplication.getAppContext(), post.getId(),
                    post.getTitle().isEmpty() ? null : post.getTitle(), post.getContent());
            if (poster != null) card.setImageUri(poster.toString());
        }
        if (isVideo) {
            card.setVideoUri(resolveMediaUrl(post.getVideoUrl()));
        }
        return card;
    }

    private String firstLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String line = content.trim().split("\\R", 2)[0].trim();
        return line.length() > 36 ? line.substring(0, 36) + "…" : line;
    }

    /** 后台相对媒体路径补全为完整地址（与 ImageLoader 补全规则一致） */
    private String resolveMediaUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("/") && !url.startsWith("//")) {
            try {
                String base = AppApplication.getAppContext().getString(com.xiaohongshu.R.string.backend_base_url);
                String host = base.endsWith("/api") ? base.substring(0, base.length() - "/api".length()) : base;
                return host + url;
            } catch (Exception ignored) {
            }
        }
        return url;
    }
    /**
     * 获取用户收藏的笔记
     */
    public void getCollectedNotes(DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                Context context = AppApplication.getAppContext();
                String userId = getCurrentUserIdSync(context);
                
                if (userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                List<String> noteIds = database.collectionDao().getCollectedNoteIdsSync(userId);
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<NoteEntity> notes = new ArrayList<>();
                for (String noteId : noteIds) {
                    NoteEntity note = database.noteDao().getNoteById(noteId);
                    if (com.xiaohongshu.network.ModerationSync.publiclyVisible(note)) {
                        notes.add(note);
                    }
                }
                
                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 获取用户点赞的笔记
     */
    public void getLikedNotes(DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                Context context = AppApplication.getAppContext();
                String userId = getCurrentUserIdSync(context);
                
                if (userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                List<String> noteIds = database.likeDao().getLikedNoteIdsSync(userId);
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<NoteEntity> notes = new ArrayList<>();
                for (String noteId : noteIds) {
                    NoteEntity note = database.noteDao().getNoteById(noteId);
                    if (com.xiaohongshu.network.ModerationSync.publiclyVisible(note)) {
                        notes.add(note);
                    }
                }
                
                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 获取指定用户的笔记列表（公开笔记）
     */
    public void getNotesByUserId(String userId, DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                if (userId == null || userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                List<NoteEntity> notes = database.noteDao().getPublicNotesByUserIdSync(userId);
                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 获取指定用户收藏的笔记
     */
    public void getCollectedNotesByUserId(String userId, DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                if (userId == null || userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                List<String> noteIds = database.collectionDao().getCollectedNoteIdsSync(userId);
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<NoteEntity> notes = new ArrayList<>();
                for (String noteId : noteIds) {
                    NoteEntity note = database.noteDao().getNoteById(noteId);
                    if (com.xiaohongshu.network.ModerationSync.publiclyVisible(note)) {
                        notes.add(note);
                    }
                }

                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 获取指定用户点赞过的笔记（他人主页「赞过」Tab 用）
     */
    public void getLikedNotesByUserId(String userId, DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                if (userId == null || userId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                List<String> noteIds = database.likeDao().getLikedNoteIdsSync(userId);
                if (noteIds == null || noteIds.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<NoteEntity> notes = new ArrayList<>();
                for (String noteId : noteIds) {
                    NoteEntity note = database.noteDao().getNoteById(noteId);
                    if (com.xiaohongshu.network.ModerationSync.publiclyVisible(note)) {
                        notes.add(note);
                    }
                }

                List<GraphicCardBean> cards = convertToGraphicCardBeans(notes);
                callback.onSuccess(cards);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 获取笔记统计信息（公开/私密/草稿计数）：本地 Room 计数 + 服务端公开笔记数。
     * 网页端发布的笔记只存在于服务器，需合并进“公开”计数。
     */
    public void getNoteStatistics(DataCallback<NoteStatistics> callback) {
        executorService.execute(() -> {
            try {
                Context context = AppApplication.getAppContext();
                String userId = getCurrentUserIdSync(context);

                if (userId.isEmpty()) {
                    callback.onSuccess(new NoteStatistics(0, 0, 0));
                    return;
                }

                int publicCount = database.noteDao().countPublicNotesSync(userId);
                int privateCount = database.noteDao().countPrivateNotesSync(userId);
                int draftCount = database.noteDao().countDraftsSync(userId);

                // 服务端公开笔记数（去重：App 发布并同步成功的已在本地计数里）
                try {
                    com.xiaohongshu.bean.UserBean me =
                            LoginDataRepository.getInstance(context).getCurrentUser();
                    if (me != null && me.getToken() != null && !me.getToken().isEmpty()) {
                        List<NoteEntity> locals = database.noteDao().getPublicNotesByUserIdSync(userId);
                        java.util.Set<String> localIds = new java.util.HashSet<>();
                        if (locals != null) for (NoteEntity n : locals) localIds.add(n.id);
                        List<com.xiaohongshu.network.RemotePost> remotePosts =
                                remoteApiClient.getUserPosts(me.getUsername(), 1, 50, me.getToken());
                        for (com.xiaohongshu.network.RemotePost p : remotePosts) {
                            if (!localIds.contains(p.getId())) publicCount++;
                        }
                    }
                } catch (Exception ignored) {
                    // 断网时仅统计本地
                }

                callback.onSuccess(new NoteStatistics(publicCount, privateCount, draftCount));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 将NoteEntity列表转换为GraphicCardBean列表
     */
    private List<GraphicCardBean> convertToGraphicCardBeans(List<NoteEntity> notes) {
        List<GraphicCardBean> cards = new ArrayList<>();
        if (notes == null) {
            return cards;
        }
        
        for (NoteEntity note : notes) {
            // 加载用户信息 - 每次都从数据库查询，确保获取最新的UserEntity
            UserEntity userEntity = null;
            if (note.userId != null && !note.userId.isEmpty()) {
                // 直接从数据库查询，不使用任何缓存，确保获取最新数据
                userEntity = database.userDao().getUserById(note.userId);
                
                // 如果通过ID查询失败，尝试通过username查询（备用方案）
                if (userEntity == null) {
                    android.util.Log.w("MineDataRepository", "User not found by ID: " + note.userId);
                }
            }
            
            UserBean userBean = null;
            if (userEntity != null) {
                // 使用与MineViewModel相同的头像获取逻辑
                // 优先使用userEntity.avatar，否则根据用户ID生成
                int avatarRes;
                if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                    // 如果用户设置了头像，使用用户设置的头像（这是最新的头像）
                    avatarRes = userEntity.avatar;
                    android.util.Log.d("MineDataRepository", "Using user avatar for userId " + note.userId + ": " + avatarRes);
                } else {
                    // 否则根据用户ID生成
                    String userId = userEntity.id;
                    if (userId != null && !userId.isEmpty()) {
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
            
            // 获取点赞数
            int likes = 0;
            try {
                likes = database.likeDao().getLikeCountSync(note.id);
            } catch (Exception e) {
                android.util.Log.e("MineDataRepository", "Error getting like count", e);
            }
            
            // 获取图片资源：优先使用保存的imageUris，否则根据noteId生成
            int imageRes = getNoteImageResource(note);
            
            String displayTitle = note.title != null && !note.title.trim().isEmpty()
                    ? note.title : firstContentLine(note.content);
            GraphicCardBean card = new GraphicCardBean(
                note.id,
                displayTitle,
                imageRes,
                0,
                userBean,
                likes,
                com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic
            );
            card.setContent(note.content);
            if (note.imageUris != null && !note.imageUris.isEmpty()) {
                card.setImageUri(note.imageUris.get(0));
            }
            String stateLabel=com.xiaohongshu.network.ModerationSync.label(note);
            if (!"已通过".equals(stateLabel) && !"仅自己可见".equals(stateLabel)) {
                card.setTitle("["+stateLabel+"] "+card.getTitle());card.setNeedsReviewEdit(true);
            }
            cards.add(card);
        }
        
        return cards;
    }

    private String firstContentLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String firstLine = content.trim().split("\\R", 2)[0].trim();
        return firstLine.length() > 36 ? firstLine.substring(0, 36) + "…" : firstLine;
    }
    
    /**
     * 根据索引获取头像资源（使用p1-p11）
     * @param index 索引值（可以是任意整数）
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
    
    /**
     * 获取笔记图片资源：优先使用保存的imageUris，否则根据noteId生成
     */
    private int getNoteImageResource(NoteEntity note) {
        // 优先使用保存的imageUris中的第一张图片
        if (note.imageUris != null && !note.imageUris.isEmpty()) {
            String firstImageUri = note.imageUris.get(0);
            int resourceId = extractResourceIdFromUri(firstImageUri);
            if (resourceId != 0) {
                return resourceId;
            }
        }
        
        // 纯文字笔记由 TextToImageConverter 生成文字海报；未生成时保持无图状态。
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
            android.util.Log.e("MineDataRepository", "Failed to extract resource ID from URI: " + uri, e);
        }
        
        return 0;
    }
    
    /**
     * 根据索引获取图片资源ID（image_1.jpg 到 image_15.jpg）
     */
    private int getImageResourceByIndex(int index) {
        switch (index) {
            case 1: return com.xiaohongshu.R.drawable.image_1;
            case 2: return com.xiaohongshu.R.drawable.image_2;
            case 3: return com.xiaohongshu.R.drawable.image_3;
            case 4: return com.xiaohongshu.R.drawable.image_4;
            case 5: return com.xiaohongshu.R.drawable.image_5;
            case 6: return com.xiaohongshu.R.drawable.image_6;
            case 7: return com.xiaohongshu.R.drawable.image_7;
            case 8: return com.xiaohongshu.R.drawable.image_8;
            case 9: return com.xiaohongshu.R.drawable.image_9;
            case 10: return com.xiaohongshu.R.drawable.image_10;
            case 11: return com.xiaohongshu.R.drawable.image_11;
            case 12: return com.xiaohongshu.R.drawable.image_12;
            case 13: return com.xiaohongshu.R.drawable.image_13;
            case 14: return com.xiaohongshu.R.drawable.image_14;
            case 15: return com.xiaohongshu.R.drawable.image_15;
            default: return com.xiaohongshu.R.drawable.image_1;
        }
    }
    
    /**
     * 笔记统计信息
     */
    public static class NoteStatistics {
        public int publicCount;
        public int privateCount;
        public int draftCount;
        
        public NoteStatistics(int publicCount, int privateCount, int draftCount) {
            this.publicCount = publicCount;
            this.privateCount = privateCount;
            this.draftCount = draftCount;
        }
    }
}
