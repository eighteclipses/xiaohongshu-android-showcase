package com.xiaohongshu.activity.graphic;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.NoteEntity;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;
import com.xiaohongshu.ui.home.discovery.DiscoveryAdapter;
import com.xiaohongshu.ui.home.bean.GraphicCardType;
import com.xiaohongshu.util.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记详情页Activity
 * 显示笔记详情，支持点赞、评论、收藏、关注等功能
 */
public class GraphicActivity extends BaseActivity {
    public static final String KEY_ID = "key_id";
    private GraphicViewModel viewModel;
    private ImageView imageView;
    private androidx.viewpager2.widget.ViewPager2 imagesViewPager;
    private TextView imageIndicator;
    private DetailImageAdapter detailImageAdapter;
    private TextView titleText;
    private TextView contentText;
    private TextView editInfoText;
    private TextView commentCountText;
    private RecyclerView commentsRecyclerView;
    private CommentAdapter commentAdapter;
    private EditText commentInput;
    private View sendCommentButton;
    /** 当前正在回复的评论；null 表示发普通评论 */
    private CommentBean replyToComment;
    private TextView replyingHint;

    /** 详情页底部"相关推荐"：双列瀑布流，与首页发现流卡片样式一致 */
    private TextView relatedTitle;
    private RecyclerView relatedRecyclerView;
    private DiscoveryAdapter relatedAdapter;
    
    private ImageView backButton;
    private ImageView userAvatar;
    private TextView userName;
    private TextView followButton;
    private ImageView shareButton;
    private ImageView deleteButton;
    
    private ImageView likeButton;
    private TextView likesText;
    private ImageView collectButton;
    private TextView collectsText;
    private ImageView commentButton;
    private TextView commentsText;
    private ImageView bottomShareButton;
    
    private AppDatabase database;
    
    public static void newInstance(Context context, String id) {
        Intent intent = new Intent(context, GraphicActivity.class);
        intent.putExtra(KEY_ID, id);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphic);
        
        database = AppApplication.getDatabase();
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(GraphicViewModel.class);
        String id = getIntent().getStringExtra(KEY_ID);
        if (id != null) {
            viewModel.setNoteId(id);
            viewModel.init();
            
            // 记录浏览历史
            recordBrowsingHistory(id);
        }
        
