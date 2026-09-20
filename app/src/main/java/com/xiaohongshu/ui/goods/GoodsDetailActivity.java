package com.xiaohongshu.ui.goods;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.CartEntity;
import com.xiaohongshu.ui.goods.viewmodel.GoodsDetailViewModel;
import com.xiaohongshu.ui.shop.bean.GoodsBean;

/**
 * 商品详情页Activity
 * 显示商品详情，支持加入购物车和立即购买
 */
public class GoodsDetailActivity extends BaseActivity {
    public static final String KEY_GOODS_ID = "key_goods_id";
    
    private GoodsDetailViewModel viewModel;
    private ImageView goodsImage;
    private TextView goodsTitle;
    private TextView goodsPrice;
    private TextView goodsDescription;
    private TextView quantityText;
    private Button addToCartButton;
    private Button buyNowButton;
    private View decreaseButton;
    private View increaseButton;
    private int quantity = 1;
    
    private AppDatabase database;
    
    public static void start(Context context, String goodsId) {
        Intent intent = new Intent(context, GoodsDetailActivity.class);
        intent.putExtra(KEY_GOODS_ID, goodsId);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goods_detail);
        
        database = AppApplication.getDatabase();
        
        String goodsId = getIntent().getStringExtra(KEY_GOODS_ID);
        if (goodsId == null) {
            finish();
            return;
        }
        
        viewModel = new ViewModelProvider(this).get(GoodsDetailViewModel.class);
        viewModel.init(getApplicationContext());
        viewModel.setGoodsId(goodsId);
        viewModel.load();
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        markAsDemoFeature();
        goodsImage = findViewById(R.id.goodsImage);
        goodsTitle = findViewById(R.id.goodsTitle);
        goodsPrice = findViewById(R.id.goodsPrice);
        goodsDescription = findViewById(R.id.goodsDescription);
        quantityText = findViewById(R.id.quantityText);
        addToCartButton = findViewById(R.id.addToCartButton);
        buyNowButton = findViewById(R.id.buyNowButton);
        decreaseButton = findViewById(R.id.decreaseButton);
        increaseButton = findViewById(R.id.increaseButton);
        
        // 减少数量
        if (decreaseButton != null) {
            decreaseButton.setOnClickListener(v -> {
                if (quantity > 1) {
                    quantity--;
                    updateQuantityText();
                }
            });
        }
        
        // 增加数量
        if (increaseButton != null) {
            increaseButton.setOnClickListener(v -> {
                quantity++;
                updateQuantityText();
            });
        }
        
        // 加入购物车
        if (addToCartButton != null) {
            addToCartButton.setOnClickListener(v -> {
                addToCart();
            });
        }
        
        // 立即购买
        if (buyNowButton != null) {
            buyNowButton.setOnClickListener(v -> {
                buyNow();
            });
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 隐藏标题（商品详情页已有商品标题，不需要 toolbar 标题）
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setVisibility(View.GONE);
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getGoods().observe(this, goods -> {
            if (goods != null) {
                updateGoodsInfo(goods);
            }
        });
    }
    
    /**
     * 更新商品信息显示
     */
    private void updateGoodsInfo(GoodsBean goods) {
        if (goodsImage != null) {
            int imageRes = 0;
            
            // 首先尝试使用goods.getImage()
            if (goods.getImage() != 0) {
                imageRes = goods.getImage();
            } else {
                // 如果image为0，尝试从商品ID中提取索引来获取图片
                imageRes = getImageResourceFromGoodsId(goods.getId());
                if (imageRes == 0) {
                    // 如果还是无法获取，随机分配一个图片
                    imageRes = getRandomGoodsImageResource();
                }
            }
            
            if (imageRes != 0) {
                goodsImage.setImageResource(imageRes);
            } else {
                // 最后的备用方案
                goodsImage.setImageResource(R.drawable.icon_shop);
            }
        }
        if (goodsTitle != null) {
            goodsTitle.setText(goods.getTitle());
        }
        if (goodsPrice != null) {
            goodsPrice.setText("¥" + String.format("%.2f", goods.getPrice()));
        }
        if (goodsDescription != null) {
            goodsDescription.setText("高品质商品，值得信赖");
        }
    }
    
