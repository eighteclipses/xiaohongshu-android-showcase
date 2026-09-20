package com.xiaohongshu.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.GoodsEntity;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.database.entity.FollowEntity;
import com.xiaohongshu.ui.goods.GoodsDetailActivity;
import com.xiaohongshu.ui.home.discovery.DiscoveryAdapter;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.shop.ShopGoodsAdapter;
import com.xiaohongshu.ui.shop.bean.GoodsBean;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.profile.UserProfileActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 搜索结果Activity
 * 显示搜索到的笔记和商品
 */
public class SearchResultActivity extends BaseActivity {
    public static final String KEY_KEYWORD = "key_keyword";
    
    private AppDatabase database;
    private ExecutorService executorService;
    private RecyclerView notesRecyclerView;
    private RecyclerView goodsRecyclerView;
    private RecyclerView usersRecyclerView;
    private DiscoveryAdapter notesAdapter;
    private ShopGoodsAdapter goodsAdapter;
    private SearchUserAdapter usersAdapter;
    private TabLayout tabLayout;
    private TextView emptyStateText;
    private TextView sortDefault;
    private TextView sortLatest;
    private TextView sortHottest;
    private String keyword;
    private boolean loading;
    private String searchMessage = "";
    private int currentTab = 0; // 0: 笔记, 1: 用户, 2: 商品
    private int currentSort = 0; // 0: 综合, 1: 最新, 2: 最热
    /** 合并后的完整笔记结果（综合排序 = 原始顺序）；排序切换只重新排列这份列表 */
    private List<GraphicCardBean> mergedNoteCards = new ArrayList<>();
    
    public static void start(Context context, String keyword) {
        Intent intent = new Intent(context, SearchResultActivity.class);
        intent.putExtra(KEY_KEYWORD, keyword);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_result);
        
        keyword = getIntent().getStringExtra(KEY_KEYWORD);
        if (keyword == null || keyword.isEmpty()) {
            finish();
            return;
        }
        
        database = AppApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();
        
