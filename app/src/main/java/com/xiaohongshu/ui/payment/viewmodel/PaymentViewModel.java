package com.xiaohongshu.ui.payment.viewmodel;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.OrderEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 支付ViewModel
 */
public class PaymentViewModel extends AndroidViewModel {
    private String orderId = "";
    private final MutableLiveData<String> orderIdLive = new MutableLiveData<>("");
    private final MutableLiveData<Double> amount = new MutableLiveData<>(0.0);
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private AppDatabase database;
    
    public PaymentViewModel(Application application) {
        super(application);
        database = AppApplication.getDatabase();
    }
    
    public void init(android.content.Context context) {
        // 初始化
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
        this.orderIdLive.postValue(orderId);
    }
    
    public LiveData<String> getOrderId() {
        return orderIdLive;
    }
    
    public LiveData<Double> getAmount() {
        return amount;
    }
    
    public void load() {
        executorService.execute(() -> {
            // 计算订单总金额
            String userId = getCurrentUserId();
            if (userId.isEmpty()) {
                return;
            }
            
            List<OrderEntity> orders = database.orderDao().getOrdersByUserIdAndStatusSync(userId, 0);
            
            double total = 0.0;
            if (orders != null) {
                for (OrderEntity order : orders) {
                    if (order.id.startsWith(orderId) || order.id.equals(orderId)) {
                        total += order.totalPrice;
                    }
                }
            }
            
            amount.postValue(total);
        });
    }
    
    /**
     * 支付
     */
    public void pay() {
        executorService.execute(() -> {
            // 更新订单状态为已付款（状态1：待发货）
            String userId = getCurrentUserId();
            if (userId.isEmpty()) {
                return;
            }
            
            List<OrderEntity> orders = database.orderDao().getOrdersByUserIdAndStatusSync(userId, 0);
            
            if (orders != null) {
                for (OrderEntity order : orders) {
                    if (order.id.startsWith(orderId) || order.id.equals(orderId)) {
                        order.status = 1; // 待发货
                        order.updateTime = System.currentTimeMillis();
                        database.orderDao().update(order);
                    }
                }
            }
        });
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        com.xiaohongshu.bean.UserBean currentUser = 
            com.xiaohongshu.ui.login.LoginDataRepository.getInstance(getApplication()).getCurrentUser();
        if (currentUser != null) {
            com.xiaohongshu.database.entity.UserEntity userEntity = 
                database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                return userEntity.id;
            }
        }
        return "";
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

