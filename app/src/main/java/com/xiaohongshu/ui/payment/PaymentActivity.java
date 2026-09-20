package com.xiaohongshu.ui.payment;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.OrderEntity;
import com.xiaohongshu.ui.order.OrderListActivity;
import com.xiaohongshu.ui.payment.viewmodel.PaymentViewModel;

/**
 * 支付页面Activity
 * 显示支付金额，实现模拟支付流程
 */
public class PaymentActivity extends BaseActivity {
    public static final String KEY_ORDER_ID = "key_order_id";
    
    private PaymentViewModel viewModel;
    private TextView orderIdText;
    private TextView amountText;
    private Button payButton;
    private TextView methodWechat;
    private TextView methodAlipay;
    private TextView methodBank;
    private String payMethod = "微信支付";
    private AppDatabase database;
    private final android.os.Handler payHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        
        database = AppApplication.getDatabase();
        
        String orderId = getIntent().getStringExtra(KEY_ORDER_ID);
        if (orderId == null) {
            finish();
            return;
        }
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(PaymentViewModel.class);
        viewModel.init(getApplicationContext());
        viewModel.setOrderId(orderId);
        viewModel.load();
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        markAsDemoFeature();
        orderIdText = findViewById(R.id.orderIdText);
        amountText = findViewById(R.id.amountText);
        payButton = findViewById(R.id.payButton);
        methodWechat = findViewById(R.id.methodWechat);
        methodAlipay = findViewById(R.id.methodAlipay);
        methodBank = findViewById(R.id.methodBank);

        // 支付方式选择（默认微信）
        selectMethod(methodWechat, "微信支付");
        if (methodAlipay != null) {
            methodAlipay.setOnClickListener(v -> selectMethod(methodAlipay, "支付宝"));
        }
        if (methodBank != null) {
            methodBank.setOnClickListener(v -> selectMethod(methodBank, "银行卡"));
        }

        // 支付按钮
        if (payButton != null) {
            payButton.setOnClickListener(v -> {
                pay();
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
            titleText.setText("支付");
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getOrderId().observe(this, id -> {
            if (id != null && orderIdText != null) {
                orderIdText.setText("订单号：" + id);
            }
        });
        
        viewModel.getAmount().observe(this, amount -> {
            if (amount != null && amountText != null) {
                amountText.setText("支付金额：¥" + String.format("%.2f", amount));
            }
        });
    }
    
    /**
     * 选中支付方式：更新卡片选中态与当前方式名
     */
    private void selectMethod(TextView selected, String name) {
        payMethod = name;
        if (methodWechat != null) methodWechat.setSelected(selected == methodWechat);
        if (methodAlipay != null) methodAlipay.setSelected(selected == methodAlipay);
        if (methodBank != null) methodBank.setSelected(selected == methodBank);
    }

    /**
     * 支付
     */
    private void pay() {
        // 模拟支付流程
        payButton.setEnabled(false);
        payButton.setText("支付中...");

        // 延迟2秒模拟支付过程（payHandler 在 onDestroy 中清理，避免持有 Activity 泄漏）
        payHandler.postDelayed(() -> {
            // 更新订单状态为已付款
            viewModel.pay();

            android.widget.Toast.makeText(this, payMethod + "成功", android.widget.Toast.LENGTH_SHORT).show();

            // 跳转到订单列表
            Intent intent = new Intent(this, OrderListActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        payHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

