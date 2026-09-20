package com.xiaohongshu.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.FollowEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.discovery.DiscoveryAdapter;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.profile.viewmodel.UserProfileViewModel;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 用户主页Activity
 * 显示其他用户的主页信息
 */
public class UserProfileActivity extends BaseActivity {
    public static final String KEY_USER_ID = "key_user_id";
    
    private UserProfileViewModel viewModel;
    private AppDatabase database;
    private ExecutorService executorService;
    
    private ImageView userAvatar;
    private TextView userName;
    private TextView xiaohongshuId;
    private TextView ipLocationText;
    private TextView bioText;
    private TextView followingCount;
    private TextView followersCount;
    private TextView likesCount;
    private TextView followButton;
    private TextView messageButton;
    private TabLayout tabLayout;
    private RecyclerView contentRecyclerView;
    private DiscoveryAdapter contentAdapter;
    
    private String targetUserId;
    private String currentUserId;
    private boolean isFollowed = false;
    private boolean isBlocked = false;
    // 远程资料加载成功后，本地 Room 统计不再覆盖服务端真实数值
    private boolean remoteProfileLoaded = false;
    private int currentTab = 0; // 0-笔记, 1-收藏
    private android.widget.ImageView bgImage;
    private android.widget.TextView levelText;
    
    public static void start(Context context, String userId) {
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra(KEY_USER_ID, userId);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);
        
        targetUserId = getIntent().getStringExtra(KEY_USER_ID);
        if (targetUserId == null || targetUserId.isEmpty()) {
            finish();
            return;
        }
        
        database = AppApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(UserProfileViewModel.class);
        viewModel.init(targetUserId);
        
        initViews();
        initData();
        loadRemoteProfile();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("用户主页");
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 获取视图
        userAvatar = findViewById(R.id.userAvatar);
        userName = findViewById(R.id.userName);
        xiaohongshuId = findViewById(R.id.xiaohongshuId);
        ipLocationText = findViewById(R.id.ipLocationText);
        bioText = findViewById(R.id.bioText);
        followingCount = findViewById(R.id.followingCount);
        followersCount = findViewById(R.id.followersCount);
        likesCount = findViewById(R.id.likesCount);
        followButton = findViewById(R.id.followButton);
        messageButton = findViewById(R.id.messageButton);
        tabLayout = findViewById(R.id.tabLayout);
        contentRecyclerView = findViewById(R.id.contentRecyclerView);
        
        // 设置内容网格
        contentRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        contentAdapter = new DiscoveryAdapter(card -> {
            // 视频进刷视频页，图文进详情页
            if (card != null && card.getId() != null) {
                if (card.getType() == com.xiaohongshu.ui.home.bean.GraphicCardType.Video) {
                    com.xiaohongshu.activity.video.VideoActivity.newInstance(this, card.getId());
                } else {
                    GraphicActivity.newInstance(this, card.getId());
                }
            }
        });
        contentRecyclerView.setAdapter(contentAdapter);
        
