package com.xiaohongshu.database.converter;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 字符串列表类型转换器
 * 用于Room数据库存储List<String>
 */
public class StringListConverter {
    private static Gson gson = new Gson();
    
    @TypeConverter
    public static List<String> fromString(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<String>>(){}.getType();
        return gson.fromJson(value, listType);
    }
    
    @TypeConverter
    public static String fromList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return gson.toJson(list);
    }
}

