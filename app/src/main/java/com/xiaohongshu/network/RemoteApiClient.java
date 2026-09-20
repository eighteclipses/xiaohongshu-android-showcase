package com.xiaohongshu.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaohongshu.R;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.network.api.ApiResponse;
import com.xiaohongshu.network.api.ApiService;
import com.xiaohongshu.ui.publish.model.NoteModel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Synchronous API facade used from repositories' worker threads.
 * Internally backed by Retrofit/OkHttp; public signatures unchanged.
 */
public class RemoteApiClient {
    private static final String TAG = "RemoteApiClient";
    private static final MediaType JSON = MediaType.parse("application/json; charset=UTF-8");

    private final String baseUrl;
    private final boolean debuggable;
    private final Gson gson = new Gson();
    private volatile ApiService service;

    public RemoteApiClient(Context context) {
        String configuredUrl = context.getString(R.string.backend_base_url);
        baseUrl = configuredUrl.endsWith("/")
                ? configuredUrl
                : configuredUrl + "/";
        debuggable = (context.getApplicationContext().getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private ApiService service() throws IOException {
        if (baseUrl.isEmpty() || baseUrl.equals("/")) throw new IOException("未配置后台地址");
        ApiService local = service;
        if (local == null) {
            synchronized (this) {
                if (service == null) {
                    HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
                    logging.setLevel(debuggable ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(15, TimeUnit.SECONDS)
                            .writeTimeout(15, TimeUnit.SECONDS)
                            .addInterceptor(logging)
                            .build();
                    service = new Retrofit.Builder()
                            .baseUrl(baseUrl)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService.class);
                }
                local = service;
            }
        }
        return local;
    }

    public UserBean login(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return parseUser(checked(service().login(jsonBody(body))));
    }

    public UserBean register(String username, String password) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("nickname", username);
        body.addProperty("password", password);
        return parseUser(checked(service().register(jsonBody(body))));
    }

    public UserBean updateProfile(String nickname, String bio, String avatar, String token) throws IOException {
        JsonObject body = new JsonObject();
        if (nickname != null) body.addProperty("nickname", nickname);
        if (bio != null) body.addProperty("bio", bio);
        if (avatar != null) body.addProperty("avatar", avatar);
        JsonObject data = checked(service().updateMe(bearer(token), jsonBody(body))).getDataAsObject();
        UserBean user = new UserBean(string(data, "username", string(data, "user_id", "")), "", token,
                string(data, "avatar", ""));
        if (user.getUsername().isEmpty()) throw new IOException("后台返回的用户数据不完整");
        return user;
    }

    public RemotePost saveDraft(NoteModel note, String token) throws IOException {
        RemotePost result = parsePost(checked(service().saveDraft(bearer(token), jsonBody(noteBody(note)))).getDataAsObject());
        ModerationSync.apply(result); return result;
    }

    public RemotePost publishNote(NoteModel note, String token) throws IOException {
        RemotePost result = parsePost(checked(service().publishPost(bearer(token), jsonBody(noteBody(note)))).getDataAsObject());
        ModerationSync.apply(result); return result;
    }
    public JsonObject getReviewNotices(int page, String token) throws IOException {
        return checked(service().getReviewNotices("moderation",page,50,bearer(token))).getDataAsObject();
    }
    public JsonObject syncPostStates(String ids, String token) throws IOException {
        return checked(service().syncPostStates(ids, bearer(token))).getDataAsObject();
    }
    public List<RemotePost> getCreatorPosts(int page, String filter, String token) throws IOException {
        return parsePosts(checked(service().getCreatorPosts(page, 50, filter, bearer(token))));
    }

    public List<RemotePost> getPosts(int page, int limit, String keyword, String token) throws IOException {
        return parsePosts(checked(service().getPosts(page, limit, keyword, bearer(token))));
    }

    public List<RemotePost> getUserPosts(String userId, int page, int limit, String token) throws IOException {
        return parsePosts(checked(service().getUserPosts(userId, page, limit, bearer(token))));
    }

    /** 用户公开详情（主页数据源）：返回原始 JsonObject（含 background/level/coins 等）。 */
    public JsonObject getUserProfile(String userId, String token) throws IOException {
        return checked(service().getUserProfile(userId, bearer(token))).getDataAsObject();
    }

    /** 拉取与某人的私信会话（返回 {user, can_send, messages:[...]} 原始对象）。 */
    public JsonObject getDmThread(String toId, String token) throws IOException {
        return checked(service().getDmThread(toId, bearer(token))).getDataAsObject();
    }

    /** 发送私信；对方未关注且已发过一条时服务端返回 403，由调用方提示送礼物解锁。 */
    public JsonObject sendDm(String toId, String content, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        return checked(service().sendDm(toId, jsonBody(body), bearer(token))).getDataAsObject();
    }

    /** 礼物目录（按对方等级定价），返回原始对象 {level, coins, gifts:[...]}。 */
    public JsonObject getGifts(String toId, String token) throws IOException {
        return checked(service().getGifts(toId, bearer(token))).getDataAsObject();
    }

    /** 送礼物：服务端校验并扣除薯币、对方获得收益、解锁无限私信，返回 {message, balance, earned, unlocked}。 */
    public JsonObject sendGift(String toId, String giftId, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("giftId", giftId);
        return checked(service().sendGift(toId, jsonBody(body), bearer(token))).getDataAsObject();
    }

    /** 钱包薯币余额（服务端为权威源）。 */
    public int getWalletCoins(String token) throws IOException {
        return checked(service().getWallet(bearer(token))).getDataAsObject().get("coins").getAsInt();
    }

    /** 充值薯币（演示支付，金额白名单见服务端），返回充值后的余额。 */
    public int rechargeCoins(int amount, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("amount", amount);
        return checked(service().recharge(bearer(token), jsonBody(body))).getDataAsObject().get("coins").getAsInt();
    }

    /** 单独更新主页背景图（PATCH auth/me）。 */
    public void updateBackground(String background, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("background", background == null ? "" : background);
        checked(service().updateMe(bearer(token), jsonBody(body)));
    }

    public List<RemotePost> getCollectedPosts(String userId, int page, int limit, String token) throws IOException {
        return parsePosts(checked(service().getUserCollections(userId, page, limit, bearer(token))), "collections");
    }

    public List<RemotePost> getLikedPosts(String userId, int page, int limit, String token) throws IOException {
        return parsePosts(checked(service().getUserLikes(userId, page, limit, bearer(token))));
    }

    public RemotePost getPost(String postId, String token) throws IOException {
        return parsePost(checked(service().getPost(postId, bearer(token))).getDataAsObject());
    }

    public RemotePost likePost(String postId, String token) throws IOException {
        return parsePost(checked(service().likePost(postId, bearer(token))).getDataAsObject());
    }

    public RemotePost unlikePost(String postId, String token) throws IOException {
        return parsePost(checked(service().unlikePost(postId, bearer(token))).getDataAsObject());
    }

    public RemotePost collectPost(String postId, String token) throws IOException {
        return parsePost(checked(service().collectPost(postId, bearer(token))).getDataAsObject());
    }

    public RemotePost uncollectPost(String postId, String token) throws IOException {
        return parsePost(checked(service().uncollectPost(postId, bearer(token))).getDataAsObject());
    }

    public void deletePost(String postId, String token) throws IOException {
        com.xiaohongshu.database.entity.NoteEntity local = com.xiaohongshu.app.AppApplication.getDatabase().noteDao().getNoteById(postId);
        JsonObject body = new JsonObject(); body.addProperty("content_version", local == null ? getPost(postId, token).getContentVersion() : local.contentVersion);
        checked(service().deletePost(postId, bearer(token), jsonBody(body)));
    }

    public List<RemoteComment> getComments(String postId) throws IOException {
        ApiResponse envelope = checked(service().getComments(postId));
        List<RemoteComment> comments = new ArrayList<>();
        JsonArray array = envelope.getDataAsArray();
        if (array != null) {
            for (JsonElement element : array) {
                if (element.isJsonObject()) comments.add(parseComment(element.getAsJsonObject()));
            }
        } else {
            JsonObject data = envelope.getDataAsObject();
            if (data.has("comments") && data.get("comments").isJsonArray()) {
                for (JsonElement element : data.getAsJsonArray("comments")) {
                    if (element.isJsonObject()) comments.add(parseComment(element.getAsJsonObject()));
                }
            }
        }
        return comments;
    }

    public RemoteComment addComment(String postId, String content, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        return parseComment(checked(service().addComment(postId, bearer(token), jsonBody(body))).getDataAsObject());
    }

    public boolean followUser(String userId, String token) throws IOException {
        JsonObject data = checked(service().followUser(userId, bearer(token))).getDataAsObject();
        return data.has("isFollowing") && data.get("isFollowing").getAsBoolean();
    }

    public boolean unfollowUser(String userId, String token) throws IOException {
        JsonObject data = checked(service().unfollowUser(userId, bearer(token))).getDataAsObject();
        return data.has("isFollowing") && data.get("isFollowing").getAsBoolean();
    }

    /** 私信会话列表：返回 {conversations:[{user, last_message, created_at}]} 原始对象。 */
    public JsonObject getDmConversations(String token) throws IOException {
        return checked(service().getDmConversations(bearer(token))).getDataAsObject();
    }

    public boolean getBlockStatus(String userId, String token) throws IOException {
        JsonObject data = checked(service().getBlockStatus(userId, bearer(token))).getDataAsObject();
        return data.has("blocked") && data.get("blocked").getAsBoolean();
    }

    public boolean blockUser(String userId, String token) throws IOException {
        JsonObject data = checked(service().blockUser(userId, bearer(token))).getDataAsObject();
        return data.has("blocked") && data.get("blocked").getAsBoolean();
    }

    public boolean unblockUser(String userId, String token) throws IOException {
        JsonObject data = checked(service().unblockUser(userId, bearer(token))).getDataAsObject();
        return data.has("blocked") && data.get("blocked").getAsBoolean();
    }

    public void recordView(String postId, String visitorKey, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("visitor_key", visitorKey);
        checked(service().recordView(postId, bearer(token), jsonBody(body)));
    }

    /**
     * 笔记举报。服务端按 (postId, userId) 对 pending 状态去重，
     * 重复提交返回 submitted=false 而非报错。
     */
    public void reportPost(String postId, String reason, String token) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("reason", reason);
        checked(service().reportPost(postId, bearer(token), jsonBody(body)));
    }

