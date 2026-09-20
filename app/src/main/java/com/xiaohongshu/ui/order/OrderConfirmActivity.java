package com.xiaohongshu.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.order.viewmodel.OrderConfirmViewModel;
import com.xiaohongshu.ui.payment.PaymentActivity;

/**
 * 订单确认页Activity
 * 显示订单商品、收货地址、总价，确认后跳转到支付页
 */
public class OrderConfirmActivity extends BaseActivity {
    public static final String KEY_GOODS_ID = "key_goods_id";
    public static final String KEY_QUANTITY = "key_quantity";
    public static final String KEY_FROM_CART = "key_from_cart";
    public static final String KEY_SELECTED_CART_IDS = "key_selected_cart_ids";
    
    private OrderConfirmViewModel viewModel;
    private TextView goodsInfoText;
    private TextView addressText;
    private TextView totalPriceText;
    private Button confirmButton;
    
    private static final int REQUEST_CODE_SELECT_ADDRESS = 1001;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_confirm);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(OrderConfirmViewModel.class);
        viewModel.init(getApplicationContext());
        
        // 获取传递的参数
        Intent intent = getIntent();
        String goodsId = intent.getStringExtra(KEY_GOODS_ID);
        int quantity = intent.getIntExtra(KEY_QUANTITY, 1);
        boolean fromCart = intent.getBooleanExtra(KEY_FROM_CART, false);
        java.util.ArrayList<String> selectedCartIds = intent.getStringArrayListExtra(KEY_SELECTED_CART_IDS);

        if (fromCart) {
            viewModel.setSelectedCartIds(selectedCartIds);
            viewModel.loadFromCart();
        } else if (goodsId != null) {
            viewModel.setGoodsInfo(goodsId, quantity);
        }
        
        initViews();
        initData();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SELECT_ADDRESS && resultCode == RESULT_OK) {
            String address = data.getStringExtra(AddressListActivity.KEY_SELECTED_ADDRESS);
            if (address != null) {
                viewModel.setAddress(address);
            }
        }
    }
    
    @Override
    protected void initViews() {
        goodsInfoText = findViewById(R.id.goodsInfoText);
        addressText = findViewById(R.id.addressText);
        totalPriceText = findViewById(R.id.totalPriceText);
        confirmButton = findViewById(R.id.confirmButton);
        
        // 确认订单按钮
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                confirmOrder();
            });
        }
        
        // 选择地址（地址文本与"选择地址"按钮均可点）
        View.OnClickListener openAddressList = v -> {
            Intent intent = new Intent(this, AddressListActivity.class);
            intent.putExtra("select_mode", true);
            startActivityForResult(intent, REQUEST_CODE_SELECT_ADDRESS);
        };
        View selectAddressButton = findViewById(R.id.selectAddressButton);
        if (selectAddressButton != null) {
            selectAddressButton.setOnClickListener(openAddressList);
        }
        if (addressText != null) {
            addressText.setOnClickListener(openAddressList);
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("确认订单");
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getGoodsInfo().observe(this, info -> {
            if (info != null && goodsInfoText != null) {
                goodsInfoText.setText(info);
            }
        });
        
        viewModel.getTotalPrice().observe(this, price -> {
            if (price != null && totalPriceText != null) {
                totalPriceText.setText("合计：¥" + String.format("%.2f", price));
            }
        });
        
        viewModel.getAddress().observe(this, address -> {
            if (address != null && addressText != null) {
                addressText.setText(address);
            }
        });
        
        viewModel.load();
    }
    
    /**
     * 确认订单
     */
    private void confirmOrder() {
        // 创建订单并跳转到支付页
        String orderId = viewModel.createOrder();
        if (orderId != null) {
            Intent intent = new Intent(this, PaymentActivity.class);
            intent.putExtra(PaymentActivity.KEY_ORDER_ID, orderId);
            startActivity(intent);
            finish();
        } else {
            android.widget.Toast.makeText(this, "创建订单失败", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

