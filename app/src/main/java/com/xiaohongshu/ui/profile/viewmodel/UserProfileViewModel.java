package com.xiaohongshu.ui.profile.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.FollowEntity;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.mine.MineDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户主页ViewModel
 */
public class UserProfileViewModel extends AndroidViewModel {
    private final MutableLiveData<UserEntity> user = new MutableLiveData<>();
    private final MutableLiveData<List<GraphicCardBean>> graphicCardList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isFollowed = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> followingCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> followersCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> likesCount = new MutableLiveData<>(0);
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final AppDatabase database;
    private final MineDataRepository repository;
    private String targetUserId;
    
    public UserProfileViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
        repository = MineDataRepository.getInstance(application);
    }
    
    public void init(String userId) {
        this.targetUserId = userId;
    }
    
    public LiveData<UserEntity> getUser() {
        return user;
    }
    
    public LiveData<List<GraphicCardBean>> getGraphicCardList() {
        return graphicCardList;
    }
    
    public LiveData<Boolean> getIsFollowed() {
        return isFollowed;
    }
    
    public LiveData<Integer> getFollowingCount() {
        return followingCount;
    }
    
    public LiveData<Integer> getFollowersCount() {
        return followersCount;
    }
    
    public LiveData<Integer> getLikesCount() {
        return likesCount;
    }
    
    /**
     * 加载所有数据
     */
    public void load() {
        if (targetUserId == null || targetUserId.isEmpty()) {
            return;
        }
        
        executorService.execute(() -> {
            // 加载用户信息
            UserEntity userEntity = database.userDao().getUserById(targetUserId);
            if (userEntity != null) {
                user.postValue(userEntity);
            }
            
            // 加载统计数据
            loadStatistics();
        });
    }
    
    /**
     * 加载统计数据
     */
    private void loadStatistics() {
        if (targetUserId == null || targetUserId.isEmpty()) {
            return;
        }
        
        executorService.execute(() -> {
            // 关注数
            List<String> followingIds = database.followDao().getFollowedUserIdsSync(targetUserId);
            followingCount.postValue(followingIds != null ? followingIds.size() : 0);
            
            // 粉丝数
            List<String> followerIds = database.followDao().getFollowerIdsSync(targetUserId);
            followersCount.postValue(followerIds != null ? followerIds.size() : 0);
            
            // 获赞与收藏数（简化处理，统计笔记的点赞和收藏总数）
            List<NoteEntity> notes = database.noteDao().getNotesByUserIdSync(targetUserId);
            int totalLikes = 0;
            int totalCollections = 0;
            if (notes != null) {
                for (NoteEntity note : notes) {
                    totalLikes += database.likeDao().getLikeCountSync(note.id);
                    totalCollections += database.collectionDao().getCollectionCountSync(note.id);
                }
            }
            likesCount.postValue(totalLikes + totalCollections);
        });
    }
    
    /**
     * 加载笔记列表
     */
    public void loadNotes() {
        if (targetUserId == null || targetUserId.isEmpty()) {
            graphicCardList.postValue(new ArrayList<>());
            return;
        }
        
        repository.getNotesByUserId(targetUserId, new MineDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                graphicCardList.postValue(new ArrayList<>());
            }
        });
    }
    
    /**
     * 加载收藏列表
     */
    public void loadCollections() {
        if (targetUserId == null || targetUserId.isEmpty()) {
            graphicCardList.postValue(new ArrayList<>());
            return;
        }
        
        repository.getCollectedNotesByUserId(targetUserId, new MineDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                graphicCardList.postValue(new ArrayList<>());
            }
        });
    }
    
    /**
     * 切换关注状态
     */
    public void toggleFollow(String currentUserId) {
        if (targetUserId == null || targetUserId.isEmpty() || 
            currentUserId == null || currentUserId.isEmpty() ||
            currentUserId.equals(targetUserId)) {
            return;
        }
        
        executorService.execute(() -> {
            FollowEntity existingFollow = database.followDao().checkFollow(currentUserId, targetUserId);
            if (existingFollow != null) {
                // 取消关注
                database.followDao().unfollow(currentUserId, targetUserId);
                isFollowed.postValue(false);
            } else {
                // 关注
                FollowEntity follow = new FollowEntity(currentUserId, targetUserId, System.currentTimeMillis());
                database.followDao().insert(follow);
                isFollowed.postValue(true);
            }
            
            // 更新统计数据
            loadStatistics();
        });
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

