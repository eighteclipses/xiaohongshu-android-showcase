package com.xiaohongshu.ui.comments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.comments.viewmodel.MyCommentsViewModel;

/**
 * 我的评论Activity
 * 显示用户的所有评论
 */
public class MyCommentsActivity extends BaseActivity {
    private MyCommentsViewModel viewModel;
    private RecyclerView recyclerView;
    private MyCommentsAdapter adapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, MyCommentsActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_comments);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(MyCommentsViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new MyCommentsAdapter(comment -> {
            // 点击评论跳转到笔记详情
            if (comment != null && comment.noteId != null) {
                GraphicActivity.newInstance(this, comment.noteId);
            }
        });
        
        // 设置删除监听器
        adapter.setOnDeleteClickListener(comment -> {
            showDeleteCommentDialog(comment);
        });
        
        recyclerView.setAdapter(adapter);
        
        // 返回按钮
        android.view.View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getComments().observe(this, comments -> {
            if (comments != null) {
                adapter.updateData(comments);
                
                // 显示空状态
                View emptyState = findViewById(R.id.emptyState);
                if (emptyState != null) {
                    emptyState.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (recyclerView != null) {
                    recyclerView.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });
        
        viewModel.load();
    }
    
    /**
     * 显示删除评论确认对话框
     */
    private void showDeleteCommentDialog(com.xiaohongshu.database.entity.CommentEntity comment) {
        new android.app.AlertDialog.Builder(this)
            .setTitle("删除评论")
            .setMessage("确定要删除这条评论吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                deleteComment(comment);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 删除评论
     */
    private void deleteComment(com.xiaohongshu.database.entity.CommentEntity comment) {
        if (comment == null || comment.id == null) {
            return;
        }
        
        // 在后台线程删除
        new Thread(() -> {
            com.xiaohongshu.app.AppApplication.getDatabase().commentDao().delete(comment);
            
            // 在主线程刷新列表
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, "评论已删除", android.widget.Toast.LENGTH_SHORT).show();
                viewModel.load(); // 重新加载评论列表
            });
        }).start();
    }
}

