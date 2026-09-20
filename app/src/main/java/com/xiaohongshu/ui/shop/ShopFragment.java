package com.xiaohongshu.ui.shop;

import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseFragment;
import com.xiaohongshu.ui.shop.viewmodel.ShopViewModel;

public class ShopFragment extends BaseFragment {
    private ShopViewModel viewModel;
    private RecyclerView menuRecyclerView;
    private RecyclerView goodsRecyclerView;
    private ShopMenuAdapter menuAdapter;
    private ShopGoodsAdapter goodsAdapter;

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_shop;
    }

    @Override
    protected void initViews(@NonNull View rootView) {
        goodsRecyclerView = rootView.findViewById(R.id.goodsRecyclerView);
        
        // menuRecyclerView、searchLayout 和 moreButton 在 include 的 shopTopBar 中
        View shopTopBar = rootView.findViewById(R.id.shopTopBar);
        if (shopTopBar != null) {
            menuRecyclerView = shopTopBar.findViewById(R.id.menuRecyclerView);
            
            // 搜索栏点击
            android.view.View searchLayout = shopTopBar.findViewById(R.id.searchLayout);
            if (searchLayout != null) {
                searchLayout.setOnClickListener(v -> {
                    com.xiaohongshu.ui.search.SearchActivity.start(getContext());
                });
            }
            
            // 更多按钮
            android.view.View moreButton = shopTopBar.findViewById(R.id.moreButton);
            if (moreButton != null) {
                moreButton.setOnClickListener(this::showMoreMenu);
            }
        } else {
            // 如果找不到 shopTopBar，尝试直接从 rootView 查找（向后兼容）
            menuRecyclerView = rootView.findViewById(R.id.menuRecyclerView);
            android.view.View searchLayout = rootView.findViewById(R.id.searchLayout);
            if (searchLayout != null) {
                searchLayout.setOnClickListener(v -> {
                    com.xiaohongshu.ui.search.SearchActivity.start(getContext());
                });
            }
            android.view.View moreButton = rootView.findViewById(R.id.moreButton);
            if (moreButton != null) {
                moreButton.setOnClickListener(this::showMoreMenu);
            }
        }
    }

    @Override
    protected void initData() {
        viewModel = new ViewModelProvider(this).get(ShopViewModel.class);
        viewModel.init(getContext());
        
        // 初始化菜单 RecyclerView
        if (menuRecyclerView != null) {
            menuRecyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
            menuAdapter = new ShopMenuAdapter(viewModel.getMenuList());
            menuRecyclerView.setAdapter(menuAdapter);
        }
        
        // 菜单项点击事件
        if (menuAdapter != null) {
            menuAdapter.setOnItemClickListener(menu -> {
            String title = menu.getTitle();
            if ("我的订单".equals(title)) {
                // 跳转到订单列表
                com.xiaohongshu.ui.order.OrderListActivity.start(getContext());
            } else if ("购物车".equals(title)) {
                // 跳转到购物车
                com.xiaohongshu.ui.cart.ShoppingCartActivity.start(getContext());
            } else if ("客服消息".equals(title)) {
                com.xiaohongshu.ui.help.HelpActivity.start(getContext());
            } else if ("卡券".equals(title)) {
                com.xiaohongshu.ui.wallet.WalletActivity.start(getContext());
            } else if ("浏览记录".equals(title)) {
                // 跳转到浏览历史
                com.xiaohongshu.ui.history.BrowsingHistoryActivity.start(getContext());
            } else if ("关注店铺".equals(title)) {
                com.xiaohongshu.ui.common.InfoActivity.start(getContext(), "关注店铺", "你关注的店铺会显示在这里。当前为学习版演示页面。");
            } else if ("心愿单".equals(title)) {
                com.xiaohongshu.ui.common.InfoActivity.start(getContext(), "心愿单", "把喜欢的商品加入心愿单，之后可以在这里统一查看。");
            } else {
                android.widget.Toast.makeText(getContext(), title, android.widget.Toast.LENGTH_SHORT).show();
            }
            });
        }
        
        goodsAdapter = new ShopGoodsAdapter(goods -> {
            // 点击商品跳转到商品详情页
            if (goods != null && goods.getId() != null) {
                com.xiaohongshu.ui.goods.GoodsDetailActivity.start(getContext(), goods.getId());
            }
        });
        
        // 创建Header View（包含Banner）
        // 注意：使用null作为parent，避免view已经有parent导致的问题
        View headerView = LayoutInflater.from(getContext()).inflate(R.layout.shop_header_content, null);
        
        // Setup banners in header
        View liveBanner = headerView.findViewById(R.id.liveBanner);
        View priceBanner = headerView.findViewById(R.id.priceBanner);
        if (liveBanner != null) {
            android.widget.TextView title = liveBanner.findViewById(R.id.bannerTitle);
            android.widget.TextView subtitle = liveBanner.findViewById(R.id.bannerSubtitle);
            if (title != null) title.setText(R.string.live_selection);
            if (subtitle != null) subtitle.setText(R.string.heart_moving_goodies);
            
            liveBanner.setOnClickListener(v -> {
                if (title != null && subtitle != null) {
                    String currentTitle = title.getText().toString();
                    if (currentTitle.equals("直播精选")) {
                        title.setText("心动好物");
                        subtitle.setText("直播精选");
                    } else {
                        title.setText("直播精选");
                        subtitle.setText("心动好物");
                    }
                }
            });
        }
        if (priceBanner != null) {
            android.widget.TextView title = priceBanner.findViewById(R.id.bannerTitle);
            android.widget.TextView subtitle = priceBanner.findViewById(R.id.bannerSubtitle);
            if (title != null) title.setText(R.string.no_price_comparison);
            if (subtitle != null) subtitle.setText(R.string.price_match_guarantee);
            
            priceBanner.setOnClickListener(v -> {
                if (title != null && subtitle != null) {
                    String currentTitle = title.getText().toString();
                    if (currentTitle.equals("不用比价")) {
                        title.setText("买贵必陪");
                        subtitle.setText("不用比价");
                    } else {
                        title.setText("不用比价");
                        subtitle.setText("买贵必陪");
                    }
                }
            });
        }
        
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        goodsRecyclerView.setLayoutManager(gridLayoutManager);
        goodsRecyclerView.setAdapter(goodsAdapter);
        
        // 设置header view（在adapter设置之后）
        goodsAdapter.setHeaderView(headerView);
        
        // 设置span size，让header占据整行（在adapter设置之后）
        final ShopGoodsAdapter finalAdapter = goodsAdapter;
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (finalAdapter == null) {
                    return 1;
                }
                try {
                    int viewType = finalAdapter.getItemViewType(position);
                    return viewType == ShopGoodsAdapter.TYPE_HEADER ? 2 : 1;
                } catch (Exception e) {
                    // 如果获取viewType失败，默认返回1
                    return 1;
                }
            }
        });
        
        viewModel.getGoodsList().observe(getViewLifecycleOwner(), goods -> {
            if (goods != null && goodsAdapter != null) {
                android.util.Log.d("ShopFragment", "Observed goods count: " + goods.size());
                // 确保传递所有商品，不进行任何截取
                goodsAdapter.updateData(goods);
                // 确保RecyclerView重新测量和布局
                if (goodsRecyclerView != null) {
                    goodsRecyclerView.post(() -> {
                        if (goodsRecyclerView.getAdapter() != null) {
                            goodsRecyclerView.getAdapter().notifyDataSetChanged();
                        }
                        goodsRecyclerView.invalidate();
                        goodsRecyclerView.requestLayout();
                        android.util.Log.d("ShopFragment", "RecyclerView updated, adapter itemCount: " + (goodsRecyclerView.getAdapter() != null ? goodsRecyclerView.getAdapter().getItemCount() : 0));
                    });
                }
            } else {
                android.util.Log.w("ShopFragment", "Goods list is null or adapter is null");
            }
        });
        
        viewModel.load();
    }

    private void showMoreMenu(View anchor) {
        if (getContext() == null) return;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.my_orders);
        menu.getMenu().add(0, 2, 1, R.string.shopping_cart);
        menu.getMenu().add(0, 3, 2, R.string.browsing_history);
        menu.getMenu().add(0, 4, 3, R.string.settings);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: com.xiaohongshu.ui.order.OrderListActivity.start(getContext()); break;
                case 2: com.xiaohongshu.ui.cart.ShoppingCartActivity.start(getContext()); break;
                case 3: com.xiaohongshu.ui.history.BrowsingHistoryActivity.start(getContext()); break;
                case 4: com.xiaohongshu.ui.settings.SettingsActivity.start(getContext()); break;
            }
            return true;
        });
        menu.show();
    }
}
