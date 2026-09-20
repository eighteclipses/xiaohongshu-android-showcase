package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.Index;

/**
 * 评论实体类
 * 存储笔记的评论信息
 */
@Entity(tableName = "comments",
        indices = {@Index("noteId"), @Index("userId")})
public class CommentEntity {
    @PrimaryKey
    @NonNull
    public String id = "";        // 评论ID

    public String noteId;    // 笔记ID
    public String userId;    // 用户ID
    public String content;   // 评论内容
    public String parentId; // 父评论ID（用于回复）
    public long createTime;  // 创建时间
    public int likes;        // 评论点赞数（本地维护）
    public String authorName;   // 冗余作者昵称（远程评论作者不在本地用户表时展示用）
    public String authorAvatar; // 冗余作者头像URL（可空）

    public CommentEntity() {
    }

    @Ignore
    public CommentEntity(String id, String noteId, String userId, String content,
                        String parentId, long createTime) {
        this.id = id;
        this.noteId = noteId;
        this.userId = userId;
        this.content = content;
        this.parentId = parentId;
        this.createTime = createTime;
    }

    @Ignore
    public CommentEntity(String id, String noteId, String userId, String content,
                        String parentId, long createTime, String authorName, String authorAvatar) {
        this.id = id;
        this.noteId = noteId;
        this.userId = userId;
        this.content = content;
        this.parentId = parentId;
        this.createTime = createTime;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
    }

    @Ignore
    public CommentEntity(String id, String noteId, String userId, String content,
                        String parentId, long createTime, int likes, String authorName, String authorAvatar) {
        this.id = id;
        this.noteId = noteId;
        this.userId = userId;
        this.content = content;
        this.parentId = parentId;
        this.createTime = createTime;
        this.likes = likes;
        this.authorName = authorName;
        this.authorAvatar = authorAvatar;
    }
}