    /**
     * 服务端用户搜索：GET /api/users/search?keyword=。
     * 响应 data 为 { users: [...], pagination }，此处取 users 数组。
     */
    public List<JsonObject> searchRemoteUsers(String keyword, int limit) throws IOException {
        ApiResponse response = checked(service().searchUsers(keyword, limit));
        List<JsonObject> users = new ArrayList<>();
        if (response != null) {
            JsonObject data = response.getDataAsObject();
            if (data.has("users") && data.get("users").isJsonArray()) {
                for (JsonElement item : data.getAsJsonArray("users")) {
                    if (item.isJsonObject()) users.add(item.getAsJsonObject());
                }
            }
        }
        return users;
    }

    /**
     * 滑动续期：POST /api/auth/refresh（需携带当前有效 token，服务端直接签发新 token）。
     * 典型用法是在 token 临近过期或收到 401 前主动调用；token 已失效时此接口同样返回 401。
     */
    public String refreshAccessToken(String currentToken) throws IOException {
        ApiResponse response = checked(service().refreshToken(bearer(currentToken), jsonBody(new JsonObject())));
        JsonObject data = response == null ? null : response.getDataAsObject();
        if (data != null && data.has("access_token")) {
            return data.get("access_token").getAsString();
        }
        return null;
    }

