package com.xiaohongshu.ui.discover;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.FollowEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.discover.viewmodel.DiscoverFriendsViewModel;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.common.InfoActivity;

/**
 * 发现好友Activity
 * 显示推荐用户列表，支持关注
 */
public class DiscoverFriendsActivity extends BaseActivity {
    private DiscoverFriendsViewModel viewModel;
    private RecyclerView recyclerView;
    private DiscoverFriendsAdapter adapter;
    private AppDatabase database;
    
    public static void start(Context context) {
        if (context == null) {
            android.util.Log.e("DiscoverFriendsActivity", "Context is null");
            return;
        }
        try {
            Intent intent = new Intent(context, DiscoverFriendsActivity.class);
            // 如果context不是Activity，需要添加FLAG_ACTIVITY_NEW_TASK
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsActivity", "Error starting activity", e);
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover_friends);
        
        try {
            // 初始化数据库，确保不为null
            database = AppApplication.getDatabase();
            if (database == null) {
                android.util.Log.e("DiscoverFriendsActivity", "Database is null from AppApplication, trying to initialize directly");
                // 如果AppApplication中的database为null，直接调用AppDatabase.getInstance初始化
                database = com.xiaohongshu.database.AppDatabase.getInstance(getApplicationContext());
                if (database == null) {
                    android.util.Log.e("DiscoverFriendsActivity", "Failed to initialize database");
                }
            }
            
            // 初始化ViewModel
            try {
                viewModel = new ViewModelProvider(this, 
                    new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(DiscoverFriendsViewModel.class);
            } catch (Exception e) {
                android.util.Log.e("DiscoverFriendsActivity", "Error creating ViewModel", e);
                viewModel = null;
            }
            
            // 初始化视图
            initViews();
            
            // 如果ViewModel初始化成功，初始化数据
            if (viewModel != null) {
                viewModel.init(getApplicationContext());
                initData();
            } else {
                // ViewModel初始化失败，显示空状态
                android.widget.Toast.makeText(this, "页面初始化失败: ViewModel创建失败", android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsActivity", "Error in onCreate", e);
            // 即使发生异常，也不要直接finish，让用户可以看到页面
            android.widget.Toast.makeText(this, "页面初始化失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void initViews() {
        try {
            // 设置标题和信息图标
            TextView titleText = findViewById(R.id.titleText);
            if (titleText != null) {
                titleText.setText("发现好友");
            }
            
            // 初始化RecyclerView
            recyclerView = findViewById(R.id.recyclerView);
            if (recyclerView != null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                
                // 创建Adapter
                adapter = new DiscoverFriendsAdapter((user, isFollow) -> {
                    try {
                        // 关注/取消关注
                        if (database != null && database.followDao() != null) {
                            String currentUserId = getCurrentUserId();
                            if (currentUserId != null && !currentUserId.isEmpty() && user != null) {
                                if (isFollow) {
                                    // 关注
                                    FollowEntity follow = new FollowEntity(currentUserId, user.id, System.currentTimeMillis());
                                    database.followDao().insert(follow);
                                    android.widget.Toast.makeText(this, "已关注", android.widget.Toast.LENGTH_SHORT).show();
                                } else {
                                    // 取消关注
                                    database.followDao().unfollow(currentUserId, user.id);
                                    android.widget.Toast.makeText(this, "已取消关注", android.widget.Toast.LENGTH_SHORT).show();
                                }
                                if (viewModel != null) {
                                    viewModel.load(); // 重新加载
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("DiscoverFriendsActivity", "Error in follow callback", e);
                    }
                });
                adapter.setOnDismissListener(user -> {
                    adapter.removeUser(user);
                });
                recyclerView.setAdapter(adapter);
            }
            
            // 发现方式导航按钮
            View contactsButton = findViewById(R.id.contactsButton);
            if (contactsButton != null) {
                contactsButton.setOnClickListener(v -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                android.provider.ContactsContract.Contacts.CONTENT_URI);
                        startActivity(intent);
                    } catch (Exception e) {
                        InfoActivity.start(this, "通讯录", "系统未安装可用的通讯录应用。");
                    }
                });
            }
            
            View petPartnersButton = findViewById(R.id.petPartnersButton);
            if (petPartnersButton != null) {
                petPartnersButton.setOnClickListener(v -> {
                    com.xiaohongshu.ui.search.SearchResultActivity.start(this, "宠物");
                });
            }
            
            View qrCodeButton = findViewById(R.id.qrCodeButton);
            if (qrCodeButton != null) {
                qrCodeButton.setOnClickListener(v -> showMyQrCode());
            }
            
            // 返回按钮
            View backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                backButton.setOnClickListener(v -> finish());
            }
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsActivity", "Error in initViews", e);
        }
    }
    
    @Override
    protected void initData() {
        try {
            if (viewModel != null) {
                viewModel.getUsers().observe(this, users -> {
                    if (users != null && adapter != null) {
                        adapter.updateData(users);
                    }
                });
                
                viewModel.load();
            }
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsActivity", "Error in initData", e);
            android.widget.Toast.makeText(this, "数据加载失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 生成并弹出我的用户二维码，内容为 xhs://user/<用户id>，
     * 对方用「扫一扫」扫描即可跳转到我的主页
     */
    private void showMyQrCode() {
        com.xiaohongshu.bean.UserBean currentUser =
                LoginDataRepository.getInstance(this).getCurrentUser();
        if (currentUser == null) {
            InfoActivity.start(this, "我的二维码", "请先登录后再查看个人二维码。");
            return;
        }
        final String username = currentUser.getUsername();
        new Thread(() -> {
            UserEntity userEntity = database != null ? database.userDao().getUserByUsername(username) : null;
            if (userEntity == null) {
                runOnUiThread(() -> InfoActivity.start(this, "我的二维码", "未找到当前用户的本地资料。"));
                return;
            }
            final android.graphics.Bitmap qrBitmap = com.xiaohongshu.util.QRCodeUtil.create(
                    com.xiaohongshu.util.QRCodeUtil.USER_SCHEME_PREFIX + userEntity.id, 720);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (qrBitmap == null) {
                    android.widget.Toast.makeText(this, "二维码生成失败", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                android.widget.ImageView imageView = new android.widget.ImageView(this);
                imageView.setImageBitmap(qrBitmap);
                imageView.setPadding(48, 48, 48, 64);
                new android.app.AlertDialog.Builder(this)
                        .setTitle("我的二维码")
                        .setMessage("用户名：" + username + "\n让对方用「扫一扫」扫描即可查看我的主页")
                        .setView(imageView)
                        .setPositiveButton("完成", null)
                        .show();
            });
        }).start();
    }

    private String getCurrentUserId() {
        try {
            if (database != null && database.userDao() != null) {
                com.xiaohongshu.bean.UserBean currentUser = 
                    LoginDataRepository.getInstance(this).getCurrentUser();
                if (currentUser != null && currentUser.getUsername() != null && !currentUser.getUsername().isEmpty()) {
                    UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                    if (userEntity != null) {
                        return userEntity.id;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("DiscoverFriendsActivity", "Error in getCurrentUserId", e);
        }
        return null;
    }
}
