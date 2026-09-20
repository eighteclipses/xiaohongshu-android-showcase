package com.xiaohongshu.activity.graphic;

import com.xiaohongshu.ui.home.bean.UserBean;

/**
 * 评论Bean类
 * 用于UI显示的评论数据
 */
public class CommentBean {
    private String id;
    private String noteId;
    private UserBean user;
    private String content;
    private String parentId;
    private long createTime;
    private int likes;
    
    public CommentBean() {
    }
    
    public CommentBean(String id, String noteId, UserBean user, String content, 
                      String parentId, long createTime, int likes) {
        this.id = id;
        this.noteId = noteId;
        this.user = user;
        this.content = content;
        this.parentId = parentId;
        this.createTime = createTime;
        this.likes = likes;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getNoteId() {
        return noteId;
    }
    
    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }
    
    public UserBean getUser() {
        return user;
    }
    
    public void setUser(UserBean user) {
        this.user = user;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    /** 当前用户是否已赞该评论（本地维护，用于点赞红心高亮） */
    private boolean likedByMe;

    public boolean isLikedByMe() {
        return likedByMe;
    }

    public void setLikedByMe(boolean likedByMe) {
        this.likedByMe = likedByMe;
    }

    /** 回复数（仅根评论使用，用于「展开 N 条回复」） */
    private int replyCount;

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }
}

