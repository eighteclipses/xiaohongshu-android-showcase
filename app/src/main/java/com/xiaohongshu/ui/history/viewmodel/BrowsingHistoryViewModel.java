package com.xiaohongshu.ui.history.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览历史ViewModel
 */
public class BrowsingHistoryViewModel extends AndroidViewModel {
    private final MutableLiveData<List<GraphicCardBean>> historyNotes = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    
    public BrowsingHistoryViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
    }
    
    public void init(android.content.Context context) {
        // 获取当前用户ID
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            executorService.execute(() -> {
                com.xiaohongshu.database.entity.UserEntity userEntity = 
                    database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                    load();
                }
            });
        }
    }
    
    public LiveData<List<GraphicCardBean>> getHistoryNotes() {
        return historyNotes;
    }
    
    public void load() {
        executorService.execute(() -> {
            // 从SharedPreferences加载浏览历史（简化实现）
            // 实际应该使用数据库存储浏览历史
            android.content.SharedPreferences sp = getApplication().getSharedPreferences("browsing_history", 0);
            String historyJson = sp.getString("history_note_ids", "[]");
            
            List<String> noteIds = new ArrayList<>();
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                Type listType = new com.google.gson.reflect.TypeToken<List<String>>(){}.getType();
                noteIds = gson.fromJson(historyJson, listType);
                if (noteIds == null) {
                    noteIds = new ArrayList<>();
                }
            } catch (Exception e) {
                noteIds = new ArrayList<>();
            }
            
            List<GraphicCardBean> cards = new ArrayList<>();
            for (String noteId : noteIds) {
                NoteEntity note = database.noteDao().getNoteById(noteId);
                if (note != null) {
                    // 转换为GraphicCardBean
                    com.xiaohongshu.database.entity.UserEntity userEntity = 
                        database.userDao().getUserById(note.userId);
                    UserBean userBean = null;
                    if (userEntity != null) {
                        userBean = new UserBean(
                            userEntity.id,
                            userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                            userEntity.avatar,
                            null
                        );
                        if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()) userBean.setImageUri(userEntity.avatarUri);
                    }
                    
                    int likes = database.likeDao().getLikeCountSync(note.id);
                    
                    // 获取笔记图片资源：优先使用保存的imageUris，否则根据noteId生成
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
                    cards.add(card);
                }
            }
            
            historyNotes.postValue(cards);
        });
    }

    private String firstContentLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String firstLine = content.trim().split("\\R", 2)[0].trim();
        return firstLine.length() > 36 ? firstLine.substring(0, 36) + "…" : firstLine;
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
            android.util.Log.e("BrowsingHistoryViewModel", "Failed to extract resource ID from URI: " + uri, e);
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
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
