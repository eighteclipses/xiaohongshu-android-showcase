package com.xiaohongshu.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.xiaohongshu.database.entity.MessageEntity;

import java.util.List;

/**
 * 消息数据访问对象
 * 提供消息的增删改查操作
 */
@Dao
public interface MessageDao {
    /**
     * 插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MessageEntity message);
    
    /**
     * 插入多个消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MessageEntity> messages);
    
    /**
     * 更新消息
     */
    @Update
    void update(MessageEntity message);
    
    /**
     * 删除消息
     */
    @Delete
    void delete(MessageEntity message);
    
    /**
     * 根据ID查询消息
     */
    @Query("SELECT * FROM messages WHERE id = :id")
    MessageEntity getMessageById(String id);
    
    /**
     * 查询用户收到的所有消息（LiveData）
     */
    @Query("SELECT * FROM messages WHERE toUserId = :userId ORDER BY createTime DESC")
    LiveData<List<MessageEntity>> getMessagesByUserId(String userId);
    
    /**
     * 查询用户收到的所有消息（同步）
     */
    @Query("SELECT * FROM messages WHERE toUserId = :userId ORDER BY createTime DESC")
    List<MessageEntity> getMessagesByUserIdSync(String userId);
    
    /**
     * 根据类型查询用户的消息（LiveData）
     */
    @Query("SELECT * FROM messages WHERE toUserId = :userId AND type = :type ORDER BY createTime DESC")
    LiveData<List<MessageEntity>> getMessagesByUserIdAndType(String userId, int type);
    
    /**
     * 根据类型查询用户的消息（同步）
     */
    @Query("SELECT * FROM messages WHERE toUserId = :userId AND type = :type ORDER BY createTime DESC")
    List<MessageEntity> getMessagesByUserIdAndTypeSync(String userId, int type);

    /**
     * 根据多个类型查询用户的消息（同步），如赞与收藏 [0,1]
     */
    @Query("SELECT * FROM messages WHERE toUserId = :userId AND type IN (:types) ORDER BY createTime DESC")
    List<MessageEntity> getMessagesByUserIdAndTypeInSync(String userId, List<Integer> types);

    /**
     * 查询未读消息数（同步）
     */
    @Query("SELECT COUNT(*) FROM messages WHERE toUserId = :userId AND isRead = 0")
    int getUnreadMessageCountSync(String userId);
    /**
     * 查询未读消息数
     */
    @Query("SELECT COUNT(*) FROM messages WHERE toUserId = :userId AND isRead = 0")
    LiveData<Integer> getUnreadMessageCount(String userId);
    
    /**
     * 标记消息为已读
     */
    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    void markAsRead(String messageId);
    
    /**
     * 标记用户的所有消息为已读
     */
    @Query("UPDATE messages SET isRead = 1 WHERE toUserId = :userId")
    void markAllAsRead(String userId);
}

