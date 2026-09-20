package com.xiaohongshu.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.home.city.CityFragment;
import com.xiaohongshu.ui.home.discovery.DiscoveryFragment;
import com.xiaohongshu.ui.home.follow.FollowPageFragment;
import com.xiaohongshu.ui.search.SearchActivity;

public class HomeFragment extends BaseFragment {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ImageView menuButton;
    private ImageView searchButton;
    private DrawerLayout drawerLayout;
    private HomePagerAdapter pagerAdapter;
    private View discoverFriends;
    private View myDrafts;
    private View myComments;
    private View browsingHistory;
    private View orders;
    private View shoppingCart;
    private View wallet;
    private View communityGuidelines;
    private View scanButton;
    private View helpButton;
    private View settingsButton;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_home;
    }

    @Override
    protected void initViews(@NonNull View rootView) {
        viewPager = rootView.findViewById(R.id.viewPager);
        tabLayout = rootView.findViewById(R.id.tabLayout);
        menuButton = rootView.findViewById(R.id.menuButton);
        searchButton = rootView.findViewById(R.id.searchButton);
        drawerLayout = rootView.findViewById(R.id.drawerLayout);

        // Setup ViewPager
        pagerAdapter = new HomePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setCurrentItem(1, false); // Start at discovery page

        // Setup TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.home_follow);
                    break;
                case 1:
                    tab.setText(R.string.home_discovery);
                    break;
                case 2:
                    tab.setText(R.string.home_city);
                    break;
            }
        }).attach();

        // Setup menu button - open drawer
        if (menuButton != null) {
            menuButton.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(android.view.Gravity.START);
                }
            });
        }

        // Setup search button
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                startActivity(new Intent(getContext(), SearchActivity.class));
            });
        }

        // Setup drawer menu items
        View drawerContent = rootView.findViewById(R.id.drawerContent);
        if (drawerContent != null) {
            // Handle window insets for drawer menu
            ViewCompat.setOnApplyWindowInsetsListener(drawerContent, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                return insets;
            });
            
            discoverFriends = drawerContent.findViewById(R.id.discoverFriends);
            myDrafts = drawerContent.findViewById(R.id.myDrafts);
            myComments = drawerContent.findViewById(R.id.myComments);
            browsingHistory = drawerContent.findViewById(R.id.browsingHistory);
            orders = drawerContent.findViewById(R.id.orders);
            shoppingCart = drawerContent.findViewById(R.id.shoppingCart);
            wallet = drawerContent.findViewById(R.id.wallet);
            communityGuidelines = drawerContent.findViewById(R.id.communityGuidelines);
            scanButton = drawerContent.findViewById(R.id.scanButton);
            helpButton = drawerContent.findViewById(R.id.helpButton);
            settingsButton = drawerContent.findViewById(R.id.settingsButton);

            setupDrawerMenuListeners();
        }
    }

    /**
     * 设置抽屉菜单监听器
     */
    private void setupDrawerMenuListeners() {
        // 发现好友
        if (discoverFriends != null) {
            discoverFriends.setOnClickListener(v -> {
                android.content.Context context = getContext();
                if (context != null && getActivity() != null && !getActivity().isFinishing() && !getActivity().isDestroyed()) {
                    try {
                        com.xiaohongshu.ui.discover.DiscoverFriendsActivity.start(context);
                        if (drawerLayout != null) {
                            drawerLayout.closeDrawer(android.view.Gravity.START);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("HomeFragment", "Error starting DiscoverFriendsActivity", e);
                        android.widget.Toast.makeText(context, "无法打开发现好友页面", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        // 我的草稿 - 直接跳转到草稿箱
        if (myDrafts != null) {
            myDrafts.setOnClickListener(v -> {
                com.xiaohongshu.ui.draft.LocalDraftActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 我的评论
        if (myComments != null) {
            myComments.setOnClickListener(v -> {
                com.xiaohongshu.ui.comments.MyCommentsActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 浏览历史
        if (browsingHistory != null) {
            browsingHistory.setOnClickListener(v -> {
                com.xiaohongshu.ui.history.BrowsingHistoryActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 订单
        if (orders != null) {
            orders.setOnClickListener(v -> {
                com.xiaohongshu.ui.order.OrderListActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 购物车
        if (shoppingCart != null) {
            shoppingCart.setOnClickListener(v -> {
                com.xiaohongshu.ui.cart.ShoppingCartActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 钱包
        if (wallet != null) {
            wallet.setOnClickListener(v -> {
                com.xiaohongshu.ui.wallet.WalletActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 社区规范
        if (communityGuidelines != null) {
            communityGuidelines.setOnClickListener(v -> {
                com.xiaohongshu.ui.guidelines.CommunityGuidelinesActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 扫一扫
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> {
                com.xiaohongshu.ui.scan.ScanActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 帮助与反馈
        if (helpButton != null) {
            helpButton.setOnClickListener(v -> {
                com.xiaohongshu.ui.help.HelpActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // 设置
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                com.xiaohongshu.ui.settings.SettingsActivity.start(getContext());
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }
    }

    @Override
    protected void initData() {
        // Load data if needed
    }
}
