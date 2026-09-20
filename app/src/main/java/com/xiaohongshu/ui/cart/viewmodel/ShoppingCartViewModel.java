package com.xiaohongshu.ui.cart.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.CartEntity;
import com.xiaohongshu.database.entity.GoodsEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 购物车ViewModel
 */
public class ShoppingCartViewModel extends AndroidViewModel {
    private final MutableLiveData<List<CartItem>> cartItems = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    
    public ShoppingCartViewModel(Application application) {
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
                    load();
                }
            });
        }
    }
    
    public LiveData<List<CartItem>> getCartItems() {
        return cartItems;
    }
    
    /**
     * 加载购物车数据
     */
    public void load() {
        executorService.execute(() -> {
            if (currentUserId.isEmpty()) {
                cartItems.postValue(new ArrayList<>());
                return;
            }
            
            List<CartEntity> cartEntities = database.cartDao().getCartByUserIdSync(currentUserId);
            if (cartEntities == null) {
                cartEntities = new ArrayList<>();
            }
            
            // 保留当前的选择状态
            List<CartItem> currentItems = cartItems.getValue();
            java.util.Map<String, Boolean> selectionMap = new java.util.HashMap<>();
            if (currentItems != null) {
                for (CartItem item : currentItems) {
                    selectionMap.put(item.cart.goodsId, item.isSelected);
                }
            }
            
            List<CartItem> items = new ArrayList<>();
            for (CartEntity cart : cartEntities) {
                GoodsEntity goods = database.goodsDao().getGoodsById(cart.goodsId);
                if (goods != null) {
                    CartItem item = new CartItem(cart, goods);
                    // 恢复选择状态
                    Boolean wasSelected = selectionMap.get(cart.goodsId);
                    if (wasSelected != null) {
                        item.isSelected = wasSelected;
                    }
                    items.add(item);
                }
            }
            
            cartItems.postValue(items);
        });
    }
    
    /**
     * 更新商品数量
     */
    public void updateQuantity(String goodsId, int quantity) {
        // 先立即更新UI（乐观更新）
        List<CartItem> currentItems = cartItems.getValue();
        if (currentItems != null) {
            boolean updated = false;
            for (CartItem item : currentItems) {
                if (item.cart.goodsId.equals(goodsId)) {
                    item.cart.quantity = quantity;
                    item.cart.updateTime = System.currentTimeMillis();
                    updated = true;
                    break;
                }
            }
            if (updated) {
                // 创建新列表以确保LiveData触发更新
                List<CartItem> updatedItems = new ArrayList<>(currentItems);
                cartItems.postValue(updatedItems);
            }
        }
        
        // 然后在后台更新数据库
        executorService.execute(() -> {
            if (currentUserId.isEmpty()) {
                return;
            }
            
            CartEntity cart = database.cartDao().getCartItem(currentUserId, goodsId);
            if (cart != null) {
                cart.quantity = quantity;
                cart.updateTime = System.currentTimeMillis();
                database.cartDao().update(cart);
            }
        });
    }
    
    /**
     * 从购物车删除商品
     */
    public void removeFromCart(String goodsId) {
        // 先立即更新UI（乐观更新，在主线程中执行）
        List<CartItem> currentItems = cartItems.getValue();
        if (currentItems != null) {
            List<CartItem> updatedItems = new ArrayList<>();
            for (CartItem item : currentItems) {
                if (!item.cart.goodsId.equals(goodsId)) {
                    updatedItems.add(item);
                }
            }
            cartItems.postValue(updatedItems);
        }
        
        // 然后在后台更新数据库
        executorService.execute(() -> {
            if (currentUserId.isEmpty()) {
                return;
            }
            
            database.cartDao().deleteCartItem(currentUserId, goodsId);
            // 不需要重新加载，因为已经乐观更新了UI
        });
    }
    
    /**
     * 计算总价（只计算选中的商品）
     */
    public double calculateTotalPrice() {
        List<CartItem> items = getSelectedItems();
        if (items == null || items.isEmpty()) {
            return 0.0;
        }
        
        double total = 0.0;
        for (CartItem item : items) {
            total += item.goods.price * item.cart.quantity;
        }
        return total;
    }
    
    /**
     * 购物车项（包含购物车实体和商品信息）
     */
    public static class CartItem {
        public CartEntity cart;
        public GoodsEntity goods;
        public boolean isSelected; // 是否选中
        
        public CartItem(CartEntity cart, GoodsEntity goods) {
            this.cart = cart;
            this.goods = goods;
            this.isSelected = true; // 默认选中
        }
    }
    
    /**
     * 全选/取消全选
     */
    public void selectAll(boolean select) {
        List<CartItem> items = cartItems.getValue();
        if (items != null) {
            for (CartItem item : items) {
                item.isSelected = select;
            }
            cartItems.postValue(items);
        }
    }
    
    /**
     * 切换单个商品的选择状态
     */
    public void toggleSelection(String goodsId) {
        List<CartItem> items = cartItems.getValue();
        if (items != null) {
            for (CartItem item : items) {
                if (item.cart.goodsId.equals(goodsId)) {
                    item.isSelected = !item.isSelected;
                    break;
                }
            }
            cartItems.postValue(items);
        }
    }
    
    /**
     * 检查是否全选
     */
    public boolean isAllSelected() {
        List<CartItem> items = cartItems.getValue();
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (CartItem item : items) {
            if (!item.isSelected) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取选中的商品列表
     */
    public List<CartItem> getSelectedItems() {
        List<CartItem> items = cartItems.getValue();
        if (items == null) {
            return new ArrayList<>();
        }
        List<CartItem> selected = new ArrayList<>();
        for (CartItem item : items) {
            if (item.isSelected) {
                selected.add(item);
            }
        }
        return selected;
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

