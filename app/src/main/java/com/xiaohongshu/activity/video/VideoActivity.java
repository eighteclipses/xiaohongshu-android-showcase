package com.xiaohongshu.activity.video;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.viewpager2.widget.ViewPager2;

import com.gyf.immersionbar.ImmersionBar;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.Map;
import java.util.Set;

/**
 * 刷视频页：上下滑切换视频，自动播放当前视频
 */
public class VideoActivity extends BaseActivity {
    private VideoViewModel viewModel;
    private ViewPager2 viewPager;
    private VideoPagerAdapter adapter;
    private ExoPlayer player;
    private TextView emptyText;
    private int currentVideoIndex = 0;
    private boolean feedReady = false;

    public static void newInstance(Context context, String id) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra(GraphicActivity.KEY_ID, id);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        ImmersionBar.with(this).init();

        viewModel = new ViewModelProvider(this,
                new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(VideoViewModel.class);
        viewModel.setId(getIntent().getStringExtra(GraphicActivity.KEY_ID));

        initViews();
        initData();
        viewModel.init();
    }

    @Override
    protected void initViews() {
        viewPager = findViewById(R.id.viewPager);
        emptyText = findViewById(R.id.emptyText);
        View videoTopBar = findViewById(R.id.videoTopBar);
        ImageView backButton = null;
        if (videoTopBar != null) {
            backButton = videoTopBar.findViewById(R.id.backButton);
        }

        player = new ExoPlayer.Builder(this).build();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);

        adapter = new VideoPagerAdapter(player, new VideoPagerAdapter.InteractionListener() {
            @Override
            public void onLike(GraphicCardBean card) {
                if (!checkLoggedIn()) return;
                viewModel.toggleLike(card);
            }

            @Override
            public void onCollect(GraphicCardBean card) {
                if (!checkLoggedIn()) return;
                viewModel.toggleCollect(card);
            }

            @Override
            public void onComment(GraphicCardBean card) {
                // 真实小红书交互：视频页内弹出底部评论面板，视频继续播放
                showCommentSheet(card);
            }

            @Override
            public void onShare(GraphicCardBean card) {
                String text = card.getTitle() == null || card.getTitle().isEmpty()
                        ? "看看这篇小红书视频笔记"
                        : "分享一个小红书视频：" + card.getTitle();
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, text);
                try {
                    startActivity(Intent.createChooser(intent, "分享到"));
                } catch (Exception e) {
                    Toast.makeText(VideoActivity.this, "当前没有可用的分享应用", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDuet(GraphicCardBean card) {
                // 发同款：跳转到笔记发布页
                startActivity(new Intent(VideoActivity.this, com.xiaohongshu.ui.publish.NoteEditActivity.class));
            }
        });
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentVideoIndex = position;
                if (feedReady) {
                    adapter.playVideo(position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                super.onPageScrollStateChanged(state);
                if (!feedReady) return;
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    adapter.resumePlayback();
                } else if (state == ViewPager2.SCROLL_STATE_DRAGGING
                        || state == ViewPager2.SCROLL_STATE_SETTLING) {
                    adapter.pausePlayback();
                }
            }
        });

        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    @Override
    protected void initData() {
        viewModel.getVideoList().observe(this, videos -> {
            if (videos == null || videos.isEmpty()) {
                // 静默展示空状态，不再弹 Toast 打扰用户
                if (emptyText != null) emptyText.setVisibility(View.VISIBLE);
                return;
            }
            if (emptyText != null) emptyText.setVisibility(View.GONE);
            feedReady = true;
            adapter.updateVideos(videos);
            int startPosition = 0;
            String targetId = viewModel.getId();
            for (int i = 0; i < videos.size(); i++) {
                if (videos.get(i).getId().equals(targetId)) {
                    startPosition = i;
                    break;
                }
            }
            currentVideoIndex = startPosition;
            viewPager.setCurrentItem(startPosition, false);
            final int firstVideo = startPosition;
            viewPager.post(() -> adapter.playVideo(firstVideo));
        });

        viewModel.getLikedIds().observe(this, liked -> refreshInteractionState());
        viewModel.getCollectedIds().observe(this, collected -> refreshInteractionState());
        viewModel.getLikeCounts().observe(this, counts -> refreshInteractionState());
        viewModel.getCollectionCounts().observe(this, counts -> refreshInteractionState());
        viewModel.getCommentCounts().observe(this, counts -> refreshInteractionState());
    }

    private void refreshInteractionState() {
        adapter.updateInteractionState(
                latest(viewModel.getLikedIds().getValue()),
                latest(viewModel.getCollectedIds().getValue()),
                latestMap(viewModel.getLikeCounts().getValue()),
                latestMap(viewModel.getCollectionCounts().getValue()),
                latestMap(viewModel.getCommentCounts().getValue()));
    }

    /**
     * 底部评论面板：仿真实小红书——半屏弹层覆盖视频下半部，视频继续播放，
     * 面板内浏览/发表评论，不发跳转。
     */
    private void showCommentSheet(GraphicCardBean card) {
        if (card == null) return;
        android.app.Dialog sheet = new android.app.Dialog(this);
        sheet.setContentView(R.layout.sheet_video_comments);
        android.view.Window window = sheet.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.62));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        androidx.recyclerview.widget.RecyclerView list =
                sheet.findViewById(R.id.commentSheetList);
        TextView title = sheet.findViewById(R.id.commentSheetTitle);
        android.widget.EditText input = sheet.findViewById(R.id.commentSheetInput);
        android.widget.ImageView send = sheet.findViewById(R.id.commentSheetSend);
        android.widget.ImageView close = sheet.findViewById(R.id.commentSheetClose);

        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        com.xiaohongshu.activity.graphic.CommentAdapter commentAdapter =
                new com.xiaohongshu.activity.graphic.CommentAdapter();
        list.setAdapter(commentAdapter);

        viewModel.getComments().observe(this, comments -> {
            if (comments == null) comments = new java.util.ArrayList<>();
            commentAdapter.updateComments(comments);
            title.setText("共" + comments.size() + "条评论");
            if (!comments.isEmpty()) list.smoothScrollToPosition(0);
        });

        // 评论数量联动：底部计数同步
        viewModel.getCommentCounts().observe(this, counts -> {
            Integer count = counts.get(card.getId());
            if (count != null) title.setText("共" + count + "条评论");
        });

        viewModel.loadComments(card.getId());

        Runnable sendAction = () -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "评论内容不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (LoginDataRepository.getInstance(this).getCurrentUser() == null) {
                Toast.makeText(this, "请先登录后再评论", Toast.LENGTH_SHORT).show();
                return;
            }
            viewModel.addComment(card.getId(), text);
            input.setText("");
        };
        send.setOnClickListener(v -> sendAction.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendAction.run();
                return true;
            }
            return false;
        });
        close.setOnClickListener(v -> sheet.dismiss());

        sheet.show();
    }

    private Set<String> latest(Set<String> value) {
        return value != null ? value : new java.util.HashSet<>();
    }

    private Map<String, Integer> latestMap(Map<String, Integer> value) {
        return value != null ? value : new java.util.HashMap<>();
    }

    private boolean checkLoggedIn() {
        if (LoginDataRepository.getInstance(this).getCurrentUser() == null) {
            Toast.makeText(this, "请先登录后再互动", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.pausePlayback();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && feedReady) {
            viewPager.post(() -> adapter.resumePlayback());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
