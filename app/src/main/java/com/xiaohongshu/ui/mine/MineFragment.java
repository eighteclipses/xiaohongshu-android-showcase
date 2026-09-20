package com.xiaohongshu.ui.mine;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.home.discovery.DiscoveryAdapter;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.mine.viewmodel.MineViewModel;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;

/**
 * 我的页面Fragment
 * 显示用户信息、笔记列表、收藏、点赞等
 */
public class MineFragment extends BaseFragment {
    private MineViewModel viewModel;
    private RecyclerView contentRecyclerView;
    private TabLayout tabLayout;
    private ImageView menuButton;
    private ImageView shareButton;
    private ImageView settingsButton;
    private TextView editProfileButton;
    private DiscoveryAdapter contentAdapter;
    private ImageView userAvatar;
    private TextView userName;
    private TextView publicNoteCountText;
    private TextView privateNoteCountText;
    private TextView collectionCountText;
    private TextView draftCardText;
    private TextView followingCount;
    private TextView followersCount;
    private TextView likesCount;
    private android.view.View draftCard;
    private TextView xiaohongshuId;
    private TextView ipLocationText;
    private int currentTab = 0; // 0: notes, 1: collections, 2: liked
    
    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_mine;
    }
    
    @Override
    protected void initViews(@NonNull View rootView) {
        contentRecyclerView = rootView.findViewById(R.id.contentRecyclerView);
        tabLayout = rootView.findViewById(R.id.tabLayout);
        // menuButton在include的mine_top_bar中，需要从include的view中查找
        View mineTopBar = rootView.findViewById(R.id.mineTopBar);
        if (mineTopBar != null) {
            menuButton = mineTopBar.findViewById(R.id.menuButton);
            shareButton = mineTopBar.findViewById(R.id.shareButton);
        } else {
            menuButton = rootView.findViewById(R.id.menuButton);
            shareButton = rootView.findViewById(R.id.shareButton);
        }
        settingsButton = rootView.findViewById(R.id.settingsButton);
        editProfileButton = rootView.findViewById(R.id.editProfileButton);
        
        // Get profile views
        userAvatar = rootView.findViewById(R.id.userAvatar);
        userName = rootView.findViewById(R.id.userName);
        xiaohongshuId = rootView.findViewById(R.id.xiaohongshuId);
        ipLocationText = rootView.findViewById(R.id.ipLocationText);
        likesCount = rootView.findViewById(R.id.likesCount);
        View bioHint = rootView.findViewById(R.id.bioHint);
        if (bioHint != null) {
            bioHint.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) EditProfileActivity.start(context);
            });
        }
        if (userAvatar != null) {
            userAvatar.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) EditProfileActivity.start(context);
            });
        }
        
        // Get note count views
        publicNoteCountText = rootView.findViewById(R.id.publicNoteCountText);
        privateNoteCountText = rootView.findViewById(R.id.privateNoteCountText);
        collectionCountText = rootView.findViewById(R.id.collectionCountText);
        draftCard = rootView.findViewById(R.id.draftCard);
        draftCardText = rootView.findViewById(R.id.draftCardText);
        
        // 草稿卡片点击
        if (draftCard != null) {
            draftCard.setClickable(true);
            draftCard.setFocusable(true);
            draftCard.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    com.xiaohongshu.ui.draft.LocalDraftActivity.start(context);
                }
            });
        }
        
        // Setup content grid
        if (getContext() != null) {
            contentRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        }
        contentAdapter = new DiscoveryAdapter(card -> {
            // 点击笔记卡片：视频进刷视频页，图文进详情页
            if (card != null && card.getId() != null) {
                if (card.needsReviewEdit()) {
                    android.content.Intent edit=new android.content.Intent(getContext(),com.xiaohongshu.ui.publish.NoteEditActivity.class);
                    edit.putExtra(com.xiaohongshu.ui.publish.NoteEditActivity.KEY_NOTE_ID,card.getId());startActivity(edit);return;
                }
                if (card.getType() == com.xiaohongshu.ui.home.bean.GraphicCardType.Video) {
                    com.xiaohongshu.activity.video.VideoActivity.newInstance(getContext(), card.getId());
                } else {
                    GraphicActivity.newInstance(getContext(), card.getId());
                }
            }
        });
        contentRecyclerView.setAdapter(contentAdapter);
        
        // Setup tabs
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
                }
            });
        }
        
        // 菜单按钮 - 显示底部弹出菜单
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> {
                if (getContext() != null) {
                    showMenuBottomSheet();
                }
            });
        }
        
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> {
                shareProfile();
            });
        }
        
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                com.xiaohongshu.ui.settings.SettingsActivity.start(getContext());
            });
        }
        
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    com.xiaohongshu.ui.mine.EditProfileActivity.start(context);
                }
            });
        }
        
        // 订单卡片点击
        View orderCard = rootView.findViewById(R.id.orderCard);
        if (orderCard != null) {
            orderCard.setClickable(true);
            orderCard.setFocusable(true);
            orderCard.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    com.xiaohongshu.ui.order.OrderListActivity.start(context);
                }
            });
        }
        
        // 购物车卡片点击
        View shoppingCartCard = rootView.findViewById(R.id.shoppingCartCard);
        if (shoppingCartCard != null) {
            shoppingCartCard.setClickable(true);
            shoppingCartCard.setFocusable(true);
            shoppingCartCard.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    com.xiaohongshu.ui.cart.ShoppingCartActivity.start(context);
                }
            });
        }
        
        // 浏览历史卡片点击
        View browsingHistoryCard = rootView.findViewById(R.id.browsingHistoryCard);
        if (browsingHistoryCard != null) {
            browsingHistoryCard.setClickable(true);
            browsingHistoryCard.setFocusable(true);
            browsingHistoryCard.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) {
                    com.xiaohongshu.ui.history.BrowsingHistoryActivity.start(context);
                }
            });
        }
        
        // 关注按钮点击
        followingCount = rootView.findViewById(R.id.followingCount);
        if (followingCount != null) {
            followingCount.setOnClickListener(v -> {
                // 跳转到关注列表页面
                try {
                    android.app.Activity activity = getActivity();
                    if (activity != null) {
                        FollowListActivity.start(activity);
                    } else {
                        Context context = getContext();
                        if (context != null) {
                            FollowListActivity.start(context);
                        } else {
                            android.util.Log.e("MineFragment", "Cannot start FollowListActivity: both activity and context are null");
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("MineFragment", "Error starting FollowListActivity", e);
                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(), "打开关注列表失败", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        // 粉丝按钮点击
        followersCount = rootView.findViewById(R.id.followersCount);
        if (followersCount != null) {
            followersCount.setOnClickListener(v -> {
                Context context = getContext();
                if (context != null) FollowListActivity.start(context, true);
            });
        }
        
        // 公开笔记按钮点击
        if (publicNoteCountText != null) {
            publicNoteCountText.setClickable(true);
            publicNoteCountText.setFocusable(true);
            publicNoteCountText.setOnClickListener(v -> {
                // 切换到笔记标签并加载公开笔记
                if (tabLayout != null && tabLayout.getTabCount() > 0) {
                    tabLayout.getTabAt(0).select();
                }
                viewModel.loadNotes(1); // 1表示公开笔记
            });
        }
        
        // 私密笔记按钮点击
        if (privateNoteCountText != null) {
            privateNoteCountText.setClickable(true);
            privateNoteCountText.setFocusable(true);
            privateNoteCountText.setOnClickListener(v -> {
                // 切换到笔记标签并加载私密笔记
                if (tabLayout != null && tabLayout.getTabCount() > 0) {
                    tabLayout.getTabAt(0).select();
                }
                viewModel.loadNotes(2); // 2表示私密笔记
            });
        }

        if (collectionCountText != null) {
            collectionCountText.setClickable(true);
            collectionCountText.setFocusable(true);
            collectionCountText.setOnClickListener(v -> {
                if (tabLayout != null && tabLayout.getTabCount() > 1) tabLayout.getTabAt(1).select();
                if (viewModel != null) viewModel.loadCollectedNotes();
            });
        }
    }
    
    @Override
    protected void initData() {
        if (getActivity() == null || getActivity().getApplication() == null) {
            return;
        }
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getActivity().getApplication())).get(MineViewModel.class);
        
        // 观察用户信息
        viewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                if (userName != null) {
                    userName.setText(user.getName());
                }
                if (userAvatar != null) {
                    if (!user.getImageUri().trim().isEmpty()) {
                        // 统一走 Coil：http/content/file 都能加载，失败显示占位圆而非空白
                        ImageLoader.load(userAvatar, user.getImageUri(), R.drawable.placeholder_avatar);
                        userAvatar.clearColorFilter();
                        return;
                    }
                    // 如果user.getImage()是p1-p11中的一个，就使用它；否则根据用户ID生成
                    int avatarRes;
                    int userImage = user.getImage();
                    if (userImage > 0 && isP1ToP11Avatar(userImage)) {
                        avatarRes = userImage;
                    } else {
                        String userId = user.getId();
                        if (userId != null && !userId.isEmpty()) {
                            // 根据用户ID生成固定的头像索引，确保同一用户总是显示相同的头像
                            int avatarIndex = userId.hashCode();
                            avatarRes = getAvatarResource(avatarIndex);
                        } else {
                            // 如果用户ID为空，使用默认头像
                            avatarRes = com.xiaohongshu.R.drawable.p1;
                        }
                    }
                    userAvatar.setImageResource(avatarRes);
                    userAvatar.clearColorFilter();
                }
            }
        });
        
        // 观察笔记列表
        viewModel.getGraphicCardList().observe(getViewLifecycleOwner(), cards -> {
            if (cards != null) {
                contentAdapter.updateData(cards);
            }
        });
        
        // 观察笔记统计信息
        viewModel.getPublicNoteCount().observe(getViewLifecycleOwner(), count -> {
            if (publicNoteCountText != null && count != null) {
                publicNoteCountText.setText("公开 " + count);
            }
        });
        
        viewModel.getPrivateNoteCount().observe(getViewLifecycleOwner(), count -> {
            if (privateNoteCountText != null && count != null) {
                privateNoteCountText.setText("私密 " + count);
            }
        });
        
        viewModel.getDraftCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && count > 0) {
                if (draftCard != null) {
                    draftCard.setVisibility(android.view.View.VISIBLE);
                }
                if (draftCardText != null) {
                    draftCardText.setText("有" + count + "篇笔记待发布 >");
                }
            } else {
                if (draftCard != null) {
                    draftCard.setVisibility(android.view.View.GONE);
                }
            }
        });
        
        viewModel.getCollectionCount().observe(getViewLifecycleOwner(), count -> {
            if (collectionCountText != null && count != null) collectionCountText.setText("合集 " + count);
        });
        
        // 观察关注和粉丝数量：标签由布局固定文案承担，这里只填数字
        viewModel.getFollowingCount().observe(getViewLifecycleOwner(), count -> {
            if (followingCount != null && count != null) {
                followingCount.setText(String.valueOf(count));
            }
        });

        viewModel.getFollowersCount().observe(getViewLifecycleOwner(), count -> {
            if (followersCount != null && count != null) {
                followersCount.setText(String.valueOf(count));
            }
        });

        viewModel.getLikesCount().observe(getViewLifecycleOwner(), count -> {
            if (likesCount != null && count != null) {
                likesCount.setText(String.valueOf(count));
            }
        });
        
        // 加载数据
        viewModel.load();
        loadContentForTab(0);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 从编辑资料页面返回时，重新加载所有数据以更新头像和笔记列表
        // 直接调用，避免不必要的延迟
        if (viewModel != null) {
            viewModel.load(); // 重新加载所有数据，包括笔记列表
            // 同时刷新当前标签页的内容
            loadContentForTab(currentTab);
        }
    }
    
    /**
     * 根据标签页加载不同的内容
     */
    private void loadContentForTab(int tab) {
        switch (tab) {
            case 0: // 笔记
                viewModel.loadNotes(1); // 加载公开笔记
                break;
            case 1: // 收藏
                viewModel.loadCollectedNotes();
                break;
            case 2: // 赞过
                viewModel.loadLikedNotes();
                break;
            default:
                viewModel.loadNotes(1); // 默认加载公开笔记
                break;
        }
    }
    
    /**
     * 显示底部弹出菜单
     */
    private void showMenuBottomSheet() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        
        try {
            com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
                new com.google.android.material.bottomsheet.BottomSheetDialog(context);
            View menuView = getLayoutInflater().inflate(R.layout.menu_mine_bottom_sheet, null);
            bottomSheetDialog.setContentView(menuView);
        
        // 设置菜单项点击事件
        View menuScan = menuView.findViewById(R.id.menuScan);
        if (menuScan != null) {
            menuScan.setOnClickListener(v -> {
                com.xiaohongshu.ui.scan.ScanActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuDiscoverFriends = menuView.findViewById(R.id.menuDiscoverFriends);
        if (menuDiscoverFriends != null) {
            menuDiscoverFriends.setOnClickListener(v -> {
                if (context != null && getActivity() != null && !getActivity().isFinishing() && !getActivity().isDestroyed()) {
                    try {
                        com.xiaohongshu.ui.discover.DiscoverFriendsActivity.start(context);
                        bottomSheetDialog.dismiss();
                    } catch (Exception e) {
                        android.util.Log.e("MineFragment", "Error starting DiscoverFriendsActivity", e);
                        android.widget.Toast.makeText(context, "无法打开发现好友页面", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        View menuMyComments = menuView.findViewById(R.id.menuMyComments);
        if (menuMyComments != null) {
            menuMyComments.setOnClickListener(v -> {
                com.xiaohongshu.ui.comments.MyCommentsActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuBrowsingHistory = menuView.findViewById(R.id.menuBrowsingHistory);
        if (menuBrowsingHistory != null) {
            menuBrowsingHistory.setOnClickListener(v -> {
                com.xiaohongshu.ui.history.BrowsingHistoryActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuOrders = menuView.findViewById(R.id.menuOrders);
        if (menuOrders != null) {
            menuOrders.setOnClickListener(v -> {
                com.xiaohongshu.ui.order.OrderListActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuShoppingCart = menuView.findViewById(R.id.menuShoppingCart);
        if (menuShoppingCart != null) {
            menuShoppingCart.setOnClickListener(v -> {
                com.xiaohongshu.ui.cart.ShoppingCartActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuWallet = menuView.findViewById(R.id.menuWallet);
        if (menuWallet != null) {
            menuWallet.setOnClickListener(v -> {
                com.xiaohongshu.ui.wallet.WalletActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuHelp = menuView.findViewById(R.id.menuHelp);
        if (menuHelp != null) {
            menuHelp.setOnClickListener(v -> {
                com.xiaohongshu.ui.help.HelpActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
        View menuSettings = menuView.findViewById(R.id.menuSettings);
        if (menuSettings != null) {
            menuSettings.setOnClickListener(v -> {
                com.xiaohongshu.ui.settings.SettingsActivity.start(context);
                bottomSheetDialog.dismiss();
            });
        }
        
            bottomSheetDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            if (context != null) {
                android.widget.Toast.makeText(context, "打开菜单失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * 分享用户资料
     */
    private void shareProfile() {
        com.xiaohongshu.bean.UserBean currentUser = 
            com.xiaohongshu.ui.login.LoginDataRepository.getInstance(getContext()).getCurrentUser();
        if (currentUser == null) {
            android.widget.Toast.makeText(getContext(), "用户信息不存在", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        String shareText = "我在小红书，快来关注我吧！\n用户名：" + currentUser.getUsername();
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "分享我的小红书主页");
        
        try {
            startActivity(Intent.createChooser(shareIntent, "分享到"));
        } catch (Exception e) {
            android.widget.Toast.makeText(getContext(), "分享失败", android.widget.Toast.LENGTH_SHORT).show();
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
}
