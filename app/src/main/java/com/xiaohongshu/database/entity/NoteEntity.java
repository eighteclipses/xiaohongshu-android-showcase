package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;
import com.xiaohongshu.database.converter.StringListConverter;

import java.util.List;

/**
 * 笔记实体类
 * 存储用户发布的笔记信息
 */
@Entity(tableName = "notes")
@TypeConverters(StringListConverter.class)
public class NoteEntity {
    @PrimaryKey
    @NonNull
    public String id = "";              // 笔记ID
    
    @androidx.room.ColumnInfo(defaultValue = "'approved'")
    public String moderationStatus = "approved";
    @androidx.room.ColumnInfo(defaultValue = "'synced'")
    public String syncState = "synced";
    @androidx.room.ColumnInfo(defaultValue = "''")
    public String reviewNote = "";
    @androidx.room.ColumnInfo(defaultValue = "1")
    public int contentVersion = 1;
    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean deleted = false;
    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean serverKnown = false;
    public String title;           // 标题
    public String content;         // 内容
    public List<String> imageUris; // 图片URI列表
    public List<String> topics;    // 话题列表
    public String location;        // 位置
    public boolean isPublic;       // 是否公开
    public boolean isDraft;        // 是否为草稿
    public String userId;          // 用户ID
    public long createTime;        // 创建时间
    public long updateTime;        // 更新时间
    
    public NoteEntity() {
    }
    
    @Ignore
    public NoteEntity(String id, String title, String content, List<String> imageUris, 
                     List<String> topics, String location, boolean isPublic, boolean isDraft,
                     String userId, long createTime, long updateTime) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUris = imageUris;
        this.topics = topics;
        this.location = location;
        this.isPublic = isPublic;
        this.isDraft = isDraft;
        this.userId = userId;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}

