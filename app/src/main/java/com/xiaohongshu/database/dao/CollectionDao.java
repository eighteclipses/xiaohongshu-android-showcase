package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xiaohongshu.database.entity.CollectionEntity;

import java.util.List;

/**
 * 收藏数据访问对象
 * 提供收藏的增删查操作
 */
@Dao
public interface CollectionDao {
    /**
     * 插入收藏记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CollectionEntity collection);
    
    /**
     * 插入多个收藏记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CollectionEntity> collections);
    
    /**
     * 删除收藏记录
     */
    @Delete
    void delete(CollectionEntity collection);
    
    /**
     * 检查用户是否已收藏笔记
     */
    @Query("SELECT * FROM collections WHERE noteId = :noteId AND userId = :userId LIMIT 1")
    CollectionEntity checkCollection(String noteId, String userId);
    
    /**
     * 删除用户的收藏记录
     */
    @Query("DELETE FROM collections WHERE noteId = :noteId AND userId = :userId")
    void deleteCollection(String noteId, String userId);
    
    /**
     * 查询笔记的收藏数（LiveData）
     */
    @Query("SELECT COUNT(*) FROM collections WHERE noteId = :noteId")
    LiveData<Integer> getCollectionCount(String noteId);
    
    /**
     * 查询笔记的收藏数（同步）
     */
    @Query("SELECT COUNT(*) FROM collections WHERE noteId = :noteId")
    int getCollectionCountSync(String noteId);
    
    /**
     * 查询用户收藏的所有笔记ID（LiveData）
     */
    @Query("SELECT noteId FROM collections WHERE userId = :userId ORDER BY createTime DESC")
    LiveData<List<String>> getCollectedNoteIds(String userId);
    
    /**
     * 查询用户收藏的所有笔记ID（同步）
     */
    @Query("SELECT noteId FROM collections WHERE userId = :userId ORDER BY createTime DESC")
    List<String> getCollectedNoteIdsSync(String userId);
    
    /**
     * 查询笔记的所有收藏记录
     */
    @Query("SELECT * FROM collections WHERE noteId = :noteId ORDER BY createTime DESC")
    LiveData<List<CollectionEntity>> getCollectionsByNoteId(String noteId);
    
    /**
     * 删除笔记的所有收藏记录
     */
    @Query("DELETE FROM collections WHERE noteId = :noteId")
    void deleteCollectionsByNoteId(String noteId);
}

