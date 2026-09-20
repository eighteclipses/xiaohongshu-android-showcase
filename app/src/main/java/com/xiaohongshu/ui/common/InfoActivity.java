package com.xiaohongshu.ui.common;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;

/** Shared destination for small feature pages that do not need their own data model yet. */
public class InfoActivity extends BaseActivity {
    private static final String KEY_TITLE = "title";
    private static final String KEY_MESSAGE = "message";

    public static void start(Context context, String title, String message) {
        if (context == null) return;
        Intent intent = new Intent(context, InfoActivity.class)
                .putExtra(KEY_TITLE, title)
                .putExtra(KEY_MESSAGE, message);
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        TextView title = findViewById(R.id.titleText);
        TextView message = findViewById(R.id.messageText);
        View back = findViewById(R.id.backButton);
        if (title != null) title.setText(getIntent().getStringExtra(KEY_TITLE));
        if (message != null) message.setText(getIntent().getStringExtra(KEY_MESSAGE));
        if (back != null) back.setOnClickListener(v -> finish());
    }
}
