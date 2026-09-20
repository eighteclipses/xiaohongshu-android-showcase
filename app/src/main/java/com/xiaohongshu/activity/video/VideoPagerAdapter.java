package com.xiaohongshu.activity.video;

import android.animation.ObjectAnimator;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 刷视频页 Adapter
 * <p>
 * 整个列表共用一个 ExoPlayer，翻页时只切换媒体项并重新挂载 Surface，
 * 避免逐页创建/销毁播放器造成的黑屏与卡顿，实现流畅的上下滑刷视频体验。
 */
public class VideoPagerAdapter extends RecyclerView.Adapter<VideoPagerAdapter.VideoViewHolder> {

    public interface InteractionListener {
        void onLike(GraphicCardBean card);

        void onCollect(GraphicCardBean card);

        void onComment(GraphicCardBean card);

        void onShare(GraphicCardBean card);

        void onDuet(GraphicCardBean card);
    }

    private final List<GraphicCardBean> videoList = new ArrayList<>();
    private final ExoPlayer player;
    private final InteractionListener listener;
    private final android.content.Context playerViewContext;
    private int currentPosition = -1;
    private boolean playWhenReady = true;

    private Set<String> likedIds = new HashSet<>();
    private Set<String> collectedIds = new HashSet<>();
    private Map<String, Integer> likeCounts = new HashMap<>();
    private Map<String, Integer> collectionCounts = new HashMap<>();
    private Map<String, Integer> commentCounts = new HashMap<>();

    public VideoPagerAdapter(ExoPlayer player, InteractionListener listener) {
        this.player = player;
        this.listener = listener;
        this.playerViewContext = com.xiaohongshu.app.AppApplication.getAppContext();
    }

    public void updateVideos(List<GraphicCardBean> videos) {
        videoList.clear();
        if (videos != null) videoList.addAll(videos);
        notifyDataSetChanged();
    }

