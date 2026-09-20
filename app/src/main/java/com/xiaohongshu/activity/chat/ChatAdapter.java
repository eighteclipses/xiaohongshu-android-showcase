package com.xiaohongshu.activity.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 * 私信会话适配器：文字气泡（我右侧红底白字 / 对方左侧灰底），
 * 礼物消息金色卡片展示。
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private static final int TYPE_MINE = 1;
    private static final int TYPE_OTHER = 2;

    private final List<JsonObject> messages = new ArrayList<>();
    private final String myUserId;

    public ChatAdapter(String myUserId) {
        this.myUserId = myUserId == null ? "" : myUserId;
    }

    public void submit(List<JsonObject> list) {
        messages.clear();
        if (list != null) messages.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        JsonObject message = messages.get(position);
        return myUserId.equals(message.get("fromId") != null && !message.get("fromId").isJsonNull()
                ? message.get("fromId").getAsString() : "") ? TYPE_MINE : TYPE_OTHER;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean mine = viewType == TYPE_MINE;
        float density = parent.getResources().getDisplayMetrics().density;

        TextView bubble = new TextView(parent.getContext());
        bubble.setPadding((int) (14 * density), (int) (10 * density), (int) (14 * density), (int) (10 * density));
        bubble.setTextSize(14.5f);

        FrameLayout container = new FrameLayout(parent.getContext());
        container.addView(bubble, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                mine ? android.view.Gravity.END : android.view.Gravity.START));
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) (6 * density);
        params.bottomMargin = (int) (6 * density);
        container.setLayoutParams(params);
        return new ChatViewHolder(container, bubble, mine);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        JsonObject message = messages.get(position);
        String content = message.get("content") != null && !message.get("content").isJsonNull()
                ? message.get("content").getAsString() : "";
        boolean gift = message.has("gift") && message.get("gift").isJsonObject();
        holder.bubble.setText(gift ? "🎁 " + content : content);
        if (gift) {
            holder.bubble.setBackgroundResource(R.drawable.bg_gift_bubble);
            holder.bubble.setTextColor(0xFF8A5A00);
        } else if (holder.mine) {
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_mine);
            holder.bubble.setTextColor(0xFFFFFFFF);
        } else {
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_other);
            holder.bubble.setTextColor(0xFF202124);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        final TextView bubble;
        final boolean mine;

        ChatViewHolder(@NonNull View itemView, TextView bubbleView, boolean mine) {
            super(itemView);
            this.bubble = bubbleView;
            this.mine = mine;
        }
    }
}
