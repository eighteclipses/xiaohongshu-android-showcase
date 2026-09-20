package com.xiaohongshu.ui.discover;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.database.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 发现好友列表适配器
 */
public class DiscoverFriendsAdapter extends RecyclerView.Adapter<DiscoverFriendsAdapter.ViewHolder> {
    private List<UserEntity> dataList;
    private OnFollowClickListener listener;
    private OnDismissListener dismissListener;
    
    public interface OnFollowClickListener {
        void onFollowClick(UserEntity user, boolean isFollow);
    }

    public interface OnDismissListener {
        void onDismiss(UserEntity user);
    }
    
    public DiscoverFriendsAdapter(OnFollowClickListener listener) {
        this.dataList = new ArrayList<>();
        this.listener = listener;
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void removeUser(UserEntity user) {
        int index = dataList.indexOf(user);
        if (index >= 0) {
            dataList.remove(index);
            notifyItemRemoved(index);
        }
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
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView avatar;
        private TextView name;
        private TextView bio;
        private TextView followButton; // 改为TextView类型，与XML布局匹配
        private ImageView dismissButton;
        private boolean isFollowed = false;
        
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
                // 根据用户ID分配头像，确保每个用户都有正确的头像
                int avatarIndex = user.id != null ? user.id.hashCode() : 0;
                avatar.setImageResource(getAvatarResource(avatarIndex));
            }
            if (name != null) {
                name.setText(user.nickname != null ? user.nickname : user.username);
            }
            if (bio != null) {
                // 显示标签（如"生活记录内容热门作者"）
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
            
            if (followButton != null) {
                followButton.setText(isFollowed ? "已关注" : "关注");
                followButton.setOnClickListener(v -> {
                    isFollowed = !isFollowed;
                    if (listener != null) {
                        listener.onFollowClick(user, isFollowed);
                    }
                });
            }
            
            // 取消推荐按钮
            if (dismissButton != null) {
                dismissButton.setOnClickListener(v -> {
                    if (dismissListener != null) dismissListener.onDismiss(user);
                    android.widget.Toast.makeText(itemView.getContext(), 
                        "已取消推荐", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
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
