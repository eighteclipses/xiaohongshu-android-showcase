package com.xiaohongshu.ui.publish;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.gyf.immersionbar.ImmersionBar;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.publish.viewmodel.NoteEditViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NoteEditActivity extends BaseActivity {
    public static final String KEY_INITIAL_CONTENT = "key_initial_content";
    public static final String KEY_NOTE_ID = "key_note_id";
    public static final String KEY_IMAGE_URI = "key_image_uri";
    
    private static final int REQUEST_CODE_PICK_IMAGE = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    
    private NoteEditViewModel viewModel;
    private android.app.AlertDialog aiDialog;
    
    // UI Components
    private ImageView backButton;
    private TextView previewButton;
    private LinearLayout imageContainer;
    private FrameLayout addImageButton;
    private EditText titleInput;
    private EditText contentInput;
    private LinearLayout topicContainer;
    private LinearLayout locationContainer;
    private TextView visibilityText;
    private LinearLayout visibilitySection;
    private LinearLayout locationSection;
    private Button publishButton;
    private View saveDraftButton;
    private TextWatcher titleTextWatcher;
    private TextWatcher contentTextWatcher;
    private boolean isUpdatingTitleFromViewModel = false; // 标志：是否正在从ViewModel更新标题
    private boolean isUpdatingContentFromViewModel = false;
    
    // 话题/地点选项：话题从本地笔记动态聚合（无数据时用默认推荐兜底），地点为扩充后的常用地点
    private static final List<String> DEFAULT_TOPICS = Arrays.asList(
        "出印象曲", "连麦", "迷宫", "数学题", "理想之途走到哪啦",
        "穿搭", "美食", "旅行", "健身", "读书笔记"
    );

    private static final List<String> LOCATION_OPTIONS = Arrays.asList(
        "闽南理工学院宝盖", "德辉广场", "花海谷公园", "石狮荣誉国际酒店",
        "厦门中山路", "鼓浪屿", "曾厝垵", "环岛路", "沙坡尾", "厦门大学",
        "泉州西街", "开元寺", "清源山", "福州三坊七巷", "烟台山", "西湖公园"
    );

    private List<String> topicOptionsCache;

    private List<String> getTopicOptions() {
        if (topicOptionsCache == null) {
            topicOptionsCache = loadTopicOptions();
        }
        return topicOptionsCache;
    }

    /** 话题选项：本地笔记话题去重聚合并保留默认推荐，统一带 # 展示前缀 */
    private List<String> loadTopicOptions() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        try {
            List<com.xiaohongshu.database.entity.NoteEntity> notes =
                    AppApplication.getDatabase().noteDao().getAllPublicNotesSync();
            if (notes != null) {
                for (com.xiaohongshu.database.entity.NoteEntity note : notes) {
                    if (note == null || note.topics == null) continue;
                    for (String topic : note.topics) {
                        String name = topic == null ? "" : topic.trim();
                        if (name.startsWith("#")) name = name.substring(1).trim();
                        if (!name.isEmpty()) names.add(name);
                    }
                }
            }
        } catch (Exception ignored) {
            // 数据库异常时退回默认话题
        }
        names.addAll(DEFAULT_TOPICS);
        List<String> options = new ArrayList<>();
        for (String name : names) {
            options.add("#" + name);
            if (options.size() >= 12) break;
        }
        return options;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);
        
        ImmersionBar.with(this)
            .transparentStatusBar()
            .statusBarDarkFont(true)
            .init();
        
        viewModel = new ViewModelProvider(this).get(NoteEditViewModel.class);
        viewModel.init(this);
        
        String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        }
        
        initViews();
        initData();
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{permission}, REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permission granted, can now pick images
    }

    @Override
    protected void initViews() {
        backButton = findViewById(R.id.backButton);
        previewButton = findViewById(R.id.previewButton);
        imageContainer = findViewById(R.id.imageContainer);
        addImageButton = findViewById(R.id.addImageButton);
        titleInput = findViewById(R.id.titleInput);
        contentInput = findViewById(R.id.contentInput);
        topicContainer = findViewById(R.id.topicContainer);
        locationContainer = findViewById(R.id.locationContainer);
        visibilityText = findViewById(R.id.visibilityText);
        visibilitySection = findViewById(R.id.visibilitySection);
        locationSection = findViewById(R.id.locationSection);
        publishButton = findViewById(R.id.publishButton);
        saveDraftButton = findViewById(R.id.saveDraftButton);
        findViewById(R.id.aiNoteButton).setOnClickListener(v -> {
            if (aiDialog != null && aiDialog.isShowing()) return;
            aiDialog = AiNoteAssistant.show(this, contentInput.getText().toString(), (title, content, topics) -> {
                titleInput.removeCallbacks(updateTitleRunnable);
                contentInput.removeCallbacks(updateContentRunnable);
                titleInput.setText(title);
                contentInput.setText(content);
                syncEditorFields();
                List<String> generatedTopics = new ArrayList<>();
                for (String topic : topics) generatedTopics.add("#" + topic);
                viewModel.addTopics(generatedTopics);
                findViewById(R.id.aiNoteDisclosure).setVisibility(View.VISIBLE);
            });
        });
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        if (previewButton != null) {
            previewButton.setOnClickListener(v -> {
                showPreview();
            });
        }
        
        if (addImageButton != null) {
            addImageButton.setOnClickListener(v -> pickImage());
        }
        
        if (titleInput != null) {
            titleTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                
                @Override
                public void afterTextChanged(Editable s) {
                    // 如果正在从ViewModel更新，不触发更新，避免循环
                    if (!isUpdatingTitleFromViewModel) {
                        // 使用Handler延迟更新，避免频繁更新导致卡顿
                        titleInput.removeCallbacks(updateTitleRunnable);
                        titleInput.postDelayed(updateTitleRunnable, 300); // 300ms延迟，避免每次输入都更新
                    }
                }
            };
            titleInput.addTextChangedListener(titleTextWatcher);
        }

        if (contentInput != null) {
            contentTextWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (!isUpdatingContentFromViewModel) {
                        contentInput.removeCallbacks(updateContentRunnable);
                        contentInput.postDelayed(updateContentRunnable, 250);
                    }
                }
            };
            contentInput.addTextChangedListener(contentTextWatcher);
        }
        
        if (visibilitySection != null) {
            visibilitySection.setOnClickListener(v -> toggleVisibility());
        }
        
        if (locationSection != null) {
            locationSection.setOnClickListener(v -> showLocationOptions());
        }
        
        if (saveDraftButton != null) {
            saveDraftButton.setOnClickListener(v -> saveDraft());
        }
        
        if (publishButton != null) {
            publishButton.setOnClickListener(v -> publishNote());
        }

        View topicActionButton = findViewById(R.id.topicActionButton);
        if (topicActionButton != null) topicActionButton.setOnClickListener(v -> showTopicOptions());
        View userActionButton = findViewById(R.id.userActionButton);
        if (userActionButton != null) userActionButton.setOnClickListener(v -> showFeatureInfo("@ 用户", "输入 @ 后可以在正文中提及用户。"));
        View voteActionButton = findViewById(R.id.voteActionButton);
        if (voteActionButton != null) voteActionButton.setOnClickListener(v -> showFeatureInfo("投票", "投票组件会在发布后展示给读者。"));
        View longTextActionButton = findViewById(R.id.longTextActionButton);
        if (longTextActionButton != null) longTextActionButton.setOnClickListener(v -> showFeatureInfo("长文", "当前编辑器支持多行正文，可继续编辑后发布。"));
        View addComponentSection = findViewById(R.id.addComponentSection);
        if (addComponentSection != null) addComponentSection.setOnClickListener(v -> showFeatureInfo("添加组件", "地点、话题和可见范围组件已支持。"));
        View settingsButton = findViewById(R.id.settingsButton);
        if (settingsButton != null) settingsButton.setOnClickListener(v -> com.xiaohongshu.ui.settings.SettingsActivity.start(this));
        
        // Initialize topic tags
        initTopicTags();
        
        // Initialize location tags
        initLocationTags();
    }

    @Override
    protected void initData() {
        // Get initial content from intent
        String initialContent = getIntent().getStringExtra(KEY_INITIAL_CONTENT);
        String noteId = getIntent().getStringExtra(KEY_NOTE_ID);
        String imageUriString = getIntent().getStringExtra(KEY_IMAGE_URI);
        
        if (noteId != null && !noteId.isEmpty()) {
            // Load existing note
            viewModel.loadNote(noteId);
        } else {
            // Initialize new note
            viewModel.initializeNewNote(initialContent != null ? initialContent : "");
            
            // 如果传递了图片URI（从文字转换的图片），添加到图片列表
            if (imageUriString != null && !imageUriString.isEmpty()) {
                viewModel.addImageUri(imageUriString);
            }
        }
        
        // Observe ViewModel
        observeViewModel();
    }
    
    // Runnable用于延迟更新标题
    private final Runnable updateTitleRunnable = new Runnable() {
        @Override
        public void run() {
            if (titleInput != null) {
                String text = titleInput.getText() != null ? titleInput.getText().toString() : "";
                viewModel.setTitle(text);
            }
        }
    };

    private final Runnable updateContentRunnable = new Runnable() {
        @Override
        public void run() {
            if (contentInput != null) {
                String text = contentInput.getText() == null ? "" : contentInput.getText().toString();
                viewModel.setContent(text);
            }
        }
    };
    
    private void observeViewModel() {
        // 观察当前笔记数据，用于加载草稿
        viewModel.getCurrentNote().observe(this, note -> {
            if (note != null) {
                TextView moderationBanner=findViewById(R.id.moderationStatusText);
                String review=note.getReviewNote();
                moderationBanner.setText("状态：" + ("conflict".equals(note.getSyncState()) ? "版本冲突，点击加载最新版本" : "pending".equals(note.getSyncState()) ? "待同步" : "pending".equals(note.getModerationStatus()) ? "待审核" : "rejected".equals(note.getModerationStatus()) ? "已驳回" : "hidden".equals(note.getModerationStatus()) ? "已隐藏" : "已通过") + (review==null || review.isEmpty() ? "" : "\n"+review));
                moderationBanner.setOnClickListener(v -> {
                    if ("conflict".equals(note.getSyncState())) new android.app.AlertDialog.Builder(this)
                        .setTitle("加载服务器最新版本")
                        .setMessage("这会替换本机正在编辑的内容，请先复制需要保留的修改。")
                        .setNegativeButton("取消",null).setPositiveButton("加载最新版本",(d,w)->viewModel.reloadServerNote(note.getId())).show();
                });
                moderationBanner.setVisibility((review!=null&&!review.isEmpty())||!"synced".equals(note.getSyncState())?View.VISIBLE:View.GONE);
                // 更新标题 - 只在文本不同时更新，避免循环更新
                if (titleInput != null && note.getTitle() != null) {
                    String currentText = titleInput.getText() != null ? titleInput.getText().toString() : "";
                    if (!currentText.equals(note.getTitle())) {
                        // 设置标志，防止触发TextWatcher导致循环更新
                        isUpdatingTitleFromViewModel = true;
                        titleInput.setText(note.getTitle());
                        // 重置标志
                        isUpdatingTitleFromViewModel = false;
                    }
                }
                if (contentInput != null) {
                    String noteContent = note.getContent() == null ? "" : note.getContent();
                    String currentContent = contentInput.getText() == null ? "" : contentInput.getText().toString();
                    if (!currentContent.equals(noteContent)) {
                        isUpdatingContentFromViewModel = true;
                        contentInput.setText(noteContent);
                        contentInput.setSelection(contentInput.length());
                        isUpdatingContentFromViewModel = false;
                    }
                }
            }
        });
        
        viewModel.getImageUris().observe(this, uris -> {
            updateImageViews(uris);
        });
        
        viewModel.getTopics().observe(this, topics -> {
            updateTopicTags(topics);
        });
        
        viewModel.getLocation().observe(this, location -> {
            updateLocationTags(location == null || location.isEmpty() ? new ArrayList<>() : Arrays.asList(location));
        });
        
        viewModel.getIsPublic().observe(this, isPublic -> {
            if (visibilityText != null) {
                visibilityText.setText(isPublic ? getString(R.string.public_visible) : getString(R.string.private_visible));
            }
        });
        
        viewModel.getIsPublishing().observe(this, busy -> {
            boolean enabled = !Boolean.TRUE.equals(busy);
            if (publishButton != null) publishButton.setEnabled(enabled);
            if (saveDraftButton != null) saveDraftButton.setEnabled(enabled);
        });

        viewModel.getPublishResult().observe(this, result -> {
            if (result != null && !result.isEmpty()) {
                Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                if (!result.contains("失败")) {
                    finish();
                }
            }
        });
    }
    
    private void pickImage() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("添加图片")
                .setItems(new String[]{"从相册选择", "选择示例图片"}, (dialog, which) -> {
                    if (which == 0) {
                        openGallery();
                    } else {
                        showImageSelectionDialog();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        } catch (Exception error) {
            Toast.makeText(this, "暂时无法打开相册，请选择示例图片", Toast.LENGTH_SHORT).show();
            showImageSelectionDialog();
        }
    }
    
    /**
     * 显示图片选择对话框
     */
    private void showImageSelectionDialog() {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("选择图片");
            
            // 创建GridView来显示图片
            android.widget.GridView gridView = new android.widget.GridView(this);
            gridView.setNumColumns(3);
            gridView.setPadding(16, 16, 16, 16);
            gridView.setVerticalSpacing(8);
            gridView.setHorizontalSpacing(8);
            // 设置GridView可以拉伸填充
            gridView.setStretchMode(android.widget.GridView.STRETCH_COLUMN_WIDTH);
            
            // 图片资源ID列表 (image_1 到 image_15，共15张图片)
            final int[] imageResources = {
                R.drawable.image_1, R.drawable.image_2, R.drawable.image_3, R.drawable.image_4,
                R.drawable.image_5, R.drawable.image_6, R.drawable.image_7, R.drawable.image_8,
                R.drawable.image_9, R.drawable.image_10, R.drawable.image_11, R.drawable.image_12,
                R.drawable.image_13, R.drawable.image_14, R.drawable.image_15
            };
            
            // 创建适配器
            android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
                @Override
                public int getCount() {
                    return imageResources.length;
                }
                
                @Override
                public Object getItem(int position) {
                    return imageResources[position];
                }
                
                @Override
                public long getItemId(int position) {
                    return position;
                }
                
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    ImageView imageView;
                    if (convertView == null) {
                        imageView = new ImageView(NoteEditActivity.this);
                        // 使用dp转px，确保在不同屏幕上显示合适的大小
                        int sizeInDp = 100;
                        float density = getResources().getDisplayMetrics().density;
                        int sizeInPx = (int) (sizeInDp * density);
                        android.widget.GridView.LayoutParams params = new android.widget.GridView.LayoutParams(sizeInPx, sizeInPx);
                        imageView.setLayoutParams(params);
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setPadding(4, 4, 4, 4);
                    } else {
                        imageView = (ImageView) convertView;
                    }
                    
                    try {
                        imageView.setImageResource(imageResources[position]);
                    } catch (Exception e) {
                        // 如果图片资源不存在，使用默认图片
                        imageView.setImageResource(R.drawable.icon_picture);
                    }
                    return imageView;
                }
            };
            
            gridView.setAdapter(adapter);
            
            // 先设置GridView到对话框
            builder.setView(gridView);
            
            // 添加取消按钮
            builder.setNegativeButton("取消", null);
            
            // 创建对话框
            android.app.AlertDialog dialog = builder.create();
            
            // 设置点击监听器
            gridView.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    // 将资源ID转换为URI字符串格式
                    String imageUri = "android.resource://" + getPackageName() + "/" + imageResources[position];
                    viewModel.addImageUri(imageUri);
                    Toast.makeText(NoteEditActivity.this, "已添加图片", Toast.LENGTH_SHORT).show();
                    // 关闭对话框
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(NoteEditActivity.this, "添加图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
            
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "显示图片选择对话框失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void updateImageViews(List<String> imageUris) {
        if (imageContainer == null) return;
        
        // Remove all existing image views except add button
        int childCount = imageContainer.getChildCount();
        for (int i = childCount - 2; i >= 0; i--) {
            View child = imageContainer.getChildAt(i);
            if (child != addImageButton) {
                imageContainer.removeViewAt(i);
            }
        }
        
        // Add image views
        for (int i = 0; i < imageUris.size(); i++) {
            String uri = imageUris.get(i);
            View imageView = createImageView(uri, i);
            imageContainer.addView(imageView, i);
        }
    }
    
    private View createImageView(String uri, int index) {
        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(100, 100));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setPadding(8, 8, 8, 8);
        
        try {
            imageView.setImageURI(Uri.parse(uri));
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.icon_picture);
        }
        
        // Add delete button
        ImageView deleteButton = new ImageView(this);
        deleteButton.setLayoutParams(new FrameLayout.LayoutParams(24, 24));
        deleteButton.setImageResource(R.drawable.icon_close);
        deleteButton.setBackgroundResource(R.drawable.circle_white_background);
        deleteButton.setPadding(4, 4, 4, 4);
        deleteButton.setX(76);
        deleteButton.setY(0);
        deleteButton.setOnClickListener(v -> {
            viewModel.removeImageUri(uri);
        });
        
        container.addView(imageView);
        container.addView(deleteButton);
        
        return container;
    }
    
    private void initTopicTags() {
        if (topicContainer == null) return;
        
        for (String topic : getTopicOptions()) {
            View tagView = createTopicTagView(topic);
            topicContainer.addView(tagView);
        }
    }
    
    private void updateTopicTags(List<String> selectedTopics) {
        if (topicContainer == null) return;
        if (selectedTopics != null) {
            for (int index = selectedTopics.size() - 1; index >= 0; index--) {
                String topic = selectedTopics.get(index);
                boolean exists = false;
                for (int i = 0; i < topicContainer.getChildCount(); i++) {
                    View child = topicContainer.getChildAt(i);
                    if (child instanceof TextView && topic.contentEquals(((TextView) child).getText())) exists = true;
                }
                if (!exists) topicContainer.addView(createTopicTagView(topic), 0);
            }
        }
        for (int i = 0; i < topicContainer.getChildCount(); i++) {
            View child = topicContainer.getChildAt(i);
            if (child instanceof TextView) {
                String topic = ((TextView) child).getText().toString();
                boolean selected = selectedTopics != null && selectedTopics.contains(topic);
                ((TextView) child).setAlpha(selected ? 1f : 0.65f);
            }
        }
    }
    
    private View createTopicTagView(String topic) {
        TextView tagView = new TextView(this);
        tagView.setText(topic);
        tagView.setSingleLine(true);
        tagView.setTextSize(12);
        int space = Math.round(8 * getResources().getDisplayMetrics().density);
        tagView.setPadding(space + space / 2, space, space + space / 2, space);
        tagView.setBackgroundResource(R.drawable.bg_tag_rounded_transparent);
        tagView.setTextColor(getResources().getColor(R.color.xhs_red));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, space, space / 2);
        tagView.setLayoutParams(params);
        
        tagView.setOnClickListener(v -> {
            viewModel.addTopic(topic);
            Toast.makeText(this, "已添加话题: " + topic, Toast.LENGTH_SHORT).show();
        });
        
        return tagView;
    }
    
    private void initLocationTags() {
        if (locationContainer == null) return;
        
        for (String location : LOCATION_OPTIONS) {
            View locationView = createLocationTagView(location);
            locationContainer.addView(locationView);
        }
    }
    
    private void updateLocationTags(List<String> selectedLocations) {
        if (locationContainer == null) return;
        for (int i = 0; i < locationContainer.getChildCount(); i++) {
            View child = locationContainer.getChildAt(i);
            if (child instanceof TextView) {
                String location = ((TextView) child).getText().toString();
                boolean selected = selectedLocations != null && selectedLocations.contains(location);
                ((TextView) child).setAlpha(selected ? 1f : 0.65f);
            }
        }
    }
    
    private View createLocationTagView(String location) {
        TextView locationView = new TextView(this);
        locationView.setText(location);
        locationView.setSingleLine(true);
        locationView.setTextSize(12);
        int space = Math.round(8 * getResources().getDisplayMetrics().density);
        locationView.setPadding(space + space / 2, space, space + space / 2, space);
        locationView.setBackgroundResource(R.drawable.bg_tag_rounded_transparent);
        locationView.setTextColor(getResources().getColor(R.color.xhs_red));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, space, space / 2);
        locationView.setLayoutParams(params);
        
        locationView.setOnClickListener(v -> {
            viewModel.setLocation(location);
            Toast.makeText(this, "已选择地点: " + location, Toast.LENGTH_SHORT).show();
        });
        
        return locationView;
    }
    
    private void toggleVisibility() {
        Boolean isPublic = viewModel.getIsPublic().getValue();
        viewModel.setPublic(isPublic == null || !isPublic);
    }
    
    private void showLocationOptions() {
        String[] options = new String[LOCATION_OPTIONS.size() + 1];
        options[0] = "不显示地点";
        for (int i = 0; i < LOCATION_OPTIONS.size(); i++) options[i + 1] = LOCATION_OPTIONS.get(i);
        int checked = 0;
        String current = viewModel.getLocation().getValue();
        if (current != null) {
            for (int i = 0; i < LOCATION_OPTIONS.size(); i++) {
                if (current.equals(LOCATION_OPTIONS.get(i))) checked = i + 1;
            }
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.mark_location)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    viewModel.setLocation(which == 0 ? "" : options[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTopicOptions() {
        String[] topics = getTopicOptions().toArray(new String[0]);
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.topic)
                .setItems(topics, (dialog, which) -> viewModel.addTopic(topics[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showFeatureInfo(String title, String message) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm, null)
                .show();
    }

    private void showPreview() {
        com.xiaohongshu.ui.publish.model.NoteModel note = viewModel.getCurrentNote().getValue();
        if (note == null) return;
        String title = note.getTitle() == null ? "" : note.getTitle().trim();
        String content = note.getContent() == null ? "" : note.getContent().trim();
        if (title.isEmpty() && content.isEmpty() && note.getImageUris().isEmpty()) {
            Toast.makeText(this, R.string.empty_title_content, Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder message = new StringBuilder();
        if (!title.isEmpty()) message.append(title).append("\n\n");
        if (!content.isEmpty()) message.append(content);
        if (!note.getTopics().isEmpty()) message.append("\n\n").append(String.join(" ", note.getTopics()));
        if (note.getLocation() != null && !note.getLocation().isEmpty()) message.append("\n\n地点：").append(note.getLocation());
        if (!note.getImageUris().isEmpty()) message.append("\n\n图片：").append(note.getImageUris().size()).append(" 张");
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.preview)
                .setMessage(message.toString())
                .setPositiveButton(R.string.confirm, null)
                .show();
    }
    
    private void saveDraft() {
        syncEditorFields();
        viewModel.saveDraft();
    }
    
    private void publishNote() {
        syncEditorFields();
        viewModel.publishNote();
    }

    private void syncEditorFields() {
        if (titleInput != null) {
            viewModel.setTitle(titleInput.getText() == null ? "" : titleInput.getText().toString());
        }
        if (contentInput != null) {
            viewModel.setContent(contentInput.getText() == null ? "" : contentInput.getText().toString());
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            // 多选：ClipData 里的每个 URI 都必须逐个持久化授权，否则重启后读取失败、图片空白
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri imageUri = data.getClipData().getItemAt(i).getUri();
                    if (imageUri != null) {
                        takePersistableReadPermission(imageUri);
                        viewModel.addImageUri(imageUri.toString());
                    }
                }
            } else if (data.getData() != null) {
                // 单选：data.getData()
                takePersistableReadPermission(data.getData());
                viewModel.addImageUri(data.getData().toString());
            }
        }
    }

    private void takePersistableReadPermission(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // 部分 provider 不支持持久化授权
        }
    }

    @Override protected void onDestroy() {
        if (aiDialog != null) aiDialog.dismiss();
        if (titleInput != null) titleInput.removeCallbacks(updateTitleRunnable);
        if (contentInput != null) contentInput.removeCallbacks(updateContentRunnable);
        super.onDestroy();
    }
}
