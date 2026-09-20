package com.xiaohongshu.ui.search.bean;

/**
 * Hotspot bean for search
 */
public class HotspotBean {
    private String id;
    private String title;
    private double heat;       // 热度
    private String label;      // 标签

    public HotspotBean() {
        this.id = "";
        this.title = "";
        this.heat = 0.0;
        this.label = "";
    }

    public HotspotBean(String id, String title, double heat, String label) {
        this.id = id;
        this.title = title;
        this.heat = heat;
        this.label = label != null ? label : "";
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

    public double getHeat() {
        return heat;
    }

    public void setHeat(double heat) {
        this.heat = heat;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

