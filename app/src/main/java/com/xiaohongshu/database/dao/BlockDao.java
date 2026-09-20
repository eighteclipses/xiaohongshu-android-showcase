package com.xiaohongshu.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xiaohongshu.database.entity.BlockEntity;

import java.util.List;

/**
 * 拉黑数据访问对象
 * 提供拉黑关系的增删查操作
 */
@Dao
public interface BlockDao {
    /**
     * 插入拉黑记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BlockEntity block);

    /**
     * 解除拉黑
     */
    @Query("DELETE FROM blocks WHERE blockerId = :blockerId AND blockedId = :blockedId")
    void deleteBlock(String blockerId, String blockedId);

    /**
     * 检查是否已拉黑
     */
    @Query("SELECT * FROM blocks WHERE blockerId = :blockerId AND blockedId = :blockedId LIMIT 1")
    BlockEntity checkBlock(String blockerId, String blockedId);

    /**
     * 查询我拉黑的所有用户ID
     */
    @Query("SELECT blockedId FROM blocks WHERE blockerId = :blockerId ORDER BY createTime DESC")
    List<String> getBlockedIdsSync(String blockerId);
}
