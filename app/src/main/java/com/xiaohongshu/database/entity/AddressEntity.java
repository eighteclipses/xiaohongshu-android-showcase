package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 收货地址实体类
 */
@Entity(tableName = "addresses", indices = {@Index("userId")})
public class AddressEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String userId = "";

    @NonNull
    public String receiverName = "";

    @NonNull
    public String receiverPhone = "";

    @NonNull
    public String detail = "";

    public boolean isDefault = false;

    public long createTime;
}
