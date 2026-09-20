package com.xiaohongshu.ui.comments.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.CommentEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 我的评论ViewModel
 */
public class MyCommentsViewModel extends AndroidViewModel {
    private final MutableLiveData<List<CommentEntity>> comments = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    
    public MyCommentsViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
    }
    
    public void init(android.content.Context context) {
        // 获取当前用户ID
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            executorService.execute(() -> {
                UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                    load();
                }
            });
        }
    }
    
    public LiveData<List<CommentEntity>> getComments() {
        return comments;
    }
    
    public void load() {
        executorService.execute(() -> {
            if (currentUserId.isEmpty()) {
                comments.postValue(new ArrayList<>());
                return;
            }
            
            List<CommentEntity> commentList = getCommentsByUserIdSync(currentUserId);
            if (commentList == null) {
                commentList = new ArrayList<>();
            }
            
            comments.postValue(commentList);
        });
    }
    
    /**
     * 同步获取用户评论
     */
    private List<CommentEntity> getCommentsByUserIdSync(String userId) {
        return database.commentDao().getCommentsByUserIdSync(userId);
    }
    
    /**
     * 删除评论
     */
    public void deleteComment(CommentEntity comment) {
        executorService.execute(() -> {
            database.commentDao().delete(comment);
            load(); // 重新加载列表
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

