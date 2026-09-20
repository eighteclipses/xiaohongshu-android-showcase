package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 消息实体类
 * 存储用户之间的消息
 */
@Entity(tableName = "messages",
        indices = {@Index("fromUserId"), @Index("toUserId")})
public class MessageEntity {
    @PrimaryKey
    @NonNull
    public String id = "";        // 消息ID
    
    public String fromUserId; // 发送者ID
    public String toUserId;   // 接收者ID
    public int type;         // 消息类型：0-点赞，1-收藏，2-评论，3-关注，4-@我
    public String content;    // 消息内容
    public String relatedId;  // 关联ID（笔记ID、评论ID等）
    public boolean isRead;   // 是否已读
    public long createTime;  // 创建时间
    
    public MessageEntity() {
    }
    
    @Ignore
    public MessageEntity(String id, String fromUserId, String toUserId, int type,
                        String content, String relatedId, boolean isRead, long createTime) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.type = type;
        this.content = content;
        this.relatedId = relatedId;
        this.isRead = isRead;
        this.createTime = createTime;
    }
}

