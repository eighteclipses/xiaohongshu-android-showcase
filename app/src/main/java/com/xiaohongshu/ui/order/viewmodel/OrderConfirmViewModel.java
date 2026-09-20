package com.xiaohongshu.ui.order.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.*;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 订单确认ViewModel
 */
public class OrderConfirmViewModel extends AndroidViewModel {
    private final MutableLiveData<String> goodsInfo = new MutableLiveData<>("");
    private final MutableLiveData<Double> totalPrice = new MutableLiveData<>(0.0);
    private final MutableLiveData<String> address = new MutableLiveData<>("请选择收货地址");
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    private String goodsId = "";
    private int quantity = 1;
    private boolean fromCart = false;
    // 购物车结算只处理勾选条目；null 表示未指定（兼容直接调用 loadFromCart 的旧入口）
    private java.util.Set<String> selectedCartIds = null;
    
    public OrderConfirmViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
    }
    
    public void init(android.content.Context context) {
        // 获取当前用户ID
        com.xiaohongshu.bean.UserBean currentUser =
            LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            executorService.execute(() -> {
                UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
                if (userEntity != null) {
                    currentUserId = userEntity.id;
                }
                loadDefaultAddress();
            });
        }
    }

    /**
     * 自动带出默认收货地址（无默认时取第一条），未登录或无地址保持占位文案
     */
    private void loadDefaultAddress() {
        if (currentUserId.isEmpty()) {
            return;
        }
        AddressEntity target = database.addressDao().getDefaultSync(currentUserId);
        if (target == null) {
            List<AddressEntity> all = database.addressDao().getByUserSync(currentUserId);
            target = (all == null || all.isEmpty()) ? null : all.get(0);
        }
        if (target != null) {
            address.postValue(target.receiverName + " " + target.receiverPhone + " " + target.detail);
        }
    }
    
    public void setGoodsInfo(String goodsId, int quantity) {
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.fromCart = false;
    }
    
    public void loadFromCart() {
        this.fromCart = true;
    }

    public void setSelectedCartIds(java.util.List<String> ids) {
        this.selectedCartIds = ids == null ? null : new java.util.HashSet<>(ids);
    }

    private boolean isCartSelected(CartEntity cart) {
        return selectedCartIds == null || selectedCartIds.contains(String.valueOf(cart.id));
    }
    
    public LiveData<String> getGoodsInfo() {
        return goodsInfo;
    }
    
    public LiveData<Double> getTotalPrice() {
        return totalPrice;
    }
    
    public LiveData<String> getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address.postValue(address != null ? address : "请选择收货地址");
    }
    
    public void load() {
        executorService.execute(() -> {
            if (fromCart) {
                loadFromCartData();
            } else {
                loadSingleGoodsData();
            }
        });
    }
    
    /**
     * 加载单个商品数据
     */
    private void loadSingleGoodsData() {
        GoodsEntity goods = database.goodsDao().getGoodsById(goodsId);
        if (goods != null) {
            goodsInfo.postValue(goods.title + " x" + quantity);
            totalPrice.postValue(goods.price * quantity);
        }
    }
    
    /**
     * 从购物车加载数据
     */
    private void loadFromCartData() {
        if (currentUserId.isEmpty()) {
            return;
        }

        List<CartEntity> cartItems = database.cartDao().getCartByUserIdSync(currentUserId);
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }

        StringBuilder info = new StringBuilder();
        double total = 0.0;

        for (CartEntity cart : cartItems) {
            if (!isCartSelected(cart)) continue;
            GoodsEntity goods = database.goodsDao().getGoodsById(cart.goodsId);
            if (goods != null) {
                if (info.length() > 0) {
                    info.append("\n");
                }
                info.append(goods.title).append(" x").append(cart.quantity);
                total += goods.price * cart.quantity;
            }
        }

        goodsInfo.postValue(info.toString());
        totalPrice.postValue(total);
    }
    
    /**
     * 创建订单
     */
    public String createOrder() {
        if (currentUserId.isEmpty()) {
            return null;
        }
        
        String orderId = String.valueOf(System.currentTimeMillis());
        String orderAddress = address.getValue() != null ? address.getValue() : "默认地址";
        
        if (fromCart) {
            // 从购物车创建订单（只结算勾选的条目）
            List<CartEntity> cartItems = database.cartDao().getCartByUserIdSync(currentUserId);
            boolean hasSelected = false;
            if (cartItems != null) {
                for (CartEntity cart : cartItems) {
                    if (!isCartSelected(cart)) continue;
                    GoodsEntity goods = database.goodsDao().getGoodsById(cart.goodsId);
                    if (goods != null) {
                        OrderEntity order = new OrderEntity(
                            orderId + "_" + cart.goodsId,
                            currentUserId,
                            cart.goodsId,
                            cart.quantity,
                            goods.price * cart.quantity,
                            0, // 待付款
                            orderAddress,
                            System.currentTimeMillis(),
                            System.currentTimeMillis()
                        );
                        database.orderDao().insert(order);
                        // 只移除已结算的购物车条目
                        database.cartDao().deleteCartItem(currentUserId, cart.goodsId);
                        hasSelected = true;
                    }
                }
            }
            if (hasSelected) {
                return orderId;
            } else if (cartItems != null && !cartItems.isEmpty()) {
                // 传入了选中集合但一条都没匹配上（数据已变化），视为无效
                return null;
            }
        } else {
            // 单个商品创建订单
            GoodsEntity goods = database.goodsDao().getGoodsById(goodsId);
            if (goods != null) {
                OrderEntity order = new OrderEntity(
                    orderId,
                    currentUserId,
                    goodsId,
                    quantity,
                    goods.price * quantity,
                    0, // 待付款
                    orderAddress,
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
                );
                database.orderDao().insert(order);
                return orderId;
            }
        }
        
        return null;
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

