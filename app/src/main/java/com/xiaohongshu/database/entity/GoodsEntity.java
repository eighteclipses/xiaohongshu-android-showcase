package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 商品实体类
 * 存储商品信息
 */
@Entity(tableName = "goods")
public class GoodsEntity {
    @PrimaryKey
    @NonNull
    public String id = "";        // 商品ID
    
    public String title;     // 标题
    public int image;        // 封面图片资源ID
    public double price;     // 价格
    public double discount;   // 折扣
    public String description; // 描述
    public int stock;        // 库存
    public long createTime;  // 创建时间
    
    public GoodsEntity() {
    }
    
    @Ignore
    public GoodsEntity(String id, String title, int image, double price, double discount,
                      String description, int stock, long createTime) {
        this.id = id;
        this.title = title;
        this.image = image;
        this.price = price;
        this.discount = discount;
        this.description = description;
        this.stock = stock;
        this.createTime = createTime;
    }
}