    public void updateInteractionState(Set<String> liked, Set<String> collected,
                                       Map<String, Integer> likes, Map<String, Integer> collections,
                                       Map<String, Integer> comments) {
        this.likedIds = liked != null ? liked : new HashSet<>();
        this.collectedIds = collected != null ? collected : new HashSet<>();
        this.likeCounts = likes != null ? likes : new HashMap<>();
        this.collectionCounts = collections != null ? collections : new HashMap<>();
        this.commentCounts = comments != null ? comments : new HashMap<>();
        notifyDataSetChanged();
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * 切换到指定页面：重挂播放 Surface 并切换媒体项
     */
    public void playVideo(int position) {
        if (position < 0 || position >= videoList.size()) {;
            return;
        }
        int oldPosition = currentPosition;
        currentPosition = position;
        if (oldPosition != position) {
            // updateVideos 的 notifyDataSetChanged 同帧内会吞掉 item 级通知，
            // 延迟到下一帧 rebind，保证当前页真正挂载播放器 Surface
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (oldPosition >= 0) notifyItemChanged(oldPosition);
                notifyItemChanged(position);
            });
        }
        GraphicCardBean card = videoList.get(position);
        MediaItem item = buildMediaItem(card);;
        player.setMediaItem(item);
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
    }

    private MediaItem buildMediaItem(GraphicCardBean card) {
        // 优先使用远程/本地视频地址，其次使用内置 raw 视频资源
        if (!card.getVideoUri().isEmpty()) {
            return MediaItem.fromUri(resolveVideoUri(card.getVideoUri()));
        }
        String packageName = playerViewContext != null ? playerViewContext.getPackageName() : "";
        return MediaItem.fromUri("android.resource://" + packageName + "/" + card.getVideo());
    }

    /** 后台相对媒体路径（/uploads/...、/seed/...）补全为完整地址，ExoPlayer 无法直接播放相对路径 */
    private String resolveVideoUri(String uri) {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri;
        if (uri.startsWith("/") && !uri.startsWith("//") && playerViewContext != null) {
            try {
                String base = playerViewContext.getString(com.xiaohongshu.R.string.backend_base_url);
                String host = base.endsWith("/api") ? base.substring(0, base.length() - "/api".length()) : base;
                return host + uri;
            } catch (Exception ignored) {
            }
        }
        return uri;
    }

    public void pausePlayback() {
        playWhenReady = false;
        player.setPlayWhenReady(false);
        syncPlayStateIcon();
    }

    public void resumePlayback() {
        playWhenReady = true;
        player.setPlayWhenReady(true);
        if (currentPosition < 0 && !videoList.isEmpty()) {
            playVideo(0);
        }
        syncPlayStateIcon();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void releasePlayer() {
        player.release();
    }

    private void syncPlayStateIcon() {
        // 通知当前页刷新中央指示器
        if (currentPosition >= 0) notifyItemChanged(currentPosition);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_page, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.bind(videoList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    @Override
    public void onViewRecycled(@NonNull VideoViewHolder holder) {
        super.onViewRecycled(holder);
        holder.playerView.setPlayer(null);
    }

    private boolean isLiked(GraphicCardBean card) {
        return likedIds.contains(card.getId());
    }

    private boolean isCollected(GraphicCardBean card) {
        return collectedIds.contains(card.getId());
    }

    private int shownCount(Map<String, Integer> counts, GraphicCardBean card) {
        Integer value = counts.get(card.getId());
        return value != null ? value : 0;
    }

    private String formatCount(int count) {
        if (count >= 100000) {
            return count / 10000 + "万+";
        } else if (count >= 10000) {
            return String.format("%.1f万", count / 10000.0);
        } else if (count >= 1000) {
            return String.format("%.1f千", count / 1000.0);
        }
        return String.valueOf(count);
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        final PlayerView playerView;
        final ImageView playStateIcon;
        final ImageView likeButton;
        final TextView likesText;
        final ImageView commentButton;
        final TextView commentsText;
        final ImageView collectButton;
        final TextView collectsText;
        final ImageView shareButton;
        final ImageView duetButton;
        final ImageView userAvatar;
        final TextView userName;
        final TextView followButton;
        final TextView videoTitle;
        final TextView musicInfo;
        final View commentInput;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.playerView);
            playStateIcon = itemView.findViewById(R.id.playStateIcon);
            likeButton = itemView.findViewById(R.id.likeButton);
            likesText = itemView.findViewById(R.id.likesText);
            commentButton = itemView.findViewById(R.id.commentButton);
            commentsText = itemView.findViewById(R.id.commentsText);
            collectButton = itemView.findViewById(R.id.collectButton);
            collectsText = itemView.findViewById(R.id.collectsText);
            shareButton = itemView.findViewById(R.id.shareButton);
            duetButton = itemView.findViewById(R.id.duetButton);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            followButton = itemView.findViewById(R.id.followButton);
            videoTitle = itemView.findViewById(R.id.videoTitle);
            musicInfo = itemView.findViewById(R.id.musicInfo);
            commentInput = itemView.findViewById(R.id.commentInput);
        }

        void bind(GraphicCardBean card, int position) {
            // 只有当前页挂载播放器，其余页面摘除 Surface
            boolean attach = position == currentPosition;
            playerView.setPlayer(attach ? player : null);;
            refreshPlayStateIcon();

            bindUserInfo(card);
            bindInteractions(card);
            setupGestures(card);
        }

        private void bindUserInfo(GraphicCardBean card) {
            if (card.getUser() != null) {
                String avatarUri = card.getUser().getImageUri();
                if (!avatarUri.trim().isEmpty()
                        && (avatarUri.startsWith("http://") || avatarUri.startsWith("https://"))) {
                    ImageLoader.load(userAvatar, avatarUri, R.drawable.p1);
                } else if (card.getUser().getImage() > 0) {
                    userAvatar.setImageResource(card.getUser().getImage());
                } else {
                    userAvatar.setImageResource(R.drawable.p1);
                }
                userName.setText(card.getUser().getName());
            }
            videoTitle.setText(card.getTitle());
            musicInfo.setText("♫ " + (card.getUser() != null ? card.getUser().getName() : "用户") + "创作的原声 | 发同款");
        }

        private void bindInteractions(GraphicCardBean card) {
            boolean liked = isLiked(card);
            likeButton.setTag(liked);
            likeButton.setColorFilter(liked
                    ? likeButton.getContext().getResources().getColor(R.color.xhs_red, null)
                    : likeButton.getContext().getResources().getColor(R.color.white, null));
            int likeValue = shownCount(likeCounts, card);
            if (likeValue == 0 && !liked) likeValue = card.getLikes();
            likesText.setText(formatCount(likeValue));

            boolean collected = isCollected(card);
            collectButton.setTag(collected);
            collectButton.setColorFilter(collected
                    ? collectButton.getContext().getResources().getColor(R.color.xhs_red, null)
                    : collectButton.getContext().getResources().getColor(R.color.white, null));
            collectsText.setText(formatCount(shownCount(collectionCounts, card)));

            commentsText.setText(formatCount(shownCount(commentCounts, card)));

            likeButton.setOnClickListener(v -> listener.onLike(card));
            collectButton.setOnClickListener(v -> listener.onCollect(card));
            commentButton.setOnClickListener(v -> listener.onComment(card));
            commentInput.setOnClickListener(v -> listener.onComment(card));
            shareButton.setOnClickListener(v -> listener.onShare(card));
            duetButton.setOnClickListener(v -> listener.onDuet(card));

            followButton.setOnClickListener(v -> {
                boolean following = Boolean.TRUE.equals(followButton.getTag());
                followButton.setTag(!following);
                applyFollowStyle(!following);
            });
            applyFollowStyle(Boolean.TRUE.equals(followButton.getTag()));
        }

        private void applyFollowStyle(boolean following) {
            if (following) {
                followButton.setText("已关注");
                followButton.setTextColor(followButton.getContext().getResources().getColor(R.color.text_secondary, null));
            } else {
                followButton.setText("关注");
                followButton.setTextColor(followButton.getContext().getResources().getColor(R.color.xhs_red, null));
            }
        }

        private void setupGestures(GraphicCardBean card) {
            GestureDetector detector = new GestureDetector(playerView.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            togglePlayPause();
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            listener.onLike(card);
                            bounce(likeButton);
                            return true;
                        }
                    });
            playerView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
        }

        void togglePlayPause() {
            if (playWhenReady) {
                pausePlayback();
            } else {
                resumePlayback();
            }
        }

        void refreshPlayStateIcon() {
            boolean playing = playWhenReady;
            playStateIcon.setVisibility(playing ? View.GONE : View.VISIBLE);
        }

        private void bounce(View view) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.4f, 1f);
            animator.setDuration(320);
            animator.start();
            ObjectAnimator animatorX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.4f, 1f);
            animatorX.setDuration(320);
            animatorX.start();
        }
    }
}
