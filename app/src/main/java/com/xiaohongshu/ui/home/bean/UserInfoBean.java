package com.xiaohongshu.ui.home.bean;

/**
 * Description: 用户信息bean
 */
public class UserInfoBean {
    private int age;
    private int sex;
    private String address;

    public UserInfoBean() {
        this.age = 0;
        this.sex = 0;
        this.address = "";
    }

    public UserInfoBean(int age, int sex, String address) {
        this.age = age;
        this.sex = sex;
        this.address = address != null ? address : "";
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getSex() {
        return sex;
    }

    public void setSex(int sex) {
        this.sex = sex;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}

