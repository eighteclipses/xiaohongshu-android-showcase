package com.xiaohongshu.ui.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import com.xiaohongshu.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 私信会话列表适配器：复用 item_message 布局展示对方昵称、最后一条消息与相对时间。
 */
public class DmConversationAdapter extends RecyclerView.Adapter<DmConversationAdapter.ViewHolder> {
    private List<JsonObject> dataList = new ArrayList<>();
    private OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(JsonObject user);
    }

    public DmConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<JsonObject> newData) {
        dataList = newData == null ? new ArrayList<>() : newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject conversation = dataList.get(position);
        holder.bind(conversation);
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView userAvatar;
        private final TextView titleText;
        private final TextView contentText;
        private final TextView timeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            titleText = itemView.findViewById(R.id.titleText);
            contentText = itemView.findViewById(R.id.contentText);
            timeText = itemView.findViewById(R.id.timeText);
        }

        void bind(JsonObject conversation) {
            JsonObject user = conversation.has("user") && conversation.get("user").isJsonObject()
                    ? conversation.getAsJsonObject("user") : new JsonObject();
            String id = string(user, "id");
            String nickname = string(user, "nickname");
            String username = string(user, "username");
            titleText.setText(!nickname.isEmpty() ? nickname : (!username.isEmpty() ? username : "用户"));
            contentText.setText(string(conversation, "last_message"));
            timeText.setText(formatRelativeTime(string(conversation, "created_at")));

            int avatarRes;
            if (!id.isEmpty()) {
                avatarRes = getAvatarResource(Math.abs(id.hashCode()) % 16 + 1);
            } else {
                avatarRes = R.drawable.p1;
            }
            userAvatar.setImageResource(avatarRes);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onConversationClick(user);
            });
        }

        private String string(JsonObject object, String key) {
            return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
        }
    }

    private static int getAvatarResource(int index) {
        int[] avatars = {
                R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
                R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
                R.drawable.p9, R.drawable.p10, R.drawable.p11,
                R.drawable.p12, R.drawable.p13, R.drawable.p14,
                R.drawable.p15, R.drawable.p16
        };
        return avatars[Math.abs(index) % avatars.length];
    }

    /** ISO 时间转相对时间：刚刚 / x分钟前 / x小时前 / x天前 */
    public static String formatRelativeTime(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date date = format.parse(iso.length() > 19 ? iso.substring(0, 19) : iso);
            if (date == null) return "";
            long diff = System.currentTimeMillis() - date.getTime();
            long minutes = diff / 60000;
            if (minutes < 1) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            long hours = minutes / 60;
            if (hours < 24) return hours + "小时前";
            return (hours / 24) + "天前";
        } catch (Exception e) {
            return "";
        }
    }
}
