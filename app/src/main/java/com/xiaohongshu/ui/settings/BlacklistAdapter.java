package com.xiaohongshu.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.database.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑名单列表适配器：头像 + 昵称 + "解除拉黑"按钮
 */
public class BlacklistAdapter extends RecyclerView.Adapter<BlacklistAdapter.ViewHolder> {
    private List<UserEntity> dataList;
    private OnUnblockClickListener listener;

    public interface OnUnblockClickListener {
        void onUnblockClick(UserEntity user);
    }

    public BlacklistAdapter(OnUnblockClickListener listener) {
        this.dataList = new ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<UserEntity> newData) {
        if (newData == null) newData = new ArrayList<>();
        this.dataList = newData;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserEntity user = dataList.get(position);
        holder.bind(user);
    }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatar;
        private final TextView name;
        private final TextView bio;
        private final TextView followButton;
        private final ImageView dismissButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            name = itemView.findViewById(R.id.name);
            bio = itemView.findViewById(R.id.bio);
            followButton = itemView.findViewById(R.id.followButton);
            dismissButton = itemView.findViewById(R.id.dismissButton);
        }

        void bind(UserEntity user) {
            if (avatar != null) {
                if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                    com.xiaohongshu.util.ImageLoader.load(avatar, user.avatarUri, R.drawable.placeholder_avatar);
                } else {
                    int avatarRes;
                    if (user.avatar > 0 && isP1ToP11Avatar(user.avatar)) {
                        avatarRes = user.avatar;
                    } else if (user.id != null) {
                        avatarRes = getAvatarResource(Math.abs(user.id.hashCode()) % 16 + 1);
                    } else {
                        avatarRes = R.drawable.p1;
                    }
                    avatar.setImageResource(avatarRes);
                }
            }
            if (name != null) {
                name.setText(user.nickname != null && !user.nickname.isEmpty()
                        ? user.nickname : user.username);
            }
            if (bio != null) {
                bio.setText("已拉黑，无法互相私信和关注");
            }
            if (followButton != null) {
                followButton.setText("解除拉黑");
                followButton.setBackgroundResource(R.drawable.bg_button_grey);
                followButton.setTextColor(itemView.getContext().getResources().getColor(R.color.text_primary, null));
                followButton.setOnClickListener(v -> {
                    if (listener != null) listener.onUnblockClick(user);
                });
            }
            if (dismissButton != null) {
                dismissButton.setVisibility(View.GONE);
            }
        }

        private boolean isP1ToP11Avatar(int resourceId) {
            int[] avatars = {
                    R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
                    R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
                    R.drawable.p9, R.drawable.p10, R.drawable.p11,
                    R.drawable.p12, R.drawable.p13, R.drawable.p14,
                    R.drawable.p15, R.drawable.p16
            };
            for (int res : avatars) if (res == resourceId) return true;
            return false;
        }

        private int getAvatarResource(int index) {
            int[] avatars = {
                    R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
                    R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
                    R.drawable.p9, R.drawable.p10, R.drawable.p11,
                    R.drawable.p12, R.drawable.p13, R.drawable.p14,
                    R.drawable.p15, R.drawable.p16
            };
            return avatars[Math.abs(index) % avatars.length];
        }
    }
}
