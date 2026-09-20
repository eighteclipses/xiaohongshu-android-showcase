package com.xiaohongshu.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 黑名单管理页：展示我拉黑的用户，支持解除拉黑（本地 + 服务端同步）。
 */
public class BlacklistActivity extends BaseActivity {
    private RecyclerView recyclerView;
    private BlacklistAdapter adapter;
    private AppDatabase database;
    private ExecutorService executorService;
    private String currentUserId;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BlacklistActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);

        database = AppApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();

        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) titleText.setText("黑名单管理");
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlacklistAdapter(user -> unblock(user));
        recyclerView.setAdapter(adapter);

        loadCurrentUserIdAndList();
    }

    private void loadCurrentUserIdAndList() {
        executorService.execute(() -> {
            com.xiaohongshu.bean.UserBean currentUser =
                    LoginDataRepository.getInstance(this).getCurrentUser();
            if (currentUser == null) {
                runOnUiThread(this::showEmptyState);
                return;
            }
            UserEntity me = database.userDao().getUserByUsername(currentUser.getUsername());
            if (me == null) {
                runOnUiThread(this::showEmptyState);
                return;
            }
            currentUserId = me.id;
            loadBlacklist();
        });
    }

    private void loadBlacklist() {
        executorService.execute(() -> {
            List<String> blockedIds = database.blockDao().getBlockedIdsSync(currentUserId);
            List<UserEntity> users = new ArrayList<>();
            if (blockedIds != null) {
                for (String id : blockedIds) {
                    if (id == null || id.isEmpty()) continue;
                    UserEntity user = database.userDao().getUserById(id);
                    if (user == null) {
                        // 远程用户本地无实体：占位展示
                        user = new UserEntity();
                        user.id = id;
                        user.nickname = id;
                        user.username = id;
                    }
                    users.add(user);
                }
            }
            runOnUiThread(() -> {
                if (users.isEmpty()) {
                    showEmptyState();
                } else {
                    hideEmptyState();
                    adapter.updateData(users);
                }
            });
        });
    }

    private void unblock(UserEntity user) {
        if (user == null || user.id == null) return;
        executorService.execute(() -> {
            database.blockDao().deleteBlock(currentUserId, user.id);
            syncUnblockRemote(user.id);
            runOnUiThread(() -> {
                Toast.makeText(this, "已解除拉黑", Toast.LENGTH_SHORT).show();
                loadBlacklist();
            });
        });
    }

    private void syncUnblockRemote(String targetUserId) {
        try {
            com.xiaohongshu.bean.UserBean currentUser =
                    LoginDataRepository.getInstance(this).getCurrentUser();
            String token = currentUser == null || currentUser.getToken() == null ? "" : currentUser.getToken();
            if (token.isEmpty()) return;
            new com.xiaohongshu.network.RemoteApiClient(this).unblockUser(targetUserId, token);
        } catch (Exception ignored) {
            // 服务端不可达时仅本地生效
        }
    }

    private void showEmptyState() {
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        View empty = findViewById(R.id.emptyStateLayout);
        if (empty != null) empty.setVisibility(View.VISIBLE);
    }

    private void hideEmptyState() {
        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        View empty = findViewById(R.id.emptyStateLayout);
        if (empty != null) empty.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}
