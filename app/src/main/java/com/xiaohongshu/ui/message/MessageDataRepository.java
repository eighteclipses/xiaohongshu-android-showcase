package com.xiaohongshu.ui.message;

import android.content.Context;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.MessageEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.message.bean.MessageBean;
import com.xiaohongshu.ui.message.bean.RecommendFriendBean;
import com.xiaohongshu.ui.home.bean.UserBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 消息数据仓库
 * 从数据库加载消息数据
 */
public class MessageDataRepository {
    private static MessageDataRepository instance;
    private final AppDatabase database;
    private final ExecutorService executorService;
    // 多线程读写字段，volatile 保证可见性
    private volatile String currentUserId = "";
    private volatile String resolvedUsername = null;

    private MessageDataRepository(Context context) {
        this.database = AppApplication.getDatabase();
        this.executorService = Executors.newSingleThreadExecutor();

        // 同步解析当前登录用户ID（库已允许主线程查询，避免异步初始化的竞态）
        com.xiaohongshu.bean.UserBean currentUser =
            LoginDataRepository.getInstance(context).getCurrentUser();
        resolvedUsername = currentUser == null ? "" : currentUser.getUsername();
        if (currentUser != null) {
            UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                currentUserId = userEntity.id;
            }
        }
    }

    public static synchronized MessageDataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new MessageDataRepository(context);
        }
        // 单例跨登录复用：登录用户变更时重新解析 userId
        instance.ensureCurrentUser(context);
        return instance;
    }

    private void ensureCurrentUser(Context context) {
        com.xiaohongshu.bean.UserBean currentUser =
            LoginDataRepository.getInstance(context).getCurrentUser();
        String username = currentUser == null ? "" : currentUser.getUsername();
        if (username.equals(resolvedUsername)) return;
        synchronized (this) {
            if (username.equals(resolvedUsername)) return;
            UserEntity userEntity = username.isEmpty() ? null
                    : database.userDao().getUserByUsername(username);
            currentUserId = userEntity != null ? userEntity.id : "";
            resolvedUsername = username;
        }
    }
    
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onError(Exception error);
    }
    
    /**
     * 获取所有消息
     */
    public void getMessageList(DataCallback<List<MessageBean>> callback) {
        executorService.execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                com.xiaohongshu.network.ModerationSync.refresh(AppApplication.getAppContext());
                List<MessageEntity> entities = getMessagesByUserIdSync(currentUserId);
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                
                List<MessageBean> messages = convertToMessageBeans(entities);
                callback.onSuccess(messages);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 根据类型获取消息
     */
    public void getMessagesByType(int type, DataCallback<List<MessageBean>> callback) {
        getMessagesByTypes(java.util.Collections.singletonList(type), callback);
    }

    /**
     * 根据多个类型获取消息，如赞与收藏 [0, 1]、评论与@ [2, 4]
     */
    public void getMessagesByTypes(List<Integer> types, DataCallback<List<MessageBean>> callback) {
        executorService.execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }

                List<MessageEntity> entities = database.messageDao()
                        .getMessagesByUserIdAndTypeInSync(currentUserId, types);
                if (entities == null) {
                    entities = new ArrayList<>();
                }

                List<MessageBean> messages = convertToMessageBeans(entities);
                callback.onSuccess(messages);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 查询未读消息数
     */
    public void getUnreadCount(DataCallback<Integer> callback) {
        executorService.execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    callback.onSuccess(0);
                    return;
                }
                callback.onSuccess(database.messageDao().getUnreadMessageCountSync(currentUserId));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    /**
     * 标记所有消息为已读
     */
    public void markAllAsRead() {
        executorService.execute(() -> {
            try {
                if (!currentUserId.isEmpty()) {
                    database.messageDao().markAllAsRead(currentUserId);
                }
            } catch (Exception ignored) {
            }
        });
    }
    
    /**
     * 将MessageEntity列表转换为MessageBean列表
     */
    private List<MessageBean> convertToMessageBeans(List<MessageEntity> entities) {
        List<MessageBean> messages = new ArrayList<>();
        if (entities == null) {
            return messages;
        }
        
        for (MessageEntity entity : entities) {
            // 加载发送者信息
            UserEntity fromUser = database.userDao().getUserById(entity.fromUserId);
            UserBean userBean = null;
            if (fromUser != null) {
                // 确保头像资源ID有效，如果无效则根据用户ID生成一个固定的头像索引
                int avatarRes;
                if (fromUser.avatar > 0) {
                    avatarRes = fromUser.avatar;
                } else if (fromUser.id != null) {
                    // 根据用户ID生成一个固定的索引，确保每次都返回相同的头像
                    int avatarIndex = Math.abs(fromUser.id.hashCode()) % 16 + 1;
                    avatarRes = getAvatarResource(avatarIndex);
                } else {
                    avatarRes = com.xiaohongshu.R.drawable.p1;
                }
                userBean = new UserBean(
                    fromUser.id,
                    fromUser.nickname != null ? fromUser.nickname : fromUser.username,
                    avatarRes,
                    null
                );
            } else {
                // 如果找不到用户，创建一个默认用户Bean
                userBean = new UserBean(
                    entity.fromUserId != null ? entity.fromUserId : "",
                    "用户",
                    com.xiaohongshu.R.drawable.p1,
                    null
                );
            }
            
            // 生成消息标题和内容
            String title = getMessageTitle(entity.type);
            String content = entity.content != null ? entity.content : "";
            
            // 格式化时间
            String time = formatTime(entity.createTime);
            
            // 使用relatedId作为消息的ID（用于跳转到笔记详情）
            String noteId = entity.relatedId != null ? entity.relatedId : entity.id;
            MessageBean message = new MessageBean(
                noteId,
                userBean,
                title,
                content,
                time,
                !entity.isRead
            );
            messages.add(message);
        }
        
        return messages;
    }
    
    /**
     * 根据消息类型获取标题
     */
    private String getMessageTitle(int type) {
        switch (type) {
            case 0:
                return "点赞";
            case 1:
                return "收藏";
            case 2:
                return "评论";
            case 3:
                return "关注";
            case 4:
                return "@我";
            case 5:
                return "审核通知";
            default:
                return "消息";
        }
    }
    
    /**
     * 格式化时间
     */
    private String formatTime(long time) {
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - time;
        
        if (diff < 60000) { // 1分钟内
            return "刚刚";
        } else if (diff < 3600000) { // 1小时内
            return (diff / 60000) + "分钟前";
        } else if (diff < 86400000) { // 1天内
            return (diff / 3600000) + "小时前";
        } else {
            return (diff / 86400000) + "天前";
        }
    }
    
    /**
     * 同步获取用户消息
     */
    private List<MessageEntity> getMessagesByUserIdSync(String userId) {
        return database.messageDao().getMessagesByUserIdSync(userId);
    }
    
    /**
     * 同步获取用户指定类型的消息
     */
    private List<MessageEntity> getMessagesByUserIdAndTypeSync(String userId, int type) {
        return database.messageDao().getMessagesByUserIdAndTypeSync(userId, type);
    }
    
    /**
     * 根据索引获取头像资源ID（p1.jpeg 到 p11.jpeg）
     */
    private int getAvatarResource(int index) {
        switch (index) {
            case 1: return com.xiaohongshu.R.drawable.p1;
            case 2: return com.xiaohongshu.R.drawable.p2;
            case 3: return com.xiaohongshu.R.drawable.p3;
            case 4: return com.xiaohongshu.R.drawable.p4;
            case 5: return com.xiaohongshu.R.drawable.p5;
            case 6: return com.xiaohongshu.R.drawable.p6;
            case 7: return com.xiaohongshu.R.drawable.p7;
            case 8: return com.xiaohongshu.R.drawable.p8;
            case 9: return com.xiaohongshu.R.drawable.p9;
            case 10: return com.xiaohongshu.R.drawable.p10;
            case 11: return com.xiaohongshu.R.drawable.p11;
            case 12: return com.xiaohongshu.R.drawable.p12;
            case 13: return com.xiaohongshu.R.drawable.p13;
            case 14: return com.xiaohongshu.R.drawable.p14;
            case 15: return com.xiaohongshu.R.drawable.p15;
            case 16: return com.xiaohongshu.R.drawable.p16;
            default: return com.xiaohongshu.R.drawable.p1;
        }
    }
    
    /**
     * 获取推荐好友列表
     */
    public void getRecommendFriendList(DataCallback<List<RecommendFriendBean>> callback) {
        executorService.execute(() -> {
            try {
                if (currentUserId.isEmpty()) {
                    callback.onSuccess(new ArrayList<>());
                    return;
                }
                
                // 获取所有用户（排除当前用户和已关注的用户）
                List<UserEntity> allUsers = database.userDao().getAllUsersSync();
                List<String> followedUserIds = database.followDao().getFollowedUserIdsSync(currentUserId);
                
                List<RecommendFriendBean> recommendFriends = new ArrayList<>();
                String[] reasons = {
                    "可能认识的人",
                    "共同关注",
                    "同城用户",
                    "热门用户",
                    "新注册用户"
                };
                
                int count = 0;
                for (UserEntity user : allUsers) {
                    if (count >= 10) break; // 最多推荐10个
                    
                    // 排除当前用户
                    if (user.id.equals(currentUserId)) {
                        continue;
                    }
                    
                    // 排除已关注的用户
                    if (followedUserIds != null && followedUserIds.contains(user.id)) {
                        continue;
                    }
                    
                    // 创建推荐好友
                    UserBean userBean = new UserBean(
                        user.id,
                        user.nickname != null ? user.nickname : user.username,
                        user.avatar,
                        null
                    );
                    
                    String reason = reasons[count % reasons.length];
                    RecommendFriendBean friend = new RecommendFriendBean(
                        user.id,
                        userBean,
                        reason
                    );
                    recommendFriends.add(friend);
                    count++;
                }
                
                callback.onSuccess(recommendFriends);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    /**
     * 从推荐列表中移除好友（这里只是标记，实际不删除数据库记录）
     * 由于推荐列表是动态生成的，这里只需要在UI层面处理即可
     */
    public void removeRecommendFriend(RecommendFriendBean friend) {
        // 推荐列表是动态生成的，不需要真正删除
        // 如果需要持久化，可以维护一个"已忽略"的推荐用户列表
    }
}
