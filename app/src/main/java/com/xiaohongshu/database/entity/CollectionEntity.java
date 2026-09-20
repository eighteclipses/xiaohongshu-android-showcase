package com.xiaohongshu.database.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 收藏实体类
 * 存储用户对笔记的收藏记录
 */
@Entity(tableName = "collections",
        indices = {@Index("noteId"), @Index("userId"), @Index(value = {"noteId", "userId"}, unique = true)})
public class CollectionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;          // 主键ID
    
    public String noteId;    // 笔记ID
    public String userId;    // 用户ID
    public long createTime;  // 创建时间
    
    public CollectionEntity() {
    }
    
    @Ignore
    public CollectionEntity(String noteId, String userId, long createTime) {
        this.noteId = noteId;
        this.userId = userId;
        this.createTime = createTime;
    }
}

