package com.xiaohongshu.ui.mine.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.mine.MineDataRepository;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.UserBean;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 我的页面ViewModel
 * 管理用户信息、笔记列表、收藏、点赞等数据
 */
public class MineViewModel extends AndroidViewModel {
    private final MutableLiveData<UserBean> user = new MutableLiveData<>();
    private final MutableLiveData<List<GraphicCardBean>> graphicCardList = new MutableLiveData<>();
    private final MutableLiveData<Integer> publicNoteCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> privateNoteCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> draftCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> collectionCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> followingCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> followersCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> likesCount = new MutableLiveData<>(0);
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final MineDataRepository repository;
    private final AppDatabase database;
    
    public MineViewModel(Application application) {
        super(application);
        repository = MineDataRepository.getInstance(application);
        database = AppApplication.getDatabase();
        
        // 加载用户信息
        loadUserInfo();
    }
    
    /**
     * 加载用户信息
     */
    public void loadUserInfo() {
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean currentUser = 
                    LoginDataRepository.getInstance(getApplication()).getCurrentUser();
                if (currentUser != null && database != null && database.userDao() != null) {
                    UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                    if (userEntity != null) {
                        // 优先使用userEntity.avatar，这是用户在EditProfileActivity中设置的头像
                        int avatarRes;
                        if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                            avatarRes = userEntity.avatar;
                        } else {
                            // 如果用户没有设置头像，根据用户ID生成
                            String userId = userEntity.id;
                            if (userId != null && !userId.isEmpty()) {
                                int avatarIndex = userId.hashCode();
                                avatarRes = getAvatarResource(avatarIndex);
                            } else {
                                avatarRes = com.xiaohongshu.R.drawable.p1;
                            }
                        }
                        UserBean userBean = new UserBean(
                            userEntity.id,
                            userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                            avatarRes,
                            null
                        );
                        if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()
                                && (userEntity.avatarUri.startsWith("http://") || userEntity.avatarUri.startsWith("https://"))) {
                            userBean.setImageUri(userEntity.avatarUri);
                        }
                        user.postValue(userBean);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MineViewModel", "Error in loadUserInfo", e);
            }
        });
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
    
    public LiveData<UserBean> getUser() {
        return user;
    }
    
    public LiveData<List<GraphicCardBean>> getGraphicCardList() {
        return graphicCardList;
    }
    
    public LiveData<Integer> getPublicNoteCount() {
        return publicNoteCount;
    }
    
    public LiveData<Integer> getPrivateNoteCount() {
        return privateNoteCount;
    }
    
    public LiveData<Integer> getDraftCount() {
        return draftCount;
    }

    public LiveData<Integer> getCollectionCount() {
        return collectionCount;
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
     * 加载笔记列表（根据类型：0-全部，1-公开，2-私密，3-草稿）
     */
    public void loadNotes(int type) {
        repository.getNotesByType(type, new MineDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                graphicCardList.postValue(new java.util.ArrayList<>());
            }
        });
    }
    
    /**
     * 加载收藏的笔记
     */
    public void loadCollectedNotes() {
        repository.getCollectedNotes(new MineDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                graphicCardList.postValue(new java.util.ArrayList<>());
            }
        });
    }
    
    /**
     * 加载点赞的笔记
     */
    public void loadLikedNotes() {
        repository.getLikedNotes(new MineDataRepository.DataCallback<List<GraphicCardBean>>() {
            @Override
            public void onSuccess(List<GraphicCardBean> data) {
                graphicCardList.postValue(data);
            }
            
            @Override
            public void onError(Exception error) {
                graphicCardList.postValue(new java.util.ArrayList<>());
            }
        });
    }
    
    /**
     * 加载笔记统计信息
     */
    public void loadStatistics() {
        repository.getNoteStatistics(new MineDataRepository.DataCallback<MineDataRepository.NoteStatistics>() {
            @Override
            public void onSuccess(MineDataRepository.NoteStatistics data) {
                publicNoteCount.postValue(data.publicCount);
                privateNoteCount.postValue(data.privateCount);
                draftCount.postValue(data.draftCount);
            }
            
            @Override
            public void onError(Exception error) {
                publicNoteCount.postValue(0);
                privateNoteCount.postValue(0);
                draftCount.postValue(0);
                collectionCount.postValue(0);
            }
        });
        
        // 加载关注和粉丝数量
        loadFollowStatistics();
    }
    
    /**
     * 加载关注和粉丝统计信息
     */
    private void loadFollowStatistics() {
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean currentUser = 
                    LoginDataRepository.getInstance(getApplication()).getCurrentUser();
                if (currentUser != null) {
                    UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                    if (userEntity != null) {
                        String currentUserId = userEntity.id;
                        
                        // 获取关注数
                        List<String> followingIds = database.followDao().getFollowedUserIdsSync(currentUserId);
                        followingCount.postValue(followingIds != null ? followingIds.size() : 0);
                        
                        // 获取粉丝数
                        List<String> followerIds = database.followDao().getFollowerIdsSync(currentUserId);
                        followersCount.postValue(followerIds != null ? followerIds.size() : 0);
                        List<String> collectedIds = database.collectionDao().getCollectedNoteIdsSync(currentUserId);
                        collectionCount.postValue(collectedIds == null ? 0 : collectedIds.size());

                        // 获赞与收藏：自己全部笔记被点赞 + 被收藏的总数
                        List<com.xiaohongshu.database.entity.NoteEntity> notes =
                                database.noteDao().getNotesByUserIdSync(currentUserId);
                        int total = 0;
                        if (notes != null) {
                            for (com.xiaohongshu.database.entity.NoteEntity note : notes) {
                                total += database.likeDao().getLikeCountSync(note.id);
                                total += database.collectionDao().getCollectionCountSync(note.id);
                            }
                        }
                        likesCount.postValue(total);
                    }
                }
            } catch (Exception e) {
                followingCount.postValue(0);
                followersCount.postValue(0);
                likesCount.postValue(0);
            }
        });
    }
    
    /**
     * 加载所有数据
     */
    public void load() {
        loadUserInfo(); // 重新加载用户信息，包括头像
        loadStatistics();
        loadNotes(0); // 加载全部笔记
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}