        initViews();
        initData();
    }
    
    /**
     * 记录浏览历史
     */
    private void recordBrowsingHistory(String noteId) {
        android.content.SharedPreferences sp = getSharedPreferences("browsing_history", 0);
        String historyJson = sp.getString("history_note_ids", "[]");
        
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType();
            java.util.List<String> noteIds = gson.fromJson(historyJson, listType);
            
            if (noteIds == null) {
                noteIds = new java.util.ArrayList<>();
            }
            
            // 如果已存在，先移除
            noteIds.remove(noteId);
            // 添加到最前面
            noteIds.add(0, noteId);
            
            // 限制历史记录数量
            if (noteIds.size() > 100) {
                noteIds = noteIds.subList(0, 100);
            }
            
            // 保存
            android.content.SharedPreferences.Editor editor = sp.edit();
            editor.putString("history_note_ids", gson.toJson(noteIds));
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void initViews() {
        View graphicTopBar = findViewById(R.id.graphicTopBar);
        if (graphicTopBar != null) {
            backButton = graphicTopBar.findViewById(R.id.backButton);
            userAvatar = graphicTopBar.findViewById(R.id.userAvatar);
            userName = graphicTopBar.findViewById(R.id.userName);
            followButton = graphicTopBar.findViewById(R.id.followButton);
            shareButton = graphicTopBar.findViewById(R.id.shareButton);
            deleteButton = graphicTopBar.findViewById(R.id.deleteButton);
        }
        
        View graphicBody = findViewById(R.id.graphicBody);
        if (graphicBody != null) {
            imageView = graphicBody.findViewById(R.id.imageView);
            // 真实小红书详情：图片全宽、按原始比例撑高（长图封顶约 1.6 倍屏宽，超出点开全屏看）。
            // 高度在图片加载成功回调里按真实比例设置，避免 adjustViewBounds 的测量不稳定。
            if (imageView != null) {
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            imagesViewPager = graphicBody.findViewById(R.id.imagesViewPager);
            imageIndicator = graphicBody.findViewById(R.id.imageIndicator);
            if (imagesViewPager != null) {
                detailImageAdapter = new DetailImageAdapter(R.drawable.placeholder_image);
                imagesViewPager.setAdapter(detailImageAdapter);
                imagesViewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        updateIndicator(position);
                    }
                });
                detailImageAdapter.setOnImageClickListener(this::showFullScreenPreview);
            }
            titleText = graphicBody.findViewById(R.id.titleText);
            contentText = graphicBody.findViewById(R.id.contentText);
            editInfoText = graphicBody.findViewById(R.id.editInfoText);
            commentCountText = graphicBody.findViewById(R.id.commentCountText);
            commentsRecyclerView = graphicBody.findViewById(R.id.commentsRecyclerView);
            // 评论输入条在底部固定容器（comment_input_bar.xml），不在滚动体内
            commentInput = findViewById(R.id.commentInput);
            sendCommentButton = findViewById(R.id.sendCommentButton);
            replyingHint = findViewById(R.id.replyingHint);
            View mentionButton = findViewById(R.id.mentionButton);
            if (mentionButton != null) {
                mentionButton.setOnClickListener(v -> showMentionDialog());
            }
            // 键盘弹出/收起时同步给底部容器留白，评论输入条始终位于键盘上方
            View bottomContainer = findViewById(R.id.graphicBottomContainer);
            if (bottomContainer != null) {
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomContainer, (v, insets) -> {
                    androidx.core.graphics.Insets bars = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars()
                                    | androidx.core.view.WindowInsetsCompat.Type.ime());
                    v.setPadding(0, 0, 0, bars.bottom);
                    return insets;
                });
            }
        }

        View graphicBottomBar = findViewById(R.id.graphicBottomBar);
        if (graphicBottomBar != null) {
            likeButton = graphicBottomBar.findViewById(R.id.likeButton);
            likesText = graphicBottomBar.findViewById(R.id.likesText);
            collectButton = graphicBottomBar.findViewById(R.id.collectButton);
            collectsText = graphicBottomBar.findViewById(R.id.collectsText);
            commentButton = graphicBottomBar.findViewById(R.id.commentButton);
            commentsText = graphicBottomBar.findViewById(R.id.commentsText);
            bottomShareButton = graphicBottomBar.findViewById(R.id.bottomShareButton);
        }
        
        if (commentsRecyclerView != null) {
            commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            commentAdapter = new CommentAdapter();
            commentAdapter.setOnCommentClickListener(comment -> {
                if (commentInput == null || comment == null) return;
                // 进入回复态：记录被回复评论，输入框聚焦并提示
                replyToComment = comment;
                String name = comment.getUser() == null ? "用户" : comment.getUser().getName();
                if (replyingHint != null) {
                    replyingHint.setVisibility(View.VISIBLE);
                    replyingHint.setText("回复 @" + name + "（点击取消）");
                }
                commentInput.setHint("回复 @" + name + "…");
                commentInput.requestFocus();
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(commentInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            // 评论点赞：本地计数 + 红心高亮
            commentAdapter.setOnCommentLikeClickListener(comment -> viewModel.toggleCommentLike(comment));
            commentsRecyclerView.setAdapter(commentAdapter);

            // 相关推荐：双列瀑布流，与首页发现流一致；初始 GONE，数据非空时才展开
            relatedTitle = graphicBody.findViewById(R.id.relatedTitle);
            relatedRecyclerView = graphicBody.findViewById(R.id.relatedRecyclerView);
            if (relatedRecyclerView != null) {
                relatedRecyclerView.setLayoutManager(
                        new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
                relatedAdapter = new DiscoveryAdapter(card -> {
                    if (card.getType() == GraphicCardType.Graphic) {
                        GraphicActivity.newInstance(this, card.getId());
                    } else {
                        com.xiaohongshu.activity.video.VideoActivity.newInstance(this, card.getId());
                    }
                });
                relatedRecyclerView.setAdapter(relatedAdapter);
            }
        }

        // 图片区手势：单图用 GestureDetector，多图在 DetailImageAdapter 内处理双击
        if (imageView != null) {
            setupImageGestures(imageView);
        }
        if (imagesViewPager != null) {
            detailImageAdapter.setOnImageDoubleTapListener(this::likeWithBurst);
        }

        // 回复态提示条：点击取消回复
        if (replyingHint != null) {
            replyingHint.setOnClickListener(v -> clearReplyState());
        }
        
        // 返回按钮
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        // 作者头像/昵称：跳转到对方用户主页
        View.OnClickListener openProfile = v -> {
            GraphicCardBean current = viewModel.getGraphicCardBean().getValue();
            if (current != null && current.getUser() != null
                    && current.getUser().getId() != null && !current.getUser().getId().isEmpty()) {
                com.xiaohongshu.ui.profile.UserProfileActivity.start(this, current.getUser().getId());
            }
        };
        if (userAvatar != null) userAvatar.setOnClickListener(openProfile);
        if (userName != null) userName.setOnClickListener(openProfile);
        
        // 关注按钮
        if (followButton != null) {
            followButton.setOnClickListener(v -> {
                String authorId = viewModel.getAuthorId().getValue();
                if (authorId != null && !authorId.isEmpty()) {
                    viewModel.toggleFollow(authorId);
                }
            });
        }
        
        // 分享按钮：对齐真实小红书，弹出操作面板（分享 / 举报）
        if (shareButton != null) {
            shareButton.setOnClickListener(v -> {
                showShareSheet();
            });
        }

        // 底部分享按钮：保持直接分享的快捷路径
        if (bottomShareButton != null) {
            bottomShareButton.setOnClickListener(v -> {
                shareNote();
            });
        }
        
        // 点赞按钮
        if (likeButton != null) {
            likeButton.setOnClickListener(v -> {
                viewModel.toggleLike();
            });
        }
        
        // 收藏按钮
        if (collectButton != null) {
            collectButton.setOnClickListener(v -> {
                viewModel.toggleCollection();
            });
        }
        
        // 评论按钮
        if (commentButton != null) {
            commentButton.setOnClickListener(v -> {
                // 聚焦到评论输入框
                if (commentInput != null) {
                    commentInput.requestFocus();
                }
            });
        }
        
        // 发送评论按钮
        if (sendCommentButton != null) {
            sendCommentButton.setOnClickListener(v -> {
                if (commentInput != null) {
                    String content = commentInput.getText().toString().trim();
                    if (!content.isEmpty()) {
                        viewModel.addComment(content, replyToComment);
                        commentInput.setText("");
                        commentInput.setHint("说点什么...");
                        replyToComment = null;
                        if (replyingHint != null) replyingHint.setVisibility(View.GONE);
                        Toast.makeText(this, "评论成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "评论内容不能为空", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        
        // 底部分享按钮
        if (bottomShareButton != null) {
            bottomShareButton.setOnClickListener(v -> {
                shareNote();
            });
        }

        // 举报结果反馈
        viewModel.getReportResult().observe(this, result -> {
            if (result == null) return;
            int message;
            switch (result) {
                case GraphicViewModel.REPORT_OK:
                    message = R.string.report_success;
                    break;
                case GraphicViewModel.REPORT_NEED_LOGIN:
                    message = R.string.report_need_login;
                    break;
                default:
                    message = R.string.report_offline;
                    break;
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
        
        // 删除按钮（仅当是作者时显示）
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                showDeleteNoteDialog();
            });
        }
    }
    
    /**
     * 显示删除笔记确认对话框
     */
    private void showDeleteNoteDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("移入回收站")
            .setMessage("移入回收站后将不再展示，可在网页创作者中心恢复为草稿。")
            .setPositiveButton("移入回收站", (dialog, which) -> {
                deleteNote();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 删除笔记
     */
    private void deleteNote() {
        String noteId = viewModel.getNoteId();
        if (noteId == null || noteId.isEmpty()) {
            return;
        }
        
        new Thread(() -> {
            try {
                com.xiaohongshu.ui.publish.repository.NoteRepository.getInstance(getApplicationContext()).recycleNote(noteId);
                runOnUiThread(()->{Toast.makeText(this,"已移入回收站",Toast.LENGTH_SHORT).show();finish();});
            } catch(Exception error) {runOnUiThread(()->Toast.makeText(this,"操作失败："+error.getMessage(),Toast.LENGTH_LONG).show());}
        }).start();
    }

    private void shareNote() {
        String title = titleText == null ? "" : titleText.getText().toString().trim();
        String text = title.isEmpty() ? "看看这篇小红书笔记" : "分享一篇笔记：" + title;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(Intent.createChooser(intent, "分享到"));
        } catch (Exception e) {
            Toast.makeText(this, "当前没有可用的分享应用", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 分享操作面板：对齐真实小红书"分享 → 更多操作（举报）"的层级。
     * 举报是内容治理链路的客户端入口，此前服务端与管理后台已就绪但无入口，导致举报数据恒为空。
     */
    private void showShareSheet() {
        final String[] actions = {"分享给朋友", "举报该笔记"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("笔记操作")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        shareNote();
                    } else {
                        showReportDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 举报原因选择：与真实小红书的举报分类一致。
     * 提交后由服务端去重（同一用户对同一笔记的 pending 举报只保留一条）。
     */
    private void showReportDialog() {
        final String[] reasons = {"垃圾广告或营销", "不实信息或谣言", "侵犯个人权益", "不适当内容", "其他"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("举报该笔记")
                .setItems(reasons, (dialog, which) ->
                        viewModel.reportNote(reasons[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 单图手势：单击开全屏预览，双击点赞（对齐真实小红书） */
    private void setupImageGestures(View imageView) {
        android.view.GestureDetector detector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                        showFullScreenPreview();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(android.view.MotionEvent e) {
                        likeWithBurst();
                        return true;
                    }
                });
        imageView.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    /** 双击点赞：调点赞逻辑 + 播放心形动画 */
    private void likeWithBurst() {
        viewModel.toggleLike();
        showLikeBurst();
    }

    /** 图片区中央弹出的心形缩放动画，播放完自动移除 */
    private void showLikeBurst() {
        View container = findViewById(R.id.imageContainer);
        if (container instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout frame = (android.widget.FrameLayout) container;
            ImageView heart = new ImageView(this);
            heart.setImageResource(R.drawable.icon_favorite);
            heart.setColorFilter(getResources().getColor(R.color.xhs_red, null));

            int size = (int) (getResources().getDisplayMetrics().density * 96);
            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                    size, size, android.view.Gravity.CENTER);
            frame.addView(heart, params);
            heart.setScaleX(0.3f);
            heart.setScaleY(0.3f);
            heart.setAlpha(0f);

            android.animation.AnimatorSet set = new android.animation.AnimatorSet();
            set.playTogether(
                    android.animation.ObjectAnimator.ofFloat(heart, View.SCALE_X, 0.3f, 1.3f, 1f),
                    android.animation.ObjectAnimator.ofFloat(heart, View.SCALE_Y, 0.3f, 1.3f, 1f),
                    android.animation.ObjectAnimator.ofFloat(heart, View.ALPHA, 0f, 1f, 1f, 0f));
            set.setDuration(700);
            set.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    frame.removeView(heart);
                }
            });
            set.start();
        }
    }

    /**
     * @ 好友选择：弹出本地用户列表，选中后在输入框光标处插入「@昵称 」。
     */
    private void showMentionDialog() {
        new Thread(() -> {
            java.util.List<com.xiaohongshu.database.entity.UserEntity> users =
                    database.userDao().getAllUsersSync();
            runOnUiThread(() -> {
                if (users == null || users.isEmpty()) {
                    Toast.makeText(this, "暂无可@的用户", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[users.size()];
                for (int i = 0; i < users.size(); i++) {
                    names[i] = users.get(i).nickname != null && !users.get(i).nickname.isEmpty()
                            ? users.get(i).nickname : users.get(i).username;
                }
                new android.app.AlertDialog.Builder(this)
                        .setTitle("选择用户")
                        .setItems(names, (dialog, which) -> insertMention(names[which]))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }).start();
    }

    private void insertMention(String name) {
        if (commentInput == null) return;
        int start = Math.max(commentInput.getSelectionStart(), 0);
        int end = Math.max(commentInput.getSelectionEnd(), 0);
        int cursor = Math.min(start, end);
        String text = "@" + name + " ";
        commentInput.getText().insert(cursor, text);
        commentInput.setSelection(cursor + text.length());
        commentInput.requestFocus();
    }

    private void updateIndicator(int position) {
        if (imageIndicator == null || detailImageAdapter == null) return;
        int total = detailImageAdapter.getItemCount();
        if (total <= 1) {
            imageIndicator.setVisibility(View.GONE);
            return;
        }
        imageIndicator.setVisibility(View.VISIBLE);
        imageIndicator.setText((position + 1) + "/" + total);
    }

    private void clearReplyState() {
        replyToComment = null;
        if (replyingHint != null) replyingHint.setVisibility(View.GONE);
        if (commentInput != null) commentInput.setHint("说点什么...");
    }

    /** 全屏预览：Dialog 覆盖屏幕，轮播 + 双击/捏合缩放，点击空白关闭 */
    private void showFullScreenPreview() {
        GraphicCardBean card = viewModel.getGraphicCardBean().getValue();
        List<String> uris = card == null ? new ArrayList<>() : card.getImageUris();
        if (uris.isEmpty()) return;

        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        androidx.viewpager2.widget.ViewPager2 pager = new androidx.viewpager2.widget.ViewPager2(this);
        DetailImageAdapter previewAdapter = new DetailImageAdapter(R.drawable.placeholder_image);
        previewAdapter.setFullScreenMode(true);
        previewAdapter.submit(uris);
        previewAdapter.setOnImageClickListener(dialog::dismiss);
        pager.setAdapter(previewAdapter);
        root.addView(pager, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView counter = new TextView(this);
        counter.setTextColor(0xFFFFFFFF);
        counter.setTextSize(13f);
        counter.setPadding(32, 24, 32, 32);
        counter.setText((pager.getCurrentItem() + 1) + "/" + uris.size());
        root.addView(counter);
        pager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                counter.setText((position + 1) + "/" + uris.size());
            }
        });

        dialog.setContentView(root);
        dialog.show();
    }
    
    @Override
    protected void initData() {
        // 观察笔记数据
        viewModel.getGraphicCardBean().observe(this, card -> {
            if (card != null) {
                if (imageView != null) {
                    List<String> imageUris = card.getImageUris();
                    if (imageUris.size() > 1 && imagesViewPager != null && imageIndicator != null) {
                        // 多图：轮播 + 指示器，隐藏单图视图
                        imageView.setVisibility(View.GONE);
                        imagesViewPager.setVisibility(View.VISIBLE);
                        imageIndicator.setVisibility(View.VISIBLE);
                        detailImageAdapter.setFullScreenMode(false);
                        detailImageAdapter.submit(imageUris);
                        imagesViewPager.setCurrentItem(0, false);
                        updateIndicator(0);
                    } else if (!card.getImageUri().trim().isEmpty()) {
                        if (imagesViewPager != null) imagesViewPager.setVisibility(View.GONE);
                        if (imageIndicator != null) imageIndicator.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                        imageView.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                        ImageLoader.load(imageView, card.getImageUri(), R.drawable.placeholder_image,
                                (w, h) -> {
                                    if (w <= 0 || h <= 0 || imageView.getWidth() <= 0) return;
                                    int target = imageView.getWidth() * h / w;
                                    int cap = (int) (getResources().getDisplayMetrics().widthPixels * 1.6f);
                                    if (target > cap) target = cap;
                                    if (target < imageView.getWidth() / 3) target = imageView.getWidth() / 3;
                                    android.view.ViewGroup.LayoutParams lp = imageView.getLayoutParams();
                                    if (lp.height != target) {
                                        lp.height = target;
                                        imageView.setLayoutParams(lp);
                                    }
                                });
                        // 单击/双击手势已在 initViews 统一挂载
                    } else if (card.getImage() == 0) {
                        imageView.setVisibility(View.GONE);
                        if (imagesViewPager != null) imagesViewPager.setVisibility(View.GONE);
                        if (imageIndicator != null) imageIndicator.setVisibility(View.GONE);
                    } else {
                        if (imagesViewPager != null) imagesViewPager.setVisibility(View.GONE);
                        if (imageIndicator != null) imageIndicator.setVisibility(View.GONE);
                        imageView.setVisibility(View.VISIBLE);
                        imageView.setImageResource(card.getImage());
                    }
                }
                
                if (titleText != null) {
                    titleText.setText(card.getTitle());
                    titleText.setVisibility(com.xiaohongshu.ui.publish.TextToImageConverter
                            .isGeneratedTextImage(card.getImageUri()) ? View.GONE : View.VISIBLE);
                }
                if (contentText != null) {
                    String content = card.getContent();
                    contentText.setText(content);
                    boolean generatedTextImage = com.xiaohongshu.ui.publish.TextToImageConverter
                            .isGeneratedTextImage(card.getImageUri());
                    contentText.setVisibility(content.trim().isEmpty() || generatedTextImage ? View.GONE : View.VISIBLE);
                }
                
                if (card.getUser() != null) {
                    if (userAvatar != null) {
                        // 优先使用UserBean中设置的头像，确保显示最新的头像；本地 URI 也走 Coil，失败回退占位圆
                        String imageUri = card.getUser().getImageUri();
                        if (!imageUri.trim().isEmpty()) {
                            ImageLoader.load(userAvatar, imageUri, R.drawable.placeholder_avatar);
                        } else {
                            int avatarRes;
                            int userImage = card.getUser().getImage();
                            if (userImage > 0 && isP1ToP11Avatar(userImage)) {
                                avatarRes = userImage;
                            } else {
                                String userId = card.getUser().getId();
                                avatarRes = userId == null || userId.isEmpty() ? R.drawable.p1 : getAvatarResource(userId.hashCode());
                            }
                            userAvatar.setImageResource(avatarRes);
                        }
                    }
                    if (userName != null) {
                        userName.setText(card.getUser().getName());
                    }
                }
            }
        });
        
        // 观察编辑信息
        viewModel.getEditInfo().observe(this, info -> {
            if (editInfoText != null && info != null) {
                editInfoText.setText(info);
            }
        });
        
        // 观察点赞数
        viewModel.getLikeCount().observe(this, count -> {
            if (likesText != null && count != null) {
                likesText.setText(String.valueOf(count));
            }
        });
        
        // 观察收藏数
        viewModel.getCollectionCount().observe(this, count -> {
            if (collectsText != null && count != null) {
                collectsText.setText(String.valueOf(count));
            }
        });
        
        // 观察点赞状态
        viewModel.getIsLiked().observe(this, isLiked -> {
            if (likeButton != null && isLiked != null) {
                if (isLiked) {
                    likeButton.setColorFilter(getResources().getColor(R.color.xhs_red, null));
                } else {
                    likeButton.clearColorFilter();
                }
            }
        });
        
        // 观察收藏状态
        viewModel.getIsCollected().observe(this, isCollected -> {
            if (collectButton != null && isCollected != null) {
                if (isCollected) {
                    collectButton.setColorFilter(getResources().getColor(R.color.xhs_red, null));
                } else {
                    collectButton.clearColorFilter();
                }
            }
        });
        
        // 观察关注状态
        viewModel.getIsFollowed().observe(this, isFollowed -> {
            if (followButton != null && isFollowed != null) {
                if (isFollowed) {
                    followButton.setText("已关注");
                    followButton.setTextColor(getResources().getColor(R.color.text_secondary, null));
                } else {
                    followButton.setText("关注");
                    followButton.setTextColor(getResources().getColor(R.color.xhs_red, null));
                }
            }
        });
        
        // 观察作者ID，判断是否显示删除按钮
        viewModel.getAuthorId().observe(this, authorId -> {
            if (authorId != null && !authorId.isEmpty()) {
                // 获取当前用户ID
                String currentUserId = getCurrentUserId();
                if (currentUserId != null && currentUserId.equals(authorId)) {
                    // 是作者，显示删除按钮
                    if (deleteButton != null) {
                        deleteButton.setVisibility(View.VISIBLE);
                    }
                    // 隐藏关注按钮（自己不能关注自己）
                    if (followButton != null) {
                        followButton.setVisibility(View.GONE);
                    }
                } else {
                    // 不是作者，隐藏删除按钮
                    if (deleteButton != null) {
                        deleteButton.setVisibility(View.GONE);
                    }
                }
            }
        });
        
        // 观察评论列表
        viewModel.getCommentList().observe(this, comments -> {
            if (comments != null) {
                if (commentAdapter != null) {
                    commentAdapter.updateComments(comments);
                }

                if (commentCountText != null) {
                    commentCountText.setText("共" + comments.size() + "条评论");
                }

                if (commentsText != null) {
                    commentsText.setText(String.valueOf(comments.size()));
                }
            }
        });

        // 观察相关推荐：仅在结果非空时展开整块区域，避免空标题抢走视觉
        viewModel.getRelatedNotes().observe(this, related -> {
            int count = related == null ? 0 : related.size();
            if (relatedAdapter != null) relatedAdapter.updateData(related == null ? new ArrayList<>() : related);
            boolean show = count > 0;
            if (relatedTitle != null) relatedTitle.setVisibility(show ? View.VISIBLE : View.GONE);
            if (relatedRecyclerView != null) relatedRecyclerView.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        com.xiaohongshu.bean.UserBean currentUser = 
            com.xiaohongshu.ui.login.LoginDataRepository.getInstance(this).getCurrentUser();
        if (currentUser != null) {
            com.xiaohongshu.database.entity.UserEntity userEntity = 
                database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                return userEntity.id;
            }
        }
        return null;
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