        initViews();
        performSearch();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("搜索结果: " + keyword);
        }
        
        tabLayout = findViewById(R.id.tabLayout);
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        goodsRecyclerView = findViewById(R.id.goodsRecyclerView);
        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        sortDefault = findViewById(R.id.sortDefault);
        sortLatest = findViewById(R.id.sortLatest);
        sortHottest = findViewById(R.id.sortHottest);

        View.OnClickListener sortListener = v -> {
            int next = v.getId() == R.id.sortLatest ? 1 : v.getId() == R.id.sortHottest ? 2 : 0;
            if (currentSort == next) return;
            currentSort = next;
            applySort();
        };
        if (sortDefault != null) sortDefault.setOnClickListener(sortListener);
        if (sortLatest != null) sortLatest.setOnClickListener(sortListener);
        if (sortHottest != null) sortHottest.setOnClickListener(sortListener);
        
        // 设置标签页
        if (tabLayout != null) {
            tabLayout.addTab(tabLayout.newTab().setText("笔记"));
            tabLayout.addTab(tabLayout.newTab().setText("用户"));
            tabLayout.addTab(tabLayout.newTab().setText("商品"));
            
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    currentTab = tab.getPosition();
                    switchTab(currentTab);
                }
                
                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }
                
                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
        }
        
        // 笔记列表
        notesRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        notesAdapter = new DiscoveryAdapter(card -> {
            if (card != null && card.getId() != null) {
                if (card.getType() == GraphicCardType.Video) {
                    com.xiaohongshu.activity.video.VideoActivity.newInstance(this, card.getId());
                } else {
                    GraphicActivity.newInstance(this, card.getId());
                }
            }
        });
        notesRecyclerView.setAdapter(notesAdapter);
        
        // 商品列表
        goodsRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        goodsAdapter = new ShopGoodsAdapter(goods -> {
            if (goods != null && goods.getId() != null) {
                GoodsDetailActivity.start(this, goods.getId());
            }
        });
        goodsRecyclerView.setAdapter(goodsAdapter);

        // 用户列表：搜索"用户"分类用，本地 userDao 模糊匹配 username/nickname
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        usersAdapter = new SearchUserAdapter();
        usersAdapter.setOnUserClickListener(new SearchUserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(UserEntity user) {
                if (user != null && user.id != null && !user.id.isEmpty()) {
                    UserProfileActivity.start(SearchResultActivity.this, user.id);
                }
            }

            @Override
            public void onFollowClick(UserEntity user) {
                // 搜索结果页内点击关注：直接用 FollowDao 写入本地（与详情页同源），
                // 真实小红书行为是立即切换为"已关注"，本地 Room 同步生效。
                if (user == null || user.id == null || user.id.isEmpty()) return;
                String currentUserId = getCurrentUserIdOrNull();
                if (currentUserId == null) {
                    android.widget.Toast.makeText(SearchResultActivity.this,
                            "请先登录", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentUserId.equals(user.id)) return; // 不能关注自己
                new Thread(() -> {
                    try {
                        FollowEntity existing = database.followDao().checkFollow(currentUserId, user.id);
                        if (existing != null) {
                            database.followDao().unfollow(currentUserId, user.id);
                        } else {
                            database.followDao().insert(
                                    new FollowEntity(currentUserId, user.id, System.currentTimeMillis()));
                        }
                    } catch (Exception ignored) {
                        // 数据库写入失败静默兜底：toast 由下次进首页刷新生效
                    }
                }).start();
            }
        });
        usersRecyclerView.setAdapter(usersAdapter);
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        // 数据在performSearch中加载
    }
    
    private void performSearch() {
        loading = true;
        searchMessage = "正在搜索…";
        updateEmptyState();
        executorService.execute(() -> {
          try {
            // 等待 DatabaseInitializer 完成，避免冷启动直接搜索时读到空库
            com.xiaohongshu.database.DatabaseInitializer.awaitReady(5000);
            com.xiaohongshu.network.ModerationSync.refresh(getApplicationContext());
            // 搜索笔记
            List<NoteEntity> notes = database.noteDao().searchNotes(keyword);

            // 搜索商品
            List<GoodsEntity> goods = database.goodsDao().searchGoodsSync(keyword);

            // 搜索用户：本地账号/昵称模糊匹配，再合并服务端结果（断网时静默保留本地）
            List<UserEntity> matchedUsers = mergeRemoteUsers(database.userDao().searchUsersSync(keyword, 20));

            // 在后台线程转换笔记数据（包含数据库操作）
            List<GraphicCardBean> noteCards = convertToGraphicCardBeans(notes);

            // 合并后台搜索结果：远程笔记以 URL 主键去重后追加；断网时静默保留本地结果
            mergeRemoteResults(noteCards);

            // 记住合并结果，排序切换时直接重排，不再重新拉取

            // 转换商品数据
            List<GoodsBean> goodsList = new ArrayList<>();
            if (goods != null) {
                for (GoodsEntity goodsEntity : goods) {
                    GoodsBean goodsBean = new GoodsBean();
                    goodsBean.setId(goodsEntity.id);
                    goodsBean.setTitle(goodsEntity.title);
                    goodsBean.setImage(goodsEntity.image);
                    goodsBean.setPrice(goodsEntity.price);
                    goodsList.add(goodsBean);
                }
            }

            // 切换到主线程更新UI
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                mergedNoteCards = new ArrayList<>(noteCards);
                loading = false;
                searchMessage = "";
                applySort();
                goodsAdapter.updateData(goodsList);
                if (usersAdapter != null) usersAdapter.updateData(matchedUsers);
                updateEmptyState();
            });
          } catch (Exception error) {
            android.util.Log.e("SearchResultActivity", "Search failed", error);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                searchMessage = "搜索加载失败，点此重试";
                updateEmptyState();
            });
          }
        });
    }

    /**
     * 合并服务端用户搜索结果（GET /api/users/search）：
     * 按 id 去重追加到本地结果之后；断网时静默返回本地结果。
     * 远程头像为 URL 字符串，映射到 UserEntity.avatarUri。
     */
    private List<UserEntity> mergeRemoteUsers(List<UserEntity> local) {
        List<UserEntity> merged = new ArrayList<>(local == null ? new ArrayList<>() : local);
        try {
            java.util.List<com.google.gson.JsonObject> remote =
                    new com.xiaohongshu.network.RemoteApiClient(this)
                            .searchRemoteUsers(keyword, 20);
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (UserEntity user : merged) seen.add(user.id);
            for (com.google.gson.JsonObject item : remote) {
                if (item == null || !item.has("id")) continue;
                String id = item.get("id").getAsString();
                if (id == null || id.isEmpty() || seen.contains(id)) continue;
                UserEntity entity = new UserEntity();
                entity.id = id;
                entity.username = item.has("username") ? item.get("username").getAsString() : "";
                entity.nickname = item.has("nickname") ? item.get("nickname").getAsString() : "";
                entity.bio = item.has("bio") ? item.get("bio").getAsString() : "";
                if (item.has("avatar") && !item.get("avatar").isJsonNull()) {
                    String avatar = item.get("avatar").getAsString();
                    if (avatar != null && !avatar.isEmpty()) entity.avatarUri = avatar;
                }
                merged.add(entity);
            }
        } catch (Exception ignored) {
            // 后台离线：只展示本地搜索结果
        }
        return merged;
    }

    /**
     * 拉取后台 /api/posts?keyword= 结果并合并。
     * 后台不可达时静默跳过，本地 Room 结果仍是兜底数据源。
     * 除按笔记 id 去重外，还按「标题+作者」识别同一笔记的本地种子副本与服务端种子副本
     * （两者 id 不同、内容相同），命中时用服务端权威数据替换本地副本，避免搜索结果重复。
     */
    private void mergeRemoteResults(List<GraphicCardBean> cards) {
        try {
            com.xiaohongshu.bean.UserBean currentUser = com.xiaohongshu.ui.login.LoginDataRepository
                    .getInstance(this).getCurrentUser();
            String token = currentUser == null ? null : currentUser.getToken();
            List<com.xiaohongshu.network.RemotePost> remotePosts =
                    new com.xiaohongshu.network.RemoteApiClient(this).getPosts(1, 20, keyword, token);
            for (com.xiaohongshu.network.RemotePost remote : remotePosts) {
                String id = remote.getId();
                if (id == null || id.isEmpty()) continue;
                GraphicCardBean card = buildRemoteCard(remote);
                boolean replaced = false;
                for (int i = 0; i < cards.size(); i++) {
                    GraphicCardBean existing = cards.get(i);
                    if (id.equals(existing.getId()) || isSameNote(existing, card)) {
                        cards.set(i, card);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    cards.add(card);
                }
            }
        } catch (Exception ignored) {
            // 后台离线：只展示本地搜索结果
        }
    }

    private GraphicCardBean buildRemoteCard(com.xiaohongshu.network.RemotePost remote) {
        String id = remote.getId();
        GraphicCardBean card = new GraphicCardBean(
                id,
                remote.getTitle().isEmpty() ? firstContentLine(remote.getContent()) : remote.getTitle(),
                0, 0,
                new UserBean(remote.getAuthorId(), remote.getAuthorName(),
                        com.xiaohongshu.R.drawable.p1, null),
                remote.getLikeCount(), "video".equals(remote.getMediaType()) ? GraphicCardType.Video : GraphicCardType.Graphic);
        card.setVideoUri(remote.getVideoUrl());
        card.setContent(remote.getContent());
        card.setSortTime(remote.getCreatedAt());
        if (!remote.getImages().isEmpty()) {
            card.setImageUris(remote.getImages());
        } else {
            // 远程纯文字笔记：生成/复用仿真文字海报
            android.net.Uri poster = com.xiaohongshu.ui.publish.TextToImageConverter.createPosterForNote(
                    this, remote.getId(),
                    remote.getTitle().isEmpty() ? null : remote.getTitle(), remote.getContent());
            if (poster != null) card.setImageUri(poster.toString());
        }
        return card;
    }

    /** 同一笔记的不同副本：标题相同且作者相同（本地种子与服务端种子的 id 体系不同） */
    private boolean isSameNote(GraphicCardBean a, GraphicCardBean b) {
        if (a.getTitle() == null || b.getTitle() == null) return false;
        if (!a.getTitle().equals(b.getTitle())) return false;
        String authorA = a.getUser() == null ? null : a.getUser().getName();
        String authorB = b.getUser() == null ? null : b.getUser().getName();
        return authorA != null && authorA.equals(authorB);
    }
    
    private void switchTab(int tab) {
        notesRecyclerView.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        usersRecyclerView.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        goodsRecyclerView.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        findViewById(R.id.sortBar).setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        updateEmptyState();
    }

    /** 排序入口：更新高亮后重排列表 */
    private void applySort() {
        if (sortDefault != null) {
            sortDefault.setTextColor(getColor(currentSort == 0 ? R.color.xhs_red : R.color.text_secondary));
            sortLatest.setTextColor(getColor(currentSort == 1 ? R.color.xhs_red : R.color.text_secondary));
            sortHottest.setTextColor(getColor(currentSort == 2 ? R.color.xhs_red : R.color.text_secondary));
        }
        List<GraphicCardBean> cards = new ArrayList<>(mergedNoteCards);
        applySortOnCards(cards);
        notesAdapter.updateData(cards);
        updateEmptyState();
    }

    /** 综合 = 原始顺序（本地优先）；最新 = 创建时间倒序；最热 = 点赞数倒序 */
    private void applySortOnCards(List<GraphicCardBean> cards) {
        if (currentSort == 1) {
            java.util.Collections.sort(cards, (a, b) -> Long.compare(b.getSortTime(), a.getSortTime()));
        } else if (currentSort == 2) {
            java.util.Collections.sort(cards, (a, b) -> Integer.compare(b.getLikes(), a.getLikes()));
        }
    }
    
    private void updateEmptyState() {
        boolean isEmpty = false;
        if (currentTab == 0) {
            isEmpty = notesAdapter.getItemCount() == 0;
        } else if (currentTab == 1) {
            isEmpty = usersAdapter == null || usersAdapter.getItemCountSafe() == 0;
        } else {
            isEmpty = goodsAdapter.getItemCount() == 0;
        }

        if (emptyStateText != null) {
            emptyStateText.setVisibility(loading || !searchMessage.isEmpty() || isEmpty ? View.VISIBLE : View.GONE);
            String category = currentTab == 0 ? "笔记" : currentTab == 1 ? "用户" : "商品";
            emptyStateText.setText(!searchMessage.isEmpty() ? searchMessage : "没有找到相关" + category + "，试试其他关键词");
            emptyStateText.setOnClickListener(v -> { if (!loading && !searchMessage.isEmpty()) performSearch(); });
        }
    }

    /** 关注按钮使用：当前登录用户 id，未登录返回 null。 */
    private String getCurrentUserIdOrNull() {
        com.xiaohongshu.bean.UserBean currentUser =
                com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
        if (currentUser == null || currentUser.getUsername() == null) return null;
        UserEntity localUser = database.userDao().getUserByUsername(currentUser.getUsername());
        return localUser == null ? null : localUser.id;
    }
    
    private List<GraphicCardBean> convertToGraphicCardBeans(List<NoteEntity> notes) {
        List<GraphicCardBean> cards = new ArrayList<>();
        if (notes == null || notes.isEmpty()) {
            return cards;
        }
        
        for (NoteEntity note : notes) {
            if (note == null || note.id == null) {
                continue;
            }
            
            // 加载用户信息 - 每次都从数据库查询，确保获取最新的UserEntity
            UserEntity userEntity = null;
            if (note.userId != null && !note.userId.isEmpty()) {
                // 直接从数据库查询，不使用任何缓存，确保获取最新数据
                userEntity = database.userDao().getUserById(note.userId);
                
                if (userEntity == null) {
                    android.util.Log.w("SearchResultActivity", "User not found by ID: " + note.userId);
                }
            }
            
            UserBean userBean = null;
            if (userEntity != null) {
                // 使用与其他Repository一致的头像获取逻辑
                int avatarRes;
                if (userEntity.avatar > 0 && isP1ToP11Avatar(userEntity.avatar)) {
                    // 使用用户设置的头像（这是最新的头像）
                    avatarRes = userEntity.avatar;
                    android.util.Log.d("SearchResultActivity", "Using user avatar for userId " + note.userId + ": " + avatarRes);
                } else {
                    // 否则根据用户ID生成
                    String userId = userEntity.id;
                    if (userId != null && !userId.isEmpty()) {
                        int avatarIndex = userId.hashCode();
                        avatarRes = getAvatarResource(avatarIndex);
                    } else {
                        avatarRes = com.xiaohongshu.R.drawable.p1;
                    }
                }
                userBean = new UserBean(
                    userEntity.id,
                    userEntity.nickname != null ? userEntity.nickname : userEntity.username,
                    avatarRes,
                    null
                );
                if (userEntity.avatarUri != null && !userEntity.avatarUri.isEmpty()) userBean.setImageUri(userEntity.avatarUri);
            } else {
                // 如果用户不存在，创建一个默认用户
                String userId = note.userId != null ? note.userId : "unknown";
                String displayName = "用户" + (userId.length() > 6 ? userId.substring(0, 6) : userId);
                userBean = new UserBean(
                    userId,
                    displayName,
                    com.xiaohongshu.R.drawable.p1,
                    null
                );
            }
            
            // 获取点赞数
            int likes = 0;
            if (note.id != null && !note.id.isEmpty()) {
                likes = database.likeDao().getLikeCountSync(note.id);
            }
            
            // 获取图片资源：优先使用保存的imageUris，否则根据noteId生成
            int imageRes = getNoteImageResource(note);
            
            String displayTitle = note.title != null && !note.title.trim().isEmpty()
                    ? note.title : firstContentLine(note.content);
            GraphicCardBean card = new GraphicCardBean(
                note.id,
                displayTitle,
                imageRes,
                0,
                userBean,
                likes,
                GraphicCardType.Graphic
            );
            card.setContent(note.content);
            card.setSortTime(note.createTime);
            if (note.imageUris != null && !note.imageUris.isEmpty()) {
                card.setImageUris(note.imageUris);
            }
            cards.add(card);
        }
        
        return cards;
    }

    private String firstContentLine(String content) {
        if (content == null || content.trim().isEmpty()) return "无标题笔记";
        String firstLine = content.trim().split("\\R", 2)[0].trim();
        return firstLine.length() > 36 ? firstLine.substring(0, 36) + "…" : firstLine;
    }
    
    /**
     * 获取笔记图片资源：优先使用保存的imageUris，否则根据noteId生成
     */
    private int getNoteImageResource(NoteEntity note) {
        // 优先使用保存的imageUris中的第一张图片
        if (note.imageUris != null && !note.imageUris.isEmpty()) {
            String firstImageUri = note.imageUris.get(0);
            int resourceId = extractResourceIdFromUri(firstImageUri);
            if (resourceId != 0) {
                return resourceId;
            }
        }
        
        // 纯文字笔记由 TextToImageConverter 生成文字海报；未生成时保持无图状态。
        return 0;
    }
    
    /**
     * 从URI中提取资源ID
     * URI格式：android.resource://包名/资源ID
     */
    private int extractResourceIdFromUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return 0;
        }
        
        try {
            // 解析android.resource://格式的URI
            if (uri.startsWith("android.resource://")) {
                // 使用Uri类解析
                android.net.Uri parsedUri = android.net.Uri.parse(uri);
                String path = parsedUri.getPath();
                if (path != null && !path.isEmpty()) {
                    // 路径格式：/资源ID，需要去掉开头的"/"
                    String resourceIdStr = path.startsWith("/") ? path.substring(1) : path;
                    return Integer.parseInt(resourceIdStr);
                }
                
                // 如果Uri解析失败，尝试直接分割字符串
                String[] parts = uri.split("/");
                if (parts.length > 0) {
                    String resourceIdStr = parts[parts.length - 1];
                    // 确保是纯数字
                    if (resourceIdStr.matches("\\d+")) {
                        return Integer.parseInt(resourceIdStr);
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回0
            android.util.Log.e("SearchResultActivity", "Failed to extract resource ID from URI: " + uri, e);
        }
        
        return 0;
    }
    
    private int getImageResourceByIndex(int index) {
        switch (index) {
            case 1: return com.xiaohongshu.R.drawable.image_1;
            case 2: return com.xiaohongshu.R.drawable.image_2;
            case 3: return com.xiaohongshu.R.drawable.image_3;
            case 4: return com.xiaohongshu.R.drawable.image_4;
            case 5: return com.xiaohongshu.R.drawable.image_5;
            case 6: return com.xiaohongshu.R.drawable.image_6;
            case 7: return com.xiaohongshu.R.drawable.image_7;
            case 8: return com.xiaohongshu.R.drawable.image_8;
            case 9: return com.xiaohongshu.R.drawable.image_9;
            case 10: return com.xiaohongshu.R.drawable.image_10;
            case 11: return com.xiaohongshu.R.drawable.image_11;
            case 12: return com.xiaohongshu.R.drawable.image_12;
            case 13: return com.xiaohongshu.R.drawable.image_13;
            case 14: return com.xiaohongshu.R.drawable.image_14;
            case 15: return com.xiaohongshu.R.drawable.image_15;
            default: return com.xiaohongshu.R.drawable.image_1;
        }
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
        return avatars[Math.floorMod(index, avatars.length)];
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
