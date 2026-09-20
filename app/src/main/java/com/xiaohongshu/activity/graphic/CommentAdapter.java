package com.xiaohongshu.activity.graphic;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 评论列表适配器
 * 根评论与二级回复嵌套展示：回复默认折叠，点击「展开 N 条回复」查看，「收起」折叠；
 * 支持评论点赞（红心高亮 + 计数），时间显示为中文相对时间。
 */
public class CommentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_COMMENT = 0;
    private static final int TYPE_EXPAND = 1;

    /** 「展开/收起回复」行 */
    private static class ExpandItem {
        final String rootId;
        final int replyCount;
        final boolean expanded;

        ExpandItem(String rootId, int replyCount, boolean expanded) {
            this.rootId = rootId;
            this.replyCount = replyCount;
            this.expanded = expanded;
        }
    }

    private List<CommentBean> allComments = new ArrayList<>();
    private final List<Object> displayItems = new ArrayList<>();
    private final Set<String> expandedRoots = new HashSet<>();
    private OnCommentClickListener listener;
    private OnCommentLikeClickListener likeListener;

    /**
     * 评论点击监听器（进入回复态）
     */
    public interface OnCommentClickListener {
        void onCommentClick(CommentBean comment);
    }

    /** 评论点赞点击监听器 */
    public interface OnCommentLikeClickListener {
        void onCommentLikeClick(CommentBean comment);
    }

    public void setOnCommentClickListener(OnCommentClickListener listener) {
        this.listener = listener;
    }

    public void setOnCommentLikeClickListener(OnCommentLikeClickListener listener) {
        this.likeListener = listener;
    }

    /**
     * 更新评论列表并重建展示结构：根评论后跟折叠行或其回复列表
     */
    public void updateComments(List<CommentBean> comments) {
        this.allComments = comments != null ? comments : new ArrayList<>();
        rebuildDisplayItems();
        notifyDataSetChanged();
    }

    private void rebuildDisplayItems() {
        displayItems.clear();
        for (CommentBean comment : allComments) {
            if (comment.getParentId() != null && !comment.getParentId().isEmpty()) {
                continue; // 回复由根评论的展开状态控制展示
            }
            displayItems.add(comment);
            boolean hasReplies = comment.getReplyCount() > 0;
            boolean expanded = expandedRoots.contains(comment.getId());
            if (!hasReplies) continue;
            if (expanded) {
                for (CommentBean reply : allComments) {
                    if (comment.getId().equals(reply.getParentId())) {
                        displayItems.add(reply);
                    }
                }
                displayItems.add(new ExpandItem(comment.getId(), comment.getReplyCount(), true));
            } else {
                displayItems.add(new ExpandItem(comment.getId(), comment.getReplyCount(), false));
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position) instanceof ExpandItem ? TYPE_EXPAND : TYPE_COMMENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_EXPAND) {
            return new ExpandViewHolder(inflater.inflate(R.layout.item_comment_expand, parent, false));
        }
        return new CommentViewHolder(inflater.inflate(R.layout.item_comment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayItems.get(position);
        if (item instanceof ExpandItem) {
            ExpandItem expand = (ExpandItem) item;
            ExpandViewHolder expandHolder = (ExpandViewHolder) holder;
            expandHolder.expandText.setText(expand.expanded ? "收起回复" : "展开 " + expand.replyCount + " 条回复");
            expandHolder.expandText.setOnClickListener(v -> {
                if (expand.expanded) {
                    expandedRoots.remove(expand.rootId);
                } else {
                    expandedRoots.add(expand.rootId);
                }
                rebuildDisplayItems();
                notifyDataSetChanged();
            });
            return;
        }

        CommentBean comment = (CommentBean) item;
        CommentViewHolder commentHolder = (CommentViewHolder) holder;
        boolean isReply = comment.getParentId() != null && !comment.getParentId().isEmpty();
        commentHolder.bind(comment, isReply);

        // 点击评论进入回复态
        if (listener != null) {
            commentHolder.itemView.setOnClickListener(v -> listener.onCommentClick(comment));
        }
        // 点赞
        if (likeListener != null) {
            commentHolder.likesLayout.setOnClickListener(v -> likeListener.onCommentLikeClick(comment));
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView userAvatar;
        private final TextView userName;
        private final TextView commentText;
        private final TextView likesText;
        private final ImageView likeIcon;
        private final TextView timeText;
        private final View likesLayout;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            commentText = itemView.findViewById(R.id.commentText);
            likesText = itemView.findViewById(R.id.likesText);
            likeIcon = itemView.findViewById(R.id.likeIcon);
            timeText = itemView.findViewById(R.id.timeText);
            likesLayout = itemView.findViewById(R.id.likesLayout);
        }

        public void bind(CommentBean comment, boolean isReply) {
            if (comment.getUser() != null) {
                if (userAvatar != null) {
                    // 优先使用UserBean中设置的头像，确保显示最新的头像
                    int avatarRes;
                    int userImage = comment.getUser().getImage();
                    if (userImage > 0 && isP1ToP11Avatar(userImage)) {
                        // 使用UserBean中设置的头像（最新的头像）
                        avatarRes = userImage;
                    } else {
                        // 如果UserBean没有设置有效头像，根据用户ID生成
                        String userId = comment.getUser().getId();
                        if (userId != null && !userId.isEmpty()) {
                            int avatarIndex = userId.hashCode();
                            avatarRes = getAvatarResource(avatarIndex);
                        } else {
                            avatarRes = R.drawable.p1;
                        }
                    }
                    userAvatar.setImageResource(avatarRes);
                }
                if (userName != null) {
                    userName.setText(comment.getUser().getName());
                }
            }

            if (commentText != null) {
                commentText.setText(comment.getContent());
            }

            if (likesText != null) {
                likesText.setText(String.valueOf(comment.getLikes()));
            }

            if (likeIcon != null) {
                likeIcon.setColorFilter(itemView.getContext().getColor(
                        comment.isLikedByMe() ? R.color.xhs_red : R.color.text_secondary));
            }

            // 回复项整体缩进，体现层级
            itemView.setPadding(isReply ? (int) (itemView.getResources().getDisplayMetrics().density * 44) : 0,
                    0, 0, 0);

            if (timeText != null) {
                timeText.setText(formatRelativeTime(comment.getCreateTime()) + " 回复");
            }
        }

        /** 中文相对时间：刚刚 / N分钟前 / N小时前 / 昨天 / N天前 / MM月dd日 */
        private String formatRelativeTime(long time) {
            long diff = System.currentTimeMillis() - time;
            if (diff < 60000) return "刚刚";
            if (diff < 3600000) return (diff / 60000) + "分钟前";
            if (diff < 86400000) return (diff / 3600000) + "小时前";
            if (diff < 86400000 * 2) return "昨天";
            if (diff < 86400000 * 7) return (diff / 86400000) + "天前";
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(time));
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

        /**
         * 检查资源ID是否是p1-p11头像之一
         * @param resourceId 资源ID
         * @return 如果是p1-p11之一返回true，否则返回false
         */
        private boolean isP1ToP11Avatar(int resourceId) {
            int[] avatars = {
                R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
                R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
                R.drawable.p9, R.drawable.p10, R.drawable.p11,
                R.drawable.p12, R.drawable.p13, R.drawable.p14,
                R.drawable.p15, R.drawable.p16
            };
            for (int avatar : avatars) {
                if (avatar == resourceId) {
                    return true;
                }
            }
            return false;
        }
    }

    static class ExpandViewHolder extends RecyclerView.ViewHolder {
        final TextView expandText;

        ExpandViewHolder(@NonNull View itemView) {
            super(itemView);
            expandText = itemView.findViewById(R.id.expandText);
        }
    }
}
