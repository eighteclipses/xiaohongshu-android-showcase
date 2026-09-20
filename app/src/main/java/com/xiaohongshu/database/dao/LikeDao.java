package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xiaohongshu.database.entity.LikeEntity;

import java.util.List;

/**
 * 点赞数据访问对象
 * 提供点赞的增删查操作
 */
@Dao
public interface LikeDao {
    /**
     * 插入点赞记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LikeEntity like);
    
    /**
     * 插入多个点赞记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LikeEntity> likes);
    
    /**
     * 删除点赞记录
     */
    @Delete
    void delete(LikeEntity like);
    
    /**
     * 检查用户是否已点赞笔记
     */
    @Query("SELECT * FROM likes WHERE noteId = :noteId AND userId = :userId LIMIT 1")
    LikeEntity checkLike(String noteId, String userId);
    
    /**
     * 删除用户的点赞记录
     */
    @Query("DELETE FROM likes WHERE noteId = :noteId AND userId = :userId")
    void deleteLike(String noteId, String userId);
    
    /**
     * 查询笔记的点赞数（LiveData）
     */
    @Query("SELECT COUNT(*) FROM likes WHERE noteId = :noteId")
    LiveData<Integer> getLikeCount(String noteId);
    
    /**
     * 查询笔记的点赞数（同步）
     */
    @Query("SELECT COUNT(*) FROM likes WHERE noteId = :noteId")
    int getLikeCountSync(String noteId);
    
    /**
     * 查询用户点赞的所有笔记ID（LiveData）
     */
    @Query("SELECT noteId FROM likes WHERE userId = :userId ORDER BY createTime DESC")
    LiveData<List<String>> getLikedNoteIds(String userId);
    
    /**
     * 查询用户点赞的所有笔记ID（同步）
     */
    @Query("SELECT noteId FROM likes WHERE userId = :userId ORDER BY createTime DESC")
    List<String> getLikedNoteIdsSync(String userId);
    
    /**
     * 查询笔记的所有点赞记录
     */
    @Query("SELECT * FROM likes WHERE noteId = :noteId ORDER BY createTime DESC")
    LiveData<List<LikeEntity>> getLikesByNoteId(String noteId);
    
    /**
     * 删除笔记的所有点赞记录
     */
    @Query("DELETE FROM likes WHERE noteId = :noteId")
    void deleteLikesByNoteId(String noteId);
}

