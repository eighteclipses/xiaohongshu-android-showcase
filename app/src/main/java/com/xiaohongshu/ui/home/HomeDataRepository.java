package com.xiaohongshu.ui.home;

import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.mock.ImageMock;
import com.xiaohongshu.mock.TitleMock;
import com.xiaohongshu.mock.UserMock;
import com.xiaohongshu.mock.VideoMock;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.publish.TextToImageConverter;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.network.RemotePost;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Description: home 数据仓库，代替网络请求
 */
public class HomeDataRepository {
    private static HomeDataRepository instance;
    private final ExecutorService executorService;
    private final AppDatabase database;
    private final RemoteApiClient remoteApiClient;
    
    private final List<GraphicCardBean> graphicCardList = new ArrayList<>();
    private final List<GraphicCardBean> cityGraphicCardList = new ArrayList<>();
    private final Random random = new Random();

    private HomeDataRepository() {
        this.executorService = Executors.newCachedThreadPool();
        this.database = AppApplication.getDatabase();
        this.remoteApiClient = new RemoteApiClient(AppApplication.getAppContext());
    }

    public static synchronized HomeDataRepository getInstance() {
        if (instance == null) {
            instance = new HomeDataRepository();
        }
        return instance;
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception error);
    }

    public void getGraphicCardList(boolean reload, DataCallback<List<GraphicCardBean>> callback) {
        executorService.execute(() -> {
            try {
                // 等待 DatabaseInitializer 完成种子数据，避免冷启动时误判为无数据而全量 mock。
                com.xiaohongshu.database.DatabaseInitializer.awaitReady(5000);
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                // 优先从数据库加载公开笔记
                List<NoteEntity> publicNotes = database.noteDao().getAllPublicNotesSync();
                
                List<GraphicCardBean> tempList = new ArrayList<>();

                addRemoteCards(tempList);

                if (publicNotes != null && !publicNotes.isEmpty()) {
                    // 从数据库加载笔记
                    for (NoteEntity note : publicNotes) {
                        GraphicCardBean card = convertToGraphicCardBean(note);
                        if (card != null && tempList.stream().noneMatch(item -> item.getId().equals(card.getId()))) {
                            tempList.add(card);
                        }
                    }
                }

                dedupeByTitleAndAuthor(tempList);

                // Public feeds contain only server-approved content, never unreviewed mock fillers.
                graphicCardList.clear();
                graphicCardList.addAll(tempList);
                callback.onSuccess(new ArrayList<>(graphicCardList));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 保证列表中至少有 minCount 张视频卡片：真实笔记再多也补充 mock 视频，
     * 避免视频页因真实图文笔记占满而无内容。
     */


    /**
     * 按「标题+作者」去掉同一笔记的重复副本（本地种子与服务端种子 id 体系不同，
     * 仅按 id 去重无法识别），保留列表中先出现的卡片（服务端数据优先）。
     */
    private void dedupeByTitleAndAuthor(List<GraphicCardBean> cards) {
        if (cards == null || cards.size() < 2) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Iterator<GraphicCardBean> iterator = cards.iterator();
        while (iterator.hasNext()) {
            GraphicCardBean card = iterator.next();
            if (card == null || card.getTitle() == null || card.getTitle().isEmpty()) continue;
            String author = card.getUser() == null || card.getUser().getName() == null ? "" : card.getUser().getName();
            String key = card.getTitle() + "|" + author;
            if (!seen.add(key)) {
                iterator.remove();
            }
        }
    }

    /** 后台相对媒体路径补全为完整地址（与 ImageLoader 的补全规则一致） */
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

    private void addRemoteCards(List<GraphicCardBean> target) {        try {
            com.xiaohongshu.bean.UserBean current = com.xiaohongshu.ui.login.LoginDataRepository
                    .getInstance(AppApplication.getAppContext()).getCurrentUser();
            String token = current == null ? null : current.getToken();
            List<RemotePost> remotePosts = remoteApiClient.getPosts(1, 50, null, token);
            for (RemotePost remote : remotePosts) {
                boolean isVideo = "video".equals(remote.getMediaType()) && !remote.getVideoUrl().isEmpty();
                GraphicCardBean card = new GraphicCardBean(
                        remote.getId(),
                        remote.getTitle().isEmpty() ? firstContentLine(remote.getContent()) : remote.getTitle(),
                        0, 0,
                        new UserBean(remote.getAuthorId(), remote.getAuthorName(), com.xiaohongshu.R.drawable.p1, null),
                        remote.getLikeCount(),
                        isVideo ? GraphicCardType.Video : GraphicCardType.Graphic);
                card.setContent(remote.getContent());
                if (!remote.getImages().isEmpty()) {
                    card.setImageUris(remote.getImages());
                } else if (!isVideo) {
                    // 远程纯文字笔记：生成/复用仿真文字海报，观感与本地文字笔记一致
                    android.net.Uri poster = TextToImageConverter.createPosterForNote(
                            AppApplication.getAppContext(), remote.getId(),
                            remote.getTitle().isEmpty() ? null : remote.getTitle(), remote.getContent());
                    if (poster != null) card.setImageUri(poster.toString());
                }
                if (isVideo) {
                    // 视频源：相对路径补全为服务端完整地址，供 ExoPlayer 播放
                    card.setVideoUri(resolveMediaUrl(remote.getVideoUrl()));
                }
                target.add(card);
            }
        } catch (Exception ignored) {
            // Keep the local demo feed available when the backend is offline.
        }
    }
    
    /**
     * 更新缓存列表中每个GraphicCardBean的用户头像，确保显示最新的头像
     */
    private void updateUserAvatarsInCachedList() {
        for (GraphicCardBean card : graphicCardList) {
            if (card != null && card.getUser() != null) {
                String userId = card.getUser().getId();
                if (userId != null && !userId.isEmpty()) {
                    // 从数据库重新查询UserEntity，获取最新的头像
                    UserEntity userEntity = database.userDao().getUserById(userId);
                    if (userEntity != null) {
                        // 更新UserBean中的头像
                        int avatarRes;
                        if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                            // 使用用户设置的头像（最新的头像）
                            avatarRes = userEntity.avatar;
                        } else {
                            // 否则根据用户ID生成
                            String userEntityId = userEntity.id;
                            if (userEntityId != null && !userEntityId.isEmpty()) {
                                int avatarIndex = userEntityId.hashCode();
                                avatarRes = getAvatarResource(avatarIndex);
                            } else {
                                avatarRes = com.xiaohongshu.R.drawable.p1;
                            }
                        }
                        // 更新UserBean的头像
                        card.getUser().setImage(avatarRes);
                        if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()
                                && (userEntity.avatarUri.startsWith("http://") || userEntity.avatarUri.startsWith("https://"))) {
                            card.getUser().setImageUri(userEntity.avatarUri);
                        }
                        // 同时更新昵称（以防昵称也更新了）
                        if (userEntity.nickname != null) {
                            card.getUser().setName(userEntity.nickname);
                        } else if (userEntity.username != null) {
                            card.getUser().setName(userEntity.username);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 将NoteEntity转换为GraphicCardBean
     */
    private GraphicCardBean convertToGraphicCardBean(NoteEntity note) {
        if (note == null) {
            return null;
        }
        
        // 加载用户信息 - 每次都从数据库查询，确保获取最新的UserEntity
        UserEntity userEntity = null;
        if (note.userId != null && !note.userId.isEmpty()) {
            // 直接从数据库查询，不使用任何缓存，确保获取最新数据
            userEntity = database.userDao().getUserById(note.userId);
            
            if (userEntity == null) {
                android.util.Log.w("HomeDataRepository", "User not found by ID: " + note.userId);
            }
        }
        
        UserBean userBean = null;
        if (userEntity != null) {
            // 如果userEntity.avatar是p1-p11中的一个，就使用它；否则根据用户ID生成
            int avatarRes;
            if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                // 使用用户设置的头像（这是最新的头像）
                avatarRes = userEntity.avatar;
                android.util.Log.d("HomeDataRepository", "Using user avatar for userId " + note.userId + ": " + avatarRes);
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
            if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()
                    && (userEntity.avatarUri.startsWith("http://") || userEntity.avatarUri.startsWith("https://"))) {
                userBean.setImageUri(userEntity.avatarUri);
            }
        } else {
            // 如果用户不存在，创建一个默认用户
            userBean = new UserBean(
                note.userId != null ? note.userId : "unknown",
                "用户" + (note.userId != null && note.userId.length() > 6 ? note.userId.substring(0, 6) : (note.userId != null ? note.userId : "unknown")),
                com.xiaohongshu.R.drawable.p1,
                null
            );
        }
        
        ensureTextImage(note);

        // 获取点赞数
        int likes = database.likeDao().getLikeCountSync(note.id);
        
        // 获取图片资源：优先使用保存的imageUris，否则根据noteId生成
        int imageRes = getNoteImageResource(note);
        
        String displayTitle = note.title != null && !note.title.trim().isEmpty()
                ? note.title
                : firstContentLine(note.content);
        GraphicCardBean card = new GraphicCardBean(
            note.id,
            displayTitle,
            imageRes,
            0,
            userBean,
            likes,
            GraphicCardType.Graphic
        );
        card.setContent(note.content);
        if (note.imageUris != null && !note.imageUris.isEmpty()) {
            card.setImageUris(note.imageUris);
        }

        return card;
    }

    private String firstContentLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String firstLine = content.trim().split("\\R", 2)[0].trim();
        return firstLine.length() > 36 ? firstLine.substring(0, 36) + "…" : firstLine;
    }

    private void ensureTextImage(NoteEntity note) {
        if (note == null || note.imageUris == null || !note.imageUris.isEmpty()) return;
        android.net.Uri image = TextToImageConverter.createIfNeeded(
                AppApplication.getAppContext(), note.title, note.content, note.imageUris);
        if (image != null) {
            note.imageUris = new ArrayList<>();
            note.imageUris.add(image.toString());
            database.noteDao().update(note);
        }
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
            android.util.Log.e("HomeDataRepository", "Failed to extract resource ID from URI: " + uri, e);
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

    public void getCityGraphicCardList(boolean reload, DataCallback<List<GraphicCardBean>> callback) {

        executorService.execute(() -> {
            try {
                com.xiaohongshu.database.DatabaseInitializer.awaitReady(5000);
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                // 优先从数据库加载公开笔记
                List<NoteEntity> publicNotes = database.noteDao().getAllPublicNotesSync();

                List<GraphicCardBean> tempList = new ArrayList<>();

                if (publicNotes != null && !publicNotes.isEmpty()) {
                    // 从数据库加载笔记
                    for (NoteEntity note : publicNotes) {
                        GraphicCardBean card = convertToGraphicCardBean(note);
                        if (card != null) {
                            tempList.add(card);
                        }
                    }
                }

                dedupeByTitleAndAuthor(tempList);

                // City feed uses the same confirmed moderation state as discovery.
                cityGraphicCardList.clear();
                cityGraphicCardList.addAll(tempList);
                callback.onSuccess(new ArrayList<>(cityGraphicCardList));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getRecommendUserList(DataCallback<List<UserBean>> callback) {
        executorService.execute(() -> {
            try {
                List<UserBean> recommendUserList = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    UserBean user = new UserBean(
                            UUID.randomUUID().toString(),
                            UserMock.getRandomName(),
                            UserMock.getRandomImage(),
                            UserMock.getRandomUserInfo()
                    );
                    recommendUserList.add(user);
                    Thread.sleep(50);
                }
                callback.onSuccess(recommendUserList);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
}
