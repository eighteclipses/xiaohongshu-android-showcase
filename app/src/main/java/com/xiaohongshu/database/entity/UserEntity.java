package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 用户实体类
 * 存储用户基本信息
 */
@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    public String id = "";          // 用户ID
    
    public String username;     // 用户名
    public String password;     // 密码
    public int avatar;          // 头像资源ID
    public String avatarUri = ""; // 相册 URI 或服务器头像 URL
    public String nickname;     // 昵称
    public String bio;          // 个人简介
    public String background = ""; // 主页背景图（本地 URI 或服务器 URL）
    public long createTime;     // 创建时间

    public UserEntity() {
    }

    @Ignore
    public UserEntity(String id, String username, String password, int avatar,
                     String nickname, String bio, long createTime) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.avatar = avatar;
        this.nickname = nickname;
        this.bio = bio;
        this.createTime = createTime;
    }
}

