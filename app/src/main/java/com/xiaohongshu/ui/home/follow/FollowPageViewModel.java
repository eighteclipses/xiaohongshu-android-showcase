package com.xiaohongshu.ui.home.follow;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.ui.home.HomeDataRepository;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.network.RemoteApiClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Follow Page ViewModel
 * 继承 AndroidViewModel：默认工厂可实例化带 Application 参数的构造，
 * 修复首页「关注」tab 因反射创建失败导致的崩溃。
 */
public class FollowPageViewModel extends AndroidViewModel {
    private final MutableLiveData<List<UserBean>> recommendUserList = new MutableLiveData<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final HomeDataRepository repository = HomeDataRepository.getInstance();
    private final AppDatabase database;
    private final RemoteApiClient remoteApiClient;
    private String currentUserId = "";

    public FollowPageViewModel(android.app.Application application) {
        super(application);
        database = AppApplication.getDatabase();
        remoteApiClient = new RemoteApiClient(application.getApplicationContext());
    }

    public LiveData<List<UserBean>> getRecommendUserList() {
        return recommendUserList;
    }

    public void load() {
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean current = LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
                if (current != null) {
                    UserEntity entity = database.userDao().getUserByUsername(current.getUsername());
                    currentUserId = entity == null ? "" : entity.id;
                }
                List<UserBean> users = new java.util.ArrayList<>();
                if (!currentUserId.isEmpty()) {
                    for (UserEntity entity : database.userDao().getRecommendedUsersSync(currentUserId, 20)) {
                        UserBean user = new UserBean(entity.id, entity.nickname == null ? entity.username : entity.nickname,
                                entity.avatar > 0 ? entity.avatar : com.xiaohongshu.R.drawable.p1, null);
                        if (entity.avatarUri != null && (entity.avatarUri.startsWith("http://") || entity.avatarUri.startsWith("https://"))) {
                            user.setImageUri(entity.avatarUri);
                        }
                        users.add(user);
                    }
                }
                recommendUserList.postValue(users);
            } catch (Exception error) { recommendUserList.postValue(new java.util.ArrayList<>()); }
        });
    }

    public void followUser(UserBean target, Runnable onSuccess) {
        if (target == null || target.getId() == null || target.getId().isEmpty() || currentUserId.isEmpty()) return;
        executorService.execute(() -> {
            try {
                if (database.followDao().checkFollow(currentUserId, target.getId()) == null) {
                    database.followDao().insert(new com.xiaohongshu.database.entity.FollowEntity(currentUserId, target.getId(), System.currentTimeMillis()));
                    com.xiaohongshu.bean.UserBean current = LoginDataRepository.getInstance(AppApplication.getAppContext()).getCurrentUser();
                    if (current != null && current.getToken() != null && !current.getToken().isEmpty()) {
                        try { remoteApiClient.followUser(target.getId(), current.getToken()); } catch (Exception ignored) { }
                    }
                }
                if (onSuccess != null) onSuccess.run();
            } catch (Exception ignored) { }
        });
    }

    public void clearRecommendUserList() {
        recommendUserList.postValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

