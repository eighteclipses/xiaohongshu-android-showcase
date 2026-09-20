package com.xiaohongshu.ui.discover.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 发现好友ViewModel
 */
public class DiscoverFriendsViewModel extends AndroidViewModel {
    private final MutableLiveData<List<UserEntity>> users = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    
    public DiscoverFriendsViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
    }
    
    public void init(android.content.Context context) {
        try {
            // 获取当前用户ID
            if (context == null) {
                android.util.Log.e("DiscoverFriendsViewModel", "Context is null in init method");
                return;
            }
            
            LoginDataRepository loginRepo = LoginDataRepository.getInstance(context);
            if (loginRepo == null) {
                android.util.Log.e("DiscoverFriendsViewModel", "LoginDataRepository is null");
                return;
            }
            
            com.xiaohongshu.bean.UserBean currentUser = loginRepo.getCurrentUser();
            if (currentUser != null) {
                String username = currentUser.getUsername();
                if (username != null && !username.isEmpty()) {
                    executorService.execute(() -> {
                        try {
                            if (database != null && database.userDao() != null) {
                                UserEntity userEntity = database.userDao().getUserByUsername(username);
                                if (userEntity != null) {
                                    currentUserId = userEntity.id;
                                }
                                // 无论是否获取到currentUserId，都调用load方法
                                load();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("DiscoverFriendsViewModel", "Error in init executor task", e);
                            // 即使发生异常，也调用load方法，确保UI能显示
                            users.postValue(new ArrayList<>());
                        }
                    });
                } else {
                    // 用户名为空，直接调用load方法
                    load();
                }
            } else {
                // 当前用户为空，直接调用load方法
                load();
            }
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsViewModel", "Error in init method", e);
            // 发生异常，调用load方法，确保UI能显示
            load();
        }
    }
    
    public LiveData<List<UserEntity>> getUsers() {
        return users;
    }
    
    public void load() {
        executorService.execute(() -> {
            try {
                // 获取推荐用户（排除当前用户）
                List<UserEntity> allUsers = getAllUsersSync();
                if (allUsers == null) {
                    allUsers = new ArrayList<>();
                }
                
                List<UserEntity> recommendedUsers = new ArrayList<>();
                for (UserEntity user : allUsers) {
                    if (user != null && !user.id.equals(currentUserId)) {
                        recommendedUsers.add(user);
                    }
                }
                
                // 限制数量
                if (recommendedUsers.size() > 20) {
                    recommendedUsers = recommendedUsers.subList(0, 20);
                }
                
                users.postValue(recommendedUsers);
            } catch (Exception e) {
                android.util.Log.e("DiscoverFriendsViewModel", "Error in load method", e);
                // 返回空列表，避免UI崩溃
                users.postValue(new ArrayList<>());
            }
        });
    }
    
    /**
     * 同步获取所有用户
     */
    private List<UserEntity> getAllUsersSync() {
        return database.userDao().getAllUsersSync();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

