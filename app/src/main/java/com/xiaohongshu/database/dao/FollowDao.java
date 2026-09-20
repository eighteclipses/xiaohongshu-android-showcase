package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xiaohongshu.database.entity.FollowEntity;

import java.util.List;

/**
 * 关注数据访问对象
 * 提供关注的增删查操作
 */
@Dao
public interface FollowDao {
    /**
     * 插入关注记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FollowEntity follow);
    
    /**
     * 删除关注记录
     */
    @Delete
    void delete(FollowEntity follow);
    
    /**
     * 检查用户是否已关注
     */
    @Query("SELECT * FROM follows WHERE followerId = :followerId AND followingId = :followingId LIMIT 1")
    FollowEntity checkFollow(String followerId, String followingId);
    
    /**
     * 取消关注
     */
    @Query("DELETE FROM follows WHERE followerId = :followerId AND followingId = :followingId")
    void unfollow(String followerId, String followingId);
    
    /**
     * 查询用户的关注列表
     */
    @Query("SELECT followingId FROM follows WHERE followerId = :userId ORDER BY createTime DESC")
    LiveData<List<String>> getFollowingIds(String userId);
    
    /**
     * 查询用户的关注列表（同步）
     */
    @Query("SELECT followingId FROM follows WHERE followerId = :userId ORDER BY createTime DESC")
    List<String> getFollowedUserIdsSync(String userId);
    
    /**
     * 查询用户的粉丝列表
     */
    @Query("SELECT followerId FROM follows WHERE followingId = :userId ORDER BY createTime DESC")
    LiveData<List<String>> getFollowerIds(String userId);
    
    /**
     * 查询用户的关注数
     */
    @Query("SELECT COUNT(*) FROM follows WHERE followerId = :userId")
    LiveData<Integer> getFollowingCount(String userId);
    
    /**
     * 查询用户的粉丝数
     */
    @Query("SELECT COUNT(*) FROM follows WHERE followingId = :userId")
    LiveData<Integer> getFollowerCount(String userId);
    
    /**
     * 查询用户的粉丝列表（同步）
     */
    @Query("SELECT followerId FROM follows WHERE followingId = :userId ORDER BY createTime DESC")
    List<String> getFollowerIdsSync(String userId);
}

