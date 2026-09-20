package com.xiaohongshu.ui.publish.repository;

import android.content.Context;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.publish.model.NoteModel;
import com.xiaohongshu.ui.publish.TextToImageConverter;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.network.RemoteApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 笔记数据仓库
 * 使用Room数据库管理笔记数�? */
public class NoteRepository {
    private static NoteRepository instance;
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final RemoteApiClient remoteApiClient;
    private String currentUserId = "";
    
    private NoteRepository(Context context) {
        this.database = AppApplication.getDatabase();
        this.executorService = Executors.newSingleThreadExecutor();
        this.remoteApiClient = new RemoteApiClient(context.getApplicationContext());
        
        // 获取当前登录用户ID
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            executorService.execute(() -> {
                UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                }
            });
        }
    }
    
    public static synchronized NoteRepository getInstance(Context context) {
        if (instance == null) {
            instance = new NoteRepository(context);
        }
        return instance;
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
            // 验证缓存的ID是否仍然有效
            UserEntity cachedUser = database.userDao().getUserById(currentUserId);
            if (cachedUser != null) {
                // 验证缓存的userId对应的username是否与当前登录用户的username一致
                if (currentUser.getUsername().equals(cachedUser.username)) {
                    // 验证通过，返回缓存的userId
                    return currentUserId;
                } else {
                    // 用户已切换，清空缓存并重新获取
                    currentUserId = "";
                }
            } else {
                // 如果缓存的ID无效，清空并重新获取
                currentUserId = "";
            }
        }

        // 重新获取当前用户的userId
        UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
        if (userEntity != null) {
            currentUserId = userEntity.id;
            return currentUserId;
        } else {
            // 如果UserEntity不存在，尝试创建（这种情况应该很少，因为注册时会创建）
            // 但为了兼容性，我们仍然尝试创建
            userEntity = createUserEntityIfNeeded(currentUser);
            if (userEntity != null) {
                currentUserId = userEntity.id;
                return currentUserId;
            }
        }
        return "";
    }

    /**
     * 如果需要，创建UserEntity
     */
    private UserEntity createUserEntityIfNeeded(com.xiaohongshu.bean.UserBean userBean) {
        try {
            // 再次检查，避免重复创建
            UserEntity existing = database.userDao().getUserByUsername(userBean.getUsername());
            if (existing != null) {
                return existing;
            }

            // 创建新的UserEntity（明文密码不写入本地数据库）
            UserEntity userEntity = new UserEntity();
            // 生成唯一的用户ID
            userEntity.id = String.valueOf(System.currentTimeMillis()) + "_" + (int)(Math.random() * 10000);
            userEntity.username = userBean.getUsername();

            // 根据用户名hash选择默认头像（p1-p16）
            int avatarIndex = Math.abs(userBean.getUsername().hashCode()) % 16 + 1;
            userEntity.avatar = getAvatarResource(avatarIndex);

            userEntity.nickname = userBean.getUsername();
            userEntity.bio = "";
            userEntity.createTime = System.currentTimeMillis();

            // 插入数据库
            database.userDao().insert(userEntity);

            return userEntity;
        } catch (Exception e) {
            android.util.Log.e("NoteRepository", "Error creating UserEntity", e);
            return null;
        }
    }

    /**
     * 根据索引获取头像资源（使用p1-p16）
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
        return avatars[(index - 1) % avatars.length];
    }
    
    /**
     * 保存草稿
     */
    public String saveDraft(NoteModel note) {
        note.setDraft(true);
        NoteEntity entity=convertToEntity(note);entity.userId=getCurrentUserIdSync(AppApplication.getAppContext());
        entity.syncState="pending";entity.serverKnown=false;entity.updateTime=System.currentTimeMillis();database.noteDao().insert(entity);
        syncDraftToBackend(note);
        NoteEntity saved=database.noteDao().getNoteById(note.getId());
        if(saved!=null && "conflict".equals(saved.syncState))throw new IllegalStateException(saved.reviewNote);
        return saved!=null && "synced".equals(saved.syncState) ? "草稿已保存" : "草稿已保存到本机，待同步";
    }

    /**
     * 获取所有草稿
     */
    public List<NoteModel> getDrafts() {
        List<NoteModel> drafts = new ArrayList<>();
        Context context = AppApplication.getAppContext();
        String userId = getCurrentUserIdSync(context);
        
        if (userId.isEmpty()) {
            return drafts;
        }
        
        List<NoteEntity> entities = database.noteDao().getDraftsByUserIdSync(userId);
        if (entities == null) {
            return drafts;
        }
        
        for (NoteEntity entity : entities) {
            drafts.add(convertToModel(entity));
        }
        return drafts;
    }
    
    /**
     * 删除草稿
     */
    public void deleteDraft(String noteId) throws java.io.IOException { recycleNote(noteId); }

    public void recycleNote(String noteId) throws java.io.IOException {
        NoteEntity entity=database.noteDao().getNoteById(noteId);
        if(entity==null)throw new java.io.IOException("笔记不存在");
        if(!entity.userId.equals(getCurrentUserIdSync(AppApplication.getAppContext())))throw new java.io.IOException("只能操作本人的笔记");
        if(!entity.serverKnown || !"synced".equals(entity.syncState))throw new java.io.IOException("内容尚未同步，请联网同步后再移入回收站");
        UserBean user=LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
        if(user==null)throw new java.io.IOException("请先登录");
        remoteApiClient.deletePost(noteId,user.getToken());
        entity.deleted=true;entity.contentVersion+=1;entity.updateTime=System.currentTimeMillis();database.noteDao().update(entity);
    }

    /**
     * 发布笔记
     */
    public String publishNote(NoteModel note) {
        Context context=AppApplication.getAppContext();note.setDraft(false);ensureTextImage(note,context);
        NoteEntity entity=convertToEntity(note);entity.userId=getCurrentUserIdSync(context);
        if(entity.userId.isEmpty())throw new IllegalStateException("请先登录");
        entity.syncState="pending";entity.moderationStatus="pending";entity.serverKnown=false;entity.updateTime=System.currentTimeMillis();database.noteDao().insert(entity);
        syncPublishedToBackend(note);
        NoteEntity saved=database.noteDao().getNoteById(note.getId());
        if(saved!=null && "conflict".equals(saved.syncState))throw new IllegalStateException(saved.reviewNote);
        if(saved==null || !"synced".equals(saved.syncState))return "内容已保存到本机，待同步";
        return !saved.isPublic?"私密笔记已保存":"approved".equals(saved.moderationStatus)?"发布成功":"已提交，等待审核";
    }

    private void ensureTextImage(NoteModel note, Context context) {
        if (note == null) return;
        android.net.Uri generated = TextToImageConverter.createIfNeeded(
                context, note.getTitle(), note.getContent(), note.getImageUris());
        if (generated != null) {
            List<String> images = new ArrayList<>();
            images.add(generated.toString());
            note.setImageUris(images);
        }
    }
    
    /**
     * 获取已发布的笔记
     */
    public List<NoteModel> getPublishedNotes() {
        List<NoteModel> notes = new ArrayList<>();
        Context context = AppApplication.getAppContext();
        String userId = getCurrentUserIdSync(context);
        
        if (userId.isEmpty()) {
            return notes;
        }
        
        List<NoteEntity> entities = database.noteDao().getPublicNotesByUserIdSync(userId);
        if (entities == null) {
            return notes;
        }
        
        for (NoteEntity entity : entities) {
            notes.add(convertToModel(entity));
        }
        return notes;
    }
    
    /**
     * 根据ID获取笔记
     */
    public NoteModel getNoteById(String noteId) {
        com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
        NoteEntity entity = database.noteDao().getNoteById(noteId);
        if (entity != null) {
            return convertToModel(entity);
        }
        return null;
    }
    
    /**
     * 将NoteModel转换为NoteEntity
     */
    private NoteEntity convertToEntity(NoteModel model) {
        NoteEntity entity = new NoteEntity();
        entity.id = model.getId();
        entity.contentVersion=model.getContentVersion();entity.reviewNote=model.getReviewNote();entity.moderationStatus=model.getModerationStatus();entity.syncState=model.getSyncState();
        entity.title = model.getTitle();
        entity.content = model.getContent();
        entity.imageUris = model.getImageUris() != null ? model.getImageUris() : new ArrayList<>();
        entity.topics = model.getTopics() != null ? model.getTopics() : new ArrayList<>();
        entity.location = model.getLocation();
        entity.isPublic = model.isPublic();
        entity.isDraft = model.isDraft();
        entity.createTime = model.getCreateTime();
        entity.updateTime = model.getUpdateTime();
        return entity;
    }
    
    /**
     * 将NoteEntity转换为NoteModel
     */
    private NoteModel convertToModel(NoteEntity entity) {
        NoteModel model = new NoteModel();
        model.setId(entity.id);
        model.setContentVersion(entity.contentVersion);model.setReviewNote(entity.reviewNote);model.setModerationStatus(entity.moderationStatus);model.setSyncState(entity.syncState);
        model.setTitle(entity.title);
        model.setContent(entity.content);
        model.setImageUris(entity.imageUris != null ? entity.imageUris : new ArrayList<>());
        model.setTopics(entity.topics != null ? entity.topics : new ArrayList<>());
        model.setLocation(entity.location);
        model.setPublic(entity.isPublic);
        model.setDraft(entity.isDraft);
        model.setCreateTime(entity.createTime);
        model.setUpdateTime(entity.updateTime);
        return model;
    }

    private boolean recordPermanentFailure(NoteModel note, Exception error) {
        if (error instanceof com.xiaohongshu.network.RemoteApiException) {
            int status=((com.xiaohongshu.network.RemoteApiException)error).getStatusCode();
            if(status>=400 && status<500 && status!=429) {
                NoteEntity local=database.noteDao().getNoteById(note.getId());
                if(local!=null){local.syncState="conflict";local.reviewNote=error.getMessage();database.noteDao().update(local);}
                return true;
            }
        }
        return false;
    }
    public void retryPendingNote(String id) {
        NoteEntity local=database.noteDao().getNoteById(id);
        if(local==null||!"pending".equals(local.syncState)||!local.userId.equals(getCurrentUserIdSync(AppApplication.getAppContext())))return;
        NoteModel note=convertToModel(local);
        if(local.isDraft)syncDraftToBackend(note);else syncPublishedToBackend(note);
    }
    public void reloadServerNote(String id) throws java.io.IOException {
        UserBean user=LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
        if(user==null)throw new java.io.IOException("请先登录");
        com.xiaohongshu.network.ModerationSync.apply(remoteApiClient.getPost(id,user.getToken()));
    }

    private void syncDraftToBackend(NoteModel note) {
        UserBean user = LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
        if (user == null || user.getToken() == null || user.getToken().isEmpty()) return;
        try {
            boolean replaced = uploadImagesForRemote(note, user.getToken());
            if (replaced) persistImageUris(note);
            remoteApiClient.saveDraft(note, user.getToken());
        } catch (Exception ignored) {
            if (recordPermanentFailure(note, ignored)) return;
            // 后台不可达或上传失败：不把本地 URI 发给后端，登记待同步，详情页打开时重试
            com.xiaohongshu.network.PendingSyncStore.enqueue(
                    AppApplication.getAppContext(), "draft", note.getId(), null);
        }
    }

    private void syncPublishedToBackend(NoteModel note) {
        UserBean user = LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
        if (user == null || user.getToken() == null || user.getToken().isEmpty()) return;
        try {
            boolean replaced = uploadImagesForRemote(note, user.getToken());
            if (replaced) persistImageUris(note);
            remoteApiClient.publishNote(note, user.getToken());
        } catch (Exception ignored) {
            if (recordPermanentFailure(note, ignored)) return;
            // 上传/发布失败：不静默发布无图笔记，登记待同步，成功联网后重试完整发布
            com.xiaohongshu.network.PendingSyncStore.enqueue(
                    AppApplication.getAppContext(), "publish", note.getId(), null);
        }
    }

    /**
     * 图片上传成功后把 http 地址回写 Room。
     * 否则本地上传失败时重启会导致 content:// 授权失效、图片无法显示。
     */
    private void persistImageUris(NoteModel note) {
        if (note == null || note.getId() == null || note.getId().isEmpty()) return;
        NoteEntity entity = database.noteDao().getNoteById(note.getId());
        if (entity == null) return;
        entity.imageUris = new ArrayList<>(note.getImageUris());
        entity.updateTime = System.currentTimeMillis();
        database.noteDao().update(entity);
    }

    /**
     * @return true 表示至少一张图片上传成功且 imageUris 已被替换为 http 地址
     * @throws IOException 上传失败时抛出，由调用方决定入队重试，避免静默发布无图笔记
     */
    private boolean uploadImagesForRemote(NoteModel note, String token) throws IOException {
        if (note == null || note.getImageUris() == null || note.getImageUris().isEmpty()) return false;
        boolean hasUploadableImage = false;
        for (String uri : note.getImageUris()) {
            // android.resource:// 包内图片由 RemoteApiClient 读取字节真实上传，同样视为可上传；
            // 仅已上传的 http(s)/服务端相对路径无需再传
            if (uri != null && !uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("/")) {
                hasUploadableImage = true;
                break;
            }
        }
        if (!hasUploadableImage) return false;
        List<String> uploaded = remoteApiClient.uploadImages(
                AppApplication.getAppContext(), note.getImageUris(), token);
        if (!uploaded.isEmpty() && !uploaded.equals(note.getImageUris())) {
            note.setImageUris(uploaded);
            return true;
        }
        return false;
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}

