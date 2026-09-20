package com.xiaohongshu.ui.order.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.GoodsEntity;
import com.xiaohongshu.database.entity.OrderEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 订单列表ViewModel
 */
public class OrderListViewModel extends AndroidViewModel {
    private final MutableLiveData<List<OrderItem>> orders = new MutableLiveData<>(new ArrayList<>());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    private String currentUserId = "";
    
    public OrderListViewModel(Application application) {
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
            });
        }
    }
    
    public LiveData<List<OrderItem>> getOrders() {
        return orders;
    }
    
    /**
     * 加载订单列表
     */
    public void loadOrders(int status) {
        executorService.execute(() -> {
            if (currentUserId.isEmpty()) {
                orders.postValue(new ArrayList<>());
                return;
            }
            
            List<OrderEntity> orderEntities;
            if (status == -1) {
                // 全部订单
                orderEntities = database.orderDao().getOrdersByUserIdSync(currentUserId);
            } else {
                // 按状态筛选
                orderEntities = database.orderDao().getOrdersByUserIdAndStatusSync(currentUserId, status);
            }
            
            if (orderEntities == null) {
                orderEntities = new ArrayList<>();
            }
            
            List<OrderItem> items = new ArrayList<>();
            for (OrderEntity order : orderEntities) {
                GoodsEntity goods = database.goodsDao().getGoodsById(order.goodsId);
                if (goods != null) {
                    items.add(new OrderItem(order, goods));
                }
            }
            
            orders.postValue(items);
        });
    }
    
    /**
     * 订单项（包含订单实体和商品信息）
     */
    public static class OrderItem {
        public OrderEntity order;
        public GoodsEntity goods;
        
        public OrderItem(OrderEntity order, GoodsEntity goods) {
            this.order = order;
            this.goods = goods;
        }
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

