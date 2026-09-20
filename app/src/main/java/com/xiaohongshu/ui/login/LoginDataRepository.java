package com.xiaohongshu.ui.login;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.network.RemoteApiException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login data repository
 * Handles user login and registration logic
 */
public class LoginDataRepository {
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_REGISTERED_USERS = "registered_users";
    private static final String KEY_CURRENT_USER = "current_user";
    
    private static LoginDataRepository instance;
    private final Context context;
    private final Gson gson;
    private final ExecutorService executorService;
    private final RemoteApiClient remoteApiClient;

    private LoginDataRepository(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.executorService = Executors.newSingleThreadExecutor();
        this.remoteApiClient = new RemoteApiClient(this.context);
    }

    public static synchronized LoginDataRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LoginDataRepository(context);
        }
        return instance;
    }

    private void saveRegisteredUsers(List<UserBean> users) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(KEY_REGISTERED_USERS, gson.toJson(users));
        editor.apply();
    }

    private List<UserBean> getRegisteredUsers() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String usersJson = sp.getString(KEY_REGISTERED_USERS, "[]");
        try {
            Type listType = new TypeToken<List<UserBean>>(){}.getType();
            return gson.fromJson(usersJson, listType);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveLoginUser(UserBean user) {
        // 明文密码不落盘：会话只保存 token 与必要资料
        user.setPassword(null);
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(KEY_CURRENT_USER, gson.toJson(user));
        editor.apply();
    }

    public UserBean getCurrentUser() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String userJson = sp.getString(KEY_CURRENT_USER, null);
        try {
            if (userJson == null || userJson.isEmpty()) {
                return null;
            }
            UserBean user = gson.fromJson(userJson, UserBean.class);
            // 确保返回的UserBean与数据库中的UserEntity保持同步
            // 这里不直接修改，而是由调用方负责从数据库获取最新的UserEntity
            return user;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 滑动续期：对齐 Web 创作服务平台的"token 过期自动续期"。
     * 服务端 /api/auth/refresh 依据当前有效 token 签发新 token（7 天有效期），
     * 在 token 仍有效时调用即可延长会话；token 已失效或后台不可达时静默保留原状，
     * 由后续 401 交互兜底（重新登录）。
     * 调用方应在后台线程执行。
     */
    public void refreshSessionToken() {
        try {
            UserBean current = getCurrentUser();
            if (current == null || current.getToken() == null || current.getToken().isEmpty()) return;
            String fresh = remoteApiClient.refreshAccessToken(current.getToken());
            if (fresh != null && !fresh.isEmpty() && !fresh.equals(current.getToken())) {
                current.setToken(fresh);
                saveLoginUser(current);
            }
        } catch (Exception ignored) {
            // 续期失败：保留旧 token（可能仍有效），不打断用户
        }
    }

    public void clearLoginState() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.remove(KEY_CURRENT_USER);
        editor.apply();
    }

    public UserBean login(String username, String password) throws IllegalArgumentException {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }

        try {
            UserBean remoteUser = remoteApiClient.login(username.trim(), password);
            // 登录成功前同步完成本地用户 upsert，保证进入主页后 currentUserId 立即可用
            ensureUserEntityExists(remoteUser);
            saveLoginUser(remoteUser);
            return remoteUser;
        } catch (RemoteApiException error) {
            throw new IllegalArgumentException(error.getMessage());
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception ignored) {
            // The local Room-backed demo remains usable when the backend is offline.
        }

        List<UserBean> registeredUsers = getRegisteredUsers();
        UserBean matchedUser = null;
        for (UserBean user : registeredUsers) {
            if (username.equals(user.getUsername())) {
                matchedUser = user;
                break;
            }
        }

        if (matchedUser == null) {
            throw new IllegalArgumentException("用户不存在，请先注册");
        }
        if (!password.equals(matchedUser.getPassword())) {
            throw new IllegalArgumentException("密码不正确");
        }

        // 确保UserEntity存在于数据库中
        ensureUserEntityExists(matchedUser);

        saveLoginUser(matchedUser);
        return matchedUser;
    }

    public UserBean register(String username, String password, String confirmPassword) throws IllegalArgumentException {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次密码输入不一致");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }

        try {
            UserBean remoteUser = remoteApiClient.register(username.trim(), password);
            createUserEntity(remoteUser);
            saveLoginUser(remoteUser);
            return remoteUser;
        } catch (RemoteApiException error) {
            throw new IllegalArgumentException(error.getMessage());
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception ignored) {
            // Fall back to the local store if no backend is reachable.
        }

        List<UserBean> registeredUsers = getRegisteredUsers();
        for (UserBean user : registeredUsers) {
            if (username.equals(user.getUsername())) {
                throw new IllegalArgumentException("用户名已被注册");
            }
        }

        UserBean newUser = new UserBean(
                username,
                password,
                "mock_token_" + System.currentTimeMillis(),
                null
        );

        List<UserBean> updatedUsers = new ArrayList<>(registeredUsers);
        updatedUsers.add(newUser);
        saveRegisteredUsers(updatedUsers);

        // 创建对应的UserEntity并插入数据库
        createUserEntity(newUser);

        return newUser;
    }

    /**
     * 创建UserEntity并插入数据库（同步执行，登录流程运行在后台线程）
     */
    private void createUserEntity(UserBean userBean) {
        try {
            AppDatabase database = AppApplication.getDatabase();
            if (database == null) {
                return;
            }

            // 检查用户是否已存在
            UserEntity existing = database.userDao().getUserByUsername(userBean.getUsername());
            if (existing != null) {
                String remoteAvatar = userBean.getImage();
                if (remoteAvatar != null && (remoteAvatar.startsWith("http://") || remoteAvatar.startsWith("https://"))
                        && !remoteAvatar.equals(existing.avatarUri)) {
                    existing.avatarUri = remoteAvatar;
                    database.userDao().update(existing);
                }
                return; // 用户已存在，仅同步头像地址
            }

            // 创建新的UserEntity（明文密码不写入本地数据库）
            UserEntity userEntity = new UserEntity();
            // 生成唯一的用户ID
            userEntity.id = String.valueOf(System.currentTimeMillis()) + "_" + (int)(Math.random() * 10000);
            userEntity.username = userBean.getUsername();

            // 根据用户名hash选择默认头像（p1-p16）
            int avatarIndex = Math.abs(userBean.getUsername().hashCode()) % 16 + 1;
            userEntity.avatar = getAvatarResource(avatarIndex);
            if (userBean.getImage() != null && (userBean.getImage().startsWith("http://") || userBean.getImage().startsWith("https://"))) {
                userEntity.avatarUri = userBean.getImage();
            }

            userEntity.nickname = userBean.getUsername();
            userEntity.bio = "";
            userEntity.createTime = System.currentTimeMillis();

            // 插入数据库
            database.userDao().insert(userEntity);
        } catch (Exception e) {
            android.util.Log.e("LoginDataRepository", "Error creating UserEntity", e);
        }
    }

    /**
     * 确保UserEntity存在于数据库中（同步执行，登录成功前完成 upsert）
     */
    private void ensureUserEntityExists(UserBean userBean) {
        try {
            AppDatabase database = AppApplication.getDatabase();
            if (database == null) {
                return;
            }
            if (database.userDao().getUserByUsername(userBean.getUsername()) == null) {
                createUserEntity(userBean);
            }
        } catch (Exception e) {
            android.util.Log.e("LoginDataRepository", "Error ensuring UserEntity exists", e);
        }
    }

    /**
     * 根据索引获取头像资源（使用p1-p11）
     */
    private int getAvatarResource(int index) {
        int[] avatars = {
            com.xiaohongshu.R.drawable.p1, com.xiaohongshu.R.drawable.p2,
            com.xiaohongshu.R.drawable.p3, com.xiaohongshu.R.drawable.p4,
            com.xiaohongshu.R.drawable.p5, com.xiaohongshu.R.drawable.p6,
            com.xiaohongshu.R.drawable.p7, com.xiaohongshu.R.drawable.p8,
com.xiaohongshu.R.drawable.p9, com.xiaohongshu.R.drawable.p10,
            com.xiaohongshu.R.drawable.p11,
            com.xiaohongshu.R.drawable.p12, com.xiaohongshu.R.drawable.p13, com.xiaohongshu.R.drawable.p14,
            com.xiaohongshu.R.drawable.p15, com.xiaohongshu.R.drawable.p16
        };
        return avatars[(index - 1) % avatars.length];
    }
}
