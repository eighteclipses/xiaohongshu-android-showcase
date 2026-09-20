package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 订单实体类
 * 存储订单信息
 */
@Entity(tableName = "orders",
        indices = {@Index("userId")})
public class OrderEntity {
    @PrimaryKey
    @NonNull
    public String id = "";        // 订单ID
    
    public String userId;    // 用户ID
    public String goodsId;   // 商品ID
    public int quantity;     // 数量
    public double totalPrice; // 总价
    public int status;       // 订单状态：0-待付款，1-待发货，2-待收货，3-已完成，4-已取消
    public String address;   // 收货地址
    public long createTime;  // 创建时间
    public long updateTime;  // 更新时间
    
    public OrderEntity() {
    }
    
    @Ignore
    public OrderEntity(String id, String userId, String goodsId, int quantity,
                      double totalPrice, int status, String address, long createTime, long updateTime) {
        this.id = id;
        this.userId = userId;
        this.goodsId = goodsId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = status;
        this.address = address;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}

