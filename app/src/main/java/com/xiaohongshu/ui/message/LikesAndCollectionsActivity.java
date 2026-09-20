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
 * 点赞和收藏消息Activity
 * 显示点赞和收藏相关的消息
 */
public class LikesAndCollectionsActivity extends BaseActivity {
    private MessageViewModel viewModel;
    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, LikesAndCollectionsActivity.class);
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
            if (message != null && message.getId() != null) {
                // 从消息中提取笔记ID（这里需要根据实际消息结构调整）
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
            titleText.setText("点赞");
        }
    }
    
    @Override
    protected void initData() {
        // 加载点赞（类型0）和收藏（类型1）消息
        viewModel.loadMessagesByTypes(java.util.Arrays.asList(0, 1));
        
        viewModel.getMessageList().observe(this, messages -> {
            if (messages != null) {
                adapter.updateData(messages);
            }
        });
    }
}

