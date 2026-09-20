package com.xiaohongshu.ui.message.bean;

import com.xiaohongshu.ui.home.bean.UserBean;

/**
 * Recommend friend bean
 */
public class RecommendFriendBean {
    private String id;
    private UserBean user;
    private String reason;     // 推荐理由

    public RecommendFriendBean() {
        this.id = "";
        this.reason = "";
    }

    public RecommendFriendBean(String id, UserBean user, String reason) {
        this.id = id;
        this.user = user;
        this.reason = reason;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

