package com.xiaohongshu.ui.home.discovery;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.ui.home.bean.UserBean;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Discovery RecyclerView Adapter
 * 使用 ListAdapter + DiffUtil 做增量更新，避免数据错位与全量刷新。
 */
public class DiscoveryAdapter extends ListAdapter<GraphicCardBean, DiscoveryAdapter.ViewHolder> {
    private static final int[] AVATARS = {
        R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
        R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
        R.drawable.p9, R.drawable.p10, R.drawable.p11,
        R.drawable.p12, R.drawable.p13, R.drawable.p14,
        R.drawable.p15, R.drawable.p16
    };
    private static final String DEFAULT_USER_NAME = "小红书用户";

    private static final DiffUtil.ItemCallback<GraphicCardBean> DIFF = new DiffUtil.ItemCallback<GraphicCardBean>() {
        @Override
        public boolean areItemsTheSame(@NonNull GraphicCardBean oldItem, @NonNull GraphicCardBean newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull GraphicCardBean oldItem, @NonNull GraphicCardBean newItem) {
            if (!oldItem.getTitle().equals(newItem.getTitle())) return false;
            if (oldItem.getLikes() != newItem.getLikes()) return false;
            if (!oldItem.getImageUri().equals(newItem.getImageUri())) return false;
            if (oldItem.getImage() != newItem.getImage()) return false;
            if (oldItem.getType() != newItem.getType()) return false;
            return userEquals(oldItem.getUser(), newItem.getUser());
        }

        private boolean userEquals(UserBean oldUser, UserBean newUser) {
            if (oldUser == newUser) return true;
            if (oldUser == null || newUser == null) return false;
            return Objects.equals(oldUser.getId(), newUser.getId())
                    && Objects.equals(oldUser.getName(), newUser.getName())
                    && oldUser.getImage() == newUser.getImage()
                    && oldUser.getImageUri().equals(newUser.getImageUri());
        }
    };

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(GraphicCardBean card);
    }

    public DiscoveryAdapter(OnItemClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void updateData(List<GraphicCardBean> newData) {
        // 拷贝一份，防止调用方复用/继续修改同一列表实例干扰 Diff 计算
        submitList(newData == null ? new ArrayList<>() : new ArrayList<>(newData));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_graphic_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final TextView textCover;
        private final ImageView videoIcon;
        private final TextView titleText;
        private final ImageView userAvatar;
        private final TextView userName;
        private final TextView likesText;

        /** 无图卡片轮换的马卡龙渐变封面 */
        private static final int[] TEXT_COVERS = {
                R.drawable.bg_text_cover_pink, R.drawable.bg_text_cover_teal,
                R.drawable.bg_text_cover_amber, R.drawable.bg_text_cover_violet
        };

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
            textCover = itemView.findViewById(R.id.textCover);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            titleText = itemView.findViewById(R.id.titleText);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            likesText = itemView.findViewById(R.id.likesText);
        }

        public void bind(GraphicCardBean card, OnItemClickListener listener) {
            // Load image - 统一走 Coil（http/file/content/相对路径），失败显示占位图而非空白
            boolean hasImage = !card.getImageUri().trim().isEmpty() || card.getImage() != 0;
            if (!card.getImageUri().trim().isEmpty()) {
                ImageLoader.load(imageView, card.getImageUri(), R.drawable.placeholder_image);
            } else if (card.getImage() == 0) {
                // 纯文字卡片：渐变封面 + 标题文字，替代灰色占位块
                imageView.setVisibility(View.GONE);
                if (textCover != null) {
                    textCover.setVisibility(View.VISIBLE);
                    int coverIndex = Math.abs(card.getId().hashCode()) % TEXT_COVERS.length;
                    textCover.setBackgroundResource(TEXT_COVERS[coverIndex]);
                    textCover.setText(card.getTitle());
                }
            } else {
                imageView.setVisibility(View.VISIBLE);
                if (textCover != null) textCover.setVisibility(View.GONE);
                imageView.setImageResource(card.getImage());
            }
            if (hasImage && textCover != null) {
                textCover.setVisibility(View.GONE);
                imageView.setVisibility(View.VISIBLE);
            }

            // Set title
            titleText.setText(card.getTitle());
            titleText.setVisibility(com.xiaohongshu.ui.publish.TextToImageConverter
                    .isGeneratedTextImage(card.getImageUri()) ? View.GONE : View.VISIBLE);

            // Show video icon if video type
            if (card.getType() == GraphicCardType.Video) {
                videoIcon.setVisibility(View.VISIBLE);
            } else {
                videoIcon.setVisibility(View.GONE);
            }

            // Load user avatar - 优先使用UserBean中设置的头像，确保显示最新的头像；本地 URI 也走 Coil，失败回退占位圆
            if (card.getUser() != null && userAvatar != null) {
                String avatarUri = card.getUser().getImageUri();
                if (!avatarUri.trim().isEmpty()) {
                    ImageLoader.load(userAvatar, avatarUri, R.drawable.placeholder_avatar);
                } else {
                    int avatarRes;
                    // 优先使用UserBean中设置的头像（这是从数据库获取的最新头像）
                    int userImage = card.getUser().getImage();
                    if (userImage > 0 && isP1ToP16Avatar(userImage)) {
                        avatarRes = userImage;
                    } else {
                        String userId = card.getUser().getId();
                        if (userId != null && !userId.isEmpty()) {
                            int avatarIndex = userId.hashCode();
                            avatarRes = getAvatarResource(avatarIndex);
                        } else {
                            avatarRes = R.drawable.p1;
                        }
                    }
                    userAvatar.setImageResource(avatarRes);
                }
            } else if (userAvatar != null) {
                // 无作者信息时重置头像，避免复用展示上一个item的头像
                userAvatar.setImageResource(R.drawable.placeholder_avatar);
            }

            // Set user name（作者可能为空，需要判空避免崩溃）
            if (card.getUser() != null) {
                userName.setText(card.getUser().getName());
            } else {
                userName.setText(DEFAULT_USER_NAME);
            }

            // Set likes
            likesText.setText(String.valueOf(card.getLikes()));

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(card);
                }
            });
        }

        /**
         * 根据索引获取头像资源
         * @param index 索引值（可以是任意整数）
         * @return 对应的头像资源ID
         */
        private int getAvatarResource(int index) {
            return AVATARS[Math.abs(index) % AVATARS.length];
        }

        /**
         * 检查资源ID是否是p1-p16头像之一
         * @param resourceId 资源ID
         * @return 如果是p1-p16之一返回true，否则返回false
         */
        private boolean isP1ToP16Avatar(int resourceId) {
            for (int avatar : AVATARS) {
                if (avatar == resourceId) {
                    return true;
                }
            }
            return false;
        }
    }
}
