package com.xiaohongshu.network;

public class RemoteComment {
    private String id = "";
    private String userId = "";
    private String userName = "匿名用户";
    private String avatarUrl = "";
    private String content = "";
    private String createdAt = "";

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
    public void setId(String value) { id = value == null ? "" : value; }
    public void setUserId(String value) { userId = value == null ? "" : value; }
    public void setUserName(String value) { userName = value == null || value.isEmpty() ? "匿名用户" : value; }
    public void setAvatarUrl(String value) { avatarUrl = value == null ? "" : value; }
    public void setContent(String value) { content = value == null ? "" : value; }
    public void setCreatedAt(String value) { createdAt = value == null ? "" : value; }
}
