package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.NoteEntity;

import java.util.List;

/**
 * 笔记数据访问对象
 * 提供笔记的增删改查操作
 */
@Dao
public interface NoteDao {
    @Query("SELECT * FROM notes")
    List<NoteEntity> getModerationSyncNotes();
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND deleted = 0 ORDER BY createTime DESC")
    List<NoteEntity> getOwnSubmittedNotesSync(String userId);

    /**
     * 插入笔记
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(NoteEntity note);
    
    /**
     * 插入多个笔记
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<NoteEntity> notes);
    
    /**
     * 更新笔记
     */
    @Update
    void update(NoteEntity note);
    
    /**
     * 删除笔记
     */
    @Delete
    void delete(NoteEntity note);
    
    /**
     * 根据ID查询笔记
     */
    @Query("SELECT * FROM notes WHERE id = :id")
    NoteEntity getNoteById(String id);
    
    /**
     * 根据ID查询笔记（LiveData）
     */
    @Query("SELECT * FROM notes WHERE id = :id")
    LiveData<NoteEntity> getNoteByIdLive(String id);
    
    /**
     * 查询用户的所有笔记
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND deleted = 0 ORDER BY createTime DESC")
    LiveData<List<NoteEntity>> getNotesByUserId(String userId);
    
    /**
     * 查询用户的公开笔记（LiveData）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 ORDER BY createTime DESC")
    LiveData<List<NoteEntity>> getPublicNotesByUserId(String userId);
    
    /**
     * 查询用户的公开笔记（同步）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 ORDER BY createTime DESC")
    List<NoteEntity> getPublicNotesByUserIdSync(String userId);
    
    /**
     * 查询用户的私密笔记（LiveData）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 0 AND isDraft = 0 AND deleted = 0 ORDER BY createTime DESC")
    LiveData<List<NoteEntity>> getPrivateNotesByUserId(String userId);
    
    /**
     * 查询用户的私密笔记（同步）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 0 AND isDraft = 0 AND deleted = 0 ORDER BY createTime DESC")
    List<NoteEntity> getPrivateNotesByUserIdSync(String userId);
    
    /**
     * 查询用户的草稿（LiveData）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isDraft = 1 AND deleted = 0 ORDER BY updateTime DESC")
    LiveData<List<NoteEntity>> getDraftsByUserId(String userId);
    
    /**
     * 查询用户的草稿（同步）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isDraft = 1 AND deleted = 0 ORDER BY updateTime DESC")
    List<NoteEntity> getDraftsByUserIdSync(String userId);
    
    /**
     * 查询所有公开笔记（用于首页发现）
     */
    @Query("SELECT * FROM notes WHERE isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 ORDER BY createTime DESC")
    LiveData<List<NoteEntity>> getAllPublicNotes();
    
    /**
     * 查询所有公开笔记（同步版本，用于首页发现）
     */
    @Query("SELECT * FROM notes WHERE isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 ORDER BY createTime DESC")
    List<NoteEntity> getAllPublicNotesSync();
    
    /**
     * 统计用户的公开笔记数量（LiveData）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1")
    LiveData<Integer> countPublicNotes(String userId);
    
    /**
     * 统计用户的公开笔记数量（同步）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1")
    int countPublicNotesSync(String userId);
    
    /**
     * 统计用户的私密笔记数量（LiveData）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isPublic = 0 AND isDraft = 0 AND deleted = 0")
    LiveData<Integer> countPrivateNotes(String userId);
    
    /**
     * 搜索笔记（根据标题和内容）
     */
    @Query("SELECT * FROM notes WHERE (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%') AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 ORDER BY createTime DESC")
    List<NoteEntity> searchNotes(String keyword);
    
    /**
     * 统计用户的私密笔记数量（同步）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isPublic = 0 AND isDraft = 0 AND deleted = 0")
    int countPrivateNotesSync(String userId);
    
    /**
     * 统计用户的草稿数量（LiveData）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isDraft = 1 AND deleted = 0")
    LiveData<Integer> countDrafts(String userId);
    
    /**
     * 统计用户的草稿数量（同步）
     */
    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isDraft = 1 AND deleted = 0")
    int countDraftsSync(String userId);
    
    /**
     * 查询用户的所有笔记（同步）
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isDraft = 0 AND deleted = 0 ORDER BY createTime DESC")
    List<NoteEntity> getNotesByUserIdSync(String userId);

    /**
     * 按 id 集合批量查询笔记（他人主页「赞过」列表用）
     */
    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    List<NoteEntity> getNotesByIdsSync(List<String> ids);

    /**
     * 详情页相关推荐：取同作者其他公开笔记，排除当前笔记。
     * 真实小红书"相关推荐"主信号来自内容相似度（话题/标题关键词），作者是 fallback。
     * 联表/全文检索需要 FTS 扩展，暂用作者信号；与下面 getByTitleLike 一起由上层合并排序。
     */
    @Query("SELECT * FROM notes WHERE userId = :userId AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 AND id != :excludeId ORDER BY createTime DESC LIMIT :limit")
    List<NoteEntity> getRelatedByAuthorSync(String userId, String excludeId, int limit);

    /**
     * 详情页相关推荐：标题或正文包含关键词（已做 URL 编码的 SQL LIKE 占位）。
     * 排除当前笔记；与作者信号合并后按 createTime 取 Top N。
     */
    @Query("SELECT * FROM notes WHERE (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%') AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 AND id != :excludeId ORDER BY createTime DESC LIMIT :limit")
    List<NoteEntity> getRelatedByKeywordSync(String keyword, String excludeId, int limit);

    /**
     * 详情页相关推荐：按话题精确匹配（topics 在库中以 JSON 数组存储，
     * LIKE '%"话题"%' 的引号边界可避免"医学生"误匹配"大学生"这类子串）。
     */
    @Query("SELECT * FROM notes WHERE topics LIKE '%\"' || :topic || '\"%' AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 AND id != :excludeId ORDER BY createTime DESC LIMIT :limit")
    List<NoteEntity> getRelatedByTopicSync(String topic, String excludeId, int limit);

    /**
     * 按标题精确查找本地笔记：详情页以服务端 ID（seed_post_N）打开时，
     * 用标题把远程笔记映射回本地实体，使相关推荐等本地查询可用。
     */
    @Query("SELECT * FROM notes WHERE title = :title AND isPublic = 1 AND isDraft = 0 AND moderationStatus = 'approved' AND syncState = 'synced' AND deleted = 0 AND serverKnown = 1 LIMIT 1")
    NoteEntity getNoteByTitleSync(String title);
}

