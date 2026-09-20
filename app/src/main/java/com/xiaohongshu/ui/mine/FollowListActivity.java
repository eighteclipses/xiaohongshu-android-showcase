package com.xiaohongshu.ui.mine;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.FollowEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 关注列表Activity
 * 显示当前用户关注的所有用户
 */
public class FollowListActivity extends BaseActivity {
    public static final String KEY_SHOW_FOLLOWERS = "show_followers";
    private RecyclerView recyclerView;
    private FollowListAdapter adapter;
    private AppDatabase database;
    private ExecutorService executorService;
    private boolean showFollowers;
    
    public static void start(Context context) {
        start(context, false);
    }

    public static void start(Context context, boolean showFollowers) {
        if (context == null) {
            android.util.Log.e("FollowListActivity", "Context is null, cannot start activity");
            return;
        }
        try {
            Intent intent = new Intent(context, FollowListActivity.class);
            intent.putExtra(KEY_SHOW_FOLLOWERS, showFollowers);
            context.startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error starting activity", e);
        }
    }
    
    private String cachedCurrentUserId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_follow_list);
            showFollowers = getIntent().getBooleanExtra(KEY_SHOW_FOLLOWERS, false);
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error setting content view", e);
            android.widget.Toast.makeText(this, "页面加载失败", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        try {
            database = AppApplication.getDatabase();
            if (database == null) {
                android.util.Log.e("FollowListActivity", "Database is null");
                android.widget.Toast.makeText(this, "数据库初始化失败", android.widget.Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            executorService = Executors.newSingleThreadExecutor();
            
            initViews();
            
            // 先获取当前用户ID，然后加载关注列表
            if (executorService != null) {
                loadCurrentUserIdAndThenLoadFollowList();
            } else {
                showEmptyState();
            }
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error in onCreate", e);
            e.printStackTrace();
            android.widget.Toast.makeText(this, "初始化失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            finish();
        }
    }
    
    @Override
    protected void initViews() {
        try {
            // 设置标题
            TextView titleText = findViewById(R.id.titleText);
            if (titleText != null) {
                titleText.setText(showFollowers ? "粉丝" : "关注");
            }
            
            // 返回按钮
            View backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                backButton.setOnClickListener(v -> {
                    try {
                        finish();
                    } catch (Exception e) {
                        android.util.Log.e("FollowListActivity", "Error finishing activity", e);
                    }
                });
            }
            
            recyclerView = findViewById(R.id.recyclerView);
            
            // 检查recyclerView是否为null
            if (recyclerView == null) {
                android.util.Log.e("FollowListActivity", "recyclerView is null");
                showEmptyState();
                return;
            }
            
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            
            adapter = new FollowListAdapter((user, isUnfollow) -> {
                // 取消关注
                if (cachedCurrentUserId != null && !cachedCurrentUserId.isEmpty() && 
                    user != null && user.id != null && !user.id.isEmpty() &&
                    executorService != null && database != null) {
                    executorService.execute(() -> {
                        try {
                            database.followDao().unfollow(cachedCurrentUserId, user.id);
                            runOnUiThread(() -> {
                                try {
                                    android.widget.Toast.makeText(this, "已取消关注", android.widget.Toast.LENGTH_SHORT).show();
                                    loadFollowList(); // 重新加载列表
                                } catch (Exception e) {
                                    android.util.Log.e("FollowListActivity", "Error in UI update after unfollow", e);
                                }
                            });
                        } catch (Exception e) {
                            android.util.Log.e("FollowListActivity", "Error unfollowing user", e);
                            runOnUiThread(() -> {
                                try {
                                    android.widget.Toast.makeText(this, "取消关注失败", android.widget.Toast.LENGTH_SHORT).show();
                                } catch (Exception ex) {
                                    android.util.Log.e("FollowListActivity", "Error showing toast", ex);
                                }
                            });
                        }
                    });
                }
            });
            
            // 点击用户项进入其主页
            adapter.setOnUserClickListener(user -> {
                if (user != null && user.id != null && !user.id.isEmpty()) {
                    com.xiaohongshu.ui.profile.UserProfileActivity.start(this, user.id);
                }
            });

            recyclerView.setAdapter(adapter);
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error initializing views", e);
            e.printStackTrace();
            // 如果初始化失败，显示空状态
            showEmptyState();
        }
    }
    
    @Override
    protected void initData() {
        // 数据加载在loadFollowList中处理
    }
    
    private void loadCurrentUserIdAndThenLoadFollowList() {
        if (executorService == null || database == null) {
            showEmptyState();
            return;
        }
        
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean currentUser = 
                    LoginDataRepository.getInstance(this).getCurrentUser();
                if (currentUser != null && currentUser.getUsername() != null) {
                    UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                    if (userEntity != null && userEntity.id != null) {
                        cachedCurrentUserId = userEntity.id;
                        loadFollowList();
                    } else {
                        runOnUiThread(this::showEmptyState);
                    }
                } else {
                    runOnUiThread(this::showEmptyState);
                }
            } catch (Exception e) {
                android.util.Log.e("FollowListActivity", "Error loading current user ID", e);
                runOnUiThread(this::showEmptyState);
            }
        });
    }
    
    private void loadFollowList() {
        if (cachedCurrentUserId == null || cachedCurrentUserId.isEmpty()) {
            showEmptyState();
            return;
        }
        
        if (executorService == null || database == null) {
            android.util.Log.e("FollowListActivity", "executorService or database is null");
            showEmptyState();
            return;
        }
        
        if (adapter == null) {
            android.util.Log.e("FollowListActivity", "adapter is null");
            showEmptyState();
            return;
        }
        
        executorService.execute(() -> {
            try {
                // 获取关注或粉丝列表的用户ID
                List<String> followedUserIds = showFollowers
                        ? database.followDao().getFollowerIdsSync(cachedCurrentUserId)
                        : database.followDao().getFollowedUserIdsSync(cachedCurrentUserId);
                
                if (followedUserIds == null || followedUserIds.isEmpty()) {
                    runOnUiThread(this::showEmptyState);
                    return;
                }
                
                // 根据用户ID查询用户详细信息
                List<UserEntity> followedUsers = new ArrayList<>();
                for (String userId : followedUserIds) {
                    if (userId != null && !userId.isEmpty()) {
                        try {
                            UserEntity user = database.userDao().getUserById(userId);
                            if (user != null) {
                                followedUsers.add(user);
                            }
                        } catch (Exception e) {
                            android.util.Log.e("FollowListActivity", "Error loading user: " + userId, e);
                        }
                    }
                }
                
                runOnUiThread(() -> {
                    try {
                        if (adapter != null && recyclerView != null) {
                            if (followedUsers.isEmpty()) {
                                showEmptyState();
                            } else {
                                hideEmptyState();
                                adapter.updateData(followedUsers);
                            }
                        } else {
                            android.util.Log.e("FollowListActivity", "adapter or recyclerView is null in UI thread");
                            showEmptyState();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("FollowListActivity", "Error updating UI", e);
                        showEmptyState();
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("FollowListActivity", "Error loading follow list", e);
                runOnUiThread(this::showEmptyState);
            }
        });
    }
    
    private void showEmptyState() {
        try {
            // 检查是否在主线程
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                // 在主线程，直接执行
                if (recyclerView != null) {
                    recyclerView.setVisibility(View.GONE);
                }
                View emptyStateLayout = findViewById(R.id.emptyStateLayout);
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(View.VISIBLE);
                }
            } else {
                // 在后台线程，切换到主线程
                runOnUiThread(() -> {
                    try {
                        if (recyclerView != null) {
                            recyclerView.setVisibility(View.GONE);
                        }
                        View emptyStateLayout = findViewById(R.id.emptyStateLayout);
                        if (emptyStateLayout != null) {
                            emptyStateLayout.setVisibility(View.VISIBLE);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("FollowListActivity", "Error showing empty state", e);
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error in showEmptyState", e);
        }
    }
    
    private void hideEmptyState() {
        try {
            if (recyclerView != null) {
                recyclerView.setVisibility(View.VISIBLE);
            }
            View emptyStateLayout = findViewById(R.id.emptyStateLayout);
            if (emptyStateLayout != null) {
                emptyStateLayout.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            android.util.Log.e("FollowListActivity", "Error hiding empty state", e);
        }
    }
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}