    public List<String> uploadImages(Context context, List<String> imageUris, String token) throws IOException {
        List<String> uploaded = new ArrayList<>();
        if (imageUris == null || imageUris.isEmpty()) return uploaded;
        List<MultipartBody.Part> parts = new ArrayList<>();
        int index = 0;
        for (String value : imageUris) {
            if (value == null || value.trim().isEmpty()) continue;
            if (value.startsWith("android.resource://")) {
                // 包内 drawable 素材：读取字节真实上传，不再透传本地 URI（对其他设备无意义）
                byte[] bytes = readDrawableBytes(context, value);
                if (bytes == null) continue;
                parts.add(MultipartBody.Part.createFormData("files", "drawable-" + (++index) + ".jpg",
                        RequestBody.create(bytes, MediaType.parse("image/jpeg"))));
                continue;
            }
            Uri uri = Uri.parse(value);
            String rawMime = context.getContentResolver().getType(uri);
            final String mime = rawMime != null && rawMime.startsWith("image/") ? rawMime : "image/jpeg";
            parts.add(MultipartBody.Part.createFormData("files", "image-" + (++index) + ".jpg",
                    new RequestBody() {
                        @Override public MediaType contentType() { return MediaType.parse(mime); }

                        @Override public void writeTo(okio.BufferedSink sink) throws IOException {
                            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                                if (input == null) throw new IOException("无法读取图片: " + value);
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = input.read(buffer)) != -1) sink.write(buffer, 0, read);
                            }
                        }
                    }));
        }
        if (parts.isEmpty()) return uploaded;
        JsonObject data = checked(service().uploadImages(bearer(token), parts)).getDataAsObject();
        if (data.has("files") && data.get("files").isJsonArray()) {
            for (JsonElement item : data.getAsJsonArray("files")) {
                if (item.isJsonObject()) uploaded.add(string(item.getAsJsonObject(), "url", ""));
            }
        }
        return uploaded;
    }

    /** 读取 android.resource:// 包内 drawable 的字节；返回 null 表示资源不存在。 */
    private byte[] readDrawableBytes(Context context, String resourceUri) {
        try {
            android.content.res.Resources resources = context.getResources();
            String path = resourceUri.substring("android.resource://".length());
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            int resId = name.matches("\\d+")
                    ? Integer.parseInt(name)
                    : resources.getIdentifier(name, "drawable", context.getPackageName());
            if (resId == 0) return null;
            try (InputStream input = resources.openRawResource(resId)) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                return output.toByteArray();
            }
        } catch (Exception e) {
            Log.w(TAG, "读取 drawable 失败: " + resourceUri, e);
            return null;
        }
    }

    // ---- parsing helpers ----

    private RemotePost parsePost(JsonObject object) {
        RemotePost post = new RemotePost();
        post.setId(string(object, "id", ""));
        post.setStatus(string(object, "status", "approved"));
        post.setReviewNote(string(object, "review_note", ""));
        post.setContentVersion(Math.max(1, integer(object, "content_version")));
        post.setDeleted(object.has("deleted_at") && !object.get("deleted_at").isJsonNull());
        post.setPublic(!object.has("is_public") || object.get("is_public").getAsBoolean());
        post.setDraft(object.has("is_draft") && object.get("is_draft").getAsBoolean());
        post.setLocation(string(object, "location", ""));
        post.setTitle(string(object, "title", ""));
        post.setContent(string(object, "content", ""));
        post.setLikeCount(integer(object, "like_count"));
        post.setCommentCount(integer(object, "comment_count"));
        post.setCollectionCount(integer(object, "collection_count"));
        post.setLiked(object.has("liked") && object.get("liked").getAsBoolean());
        post.setCollected(object.has("collected") && object.get("collected").getAsBoolean());
        JsonObject user = object(object, "user");
        post.setAuthorId(string(user, "id", ""));
        post.setAuthorUsername(string(user, "username", ""));
        post.setAuthorName(string(user, "nickname", string(user, "username", "匿名用户")));
        addStrings(object, "images", post.getImages());
        addStrings(object, "topics", post.getTopics());
        post.setMediaType(string(object, "media_type", "image"));
        post.setVideoUrl(string(object, "video_url", ""));
        post.setCreatedAt(parseIsoTime(string(object, "created_at", "")));
        post.setUpdatedAt(parseIsoTime(string(object, "updated_at", "")));
        return post;
    }

    /** 解析后端 ISO8601 时间（created_at）为 epoch 毫秒，失败返回 0 */
    private long parseIsoTime(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = format.parse(value.length() > 19 ? value.substring(0, 19) : value);
            return date != null ? date.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<RemotePost> parsePosts(ApiResponse envelope) {
        return parsePosts(envelope, "posts");
    }

    private List<RemotePost> parsePosts(ApiResponse envelope, String key) {
        JsonObject data = envelope.getDataAsObject();
        List<RemotePost> posts = new ArrayList<>();
        if (data.has(key) && data.get(key).isJsonArray()) {
            for (JsonElement element : data.getAsJsonArray(key)) {
                if (element.isJsonObject()) { RemotePost post = parsePost(element.getAsJsonObject()); ModerationSync.cache(post); posts.add(post); }
            }
        }
        return posts;
    }

    private void addStrings(JsonObject object, String key, List<String> target) {
        if (!object.has(key) || !object.get(key).isJsonArray()) return;
        for (JsonElement element : object.getAsJsonArray(key)) {
            if (!element.isJsonNull()) target.add(element.getAsString());
        }
    }

    private int integer(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }

    private RemoteComment parseComment(JsonObject object) {
        RemoteComment comment = new RemoteComment();
        comment.setId(string(object, "id", ""));
        comment.setContent(string(object, "content", ""));
        comment.setCreatedAt(string(object, "created_at", ""));
        JsonObject user = object(object, "user");
        comment.setUserId(string(user, "id", ""));
        comment.setUserName(string(user, "nickname", string(user, "username", "匿名用户")));
        comment.setAvatarUrl(string(user, "avatar", ""));
        return comment;
    }

    private JsonObject noteBody(NoteModel note) {
        JsonObject body = new JsonObject();
        if (note.getId() != null) body.addProperty("id", note.getId());
        body.addProperty("content_version", note.getContentVersion());
        body.addProperty("resubmit", "rejected".equals(note.getModerationStatus()) || "hidden".equals(note.getModerationStatus()));
        body.addProperty("title", note.getTitle() == null ? "" : note.getTitle());
        body.addProperty("content", note.getContent() == null ? "" : note.getContent());
        body.add("images", gson.toJsonTree(note.getImageUris()));
        body.add("topics", gson.toJsonTree(note.getTopics()));
        body.addProperty("location", note.getLocation() == null ? "" : note.getLocation());
        body.addProperty("is_public", note.isPublic());
        return body;
    }

    private UserBean parseUser(ApiResponse envelope) throws IOException {
        JsonObject data = envelope.getDataAsObject();
        JsonObject user = object(data, "user");
        JsonObject tokens = object(data, "tokens");
        String username = string(user, "username", string(user, "user_id", ""));
        String token = string(data, "access_token", string(tokens, "access_token", ""));
        String avatar = string(user, "avatar", "");
        if (username.isEmpty() || token.isEmpty()) {
            throw new IOException("后台返回的用户数据不完整");
        }
        return new UserBean(username, "", token, avatar);
    }

    // ---- transport helpers ----

    /** 同步执行请求并校验 HTTP 状态与包络 code，出错抛 RemoteApiException，与旧实现行为一致。 */
    private ApiResponse checked(retrofit2.Call<ApiResponse> call) throws IOException {
        Response<ApiResponse> response = call.execute();
        ApiResponse envelope = response.body();
        if (envelope == null && response.errorBody() != null) {
            envelope = parseErrorBody(response.errorBody());
        }
        if (!response.isSuccessful()) {
            String message = envelope != null && !envelope.getMessage().isEmpty()
                    ? envelope.getMessage() : "后台请求失败";
            throw new RemoteApiException(message, response.code());
        }
        if (envelope != null && envelope.getCode() != null && envelope.getCode() >= 400) {
            throw new RemoteApiException(envelope.getMessage(), envelope.getCode());
        }
        if (envelope == null) {
            Log.w(TAG, "后台响应为空: " + response.raw().request().url());
        }
        return envelope != null ? envelope : new ApiResponse();
    }

    private ApiResponse parseErrorBody(ResponseBody errorBody) {
        try {
            String payload = errorBody.string();
            if (!payload.trim().isEmpty()) {
                return gson.fromJson(payload, ApiResponse.class);
            }
        } catch (Exception error) {
            Log.w(TAG, "解析错误响应失败", error);
        }
        return null;
    }

    private RequestBody jsonBody(JsonObject body) {
        return RequestBody.create(JSON, gson.toJson(body));
    }

    private static String bearer(String token) {
        return token == null || token.isEmpty() ? null : "Bearer " + token;
    }

    private JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return new JsonObject();
        return parent.getAsJsonObject(key);
    }

    private String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        return object.get(key).getAsString();
    }
}
