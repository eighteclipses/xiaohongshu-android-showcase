package com.xiaohongshu.network.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 后台统一响应包络：{code, message, data}。
 * data 使用 JsonElement 承载，兼容对象（笔记、用户）与数组（评论列表）两种形态。
 */
public class ApiResponse {
    private Integer code;
    private String message;
    private JsonElement data;

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message == null ? "" : message;
    }

    public JsonElement getData() {
        return data;
    }

    /** data 为对象时返回，否则返回空对象，便于按字段解析。 */
    public JsonObject getDataAsObject() {
        return data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject();
    }

    /** data 为数组时返回，否则返回 null。 */
    public JsonArray getDataAsArray() {
        return data != null && data.isJsonArray() ? data.getAsJsonArray() : null;
    }
}
