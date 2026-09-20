package com.xiaohongshu.ui.message;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.message.viewmodel.MessageViewModel;

/**
 * 新关注消息Activity
 * 显示新关注用户的消息
 */
public class NewFollowsActivity extends BaseActivity {
    private MessageViewModel viewModel;
    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, NewFollowsActivity.class);
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
            if (message != null && message.getUser() != null && message.getUser().getId() != null
                    && !message.getUser().getId().isEmpty()) {
                com.xiaohongshu.ui.profile.UserProfileActivity.start(this, message.getUser().getId());
            } else {
                android.widget.Toast.makeText(this, "该用户资料暂不可用", android.widget.Toast.LENGTH_SHORT).show();
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
            titleText.setText("关注");
        }
    }
    
    @Override
    protected void initData() {
        // 加载关注消息（类型3）
        viewModel.loadMessagesByType(3);
        
        viewModel.getMessageList().observe(this, messages -> {
            if (messages != null) {
                adapter.updateData(messages);
            }
        });
    }
}