        // 设置标签切换
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    currentTab = tab.getPosition();
                    loadContentForTab(currentTab);
                }
                
                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }
                
                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                    // 重选当前 Tab 时刷新内容（对齐真实小红书的下拉/重选刷新习惯）
                    loadContentForTab(tab.getPosition());
                }
            });
        }
        
        // 关注按钮
        if (followButton != null) {
            followButton.setOnClickListener(v -> {
                if (isBlocked) {
                    android.widget.Toast.makeText(this, "已拉黑该用户", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                toggleFollow();
            });
        }

        // 私信按钮：进入与该用户的私信会话
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                if (isBlocked) {
                    android.widget.Toast.makeText(this, "已拉黑该用户", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (targetUserId == null || targetUserId.isEmpty()) return;
                com.xiaohongshu.activity.chat.ChatActivity.start(this, targetUserId);
            });
        }

        // 更多操作：拉黑/解除拉黑
        View moreButton = findViewById(R.id.moreButton);
        if (moreButton != null) {
            moreButton.setOnClickListener(v -> showBlockMenu());
        }

        // 背景视图
        bgImage = findViewById(R.id.bgImage);
        levelText = findViewById(R.id.levelText);

        // 远程用户（本地库不存在）的头像兜底：按 id 哈希取 p 系列占位头像，本地用户随后由 updateUserInfo 覆盖
        if (userAvatar != null) {
            userAvatar.setImageResource(getAvatarResource(targetUserId.hashCode()));
        }
    }

    /** 拉取服务端用户公开详情：覆盖昵称/简介/等级/背景（服务器数据比本地 sample 用户更真实）。 */
    private void loadRemoteProfile() {
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean currentUser =
                        com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
                String token = currentUser == null ? null : currentUser.getToken();
                com.google.gson.JsonObject profile =
                        new com.xiaohongshu.network.RemoteApiClient(this).getUserProfile(targetUserId, token);
                runOnUiThread(() -> {
                    remoteProfileLoaded = true;
                    String nickname = profile.has("nickname") && !profile.get("nickname").isJsonNull()
                            ? profile.get("nickname").getAsString() : "";
                    if (!nickname.isEmpty() && userName != null) userName.setText(nickname);
                    // 远程用户没有本地实体时，用服务端 username 展示小红书号
                    if (xiaohongshuId != null && profile.has("username") && !profile.get("username").isJsonNull()) {
                        xiaohongshuId.setText("小红书号: " + profile.get("username").getAsString());
                    }
                    if (bioText != null && profile.has("bio")) {
                        String bio = profile.get("bio").isJsonNull() ? "" : profile.get("bio").getAsString();
                        if (!bio.isEmpty()) bioText.setText(bio);
                    }
                    if (levelText != null && profile.has("level")) {
                        levelText.setText("Lv." + profile.get("level").getAsInt() + " 创作者");
                    }
                    if (bgImage != null && profile.has("background")) {
                        String bg = profile.get("background").isJsonNull() ? "" : profile.get("background").getAsString();
                        if (!bg.isEmpty()) {
                            com.xiaohongshu.util.ImageLoader.load(bgImage, bg, 0);
                        }
                    }
                    if (followersCount != null && profile.has("fans_count")) {
                        followersCount.setText(String.valueOf(profile.get("fans_count").getAsInt()));
                    }
                    if (likesCount != null && profile.has("like_count")) {
                        likesCount.setText(String.valueOf(profile.get("like_count").getAsInt()));
                    }
                    if (followingCount != null && profile.has("follow_count")) {
                        followingCount.setText(String.valueOf(profile.get("follow_count").getAsInt()));
                    }
                });
            } catch (Exception ignored) {
                // 本地用户（sample_*）在服务端不存在时保持本地展示
            }
        });
    }
    
    @Override
    protected void initData() {
        // 获取当前用户ID
        com.xiaohongshu.bean.UserBean currentUser = 
            LoginDataRepository.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            executorService.execute(() -> {
                UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                    // 检查是否已关注 / 已拉黑
                    checkFollowStatus();
                    checkBlockStatus();
                    // 本地背景图（自己或本地用户设置过的）
                    String bg = userEntity.background;
                    if (bg != null && !bg.isEmpty() && bgImage != null) {
                        runOnUiThread(() -> com.xiaohongshu.util.ImageLoader.load(bgImage, bg, 0));
                    }
                }
            });
        }
        
        // 观察用户信息
        viewModel.getUser().observe(this, user -> {
            if (user != null) {
                updateUserInfo(user);
            }
        });
        
        // 观察笔记列表
        viewModel.getGraphicCardList().observe(this, cards -> {
            if (cards != null) {
                contentAdapter.updateData(cards);
            }
        });
        
        // 观察关注状态
        viewModel.getIsFollowed().observe(this, followed -> {
            isFollowed = followed != null && followed;
            updateFollowButton();
        });
        
        // 观察统计数据：标签由布局固定文案承担，这里只填数字；远程资料已加载时不覆盖
        viewModel.getFollowingCount().observe(this, count -> {
            if (remoteProfileLoaded) return;
            if (followingCount != null && count != null) {
                followingCount.setText(String.valueOf(count));
            }
        });

        viewModel.getFollowersCount().observe(this, count -> {
            if (remoteProfileLoaded) return;
            if (followersCount != null && count != null) {
                followersCount.setText(String.valueOf(count));
            }
        });

        viewModel.getLikesCount().observe(this, count -> {
            if (remoteProfileLoaded) return;
            if (likesCount != null && count != null) {
                likesCount.setText(String.valueOf(count));
            }
        });
        
        // 加载数据
        viewModel.load();
        loadContentForTab(0);
    }
    
    /**
     * 更新用户信息显示
     */
    private void updateUserInfo(UserEntity user) {
        if (userAvatar != null) {
            if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                // 统一走 Coil：http/content/file 都能加载，失败显示占位圆而非空白
                ImageLoader.load(userAvatar, user.avatarUri, R.drawable.placeholder_avatar);
            } else {
                int avatarRes;
                if (user.avatar > 0 && isP1ToP11Avatar(user.avatar)) {
                    avatarRes = user.avatar;
                } else {
                    String userId = user.id;
                    avatarRes = userId == null || userId.isEmpty() ? R.drawable.p1 : getAvatarResource(userId.hashCode());
                }
                userAvatar.setImageResource(avatarRes);
            }
        }
        
        if (userName != null) {
            userName.setText(user.nickname != null ? user.nickname : user.username);
        }
        
        if (xiaohongshuId != null) {
            xiaohongshuId.setText("小红书号: " + user.id);
        }
        
        if (ipLocationText != null) {
            ipLocationText.setText("IP属地: 福建 ①");
        }
        
        if (bioText != null) {
            if (user.bio != null && !user.bio.isEmpty()) {
                bioText.setText(user.bio);
            } else {
                bioText.setText("爱探店 爱拍照 爱美食");
            }
        }
    }
    
    /**
     * 根据索引获取头像资源（使用p1-p11）
     * @param index 索引值（可以是任意整数）
     * @return 对应的头像资源ID
     */
    private int getAvatarResource(int index) {
        int[] avatars = {
            R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
            R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
            R.drawable.p9, R.drawable.p10, R.drawable.p11,
            R.drawable.p12, R.drawable.p13, R.drawable.p14,
            R.drawable.p15, R.drawable.p16
        };
        return avatars[Math.abs(index) % avatars.length];
    }

    private boolean isP1ToP11Avatar(int resourceId) {
        int[] avatars = {R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4, R.drawable.p5,
                R.drawable.p6, R.drawable.p7, R.drawable.p8, R.drawable.p9, R.drawable.p10, R.drawable.p11, R.drawable.p12, R.drawable.p13, R.drawable.p14, R.drawable.p15, R.drawable.p16};
        for (int avatar : avatars) if (avatar == resourceId) return true;
        return false;
    }
    
    /**
     * 检查关注状态
     */
    private void checkFollowStatus() {
        if (currentUserId == null || currentUserId.isEmpty() || targetUserId == null || targetUserId.isEmpty()) {
            return;
        }
        
        executorService.execute(() -> {
            FollowEntity follow = database.followDao().checkFollow(currentUserId, targetUserId);
            boolean followed = follow != null;
            runOnUiThread(() -> {
                isFollowed = followed;
                updateFollowButton();
            });
        });
    }
    
    /**
     * 切换关注状态
     */
    private void toggleFollow() {
        if (currentUserId == null || currentUserId.isEmpty() || targetUserId == null || targetUserId.isEmpty()) {
            android.widget.Toast.makeText(this, "无法执行关注操作", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentUserId.equals(targetUserId)) {
            android.widget.Toast.makeText(this, "不能关注自己", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        viewModel.toggleFollow(currentUserId);
    }
    
    /** 拉黑/解除拉黑菜单 */
    private void showBlockMenu() {
        if (targetUserId == null || targetUserId.isEmpty()) return;
        if (currentUserId == null || currentUserId.isEmpty()) {
            android.widget.Toast.makeText(this, "请先登录后再操作", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId.equals(targetUserId)) {
            android.widget.Toast.makeText(this, "不能对自己操作", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = isBlocked ? new String[]{"解除拉黑", "取消"} : new String[]{"拉黑该用户", "取消"};
        new android.app.AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        if (isBlocked) unblockUser(); else blockUser();
                    }
                })
                .show();
    }

    /** 拉黑：写本地 + 解除本地关注 + 后台同步服务端 */
    private void blockUser() {
        final String nickname = userName != null ? String.valueOf(userName.getText()) : "";
        executorService.execute(() -> {
            database.blockDao().insert(new com.xiaohongshu.database.entity.BlockEntity(
                    currentUserId, targetUserId, System.currentTimeMillis()));
            database.followDao().unfollow(currentUserId, targetUserId);
            // 远程用户本地无实体时存占位，保证黑名单页能显示昵称
            if (database.userDao().getUserById(targetUserId) == null) {
                com.xiaohongshu.database.entity.UserEntity placeholder =
                        new com.xiaohongshu.database.entity.UserEntity();
                placeholder.id = targetUserId;
                placeholder.username = targetUserId;
                placeholder.nickname = nickname.isEmpty() ? targetUserId : nickname;
                placeholder.createTime = System.currentTimeMillis();
                database.userDao().insert(placeholder);
            }
            syncBlockRemote(true);
            runOnUiThread(() -> {
                isBlocked = true;
                isFollowed = false;
                updateFollowButton();
                android.widget.Toast.makeText(this, "已拉黑", android.widget.Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    /** 解除拉黑：删本地 + 后台同步服务端 */
    private void unblockUser() {
        executorService.execute(() -> {
            database.blockDao().deleteBlock(currentUserId, targetUserId);
            syncBlockRemote(false);
            runOnUiThread(() -> {
                isBlocked = false;
                updateFollowButton();
                android.widget.Toast.makeText(this, "已解除拉黑", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void syncBlockRemote(boolean block) {
        try {
            com.xiaohongshu.bean.UserBean currentUser =
                    com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
            String token = currentUser == null || currentUser.getToken() == null ? "" : currentUser.getToken();
            if (token.isEmpty()) return;
            com.xiaohongshu.network.RemoteApiClient client = new com.xiaohongshu.network.RemoteApiClient(this);
            // 服务端用户 id 与本地可能不同，先按 targetUserId 解析（服务端兼容 sample_user_N）
            if (block) client.blockUser(targetUserId, token); else client.unblockUser(targetUserId, token);
        } catch (Exception ignored) {
            // 服务端不可达时仅本地生效
        }
    }

    /** 进页检查本地拉黑状态 */
    private void checkBlockStatus() {
        if (currentUserId == null || currentUserId.isEmpty() || targetUserId == null || targetUserId.isEmpty()) return;
        executorService.execute(() -> {
            boolean blocked = database.blockDao().checkBlock(currentUserId, targetUserId) != null;
            runOnUiThread(() -> {
                isBlocked = blocked;
                updateFollowButton();
            });
        });
    }

    /**
     * 更新关注按钮显示
     */
    private void updateFollowButton() {
        if (followButton == null) {
            return;
        }
        
        if (isBlocked) {
            followButton.setText("已拉黑");
            followButton.setBackgroundResource(R.drawable.bg_button_grey);
            followButton.setTextColor(getResources().getColor(R.color.text_secondary, null));
        } else if (isFollowed) {
            followButton.setText("已关注");
            followButton.setBackgroundResource(R.drawable.bg_button_grey);
            followButton.setTextColor(getResources().getColor(R.color.text_secondary, null));
        } else {
            followButton.setText("关注");
            followButton.setBackgroundResource(R.drawable.bg_follow_button_solid);
            followButton.setTextColor(getResources().getColor(R.color.white, null));
        }
    }
    
    /**
     * 根据标签加载内容
     */
    private void loadContentForTab(int tabPosition) {
        if (tabPosition == 0) {
            // 笔记
            viewModel.loadNotes();
            loadRemoteNotes();
        } else if (tabPosition == 1) {
            // 收藏
            viewModel.loadCollections();
        } else if (tabPosition == 2) {
            // 赞过（对齐真实小红书：他人主页可见其公开点赞）
            loadLikedNotes();
        }
    }

    /** 「赞过」Tab：服务端该用户点赞列表 + 本地 Room 点赞记录合并。 */
    private void loadLikedNotes() {
        executorService.execute(() -> {
            // 服务端该用户的公开点赞
            List<com.xiaohongshu.ui.home.bean.GraphicCardBean> remoteCards = new ArrayList<>();
            try {
                com.xiaohongshu.bean.UserBean currentUser =
                        com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
                String token = currentUser == null ? null : currentUser.getToken();
                List<com.xiaohongshu.network.RemotePost> remotePosts =
                        new com.xiaohongshu.network.RemoteApiClient(this)
                                .getLikedPosts(targetUserId, 1, 50, token);
                for (com.xiaohongshu.network.RemotePost post : remotePosts) {
                    com.xiaohongshu.ui.home.bean.GraphicCardBean card = buildRemoteCard(post);
                    if (card != null) remoteCards.add(card);
                }
            } catch (Exception ignored) {
                // 服务端不可达时仅展示本地点赞
            }
            List<com.xiaohongshu.ui.home.bean.GraphicCardBean> remote = remoteCards;

            // 本地 Room 点赞的笔记（MineDataRepository 自带线程与转换）
            com.xiaohongshu.ui.mine.MineDataRepository.getInstance(this)
                    .getLikedNotesByUserId(targetUserId, new com.xiaohongshu.ui.mine.MineDataRepository.DataCallback<List<com.xiaohongshu.ui.home.bean.GraphicCardBean>>() {
                        @Override
                        public void onSuccess(List<com.xiaohongshu.ui.home.bean.GraphicCardBean> localCards) {
                            List<com.xiaohongshu.ui.home.bean.GraphicCardBean> merged = new ArrayList<>(remote);
                            java.util.Set<String> ids = new java.util.HashSet<>();
                            for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : merged) {
                                ids.add(card.getId());
                            }
                            if (localCards != null) {
                                for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : localCards) {
                                    if (card.getId() == null || !ids.contains(card.getId())) {
                                        merged.add(card);
                                    }
                                }
                            }
                            runOnUiThread(() -> {
                                if (currentTab == 2) {
                                    contentAdapter.updateData(merged);
                                }
                            });
                        }

                        @Override
                        public void onError(Exception error) {
                            runOnUiThread(() -> {
                                if (currentTab == 2) {
                                    contentAdapter.updateData(remote);
                                }
                            });
                        }
                    });
        });
    }

    /** 服务端笔记 → 首页卡片（供笔记/赞过 Tab 复用） */
    private com.xiaohongshu.ui.home.bean.GraphicCardBean buildRemoteCard(
            com.xiaohongshu.network.RemotePost post) {
        boolean isVideo = "video".equals(post.getMediaType()) && !post.getVideoUrl().isEmpty();
        String title = post.getTitle().isEmpty()
                ? (post.getContent().isEmpty() ? "无标题笔记" : post.getContent().split("\n")[0])
                : post.getTitle();
        com.xiaohongshu.ui.home.bean.GraphicCardBean card = new com.xiaohongshu.ui.home.bean.GraphicCardBean(
                post.getId(), title, 0, 0,
                new com.xiaohongshu.ui.home.bean.UserBean(post.getAuthorId(),
                        post.getAuthorName(), com.xiaohongshu.R.drawable.p1, null),
                post.getLikeCount(),
                isVideo ? com.xiaohongshu.ui.home.bean.GraphicCardType.Video
                        : com.xiaohongshu.ui.home.bean.GraphicCardType.Graphic);
        card.setContent(post.getContent());
        if (!post.getImages().isEmpty()) {
            card.setImageUris(post.getImages());
        } else if (!isVideo) {
            android.net.Uri poster = com.xiaohongshu.ui.publish.TextToImageConverter.createPosterForNote(
                    getApplication(), post.getId(),
                    post.getTitle().isEmpty() ? null : post.getTitle(), post.getContent());
            if (poster != null) card.setImageUri(poster.toString());
        }
        if (isVideo) card.setVideoUri(post.getVideoUrl().startsWith("/")
                ? getString(com.xiaohongshu.R.string.backend_base_url).replace("/api", "") + post.getVideoUrl()
                : post.getVideoUrl());
        return card;
    }

    /** 合并服务端该用户的公开笔记（远程用户主页的数据源）。 */
    private void loadRemoteNotes() {
        executorService.execute(() -> {
            try {
                com.xiaohongshu.bean.UserBean currentUser =
                        com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
                String token = currentUser == null ? null : currentUser.getToken();
                List<com.xiaohongshu.network.RemotePost> remotePosts =
                        new com.xiaohongshu.network.RemoteApiClient(this).getUserPosts(targetUserId, 1, 50, token);
                List<com.xiaohongshu.ui.home.bean.GraphicCardBean> cards = new ArrayList<>();
                for (com.xiaohongshu.network.RemotePost post : remotePosts) {
                    cards.add(buildRemoteCard(post));
                }
                runOnUiThread(() -> {
                    if (currentTab != 0) return;
                    // 远程笔记 + 本地笔记合并去重（id 或标题相同视为同一条，优先保留远程数据），
                    // 避免本地笔记被远程结果覆盖丢失，也避免本地/远程双份种子重复展示
                    List<com.xiaohongshu.ui.home.bean.GraphicCardBean> merged = new ArrayList<>(cards);
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    java.util.Set<String> titles = new java.util.HashSet<>();
                    for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : merged) {
                        ids.add(card.getId());
                        if (card.getTitle() != null) titles.add(card.getTitle());
                    }
                    List<com.xiaohongshu.ui.home.bean.GraphicCardBean> local = viewModel.getGraphicCardList().getValue();
                    if (local != null) {
                        for (com.xiaohongshu.ui.home.bean.GraphicCardBean card : local) {
                            boolean sameId = card.getId() != null && ids.contains(card.getId());
                            boolean sameTitle = card.getTitle() != null && titles.contains(card.getTitle());
                            if (!sameId && !sameTitle) merged.add(card);
                        }
                    }
                    contentAdapter.updateData(merged);
                });
            } catch (Exception ignored) {
                // 远程笔记拉取失败时保持本地结果
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

