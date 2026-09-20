package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.GoodsEntity;

import java.util.List;

/**
 * 商品数据访问对象
 * 提供商品的增删改查操作
 */
@Dao
public interface GoodsDao {
    /**
     * 插入商品
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GoodsEntity goods);
    
    /**
     * 插入多个商品
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<GoodsEntity> goodsList);
    
    /**
     * 更新商品
     */
    @Update
    void update(GoodsEntity goods);
    
    /**
     * 删除商品
     */
    @Delete
    void delete(GoodsEntity goods);
    
    /**
     * 根据ID查询商品
     */
    @Query("SELECT * FROM goods WHERE id = :id")
    GoodsEntity getGoodsById(String id);
    
    /**
     * 根据ID查询商品（LiveData）
     */
    @Query("SELECT * FROM goods WHERE id = :id")
    LiveData<GoodsEntity> getGoodsByIdLive(String id);
    
    /**
     * 查询所有商品（LiveData）
     */
    @Query("SELECT * FROM goods ORDER BY createTime DESC")
    LiveData<List<GoodsEntity>> getAllGoods();
    
    /**
     * 查询所有商品（同步）
     */
    @Query("SELECT * FROM goods ORDER BY createTime DESC")
    List<GoodsEntity> getAllGoodsSync();
    
    /**
     * 根据分类查询商品
     */
    @Query("SELECT * FROM goods WHERE title LIKE :keyword ORDER BY createTime DESC")
    LiveData<List<GoodsEntity>> searchGoods(String keyword);
    
    /**
     * 搜索商品（同步版本）
     */
    @Query("SELECT * FROM goods WHERE title LIKE '%' || :keyword || '%' OR description LIKE '%' || :keyword || '%' ORDER BY createTime DESC")
    List<GoodsEntity> searchGoodsSync(String keyword);
}

