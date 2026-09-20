package com.xiaohongshu.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.AddressEntity;

import java.util.List;

/**
 * 收货地址数据访问对象
 */
@Dao
public interface AddressDao {
    @Insert
    long insert(AddressEntity address);

    @Update
    void update(AddressEntity address);

    @Delete
    void delete(AddressEntity address);

    @Query("SELECT * FROM addresses WHERE userId = :userId ORDER BY isDefault DESC, createTime DESC")
    List<AddressEntity> getByUserSync(String userId);

    @Query("SELECT * FROM addresses WHERE userId = :userId AND isDefault = 1 LIMIT 1")
    AddressEntity getDefaultSync(String userId);

    @Query("SELECT * FROM addresses WHERE id = :id LIMIT 1")
    AddressEntity getByIdSync(long id);

    @Query("UPDATE addresses SET isDefault = 0 WHERE userId = :userId")
    void clearDefault(String userId);
}
