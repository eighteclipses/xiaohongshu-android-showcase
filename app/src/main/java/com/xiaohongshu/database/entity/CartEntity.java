package com.xiaohongshu.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 购物车实体类
 * 存储用户购物车中的商品
 */
@Entity(tableName = "cart",
        indices = {@Index("userId"), @Index("goodsId")})
public class CartEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;        // 主键ID
    
    public String userId;   // 用户ID
    public String goodsId;  // 商品ID
    public int quantity;    // 数量
    public long createTime; // 创建时间
    public long updateTime; // 更新时间
    
    public CartEntity() {
    }
    
    @Ignore
    public CartEntity(String userId, String goodsId, int quantity, long createTime, long updateTime) {
        this.userId = userId;
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}

