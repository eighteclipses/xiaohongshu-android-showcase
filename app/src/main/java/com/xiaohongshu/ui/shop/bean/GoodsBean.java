package com.xiaohongshu.ui.shop.bean;

/**
 * Goods bean for shop
 */
public class GoodsBean {
    private String id;
    private String title;      // 标题
    private int image;         // 封面
    private double price;      // 价格
    private double discount;   // 折扣
    private int times;         // 购买次数

    public GoodsBean() {
    }

    public GoodsBean(String id, String title, int image, double price, double discount, int times) {
        this.id = id;
        this.title = title;
        this.image = image;
        this.price = price;
        this.discount = discount;
        this.times = times;
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

    public int getImage() {
        return image;
    }

    public void setImage(int image) {
        this.image = image;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public int getTimes() {
        return times;
    }

    public void setTimes(int times) {
        this.times = times;
    }
}

