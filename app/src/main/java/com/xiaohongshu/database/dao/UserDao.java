package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.UserEntity;

import java.util.List;

/**
 * 用户数据访问对象
 * 提供用户的增删改查操作
 */
@Dao
public interface UserDao {
    /**
     * 插入用户
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(UserEntity user);
    
    /**
     * 插入多个用户
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<UserEntity> users);
    
    /**
     * 更新用户
     */
    @Update
    void update(UserEntity user);
    
    /**
     * 删除用户
     */
    @Delete
    void delete(UserEntity user);
    
    /**
     * 根据ID查询用户
     */
    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity getUserById(String id);
    
    /**
     * 根据ID查询用户（LiveData）
     */
    @Query("SELECT * FROM users WHERE id = :id")
    LiveData<UserEntity> getUserByIdLive(String id);
    
    /**
     * 根据用户名查询用户
     */
    @Query("SELECT * FROM users WHERE username = :username")
    UserEntity getUserByUsername(String username);

    /**
     * 根据昵称查询用户（@提及解析用）
     */
    @Query("SELECT * FROM users WHERE nickname = :nickname LIMIT 1")
    UserEntity getUserByNickname(String nickname);

    /**
     * 查询所有用户（LiveData）
     */
    @Query("SELECT * FROM users ORDER BY createTime DESC")
    LiveData<List<UserEntity>> getAllUsers();
    
    /**
     * 查询所有用户（同步）
     */
    @Query("SELECT * FROM users ORDER BY createTime DESC")
    List<UserEntity> getAllUsersSync();
    
    /**
     * 查询推荐用户（排除当前用户）
     */
    @Query("SELECT * FROM users WHERE id != :currentUserId ORDER BY createTime DESC LIMIT :limit")
    LiveData<List<UserEntity>> getRecommendedUsers(String currentUserId, int limit);

    @Query("SELECT * FROM users WHERE id != :currentUserId ORDER BY createTime DESC LIMIT :limit")
    List<UserEntity> getRecommendedUsersSync(String currentUserId, int limit);

    /**
     * 搜索用户：按 username 或 nickname 模糊匹配（同步版，给搜索结果页用）。
     * 真实小红书搜索"用户"分类会同时检索账号与昵称；此处两条 LIKE 即可覆盖。
     */
    @Query("SELECT * FROM users WHERE username LIKE '%' || :keyword || '%' OR nickname LIKE '%' || :keyword || '%' ORDER BY createTime DESC LIMIT :limit")
    List<UserEntity> searchUsersSync(String keyword, int limit);
}

