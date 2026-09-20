package com.xiaohongshu.ui.home.bean;

import com.xiaohongshu.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Description: 图文卡片bean
 */
public class GraphicCardBean {
    private boolean needsReviewEdit;
    public boolean needsReviewEdit(){ return needsReviewEdit; }
    public void setNeedsReviewEdit(boolean value){ needsReviewEdit=value; }
    private String id;
    private String title;
    private String content;
    private String imageUri;
    /** 笔记全部图片（详情页轮播用），至少包含 imageUri 的值 */
    private List<String> imageUris = new ArrayList<>();
    private String videoUri;
    private int image;
    private int video;
    private UserBean user;
    private int likes;
    private GraphicCardType type;
    /** 排序用时间戳（笔记创建时间，epoch 毫秒；远程卡片由 created_at 解析） */
    private long sortTime;

    public GraphicCardBean() {
        this.id = "";
        this.title = "";
        this.content = "";
        this.imageUri = "";
        this.videoUri = "";
        this.image = R.drawable.image_1;
        this.video = 0;
        this.likes = 0;
        this.type = GraphicCardType.Graphic;
    }

    public GraphicCardBean(String id, String title, int image, int video, UserBean user, int likes, GraphicCardType type) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
        this.content = "";
        this.imageUri = "";
        this.image = image;
        this.video = video;
        this.user = user;
        this.likes = likes;
        this.type = type != null ? type : GraphicCardType.Graphic;
    }

    /** 设置主图时同步维护多图列表，保证 imageUris 至少含一张 */
    public void setImageUri(String imageUri) {
        this.imageUri = imageUri == null ? "" : imageUri;
        if (!getImageUri().isEmpty() && !imageUris.contains(this.imageUri)) {
            imageUris.add(0, this.imageUri);
        }
    }

    public List<String> getImageUris() {
        List<String> result = new ArrayList<>();
        if (imageUris != null) result.addAll(imageUris);
        if (result.isEmpty() && !getImageUri().isEmpty()) result.add(getImageUri());
        return result;
    }

    public void setImageUris(List<String> uris) {
        imageUris = uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        if (!imageUris.isEmpty()) {
            imageUri = imageUris.get(0);
        }
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
        return content == null ? "" : content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public String getImageUri() {
        return imageUri == null ? "" : imageUri;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public int getVideo() {
        return video;
    }

    public void setVideo(int video) {
        this.video = video;
    }

    public String getVideoUri() {
        return videoUri == null ? "" : videoUri;
    }

    public void setVideoUri(String videoUri) {
        this.videoUri = videoUri == null ? "" : videoUri;
    }

    public UserBean getUser() {
        return user;
    }

    public void setUser(UserBean user) {
        this.user = user;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public GraphicCardType getType() {
        return type;
    }

    public void setType(GraphicCardType type) {
        this.type = type;
    }

    public long getSortTime() {
        return sortTime;
    }

    public void setSortTime(long sortTime) {
        this.sortTime = sortTime;
    }
}

