package com.xiaohongshu.ui.guidelines;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;

/**
 * 社区规范Activity
 * 显示社区规范内容
 */
public class CommunityGuidelinesActivity extends BaseActivity {
    private TextView guidelinesText;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, CommunityGuidelinesActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_guidelines);
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        guidelinesText = findViewById(R.id.guidelinesText);
        
        // 返回按钮
        android.view.View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        if (guidelinesText != null) {
            guidelinesText.setText(
                "社区规范\n\n" +
                "1. 尊重他人，文明交流\n" +
                "2. 禁止发布违法违规内容\n" +
                "3. 禁止恶意刷屏、广告\n" +
                "4. 保护个人隐私\n" +
                "5. 共同维护良好的社区环境\n\n" +
                "违反社区规范的用户将受到相应处理。"
            );
        }
    }
}

