package com.xiaohongshu.ui.mine;

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
 * 关注列表适配器
 */
public class FollowListAdapter extends RecyclerView.Adapter<FollowListAdapter.ViewHolder> {
    private List<UserEntity> dataList;
    private OnUnfollowClickListener listener;
    private OnUserClickListener userClickListener;

    public interface OnUnfollowClickListener {
        void onUnfollowClick(UserEntity user, boolean isUnfollow);
    }

    public interface OnUserClickListener {
        void onUserClick(UserEntity user);
    }

    public FollowListAdapter(OnUnfollowClickListener listener) {
        this.dataList = new ArrayList<>();
        this.listener = listener;
    }

    public void setOnUserClickListener(OnUserClickListener userClickListener) {
        this.userClickListener = userClickListener;
    }
    
    public void updateData(List<UserEntity> newData) {
        if (newData == null) {
            newData = new ArrayList<>();
        }
        
        int oldSize = dataList != null ? dataList.size() : 0;
        int newSize = newData.size();
        
        this.dataList = newData;
        
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
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserEntity user = dataList.get(position);
        holder.bind(user);
        holder.itemView.setOnClickListener(v -> {
            if (userClickListener != null) userClickListener.onUserClick(user);
        });
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private TextView name;
        private TextView bio;
        private TextView followButton;
        private ImageView dismissButton;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            name = itemView.findViewById(R.id.name);
            bio = itemView.findViewById(R.id.bio);
            followButton = itemView.findViewById(R.id.followButton);
            dismissButton = itemView.findViewById(R.id.dismissButton);
        }
        
        public void bind(UserEntity user) {
            if (avatar != null) {
                if (user.avatarUri != null && !user.avatarUri.isEmpty()) {
                    // 服务器/相册头像走 Coil 加载
                    com.xiaohongshu.util.ImageLoader.load(avatar, user.avatarUri, R.drawable.placeholder_avatar);
                } else {
                    int avatarRes;
                    if (user.avatar > 0 && isP1ToP11Avatar(user.avatar)) {
                        // 数据库中存储的头像资源ID（需校验仍为 p 系列，避免旧 ID 映射到无关 drawable）
                        avatarRes = user.avatar;
                    } else if (user.id != null) {
                        // 根据用户ID生成一个固定的索引，确保每次都返回相同的头像
                        int avatarIndex = Math.abs(user.id.hashCode()) % 16 + 1;
                        avatarRes = getAvatarResource(avatarIndex);
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
                // 显示个人简介或标签
                if (user.bio != null && !user.bio.isEmpty()) {
                    bio.setText(user.bio);
                } else {
                    // 显示默认标签
                    String[] tags = {
                        "生活记录内容热门作者",
                        "时尚内容热门作者",
                        "潮流内容热门作者",
                        "美妆内容热门作者",
                        "娱乐内容热门作者",
                        "素材内容热门作者"
                    };
                    int tagIndex = Math.abs(user.id.hashCode()) % tags.length;
                    bio.setText(tags[tagIndex]);
                }
            }
            
            // 在关注列表中，按钮显示"已关注"，点击后取消关注
            if (followButton != null) {
                followButton.setText("已关注");
                followButton.setBackgroundResource(R.drawable.bg_edit_profile_button);
                followButton.setTextColor(itemView.getContext().getResources().getColor(R.color.text_primary, null));
                followButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onUnfollowClick(user, true);
                    }
                });
            }
            
            // 隐藏取消推荐按钮（在关注列表中不需要）
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

        /**
         * 根据索引获取头像资源
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

