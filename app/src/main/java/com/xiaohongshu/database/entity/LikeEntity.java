package com.xiaohongshu.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 点赞实体类
 * 存储用户对笔记的点赞记录
 */
@Entity(tableName = "likes", 
        indices = {@Index("noteId"), @Index("userId"), @Index(value = {"noteId", "userId"}, unique = true)})
public class LikeEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;          // 主键ID
    
    public String noteId;    // 笔记ID
    public String userId;    // 用户ID
    public long createTime;  // 创建时间
    
    public LikeEntity() {
    }
    
    @Ignore
    public LikeEntity(String noteId, String userId, long createTime) {
        this.noteId = noteId;
        this.userId = userId;
        this.createTime = createTime;
    }
}

