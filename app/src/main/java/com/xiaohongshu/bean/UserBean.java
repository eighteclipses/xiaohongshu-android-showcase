package com.xiaohongshu.bean;

public class UserBean {
    private String username;
    private String password;
    private String token;
    private String image;

    public UserBean() {
        this.image = "https://picsum.photos/200/200";
    }

    public UserBean(String username, String password) {
        this.username = username;
        this.password = password;
        this.image = "https://picsum.photos/200/200";
    }

    public UserBean(String username, String password, String token, String image) {
        this.username = username;
        this.password = password;
        this.token = token;
        this.image = image != null ? image : "https://picsum.photos/200/200";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}

