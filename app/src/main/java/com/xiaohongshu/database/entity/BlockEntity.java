package com.xiaohongshu.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 拉黑实体类
 * 存储用户之间的拉黑关系（blockerId 拉黑了 blockedId）
 */
@Entity(tableName = "blocks",
        indices = {@Index("blockerId"), @Index("blockedId"), @Index(value = {"blockerId", "blockedId"}, unique = true)})
public class BlockEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;          // 主键ID

    public String blockerId;   // 拉黑者ID
    public String blockedId;   // 被拉黑者ID
    public long createTime;    // 创建时间

    public BlockEntity() {
    }

    @Ignore
    public BlockEntity(String blockerId, String blockedId, long createTime) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createTime = createTime;
    }
}
