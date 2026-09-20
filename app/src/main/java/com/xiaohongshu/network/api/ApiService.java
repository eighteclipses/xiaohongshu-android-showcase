package com.xiaohongshu.network.api;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

/**
 * Express 后台接口定义。baseUrl 指向 <host>:3001/api，路径均为相对路径。
 * 鉴权通过 @Header 传入 Bearer token；传 null 时 Retrofit 自动省略该请求头。
 */
public interface ApiService {

    // ---- auth ----

    @POST("auth/login")
    Call<ApiResponse> login(@Body RequestBody body);

    @POST("auth/register")
    Call<ApiResponse> register(@Body RequestBody body);

    @PATCH("auth/me")
    Call<ApiResponse> updateMe(@Header("Authorization") String auth, @Body RequestBody body);

    @GET("notifications")
    Call<ApiResponse> getReviewNotices(@Query("type") String type, @Query("page") int page, @Query("limit") int limit, @Header("Authorization") String auth);
    @GET("posts/sync")
    Call<ApiResponse> syncPostStates(@Query("ids") String ids, @Header("Authorization") String auth);
    @GET("creators/me/posts")
    Call<ApiResponse> getCreatorPosts(@Query("page") int page, @Query("limit") int limit, @Query("filter") String filter, @Header("Authorization") String auth);

    // ---- posts ----

    @GET("posts")
    Call<ApiResponse> getPosts(@Query("page") int page, @Query("limit") int limit,
                               @Query("keyword") String keyword, @Header("Authorization") String auth);

    @GET("posts/{id}")
    Call<ApiResponse> getPost(@Path("id") String postId, @Header("Authorization") String auth);

    @POST("posts")
    Call<ApiResponse> publishPost(@Header("Authorization") String auth, @Body RequestBody body);

    @POST("posts/drafts")
    Call<ApiResponse> saveDraft(@Header("Authorization") String auth, @Body RequestBody body);

    @retrofit2.http.HTTP(method = "DELETE", path = "posts/{id}", hasBody = true)
    Call<ApiResponse> deletePost(@Path("id") String postId, @Header("Authorization") String auth, @Body RequestBody body);

    @POST("posts/{id}/like")
    Call<ApiResponse> likePost(@Path("id") String postId, @Header("Authorization") String auth);

    @DELETE("posts/{id}/like")
    Call<ApiResponse> unlikePost(@Path("id") String postId, @Header("Authorization") String auth);

    @POST("posts/{id}/collection")
    Call<ApiResponse> collectPost(@Path("id") String postId, @Header("Authorization") String auth);

    @DELETE("posts/{id}/collection")
    Call<ApiResponse> uncollectPost(@Path("id") String postId, @Header("Authorization") String auth);

    @GET("posts/{id}/comments")
    Call<ApiResponse> getComments(@Path("id") String postId);

    @POST("posts/{id}/comments")
    Call<ApiResponse> addComment(@Path("id") String postId, @Header("Authorization") String auth,
                                 @Body RequestBody body);

    @POST("posts/{id}/view")
    Call<ApiResponse> recordView(@Path("id") String postId, @Header("Authorization") String auth,
                                 @Body RequestBody body);

    @POST("posts/{id}/report")
    Call<ApiResponse> reportPost(@Path("id") String postId, @Header("Authorization") String auth,
                                @Body RequestBody body);

    @GET("users/search")
    Call<ApiResponse> searchUsers(@Query("keyword") String keyword, @Query("limit") int limit);

    @POST("auth/refresh")
    Call<ApiResponse> refreshToken(@Header("Authorization") String auth, @Body RequestBody body);

    // ---- users ----

    @GET("users/{id}/posts")
    Call<ApiResponse> getUserPosts(@Path("id") String userId, @Query("page") int page,
                                   @Query("limit") int limit, @Header("Authorization") String auth);

    @GET("users/{id}")
    Call<ApiResponse> getUserProfile(@Path("id") String userId, @Header("Authorization") String auth);

    @GET("dm/conversations")
    Call<ApiResponse> getDmConversations(@Header("Authorization") String auth);

    @GET("dm/with/{toId}")
    Call<ApiResponse> getDmThread(@Path("toId") String toId, @Header("Authorization") String auth);

    @POST("dm/{toId}")
    Call<ApiResponse> sendDm(@Path("toId") String toId, @Body RequestBody body,
                             @Header("Authorization") String auth);

    @GET("dm/{toId}/gifts")
    Call<ApiResponse> getGifts(@Path("toId") String toId, @Header("Authorization") String auth);

    @POST("dm/{toId}/gift")
    Call<ApiResponse> sendGift(@Path("toId") String toId, @Body RequestBody body,
                               @Header("Authorization") String auth);

    // ---- wallet ----

    @GET("wallet")
    Call<ApiResponse> getWallet(@Header("Authorization") String auth);

    @POST("wallet/recharge")
    Call<ApiResponse> recharge(@Header("Authorization") String auth, @Body RequestBody body);

    @GET("users/{id}/collections")
    Call<ApiResponse> getUserCollections(@Path("id") String userId, @Query("page") int page,
                                         @Query("limit") int limit, @Header("Authorization") String auth);

    @GET("users/{id}/likes")
    Call<ApiResponse> getUserLikes(@Path("id") String userId, @Query("page") int page,
                                   @Query("limit") int limit, @Header("Authorization") String auth);

    @POST("users/{id}/follow")
    Call<ApiResponse> followUser(@Path("id") String userId, @Header("Authorization") String auth);

    @DELETE("users/{id}/follow")
    Call<ApiResponse> unfollowUser(@Path("id") String userId, @Header("Authorization") String auth);

    @GET("users/{id}/block-status")
    Call<ApiResponse> getBlockStatus(@Path("id") String userId, @Header("Authorization") String auth);

    @POST("users/{id}/block")
    Call<ApiResponse> blockUser(@Path("id") String userId, @Header("Authorization") String auth);

    @DELETE("users/{id}/block")
    Call<ApiResponse> unblockUser(@Path("id") String userId, @Header("Authorization") String auth);

    // ---- media ----

    @Multipart
    @POST("media/upload")
    Call<ApiResponse> uploadImages(@Header("Authorization") String auth,
                                   @Part List<MultipartBody.Part> files);
}
