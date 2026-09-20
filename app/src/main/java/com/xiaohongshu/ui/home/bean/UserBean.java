package com.xiaohongshu.ui.home.bean;

import com.xiaohongshu.R;

/**
 * Description: 用户bean
 */
public class UserBean {
    private String id;
    private String name;
    private int image;
    private String imageUri;
    private UserInfoBean userInfo;

    public UserBean() {
        this.id = "";
        this.name = "";
        this.image = R.drawable.icon_mine;
        this.imageUri = "";
        this.userInfo = null;
    }

    public UserBean(String id) {
        this.id = id;
        this.name = "";
        this.image = R.drawable.icon_mine;
        this.imageUri = "";
        this.userInfo = null;
    }

    public UserBean(String id, String name, int image, UserInfoBean userInfo) {
        this.id = id;
        this.name = name != null ? name : "";
        this.image = image;
        this.imageUri = "";
        this.userInfo = userInfo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public String getImageUri() {
        return imageUri == null ? "" : imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri == null ? "" : imageUri;
    }

    public UserInfoBean getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfoBean userInfo) {
        this.userInfo = userInfo;
    }
}

