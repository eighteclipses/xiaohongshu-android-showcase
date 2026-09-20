package com.xiaohongshu.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果"用户"分类适配器。
 * 整行点击进对方主页，关注按钮触发关注/取关。
 */
public class SearchUserAdapter extends RecyclerView.Adapter<SearchUserAdapter.ViewHolder> {
    private final List<UserEntity> users = new ArrayList<>();
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(UserEntity user);
        void onFollowClick(UserEntity user);
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void updateData(List<UserEntity> data) {
        users.clear();
        if (data != null) users.addAll(data);
        notifyDataSetChanged();
    }

    public int getItemCountSafe() {
        return users.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserEntity user = users.get(position);
        holder.bind(user, listener);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView userAvatar;
        private final TextView userNameText;
        private final TextView subtitleText;
        private final TextView followButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userNameText = itemView.findViewById(R.id.userNameText);
            subtitleText = itemView.findViewById(R.id.subtitleText);
            followButton = itemView.findViewById(R.id.followButton);
        }

        void bind(UserEntity user, OnUserClickListener listener) {
            if (user == null) return;
            // 昵称优先用 nickname，昵称为空时退到 username
            String displayName = user.nickname != null && !user.nickname.isEmpty()
                    ? user.nickname
                    : (user.username != null ? user.username : "用户");
            userNameText.setText(displayName);
            // 副标题：与真实小红书一致显示"小红书号: <id 缩写>"
            subtitleText.setText("小红书号: " + abridgeId(user.id));
            // 头像：本地 URI 走 Coil；否则用本地资源
            if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                ImageLoader.load(userAvatar, user.avatarUri, R.drawable.placeholder_avatar);
            } else {
                userAvatar.setImageResource(avatarResFor(user));
            }
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onUserClick(user);
            });
            followButton.setOnClickListener(v -> {
                if (listener != null) listener.onFollowClick(user);
            });
        }

        private static String abridgeId(String id) {
            if (id == null || id.isEmpty()) return "未知";
            return id.length() > 8 ? id.substring(0, 8) : id;
        }

        /** 与项目其它页面保持一致：根据用户 id 哈希取一张 p1..p16 兜底头像 */
        private static int avatarResFor(UserEntity user) {
            int[] avatars = {
                    R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
                    R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
                    R.drawable.p9, R.drawable.p10, R.drawable.p11,
                    R.drawable.p12, R.drawable.p13, R.drawable.p14,
                    R.drawable.p15, R.drawable.p16
            };
            return avatars[Math.floorMod(user.id == null ? 0 : user.id.hashCode(), avatars.length)];
        }
    }
}
