package com.xiaohongshu.ui.order;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.GoodsEntity;
import com.xiaohongshu.database.entity.OrderEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 订单详情Activity
 * 显示订单详细信息
 */
public class OrderDetailActivity extends BaseActivity {
    public static final String KEY_ORDER_ID = "key_order_id";
    
    private AppDatabase database;
    private ExecutorService executorService;
    private OrderEntity order;
    private GoodsEntity goods;
    
    private TextView orderIdText;
    private TextView orderStatusText;
    private ImageView goodsImage;
    private TextView goodsTitle;
    private TextView goodsPrice;
    private TextView quantityText;
    private TextView totalPriceText;
    private TextView addressText;
    private TextView createTimeText;
    private Button actionButton;
    
    public static void start(Context context, String orderId) {
        Intent intent = new Intent(context, OrderDetailActivity.class);
        intent.putExtra(KEY_ORDER_ID, orderId);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);
        
        String orderId = getIntent().getStringExtra(KEY_ORDER_ID);
        if (orderId == null || orderId.isEmpty()) {
            finish();
            return;
        }
        
        database = AppApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();
        
        initViews();
        loadOrder(orderId);
    }
    
    @Override
    protected void initViews() {
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("订单详情");
        }
        
        orderIdText = findViewById(R.id.orderIdText);
        orderStatusText = findViewById(R.id.orderStatusText);
        goodsImage = findViewById(R.id.goodsImage);
        goodsTitle = findViewById(R.id.goodsTitle);
        goodsPrice = findViewById(R.id.goodsPrice);
        quantityText = findViewById(R.id.quantityText);
        totalPriceText = findViewById(R.id.totalPriceText);
        addressText = findViewById(R.id.addressText);
        createTimeText = findViewById(R.id.createTimeText);
        actionButton = findViewById(R.id.actionButton);
        
        // 操作按钮
        if (actionButton != null) {
            actionButton.setOnClickListener(v -> handleOrderAction());
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        // 数据在loadOrder中加载
    }
    
    private void loadOrder(String orderId) {
        executorService.execute(() -> {
            order = database.orderDao().getOrderById(orderId);
            if (order != null) {
                goods = database.goodsDao().getGoodsById(order.goodsId);
            }
            
            runOnUiThread(() -> {
                if (order != null) {
                    updateUI();
                } else {
                    android.widget.Toast.makeText(this, "订单不存在", android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }
    
    private void updateUI() {
        if (orderIdText != null) {
            orderIdText.setText("订单号：" + order.id);
        }
        
        if (orderStatusText != null) {
            orderStatusText.setText(getStatusText(order.status));
        }
        
        if (goods != null) {
            if (goodsImage != null) {
                goodsImage.setImageResource(goods.image);
            }
            if (goodsTitle != null) {
                goodsTitle.setText(goods.title);
            }
            if (goodsPrice != null) {
                goodsPrice.setText("¥" + String.format("%.2f", goods.price));
            }
        }
        
        if (quantityText != null) {
            quantityText.setText("数量：" + order.quantity);
        }
        
        if (totalPriceText != null) {
            totalPriceText.setText("总计：¥" + String.format("%.2f", order.totalPrice));
        }
        
        if (addressText != null) {
            addressText.setText("收货地址：" + (order.address != null ? order.address : "未设置"));
        }
        
        if (createTimeText != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            createTimeText.setText("创建时间：" + sdf.format(new Date(order.createTime)));
        }
        
        // 更新操作按钮
        if (actionButton != null) {
            switch (order.status) {
                case 0: // 待付款
                    actionButton.setText("立即付款");
                    actionButton.setVisibility(View.VISIBLE);
                    break;
                case 1: // 待发货
                    actionButton.setText("提醒发货");
                    actionButton.setVisibility(View.VISIBLE);
                    break;
                case 2: // 待收货
                    actionButton.setText("确认收货");
                    actionButton.setVisibility(View.VISIBLE);
                    break;
                case 3: // 已完成
                    actionButton.setText("再次购买");
                    actionButton.setVisibility(View.VISIBLE);
                    break;
                case 4: // 已取消
                    actionButton.setVisibility(View.GONE);
                    break;
                default:
                    actionButton.setVisibility(View.GONE);
                    break;
            }
        }
    }
    
    private String getStatusText(int status) {
        switch (status) {
            case 0: return "待付款";
            case 1: return "待发货";
            case 2: return "待收货";
            case 3: return "已完成";
            case 4: return "已取消";
            default: return "未知";
        }
    }
    
    private void handleOrderAction() {
        if (order == null) return;
        
        switch (order.status) {
            case 0: // 待付款
                // 跳转到支付页
                Intent intent = new Intent(this, com.xiaohongshu.ui.payment.PaymentActivity.class);
                intent.putExtra(com.xiaohongshu.ui.payment.PaymentActivity.KEY_ORDER_ID, order.id);
                startActivity(intent);
                break;
            case 1: // 待发货
                android.widget.Toast.makeText(this, "已提醒商家发货", android.widget.Toast.LENGTH_SHORT).show();
                break;
            case 2: // 待收货
                // 确认收货
                executorService.execute(() -> {
                    order.status = 3; // 已完成
                    order.updateTime = System.currentTimeMillis();
                    database.orderDao().update(order);
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, "确认收货成功", android.widget.Toast.LENGTH_SHORT).show();
                        updateUI();
                    });
                });
                break;
            case 3: // 已完成
                // 再次购买
                if (goods != null) {
                    com.xiaohongshu.ui.goods.GoodsDetailActivity.start(this, goods.id);
                }
                break;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}

