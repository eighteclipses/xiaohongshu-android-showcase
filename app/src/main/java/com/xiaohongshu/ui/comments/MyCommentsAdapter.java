package com.xiaohongshu.ui.comments;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.database.entity.CommentEntity;
import com.xiaohongshu.database.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 我的评论列表适配器
 */
public class MyCommentsAdapter extends RecyclerView.Adapter<MyCommentsAdapter.ViewHolder> {
    private List<CommentEntity> dataList;
    private OnItemClickListener listener;
    private OnDeleteClickListener deleteListener;
    
    public interface OnItemClickListener {
        void onItemClick(CommentEntity comment);
    }
    
    public interface OnDeleteClickListener {
        void onDeleteClick(CommentEntity comment);
    }
    
    public MyCommentsAdapter(OnItemClickListener listener) {
        this.dataList = new ArrayList<>();
        this.listener = listener;
    }
    
    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }
    
    public void updateData(List<CommentEntity> newData) {
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
                .inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CommentEntity comment = dataList.get(position);
        holder.bind(comment, listener, deleteListener);
        
        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onItemClick(comment));
        }
    }
    
    @Override
    public int getItemCount() {
        return dataList != null ? dataList.size() : 0;
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView userAvatar;
        private TextView userName;
        private TextView commentText;
        private TextView timeText;
        private ImageView deleteButton;
        private ExecutorService executorService;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            commentText = itemView.findViewById(R.id.commentText);
            timeText = itemView.findViewById(R.id.timeText);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            executorService = Executors.newSingleThreadExecutor();
        }
        
        public void bind(CommentEntity comment, OnItemClickListener clickListener, OnDeleteClickListener deleteListener) {
            // 加载用户信息并设置头像
            if (comment.userId != null && userAvatar != null) {
                executorService.execute(() -> {
                    UserEntity user = AppApplication.getDatabase().userDao().getUserById(comment.userId);
                    if (user != null) {
                        int avatarRes = user.avatar > 0 ? user.avatar : getAvatarResource(comment.userId.hashCode());
                        String nickname = user.nickname != null ? user.nickname : user.username;
                        
                        itemView.post(() -> {
                            userAvatar.setImageResource(avatarRes);
                            if (userName != null) {
                                userName.setText(nickname);
                            }
                        });
                    } else {
                        // 如果找不到用户，使用默认头像
                        itemView.post(() -> {
                            userAvatar.setImageResource(getAvatarResource(comment.userId.hashCode()));
                            if (userName != null) {
                                userName.setText("用户");
                            }
                        });
                    }
                });
            } else {
                // 如果没有userId，使用默认头像
                if (userAvatar != null) {
                    userAvatar.setImageResource(R.drawable.placeholder_avatar);
                }
                if (userName != null) {
                    userName.setText("用户");
                }
            }
            
            if (commentText != null) {
                commentText.setText(comment.content);
            }
            
            if (timeText != null) {
                CharSequence timeStr = DateUtils.getRelativeTimeSpanString(
                    comment.createTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                );
                timeText.setText(timeStr.toString());
            }
            
            // 删除按钮
            if (deleteButton != null) {
                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    if (deleteListener != null) {
                        deleteListener.onDeleteClick(comment);
                    }
                });
            }
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

