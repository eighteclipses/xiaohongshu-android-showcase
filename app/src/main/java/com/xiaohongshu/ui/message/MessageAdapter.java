package com.xiaohongshu.ui.message;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.message.bean.MessageBean;

import java.util.List;

/**
 * 消息列表适配器
 */
public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private List<MessageBean> dataList;
    private OnItemClickListener listener;
    
    /**
     * 消息点击监听器
     */
    public interface OnItemClickListener {
        void onItemClick(MessageBean message);
    }

    public MessageAdapter() {
        this.dataList = new java.util.ArrayList<>();
    }
    
    public MessageAdapter(OnItemClickListener listener) {
        this.dataList = new java.util.ArrayList<>();
        this.listener = listener;
    }

    public void updateData(List<MessageBean> newData) {
        if (newData == null) {
            newData = new java.util.ArrayList<>();
        }
        
        int oldSize = dataList != null ? dataList.size() : 0;
        int newSize = newData.size();
        
        this.dataList = newData;
        
        // 使用增量更新提升性能
        if (oldSize == 0) {
            // 首次加载
            notifyItemRangeInserted(0, newSize);
        } else if (newSize == 0) {
            // 清空列表
            notifyItemRangeRemoved(0, oldSize);
        } else if (oldSize == newSize) {
            // 大小相同，更新所有项
            notifyItemRangeChanged(0, newSize);
        } else if (newSize > oldSize) {
            // 新增了数据
            notifyItemRangeChanged(0, oldSize);
            notifyItemRangeInserted(oldSize, newSize - oldSize);
        } else {
            // 删除了数据
            notifyItemRangeChanged(0, newSize);
            notifyItemRangeRemoved(newSize, oldSize - newSize);
        }
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
            MessageBean message = dataList.get(position);
            holder.bind(message);
            
            // 设置点击事件
            if (listener != null) {
                holder.itemView.setOnClickListener(v -> listener.onItemClick(message));
            }
        }

    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView userAvatar;
        private final TextView titleText;
        private final TextView contentText;
        private final TextView timeText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            titleText = itemView.findViewById(R.id.titleText);
            contentText = itemView.findViewById(R.id.contentText);
            timeText = itemView.findViewById(R.id.timeText);
        }

        public void bind(MessageBean message) {
            titleText.setText(message.getTitle());
            contentText.setText(message.getContent());
            timeText.setText(message.getTime());
            
            // 加载用户头像 - 强制使用p1-p11头像
            if (message.getUser() != null && userAvatar != null) {
                int avatarRes;
                String userId = message.getUser().getId();
                if (userId != null && !userId.isEmpty()) {
                    // 根据用户ID生成固定的头像索引，确保同一用户总是显示相同的头像
                    int avatarIndex = userId.hashCode();
                    avatarRes = getAvatarResource(avatarIndex);
                } else {
                    // 如果用户ID为空，使用默认头像
                    avatarRes = com.xiaohongshu.R.drawable.p1;
                }
                userAvatar.setImageResource(avatarRes);
            } else if (userAvatar != null) {
                // 如果没有用户信息，使用默认头像
                userAvatar.setImageResource(com.xiaohongshu.R.drawable.p1);
            }
        }
        
        /**
         * 根据索引获取头像资源（使用p1-p11）
         * @param index 索引值（可以是任意整数）
         * @return 对应的头像资源ID
         */
        private int getAvatarResource(int index) {
            int[] avatars = {
                com.xiaohongshu.R.drawable.p1, com.xiaohongshu.R.drawable.p2, 
                com.xiaohongshu.R.drawable.p3, com.xiaohongshu.R.drawable.p4,
                com.xiaohongshu.R.drawable.p5, com.xiaohongshu.R.drawable.p6, 
                com.xiaohongshu.R.drawable.p7, com.xiaohongshu.R.drawable.p8,
com.xiaohongshu.R.drawable.p9, com.xiaohongshu.R.drawable.p10, 
                com.xiaohongshu.R.drawable.p11,
                com.xiaohongshu.R.drawable.p12, com.xiaohongshu.R.drawable.p13, com.xiaohongshu.R.drawable.p14,
                com.xiaohongshu.R.drawable.p15, com.xiaohongshu.R.drawable.p16
            };
            return avatars[Math.abs(index) % avatars.length];
        }
    }
}

