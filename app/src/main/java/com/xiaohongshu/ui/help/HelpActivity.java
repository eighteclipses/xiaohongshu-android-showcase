package com.xiaohongshu.ui.help;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;

/**
 * 帮助与反馈Activity
 * 显示帮助内容
 */
public class HelpActivity extends BaseActivity {
    private TextView helpText;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, HelpActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        helpText = findViewById(R.id.helpText);
        
        // 返回按钮
        android.view.View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    @Override
    protected void initData() {
        if (helpText != null) {
            helpText.setText(
                "帮助与反馈\n\n" +
                "常见问题：\n" +
                "1. 如何发布笔记？\n" +
                "   点击底部中间的+号，选择发布方式即可。\n\n" +
                "2. 如何修改个人资料？\n" +
                "   在我的页面点击编辑资料即可。\n\n" +
                "3. 如何联系客服？\n" +
                "   在消息页面可以联系客服。\n\n" +
                "如有其他问题，请通过反馈功能联系我们。"
            );
        }
    }
}

