package com.xiaohongshu.ui.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.message.bean.RecommendFriendBean;

import java.util.ArrayList;
import java.util.List;

public class RecommendFriendAdapter extends RecyclerView.Adapter<RecommendFriendAdapter.ViewHolder> {
    private List<RecommendFriendBean> friendList = new ArrayList<>();
    private OnItemClickListener listener;
    private OnDismissListener dismissListener;

    public interface OnItemClickListener {
        void onFollowClick(RecommendFriendBean friend);
    }

    public interface OnDismissListener {
        void onDismiss(RecommendFriendBean friend);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void updateData(List<RecommendFriendBean> list) {
        if (list == null) {
            list = new ArrayList<>();
        }
        
        int oldSize = friendList != null ? friendList.size() : 0;
        int newSize = list.size();
        
        this.friendList = list;
        
        // 使用增量更新提升性能
        if (oldSize == 0) {
            notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0) {
            notifyItemRangeRemoved(0, oldSize);
        } else if (oldSize == newSize) {
            notifyItemRangeChanged(0, newSize);
        } else if (newSize > oldSize) {
            notifyItemRangeChanged(0, oldSize);
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            notifyItemRangeChanged(0, newSize);
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_recommend_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecommendFriendBean friend = friendList.get(position);
        holder.bind(friend);
    }

    @Override
    public int getItemCount() {
        return friendList.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView userAvatar;
        TextView userNameText;
        TextView reasonText;
        TextView followButton;
        ImageView dismissButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userNameText = itemView.findViewById(R.id.userNameText);
            reasonText = itemView.findViewById(R.id.reasonText);
            followButton = itemView.findViewById(R.id.followButton);
            dismissButton = itemView.findViewById(R.id.dismissButton);
        }

        void bind(RecommendFriendBean friend) {
            if (friend.getUser() != null) {
                userNameText.setText(friend.getUser().getName());
                reasonText.setText(friend.getReason());
                
                // 根据用户ID的哈希值分配头像
                if (userAvatar != null && friend.getUser().getId() != null) {
                    int avatarIndex = friend.getUser().getId().hashCode();
                    userAvatar.setImageResource(getAvatarResource(avatarIndex));
                }
            }

            followButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFollowClick(friend);
                }
            });

            dismissButton.setOnClickListener(v -> {
                if (dismissListener != null) {
                    dismissListener.onDismiss(friend);
                }
            });
        }
        
        /**
         * 根据索引获取头像资源
         * @param index 索引值（可以是任意整数）
         * @return 对应的头像资源ID
         */
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

