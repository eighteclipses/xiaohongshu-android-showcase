package com.xiaohongshu.ui.message.bean;

import com.xiaohongshu.ui.home.bean.UserBean;

/**
 * Message bean
 */
public class MessageBean {
    private String id;
    private UserBean user;
    private String title;
    private String content;
    private String time;
    private boolean isNew;      // 新消息提醒

    public MessageBean() {
        this.id = "";
        this.title = "";
        this.content = "";
        this.time = "";
        this.isNew = false;
    }

    public MessageBean(String id, UserBean user, String title, String content, String time, boolean isNew) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.content = content;
        this.time = time;
        this.isNew = isNew;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserBean getUser() {
        return user;
    }

    public void setUser(UserBean user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
}

