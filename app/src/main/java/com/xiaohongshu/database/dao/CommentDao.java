package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.CommentEntity;

import java.util.List;

/**
 * 评论数据访问对象
 * 提供评论的增删改查操作
 */
@Dao
public interface CommentDao {
    /**
     * 插入评论
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CommentEntity comment);
    
    /**
     * 插入多个评论
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CommentEntity> comments);
    
    /**
     * 更新评论
     */
    @Update
    void update(CommentEntity comment);
    
    /**
     * 删除评论
     */
    @Delete
    void delete(CommentEntity comment);
    
    /**
     * 根据ID查询评论
     */
    @Query("SELECT * FROM comments WHERE id = :id")
    CommentEntity getCommentById(String id);
    
    /**
     * 查询笔记的所有评论（按时间排序，LiveData）
     */
    @Query("SELECT * FROM comments WHERE noteId = :noteId ORDER BY createTime ASC")
    LiveData<List<CommentEntity>> getCommentsByNoteId(String noteId);
    
    /**
     * 查询笔记的所有评论（按时间排序，同步）
     */
    @Query("SELECT * FROM comments WHERE noteId = :noteId ORDER BY createTime ASC")
    List<CommentEntity> getCommentsByNoteIdSync(String noteId);
    
    /**
     * 查询笔记的评论数
     */
    @Query("SELECT COUNT(*) FROM comments WHERE noteId = :noteId")
    LiveData<Integer> getCommentCount(String noteId);

    /**
     * 查询笔记的评论数（同步）
     */
    @Query("SELECT COUNT(*) FROM comments WHERE noteId = :noteId")
    int getCommentCountSync(String noteId);

    /**
     * 查询用户的所有评论（LiveData）
     */
    @Query("SELECT * FROM comments WHERE userId = :userId ORDER BY createTime DESC")
    LiveData<List<CommentEntity>> getCommentsByUserId(String userId);
    
    /**
     * 查询用户的所有评论（同步）
     */
    @Query("SELECT * FROM comments WHERE userId = :userId ORDER BY createTime DESC")
    List<CommentEntity> getCommentsByUserIdSync(String userId);
    
    /**
     * 查询评论的回复
     */
    @Query("SELECT * FROM comments WHERE parentId = :parentId ORDER BY createTime ASC")
    LiveData<List<CommentEntity>> getRepliesByParentId(String parentId);
    
    /**
     * 删除笔记的所有评论
     */
    @Query("DELETE FROM comments WHERE noteId = :noteId")
    void deleteCommentsByNoteId(String noteId);
}

