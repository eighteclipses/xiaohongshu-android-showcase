package com.xiaohongshu.ui.message;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.message.viewmodel.MessageViewModel;

/**
 * 评论和@我消息Activity
 * 显示评论和@我的消息
 */
public class CommentsAndMentionsActivity extends BaseActivity {
    private MessageViewModel viewModel;
    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, CommentsAndMentionsActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_list);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(MessageViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new MessageAdapter(message -> {
            // 点击消息跳转到笔记详情
            if (message != null && message.getId() != null && !message.getId().isEmpty()) {
                // 使用消息ID作为笔记ID（实际应该从relatedId获取，但MessageBean中id存储的是relatedId）
                GraphicActivity.newInstance(this, message.getId());
            }
        });
        recyclerView.setAdapter(adapter);
        
        // 返回按钮
        android.view.View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 设置标题
        android.widget.TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("评论");
        }
    }
    
    @Override
    protected void initData() {
        // 加载评论（类型2）和@我（类型4）消息
        viewModel.loadMessagesByTypes(java.util.Arrays.asList(2, 4));
        
        viewModel.getMessageList().observe(this, messages -> {
            if (messages != null) {
                adapter.updateData(messages);
            }
        });
    }
}

