package com.xiaohongshu.ui.cart;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.ui.cart.viewmodel.ShoppingCartViewModel;
import com.xiaohongshu.ui.goods.GoodsDetailActivity;
import com.xiaohongshu.ui.order.OrderConfirmActivity;
import com.xiaohongshu.ui.shop.ShopGoodsAdapter;
import com.xiaohongshu.ui.shop.bean.GoodsBean;
import com.xiaohongshu.ui.shop.ShopDataRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车Activity
 * 显示购物车商品列表，支持修改数量、删除商品、结算
 */
public class ShoppingCartActivity extends BaseActivity {
    private ShoppingCartViewModel viewModel;
    private RecyclerView cartRecyclerView;
    private RecyclerView recommendRecyclerView;
    private CartAdapter cartAdapter;
    private ShopGoodsAdapter recommendAdapter;
    private TextView totalPriceText;
    private Button checkoutButton;
    private CheckBox selectAllCheckBox;
    private View emptyStateLayout;
    private View divider;
    private TextView recommendTitle;
    private AppDatabase database;
    private ShopDataRepository shopDataRepository;
    
    public static void start(android.content.Context context) {
        Intent intent = new Intent(context, ShoppingCartActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_cart);
        
        database = AppApplication.getDatabase();
        shopDataRepository = ShopDataRepository.getInstance(this);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(ShoppingCartViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("购物车");
        }
        
        cartRecyclerView = findViewById(R.id.cartRecyclerView);
        recommendRecyclerView = findViewById(R.id.recommendRecyclerView);
        totalPriceText = findViewById(R.id.totalPriceText);
        checkoutButton = findViewById(R.id.checkoutButton);
        selectAllCheckBox = findViewById(R.id.selectAllCheckBox);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        divider = findViewById(R.id.divider);
        recommendTitle = findViewById(R.id.recommendTitle);
        
        // 购物车列表
        cartRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(
            (cartItem, newQuantity) -> {
                // 更新数量
                viewModel.updateQuantity(cartItem.cart.goodsId, newQuantity);
                // 立即更新总价（乐观更新）
                updateTotalPrice();
            },
            goodsId -> {
                // 删除商品
                viewModel.removeFromCart(goodsId);
            }
        );
        cartAdapter.setOnSelectionChangeListener(goodsId -> {
            viewModel.toggleSelection(goodsId);
            updateTotalPrice();
            updateSelectAllCheckBox();
        });
        cartRecyclerView.setAdapter(cartAdapter);
        
        // 推荐商品网格
        recommendRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recommendAdapter = new ShopGoodsAdapter(goods -> {
            // 点击推荐商品跳转到详情页
            if (goods != null && goods.getId() != null) {
                GoodsDetailActivity.start(this, goods.getId());
            }
        });
        recommendRecyclerView.setAdapter(recommendAdapter);
        
        // 标签页
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        viewModel.load();
                        return;
                    }
                    com.xiaohongshu.ui.common.InfoActivity.start(ShoppingCartActivity.this,
                            "心愿单", "心愿单会保存你标记的商品。当前购物车商品仍可正常结算。");
                }
                
                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }
                
                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            });
        }
        
        // 管理按钮
        TextView manageButton = findViewById(R.id.manageButton);
        if (manageButton != null) {
            manageButton.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("管理购物车")
                        .setItems(new String[]{"删除已选商品", "取消"}, (dialog, which) -> {
                            if (which == 0) {
                                for (ShoppingCartViewModel.CartItem item : viewModel.getSelectedItems()) {
                                    viewModel.removeFromCart(item.cart.goodsId);
                                }
                            }
                        }).show();
            });
        }
        
        // 全选复选框
        if (selectAllCheckBox != null) {
            selectAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.selectAll(isChecked);
                updateTotalPrice();
            });
        }
        
        // 结算按钮
        if (checkoutButton != null) {
            checkoutButton.setOnClickListener(v -> checkout());
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getCartItems().observe(this, items -> {
            if (items != null) {
                boolean isEmpty = items.isEmpty();
                
                // 显示/隐藏空状态
                if (emptyStateLayout != null) {
                    emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }
                if (cartRecyclerView != null) {
                    cartRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
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
                
                cartAdapter.updateData(items);
                updateTotalPrice();
                updateSelectAllCheckBox();
            }
        });
        
        viewModel.load();
    }
    
    /**
     * 更新全选复选框状态
     */
    private void updateSelectAllCheckBox() {
        if (selectAllCheckBox != null) {
            // 移除监听器避免循环调用
            selectAllCheckBox.setOnCheckedChangeListener(null);
            selectAllCheckBox.setChecked(viewModel.isAllSelected());
            selectAllCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.selectAll(isChecked);
                updateTotalPrice();
            });
        }
    }
    
    /**
     * 加载推荐商品
     */
    private void loadRecommendedGoods() {
        shopDataRepository.getGoodsList(new ShopDataRepository.DataCallback<List<GoodsBean>>() {
            @Override
            public void onSuccess(List<GoodsBean> data) {
                // 确保在主线程更新UI
                runOnUiThread(() -> {
                    // 只显示前4个商品
                    if (data != null && data.size() > 0) {
                        int count = Math.min(4, data.size());
                        List<GoodsBean> recommended = new ArrayList<>(data.subList(0, count));
                        if (recommendAdapter != null) {
                            recommendAdapter.updateData(recommended);
                        }
                    } else {
                        // 如果没有数据，手动创建一些推荐商品
                        android.util.Log.d("ShoppingCart", "No recommended goods data, creating mock data");
                        List<GoodsBean> mockGoods = new ArrayList<>();
                        // 手动创建4个商品
                        for (int i = 1; i <= 4; i++) {
                            GoodsBean goods = new GoodsBean(
                                "goods_" + i,
                                "推荐商品 " + i,
                                getResources().getIdentifier("goods_" + i, "drawable", getPackageName()),
                                99.0 + i,
                                0.9,
                                100 + i
                            );
                            mockGoods.add(goods);
                        }
                        if (recommendAdapter != null) {
                            recommendAdapter.updateData(mockGoods);
                        }
                    }
                });
            }
            
            @Override
            public void onError(Exception e) {
                // 确保在主线程更新UI
                runOnUiThread(() -> {
                    // 错误时也手动创建一些推荐商品
                    android.util.Log.e("ShoppingCart", "Error loading recommended goods: " + e.getMessage(), e);
                    List<GoodsBean> mockGoods = new ArrayList<>();
                    for (int i = 1; i <= 4; i++) {
                        GoodsBean goods = new GoodsBean(
                            "goods_" + i,
                            "推荐商品 " + i,
                            getResources().getIdentifier("goods_" + i, "drawable", getPackageName()),
                            99.0 + i,
                            0.9,
                            100 + i
                        );
                        mockGoods.add(goods);
                    }
                    if (recommendAdapter != null) {
                        recommendAdapter.updateData(mockGoods);
                    }
                });
            }
        });
    }
    
    /**
     * 更新总价
     */
    private void updateTotalPrice() {
        double total = viewModel.calculateTotalPrice();
        if (totalPriceText != null) {
            totalPriceText.setText("总计 ¥" + String.format("%.0f", total));
        }
    }
    
    /**
     * 结算
     */
    private void checkout() {
        List<ShoppingCartViewModel.CartItem> selectedItems = viewModel.getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            android.widget.Toast.makeText(this, "请选择要结算的商品", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        // 只结算勾选的商品：把选中条目的购物车ID传给订单确认页
        java.util.ArrayList<String> selectedIds = new java.util.ArrayList<>();
        for (ShoppingCartViewModel.CartItem item : selectedItems) {
            selectedIds.add(String.valueOf(item.cart.id));
        }

        // 跳转到订单确认页
        Intent intent = new Intent(this, OrderConfirmActivity.class);
        intent.putExtra(OrderConfirmActivity.KEY_FROM_CART, true);
        intent.putStringArrayListExtra(OrderConfirmActivity.KEY_SELECTED_CART_IDS, selectedIds);
        startActivity(intent);
    }
}
