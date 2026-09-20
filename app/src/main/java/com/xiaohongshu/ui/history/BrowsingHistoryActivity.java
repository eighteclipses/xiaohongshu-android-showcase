package com.xiaohongshu.ui.history;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaohongshu.R;
import com.xiaohongshu.activity.graphic.GraphicActivity;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.history.viewmodel.BrowsingHistoryViewModel;
import com.xiaohongshu.ui.home.bean.GraphicCardBean;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 浏览历史Activity
 * 显示用户浏览过的笔记
 */
public class BrowsingHistoryActivity extends BaseActivity {
    private BrowsingHistoryViewModel viewModel;
    private RecyclerView recyclerView;
    private com.xiaohongshu.ui.home.discovery.DiscoveryAdapter adapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, BrowsingHistoryActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browsing_history);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(BrowsingHistoryViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        android.widget.TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("浏览记录");
        }
        
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        
        adapter = new com.xiaohongshu.ui.home.discovery.DiscoveryAdapter(card -> {
            // 点击笔记跳转到详情
            if (card != null && card.getId() != null) {
                GraphicActivity.newInstance(this, card.getId());
            }
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
        viewModel.getHistoryNotes().observe(this, notes -> {
            if (notes != null) {
                adapter.updateData(notes);
            }
        });
        
        viewModel.load();
    }
}

