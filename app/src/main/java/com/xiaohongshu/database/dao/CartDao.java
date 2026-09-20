package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.CartEntity;

import java.util.List;

/**
 * 购物车数据访问对象
 * 提供购物车的增删改查操作
 */
@Dao
public interface CartDao {
    /**
     * 插入购物车项
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CartEntity cart);
    
    /**
     * 更新购物车项
     */
    @Update
    void update(CartEntity cart);
    
    /**
     * 删除购物车项
     */
    @Delete
    void delete(CartEntity cart);
    
    /**
     * 查询用户购物车中的所有商品（LiveData）
     */
    @Query("SELECT * FROM cart WHERE userId = :userId ORDER BY updateTime DESC")
    LiveData<List<CartEntity>> getCartByUserId(String userId);
    
    /**
     * 查询用户购物车中的所有商品（同步）
     */
    @Query("SELECT * FROM cart WHERE userId = :userId ORDER BY updateTime DESC")
    List<CartEntity> getCartByUserIdSync(String userId);
    
    /**
     * 查询购物车项
     */
    @Query("SELECT * FROM cart WHERE userId = :userId AND goodsId = :goodsId LIMIT 1")
    CartEntity getCartItem(String userId, String goodsId);
    
    /**
     * 删除用户的购物车项
     */
    @Query("DELETE FROM cart WHERE userId = :userId AND goodsId = :goodsId")
    void deleteCartItem(String userId, String goodsId);
    
    /**
     * 清空用户购物车
     */
    @Query("DELETE FROM cart WHERE userId = :userId")
    void clearCart(String userId);
    
    /**
     * 查询购物车商品数量
     */
    @Query("SELECT COUNT(*) FROM cart WHERE userId = :userId")
    LiveData<Integer> getCartItemCount(String userId);
}

