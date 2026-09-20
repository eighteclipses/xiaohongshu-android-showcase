package com.xiaohongshu.network;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Persists failed interaction requests until the next successful connection. */
public final class PendingSyncStore {
    private static final String PREFS = "pending_sync";
    private static final String KEY_ACTIONS = "actions";
    private static final int MAX_ACTIONS = 100;
    private static final Gson GSON = new Gson();

    private PendingSyncStore() { }

    public static synchronized void enqueue(Context context, String type, String id, String value) {
        List<Action> actions = read(context);
        Action action=new Action(type,id,value);
        com.xiaohongshu.bean.UserBean user=com.xiaohongshu.ui.login.LoginDataRepository.getInstance(context).getCurrentUser();
        action.username=user==null?"":user.getUsername();
        if ("publish".equals(type) || "draft".equals(type)) actions.removeIf(a->id.equals(a.id) && type.equals(a.type) && action.username.equals(a.username));
        actions.add(action);
        if (actions.size() > MAX_ACTIONS) actions = new ArrayList<>(actions.subList(actions.size() - MAX_ACTIONS, actions.size()));
        save(context, actions);
    }

    public static synchronized void completedNote(Context context,String id) {
        List<Action> actions=read(context);actions.removeIf(a->id.equals(a.id) && ("publish".equals(a.type)||"draft".equals(a.type)));save(context,actions);
    }
    public static synchronized List<Action> drain(Context context) {
        List<Action> actions = read(context);
        com.xiaohongshu.bean.UserBean user=com.xiaohongshu.ui.login.LoginDataRepository.getInstance(context).getCurrentUser();
        String username=user==null?"":user.getUsername();
        List<Action> mine=new ArrayList<>(),others=new ArrayList<>();
        for(Action action:actions) { if(username.equals(action.username))mine.add(action);else others.add(action); }
        save(context,others);return mine;
    }

    public static synchronized void restore(Context context, List<Action> actions) {
        if (actions == null || actions.isEmpty()) return;
        List<Action> pending = read(context);
        pending.addAll(actions);
        if (pending.size() > MAX_ACTIONS) pending = new ArrayList<>(pending.subList(pending.size() - MAX_ACTIONS, pending.size()));
        save(context, pending);
    }

    private static List<Action> read(Context context) {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIONS, "[]");
        Type type = new TypeToken<List<Action>>() { }.getType();
        try {
            List<Action> actions = GSON.fromJson(json, type);
            return actions == null ? new ArrayList<>() : actions;
        } catch (Exception ignored) { return new ArrayList<>(); }
    }

    private static void save(Context context, List<Action> actions) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIONS, GSON.toJson(actions)).apply();
    }

    public static class Action {
        public String type;
        public String id;
        public String value;
        public String username;
        public Action() { }
        public Action(String type, String id, String value) { this.type = type; this.id = id; this.value = value; }
    }
}
