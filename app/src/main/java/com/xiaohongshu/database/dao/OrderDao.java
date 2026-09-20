package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.OrderEntity;

import java.util.List;

/**
 * 订单数据访问对象
 * 提供订单的增删改查操作
 */
@Dao
public interface OrderDao {
    /**
     * 插入订单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OrderEntity order);
    
    /**
     * 插入多个订单
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<OrderEntity> orders);
    
    /**
     * 更新订单
     */
    @Update
    void update(OrderEntity order);
    
    /**
     * 删除订单
     */
    @Delete
    void delete(OrderEntity order);
    
    /**
     * 根据ID查询订单
     */
    @Query("SELECT * FROM orders WHERE id = :id")
    OrderEntity getOrderById(String id);
    
    /**
     * 根据ID查询订单（LiveData）
     */
    @Query("SELECT * FROM orders WHERE id = :id")
    LiveData<OrderEntity> getOrderByIdLive(String id);
    
    /**
     * 查询用户的所有订单
     */
    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY createTime DESC")
    LiveData<List<OrderEntity>> getOrdersByUserId(String userId);
    
    /**
     * 根据状态查询用户的订单（LiveData）
     */
    @Query("SELECT * FROM orders WHERE userId = :userId AND status = :status ORDER BY createTime DESC")
    LiveData<List<OrderEntity>> getOrdersByUserIdAndStatus(String userId, int status);
    
    /**
     * 根据状态查询用户的订单（同步）
     */
    @Query("SELECT * FROM orders WHERE userId = :userId AND status = :status ORDER BY createTime DESC")
    List<OrderEntity> getOrdersByUserIdAndStatusSync(String userId, int status);
    
    /**
     * 查询用户的所有订单（同步）
     */
    @Query("SELECT * FROM orders WHERE userId = :userId ORDER BY createTime DESC")
    List<OrderEntity> getOrdersByUserIdSync(String userId);
}

