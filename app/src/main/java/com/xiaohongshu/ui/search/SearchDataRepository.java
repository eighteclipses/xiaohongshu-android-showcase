package com.xiaohongshu.ui.search;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.ui.search.bean.HotspotBean;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Search data repository
 * 热点来自本地真实笔记（话题聚合 + 点赞热度），无数据时用固定兜底清单；
 * 搜索历史持久化到 SharedPreferences，重启不丢。
 * 所有方法涉及数据库/SP 读取，必须在后台线程调用。
 */
public class SearchDataRepository {
    private static final String PREF_NAME_SEARCH = "search_prefs";
    private static final String KEY_HISTORY = "search_history";
    private static final int MAX_HISTORY = 20;

    /** 无本地笔记时的兜底热点（固定内容，不再随机生成） */
    private static final String[] FALLBACK_HOTSPOTS = {
        "春日穿搭", "抹茶蛋糕", "云南旅行", "摄影技巧", "房间改造",
        "护肤routine", "健身打卡", "好书推荐", "音乐会", "手绘插画"
    };

    private static SearchDataRepository instance;
    private final AppDatabase database;
    private final Gson gson = new Gson();

    private SearchDataRepository() {
        this.database = AppApplication.getDatabase();
    }

    public static synchronized SearchDataRepository getInstance() {
        if (instance == null) {
            instance = new SearchDataRepository();
        }
        return instance;
    }

    /** 基于真实笔记生成热点：话题聚合点赞热度，同数据多次调用结果稳定 */
    public List<HotspotBean> getHotspotList() {
        Map<String, Double> heatByTitle = new HashMap<>();
        try {
            List<NoteEntity> notes = database.noteDao().getAllPublicNotesSync();
            if (notes != null) {
                for (NoteEntity note : notes) {
                    if (note == null) continue;
                    double likes = database.likeDao().getLikeCountSync(note.id);
                    List<String> keys = new ArrayList<>();
                    if (note.topics != null) keys.addAll(note.topics);
                    if (keys.isEmpty() && note.title != null && !note.title.trim().isEmpty()) {
                        keys.add(note.title.trim());
                    }
                    for (String key : keys) {
                        String title = key.trim();
                        if (title.isEmpty()) continue;
                        Double current = heatByTitle.get(title);
                        heatByTitle.put(title, (current == null ? 0 : current) + likes + 1);
                    }
                }
            }
        } catch (Exception ignored) {
            // 数据库异常时退回兜底清单
        }

        List<HotspotBean> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : heatByTitle.entrySet()) {
            result.add(new HotspotBean("topic_" + entry.getKey(), entry.getKey(), entry.getValue(), "话题"));
        }
        result.sort((o1, o2) -> {
            int byHeat = Double.compare(o2.getHeat(), o1.getHeat());
            return byHeat != 0 ? byHeat : o1.getTitle().compareTo(o2.getTitle());
        });
        if (result.size() > 20) {
            result = new ArrayList<>(result.subList(0, 20));
        }
        // 兜底：真实热点不足时补固定条目，保证热点区不空
        for (int i = 0; result.size() < 10 && i < FALLBACK_HOTSPOTS.length; i++) {
            String title = FALLBACK_HOTSPOTS[i];
            if (heatByTitle.containsKey(title)) continue;
            result.add(new HotspotBean("fallback_" + i, title, 500.0 - i * 10, "推荐"));
        }
        return result;
    }

    /** 猜你想搜：从真实笔记标题提取固定顺序的候选词 */
    public List<String> getSuggestionList() {
        List<String> suggestions = new ArrayList<>();
        try {
            List<NoteEntity> notes = database.noteDao().getAllPublicNotesSync();
            if (notes != null) {
                for (NoteEntity note : notes) {
                    if (note == null || note.title == null || note.title.trim().isEmpty()) continue;
                    String title = note.title.trim();
                    if (!suggestions.contains(title)) suggestions.add(title);
                    if (suggestions.size() >= 10) break;
                }
            }
        } catch (Exception ignored) {
        }
        if (suggestions.isEmpty()) {
            for (String title : FALLBACK_HOTSPOTS) suggestions.add(title);
        }
        return suggestions;
    }

    /**
     * 搜索联想词：用户输入过程中按前缀匹配本地笔记标题与历史搜索。
     * 真实小红书"联想"是输入即下拉推荐，本项目用 LIKE 模糊匹配本地数据近似实现。
     * 输入为空时返回空列表（由调用方决定是否退回默认建议）。
     */
    public List<String> getSuggestionsByPrefix(String prefix) {
        if (prefix == null) return new ArrayList<>();
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) return new ArrayList<>();

        List<String> result = new ArrayList<>();
        // 笔记标题包含匹配：真实小红书主信号
        try {
            List<NoteEntity> notes = database.noteDao().getAllPublicNotesSync();
            if (notes != null) {
                for (NoteEntity note : notes) {
                    if (note == null || note.title == null) continue;
                    String title = note.title.trim();
                    if (title.isEmpty() || result.contains(title)) continue;
                    if (title.contains(trimmed)) result.add(title);
                    if (result.size() >= 8) break;
                }
            }
        } catch (Exception ignored) { }
        // 历史搜索补足：用户搜过什么最可能想再搜
        try {
            List<String> history = getHistory();
            for (String h : history) {
                if (h == null || result.contains(h)) continue;
                if (h.contains(trimmed)) result.add(h);
                if (result.size() >= 10) break;
            }
        } catch (Exception ignored) { }
        return result;
    }

    public List<String> getHistory() {
        try {
            SharedPreferences sp = AppApplication.getAppContext()
                    .getSharedPreferences(PREF_NAME_SEARCH, Context.MODE_PRIVATE);
            String json = sp.getString(KEY_HISTORY, "[]");
            List<String> history = gson.fromJson(json, TypeToken.getParameterized(List.class, String.class).getType());
            return history != null ? history : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveHistory(List<String> history) {
        try {
            SharedPreferences sp = AppApplication.getAppContext()
                    .getSharedPreferences(PREF_NAME_SEARCH, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_HISTORY, gson.toJson(history)).apply();
        } catch (Exception ignored) {
        }
    }

    public void clearHistory() {
        saveHistory(new ArrayList<>());
    }

    /** 去重置顶后保存，返回最新历史列表 */
    public List<String> addToHistory(String keyword) {
        List<String> history = getHistory();
        history.remove(keyword);
        history.add(0, keyword);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        saveHistory(history);
        return history;
    }
}
