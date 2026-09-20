package com.xiaohongshu.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.goods.GoodsDetailActivity;
import com.xiaohongshu.ui.order.viewmodel.OrderListViewModel;
import com.xiaohongshu.ui.shop.ShopDataRepository;
import com.xiaohongshu.ui.shop.ShopGoodsAdapter;
import com.xiaohongshu.ui.shop.bean.GoodsBean;

import java.util.List;

/**
 * 订单列表Activity
 * 显示所有订单，支持按状态筛选
 */
public class OrderListActivity extends BaseActivity {
    private OrderListViewModel viewModel;
    private RecyclerView orderRecyclerView;
    private RecyclerView recommendRecyclerView;
    private OrderAdapter orderAdapter;
    private ShopGoodsAdapter recommendAdapter;
    private TabLayout statusTabLayout;
    private View emptyStateLayout;
    private View divider;
    private TextView recommendTitle;
    private ShopDataRepository shopDataRepository;
    private int currentStatus = -1; // -1表示全部
    
    public static void start(android.content.Context context) {
        Intent intent = new Intent(context, OrderListActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_list);
        
        shopDataRepository = ShopDataRepository.getInstance(this);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(OrderListViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        orderRecyclerView = findViewById(R.id.orderRecyclerView);
        recommendRecyclerView = findViewById(R.id.recommendRecyclerView);
        statusTabLayout = findViewById(R.id.statusTabLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        divider = findViewById(R.id.divider);
        recommendTitle = findViewById(R.id.recommendTitle);
        
        orderRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        orderAdapter = new OrderAdapter(order -> {
            // 点击订单查看详情
            if (order != null && order.order != null) {
                OrderDetailActivity.start(this, order.order.id);
            }
        });
        orderRecyclerView.setAdapter(orderAdapter);
        
        // 推荐商品网格
        recommendRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recommendAdapter = new ShopGoodsAdapter(goods -> {
            // 点击推荐商品跳转到详情页
            if (goods != null && goods.getId() != null) {
                GoodsDetailActivity.start(this, goods.getId());
            }
        });
        recommendRecyclerView.setAdapter(recommendAdapter);
        
        // 搜索栏
        View searchBar = findViewById(R.id.searchBar);
        if (searchBar != null) {
            searchBar.setOnClickListener(v -> {
                com.xiaohongshu.ui.search.SearchActivity.start(this);
            });
        }
        
        // 更多按钮
        ImageView moreButton = findViewById(R.id.moreButton);
        if (moreButton != null) {
            moreButton.setOnClickListener(v -> {
                android.widget.PopupMenu menu = new android.widget.PopupMenu(this, moreButton);
                menu.getMenu().add(0, 1, 0, "收货地址");
                menu.getMenu().add(0, 2, 1, "联系客服");
                menu.getMenu().add(0, 3, 2, "清空筛选");
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        AddressListActivity.start(this);
                    } else if (item.getItemId() == 2) {
                        com.xiaohongshu.ui.help.HelpActivity.start(this);
                    } else {
                        if (statusTabLayout != null && statusTabLayout.getTabCount() > 0) {
                            statusTabLayout.getTabAt(0).select();
                        }
                    }
                    return true;
                });
                menu.show();
            });
        }
        
        // 状态标签页
        if (statusTabLayout != null) {
            statusTabLayout.addTab(statusTabLayout.newTab().setText("全部"));
            statusTabLayout.addTab(statusTabLayout.newTab().setText("待付款"));
            statusTabLayout.addTab(statusTabLayout.newTab().setText("待发货"));
            statusTabLayout.addTab(statusTabLayout.newTab().setText("待收货/使用"));
            statusTabLayout.addTab(statusTabLayout.newTab().setText("评价"));
            statusTabLayout.addTab(statusTabLayout.newTab().setText("售后"));
            
            statusTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    int position = tab.getPosition();
                    switch (position) {
                        case 0:
                            currentStatus = -1; // 全部
                            break;
                        case 1:
                            currentStatus = 0; // 待付款
                            break;
                        case 2:
                            currentStatus = 1; // 待发货
                            break;
                        case 3:
                            currentStatus = 2; // 待收货/使用
                            break;
                        case 4:
                            currentStatus = 3; // 评价
                            break;
                        case 5:
                            currentStatus = 4; // 售后
                            break;
                    }
                    viewModel.loadOrders(currentStatus);
                }
                
                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }
                
                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 隐藏标题（订单列表页面已有搜索栏和标签页，不需要显示标题）
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setVisibility(View.GONE);
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getOrders().observe(this, orders -> {
            if (orders != null) {
                boolean isEmpty = orders.isEmpty();
                
                // 显示/隐藏空状态
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }
                if (orderRecyclerView != null) {
                    orderRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                }
                
                // 显示/隐藏推荐商品
                if (isEmpty) {
                    if (divider != null) divider.setVisibility(View.VISIBLE);
                    if (recommendTitle != null) recommendTitle.setVisibility(View.VISIBLE);
                    if (recommendRecyclerView != null) recommendRecyclerView.setVisibility(View.VISIBLE);
                    loadRecommendedGoods();
                } else {
                    if (divider != null) divider.setVisibility(View.GONE);
                    if (recommendTitle != null) recommendTitle.setVisibility(View.GONE);
                    if (recommendRecyclerView != null) recommendRecyclerView.setVisibility(View.GONE);
                }
                
                orderAdapter.updateData(orders);
            }
        });
        
        viewModel.loadOrders(currentStatus);
    }
    
    /**
     * 加载推荐商品
     */
    private void loadRecommendedGoods() {
        shopDataRepository.getGoodsList(new ShopDataRepository.DataCallback<List<GoodsBean>>() {
            @Override
            public void onSuccess(List<GoodsBean> data) {
                // 只显示前4个商品
                if (data != null && data.size() > 0) {
                    int count = Math.min(4, data.size());
                    List<GoodsBean> recommended = data.subList(0, count);
                    recommendAdapter.updateData(recommended);
                }
            }
            
            @Override
            public void onError(Exception e) {
                // 忽略错误
            }
        });
    }
}