    /**
     * 从商品ID中提取索引并返回对应的图片资源ID
     */
    private int getImageResourceFromGoodsId(String goodsId) {
        if (goodsId == null || goodsId.isEmpty()) {
            return 0;
        }
        try {
            // 商品ID格式通常是 "goods_1", "goods_2" 等
            if (goodsId.startsWith("goods_")) {
                String indexStr = goodsId.substring(6); // 去掉 "goods_" 前缀
                int index = Integer.parseInt(indexStr);
                return getGoodsImageResource(index);
            }
        } catch (NumberFormatException e) {
            // 解析失败，返回0
        }
        return 0;
    }
    
    /**
     * 根据索引获取商品图片资源ID
     */
    private int getGoodsImageResource(int index) {
        switch (index) {
            case 1: return R.drawable.goods_1;
            case 2: return R.drawable.goods_2;
            case 3: return R.drawable.goods_3;
            case 4: return R.drawable.goods_4;
            case 5: return R.drawable.goods_5;
            case 6: return R.drawable.goods_6;
            case 7: return R.drawable.goods_7;
            case 8: return R.drawable.goods_8;
            case 9: return R.drawable.goods_9;
            case 10: return R.drawable.goods_10;
            case 11: return R.drawable.goods_11;
            case 12: return R.drawable.goods_12;
            case 13: return R.drawable.goods_13;
            case 14: return R.drawable.goods_14;
            case 15: return R.drawable.goods_15;
            case 16: return R.drawable.goods_16;
            case 17: return R.drawable.goods_17;
            case 18: return R.drawable.goods_18;
            case 19: return R.drawable.goods_19;
            case 20: return R.drawable.goods_20;
            default: return R.drawable.goods_1;
        }
    }
    
    /**
     * 随机获取一个商品图片资源ID（用于未绑定图片的商品）
     */
    private int getRandomGoodsImageResource() {
        // 随机返回 goods_1 到 goods_20 中的一个
        int randomIndex = (int) (Math.random() * 20) + 1;
        return getGoodsImageResource(randomIndex);
    }
    
    /**
     * 更新数量显示
     */
    private void updateQuantityText() {
        if (quantityText != null) {
            quantityText.setText(String.valueOf(quantity));
        }
    }
    
    /**
     * 加入购物车
     */
    private void addToCart() {
        GoodsBean goods = viewModel.getGoods().getValue();
        if (goods == null) {
            return;
        }
        
        // 获取当前用户ID
        String userId = getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 检查购物车中是否已有该商品
        CartEntity existingCart = database.cartDao().getCartItem(userId, goods.getId());
        if (existingCart != null) {
            // 更新数量
            existingCart.quantity += quantity;
            existingCart.updateTime = System.currentTimeMillis();
            database.cartDao().update(existingCart);
        } else {
            // 添加新商品
            CartEntity cart = new CartEntity(
                userId,
                goods.getId(),
                quantity,
                System.currentTimeMillis(),
                System.currentTimeMillis()
            );
            database.cartDao().insert(cart);
        }
        
        Toast.makeText(this, "已加入购物车", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 立即购买
     */
    private void buyNow() {
        GoodsBean goods = viewModel.getGoods().getValue();
        if (goods == null) {
            return;
        }
        
        // 跳转到订单确认页
        Intent intent = new Intent(this, com.xiaohongshu.ui.order.OrderConfirmActivity.class);
        intent.putExtra(com.xiaohongshu.ui.order.OrderConfirmActivity.KEY_GOODS_ID, goods.getId());
        intent.putExtra(com.xiaohongshu.ui.order.OrderConfirmActivity.KEY_QUANTITY, quantity);
        startActivity(intent);
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        com.xiaohongshu.bean.UserBean currentUser = 
            com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            com.xiaohongshu.database.entity.UserEntity userEntity = 
                database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                return userEntity.id;
            }
        }
        return null;
    }
}

