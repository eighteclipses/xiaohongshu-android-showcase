package com.xiaohongshu.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 关注实体类
 * 存储用户之间的关注关系
 */
@Entity(tableName = "follows",
        indices = {@Index("followerId"), @Index("followingId"), @Index(value = {"followerId", "followingId"}, unique = true)})
public class FollowEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;          // 主键ID
    
    public String followerId;   // 关注者ID
    public String followingId; // 被关注者ID
    public long createTime;     // 创建时间
    
    public FollowEntity() {
    }
    
    @Ignore
    public FollowEntity(String followerId, String followingId, long createTime) {
        this.followerId = followerId;
        this.followingId = followingId;
        this.createTime = createTime;
    }
}

