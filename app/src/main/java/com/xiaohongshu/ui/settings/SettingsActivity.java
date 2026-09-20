package com.xiaohongshu.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.login.LoginActivity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.common.InfoActivity;
import com.xiaohongshu.ui.mine.EditProfileActivity;

/**
 * 设置Activity
 * 实现设置功能（账号管理、隐私设置等）
 */
public class SettingsActivity extends BaseActivity {
    private TextView accountManagement;
    private TextView privacySettings;
    private TextView notificationSettings;
    private TextView about;
    private TextView logoutButton;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        accountManagement = findViewById(R.id.accountManagement);
        privacySettings = findViewById(R.id.privacySettings);
        notificationSettings = findViewById(R.id.notificationSettings);
        about = findViewById(R.id.about);
        logoutButton = findViewById(R.id.logoutButton);
        
        // 账号管理
        if (accountManagement != null) {
            accountManagement.setOnClickListener(v -> {
                EditProfileActivity.start(this);
            });
        }
        
        // 隐私设置
        if (privacySettings != null) {
            privacySettings.setOnClickListener(v -> {
                InfoActivity.start(this, "隐私设置", "通过审核的公开笔记会展示在发现页。私密笔记仅本人可见，联网后会同步到服务端。待同步或待审核内容不会进入公共列表。");
            });
        }
        
        // 通知设置
        if (notificationSettings != null) {
            notificationSettings.setOnClickListener(v -> {
                InfoActivity.start(this, "通知设置", "点赞、收藏、评论和关注通知已开启。");
            });
        }
        
        // 黑名单管理
        TextView blacklistManagement = findViewById(R.id.blacklistManagement);
        if (blacklistManagement != null) {
            blacklistManagement.setOnClickListener(v -> BlacklistActivity.start(this));
        }

        // 关于
        if (about != null) {
            about.setOnClickListener(v -> {
                InfoActivity.start(this, "关于小红书学习版", "版本 1.0\n\n用于 Android 界面与社区功能学习。\n商品与支付为演示功能，社区支持真实账号发布和内容审核。");
            });
        }
        
        // 退出登录
        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> {
                logout();
            });
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("设置");
        }
    }
    
    @Override
    protected void initData() {
        // 初始化数据
    }
    
    /**
     * 退出登录
     */
    private void logout() {
        LoginDataRepository.getInstance(this).clearLoginState();
        android.widget.Toast.makeText(this, "已退出登录", android.widget.Toast.LENGTH_SHORT).show();
        
        // 跳转到登录页
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
