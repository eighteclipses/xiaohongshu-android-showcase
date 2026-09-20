package com.xiaohongshu.network;

import android.content.Context;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModerationSync {
    private static long lastRefresh;
    private ModerationSync() { }
    public static boolean publiclyVisible(NoteEntity note) {
        return note != null && !note.deleted && note.serverKnown && note.isPublic && !note.isDraft
                && "approved".equals(note.moderationStatus) && "synced".equals(note.syncState);
    }
    public static String label(NoteEntity note) {
        if (note.deleted) return "回收站";
        if ("conflict".equals(note.syncState)) return "版本冲突";
        if (!"synced".equals(note.syncState)) return "待同步";
        if (note.isDraft) return "草稿";
        if (!note.isPublic) return "仅自己可见";
        if ("pending".equals(note.moderationStatus)) return "待审核";
        if ("rejected".equals(note.moderationStatus)) return "已驳回";
        if ("hidden".equals(note.moderationStatus)) return "已隐藏";
        return "已通过";
    }
    public static void cache(RemotePost post) { save(post, false); }
    public static void apply(RemotePost post) { save(post, true); }
    private static synchronized void save(RemotePost post, boolean confirmedWrite) {
        if (post == null || post.getId().isEmpty() || post.getAuthorId().isEmpty()) return;
        com.xiaohongshu.database.AppDatabase db = AppApplication.getDatabase();
        NoteEntity local = db.noteDao().getNoteById(post.getId());
        if (!confirmedWrite && local != null && !"synced".equals(local.syncState)) return;
        if (local != null && local.serverKnown && (local.contentVersion>post.getContentVersion()
                || (local.contentVersion==post.getContentVersion() && post.getUpdatedAt()>0 && local.updateTime>post.getUpdatedAt()))) return;
        if (local == null) local = new NoteEntity();
        UserEntity user = post.getAuthorUsername().isEmpty() ? db.userDao().getUserById(post.getAuthorId())
                : db.userDao().getUserByUsername(post.getAuthorUsername());
        if (user == null) {
            user = new UserEntity(); user.id = post.getAuthorId();
            user.username = post.getAuthorUsername().isEmpty() ? "remote_" + user.id : post.getAuthorUsername();
            user.nickname = post.getAuthorName(); user.avatar = com.xiaohongshu.R.drawable.p1;
            user.createTime = System.currentTimeMillis(); db.userDao().insert(user);
        }
        // Existing installations use local user ids. Keep that mapping for Room ownership checks.
        local.id=post.getId();local.userId=user.id;local.title=post.getTitle();local.content=post.getContent();
        local.imageUris=new ArrayList<>(post.getImages());local.topics=new ArrayList<>(post.getTopics());local.location=post.getLocation();
        local.moderationStatus=post.getStatus();local.reviewNote=post.getReviewNote();local.contentVersion=post.getContentVersion();
        local.isPublic=post.isPublic();local.isDraft=post.isDraft();local.deleted=post.isDeleted();local.serverKnown=true;
        local.syncState="synced";if(confirmedWrite)PendingSyncStore.completedNote(AppApplication.getAppContext(),local.id);local.createTime=post.getCreatedAt();local.updateTime=post.getUpdatedAt()>0?post.getUpdatedAt():System.currentTimeMillis();db.noteDao().insert(local);
    }
    // Run on a repository executor, never the UI thread. Do not publish unknown local seed copies.
    public static synchronized void refresh(Context context) {
        if (System.currentTimeMillis()-lastRefresh < 3000) return;
        lastRefresh=System.currentTimeMillis();
        com.xiaohongshu.bean.UserBean user=LoginDataRepository.getInstance(context).getCurrentUser();
        if(user==null||user.getToken()==null||user.getToken().isEmpty())return;
        com.xiaohongshu.database.AppDatabase db=AppApplication.getDatabase();RemoteApiClient client=new RemoteApiClient(context);
        try {
            Set<String> seen=new HashSet<>();
            for(int page=1;page<=100;page++) {
                List<RemotePost> posts=client.getCreatorPosts(page,"all",user.getToken());
                int added=0;for(RemotePost post:posts)if(seen.add(post.getId())){cache(post);added++;}
                if(posts.size()<50||added==0)break;
            }
            UserEntity localUser=db.userDao().getUserByUsername(user.getUsername());
            if(localUser!=null) {
                for(int page=1;page<=100;page++) {
                    JsonObject notices=client.getReviewNotices(page,user.getToken());
                    for(JsonElement value:notices.getAsJsonArray("notifications")) {
                        JsonObject n=value.getAsJsonObject();String id="review_"+n.get("id").getAsString();
                        if(db.messageDao().getMessageById(id)!=null)continue;
                        long date=System.currentTimeMillis();
                        try { java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",java.util.Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));date=f.parse(n.get("created_at").getAsString()).getTime(); } catch(Exception ignored) { }
                        String text=n.has("message")?n.get("message").getAsString():"笔记审核状态已更新，请查看个人笔记";
                        String postId=n.has("postId")&&!n.get("postId").isJsonNull()?n.get("postId").getAsString():"";
                        db.messageDao().insert(new com.xiaohongshu.database.entity.MessageEntity(id,localUser.id,localUser.id,5,text,postId,false,date));
                    }
                    if(page>=notices.getAsJsonObject("pagination").get("pages").getAsInt())break;
                }
                int retried=0;
                for(NoteEntity local:db.noteDao().getModerationSyncNotes()) {
                    if(localUser.id.equals(local.userId) && "pending".equals(local.syncState) && retried++<5)
                        com.xiaohongshu.ui.publish.repository.NoteRepository.getInstance(context).retryPendingNote(local.id);
                }
            }
            List<NoteEntity> locals=db.noteDao().getModerationSyncNotes();
            for(int offset=0;offset<locals.size();offset+=100) {
                List<String> ids=new ArrayList<>();for(NoteEntity local:locals.subList(offset,Math.min(offset+100,locals.size())))ids.add(local.id);
                JsonObject result=client.syncPostStates(String.join(",",ids),user.getToken());
                for(JsonElement e:result.getAsJsonArray("posts")) {
                    JsonObject item=e.getAsJsonObject();NoteEntity local=db.noteDao().getNoteById(item.get("id").getAsString());
                    if(local==null||!"synced".equals(local.syncState))continue;
                    local.moderationStatus=item.get("status").getAsString();local.contentVersion=item.get("content_version").getAsInt();
                    local.isPublic=item.get("is_public").getAsBoolean();local.isDraft=item.get("is_draft").getAsBoolean();
                    local.deleted=item.has("deleted_at")&&!item.get("deleted_at").isJsonNull();local.serverKnown=true;
                    if(item.has("updated_at"))try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US);f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));local.updateTime=f.parse(item.get("updated_at").getAsString()).getTime();}catch(Exception ignored){}
                    if(item.has("review_note"))local.reviewNote=item.get("review_note").getAsString();db.noteDao().update(local);
                }
                for(JsonElement e:result.getAsJsonArray("missing_ids")) {
                    NoteEntity local=db.noteDao().getNoteById(e.getAsString());
                    if(local!=null&&local.serverKnown&&"synced".equals(local.syncState)){local.deleted=true;db.noteDao().update(local);}
                }
            }
        } catch(Exception ignored) { /* Keep last confirmed status offline; pending never becomes public. */ }
    }
}
