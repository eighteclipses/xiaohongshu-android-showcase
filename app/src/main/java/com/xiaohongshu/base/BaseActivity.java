package com.xiaohongshu.base;

import android.os.Bundle;
import androidx.core.view.WindowCompat;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    protected void initViews() {
    }

    protected void initData() {
    }

    /**
     * 演示功能页面：标题追加统一标识，避免用户误以为已接入真实业务。
     */
    protected void markAsDemoFeature() {
        CharSequence title = getTitle();
        setTitle((title != null ? title.toString() : "") + " · 演示");
    }
}

