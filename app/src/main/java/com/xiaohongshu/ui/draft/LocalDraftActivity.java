package com.xiaohongshu.ui.draft;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.draft.viewmodel.LocalDraftViewModel;
import com.xiaohongshu.ui.publish.NoteEditActivity;
import com.xiaohongshu.ui.publish.model.NoteModel;

/**
 * 本地草稿Activity
 * 显示草稿列表，支持编辑和删除
 */
public class LocalDraftActivity extends BaseActivity {
    private LocalDraftViewModel viewModel;
    private RecyclerView draftRecyclerView;
    private DraftAdapter draftAdapter;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, LocalDraftActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_draft);
        
        viewModel = new ViewModelProvider(this, 
            new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(LocalDraftViewModel.class);
        viewModel.init(getApplicationContext());
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("本地草稿");
        }
        
        draftRecyclerView = findViewById(R.id.draftRecyclerView);
        draftRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        
        draftAdapter = new DraftAdapter(
            draft -> {
                // 点击草稿，跳转到编辑页面
                if (draft != null && draft.getId() != null) {
                    Intent intent = new Intent(this, NoteEditActivity.class);
                    intent.putExtra(NoteEditActivity.KEY_NOTE_ID, draft.getId());
                    startActivity(intent);
                }
            },
            draftId -> {
                // 删除草稿
                if (draftId != null) {
                    viewModel.deleteDraft(draftId);
                }
            }
        );
        draftRecyclerView.setAdapter(draftAdapter);
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        viewModel.getOperationResult().observe(this,result->{if(result!=null)android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show();});
        viewModel.getDrafts().observe(this, drafts -> {
            if (drafts != null) {
                draftAdapter.updateData(drafts);
            }
        });
        
        viewModel.loadDrafts();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页面返回时，重新加载草稿列表
        viewModel.loadDrafts();
    }
}
