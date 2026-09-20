package com.xiaohongshu.network;

import java.util.ArrayList;
import java.util.List;

/** Normalized post returned by the Express backend. */
public class RemotePost {
    private String status = "approved", reviewNote = "", authorUsername = "", location = "";
    private int contentVersion = 1;
    private boolean isPublic = true, isDraft = false, deleted = false;
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String value) { reviewNote = value; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean value) { isPublic = value; }
    public boolean isDraft() { return isDraft; }
    public void setDraft(boolean value) { isDraft = value; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean value) { deleted = value; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String value) { authorUsername = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }

    private String id = "";
    private String title = "";
    private String content = "";
    private String authorId = "";
    private String authorName = "匿名用户";
    private int likeCount;
    private int commentCount;
    private int collectionCount;
    private boolean liked;
    private boolean collected;
    private final List<String> images = new ArrayList<>();
    private final List<String> topics = new ArrayList<>();
    /** image | video；默认 image */
    private String mediaType = "image";
    /** 视频地址（可能是相对路径，播放前需补全 host） */
    private String videoUrl = "";
    /** 服务端创建时间（epoch 毫秒，解析失败为 0） */
    private long createdAt;
    private long updatedAt;
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long value) { updatedAt=value; }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public int getCollectionCount() { return collectionCount; }
    public boolean isLiked() { return liked; }
    public boolean isCollected() { return collected; }
    public List<String> getImages() { return images; }
    public List<String> getTopics() { return topics; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String value) { mediaType = "video".equals(value) ? "video" : "image"; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String value) { videoUrl = value == null ? "" : value; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long value) { createdAt = value; }

    public void setId(String value) { id = value == null ? "" : value; }
    public void setTitle(String value) { title = value == null ? "" : value; }
    public void setContent(String value) { content = value == null ? "" : value; }
    public void setAuthorId(String value) { authorId = value == null ? "" : value; }
    public void setAuthorName(String value) { authorName = value == null || value.isEmpty() ? "匿名用户" : value; }
    public void setLikeCount(int value) { likeCount = value; }
    public void setCommentCount(int value) { commentCount = value; }
    public void setCollectionCount(int value) { collectionCount = value; }
    public void setLiked(boolean value) { liked = value; }
    public void setCollected(boolean value) { collected = value; }
}
