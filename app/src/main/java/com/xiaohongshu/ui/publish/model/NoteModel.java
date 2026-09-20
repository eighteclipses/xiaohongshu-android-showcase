package com.xiaohongshu.ui.publish.model;

import java.util.ArrayList;
import java.util.List;

public class NoteModel {
    private String moderationStatus = "approved";
    private String reviewNote = "";
    private String syncState = "synced";
    private int contentVersion = 1;
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String value) { moderationStatus = value; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String value) { reviewNote = value; }
    public String getSyncState() { return syncState; }
    public void setSyncState(String value) { syncState = value; }
    public int getContentVersion() { return contentVersion; }
    public void setContentVersion(int value) { contentVersion = value; }

    private String id;
    private String title;
    private String content;
    private List<String> imageUris;
    private List<String> topics;
    private String location;
    private boolean isPublic;
    private long createTime;
    private long updateTime;
    private boolean isDraft;

    public NoteModel() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.imageUris = new ArrayList<>();
        this.topics = new ArrayList<>();
        this.isPublic = true;
        this.isDraft = true;
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
    }

    public NoteModel(String title, String content) {
        this();
        this.title = title;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public List<String> getImageUris() {
        return imageUris;
    }

    public void setImageUris(List<String> imageUris) {
        this.imageUris = imageUris != null ? imageUris : new ArrayList<>();
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics != null ? topics : new ArrayList<>();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public boolean isDraft() {
        return isDraft;
    }

    public void setDraft(boolean draft) {
        isDraft = draft;
    }
}

