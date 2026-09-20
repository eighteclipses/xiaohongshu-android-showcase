package com.xiaohongshu.ui.publish;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;

/** Screenshot-matched entry screen for a text-only thought note. */
public class WriteThoughtsActivity extends BaseActivity {
    private EditText thoughtInput;
    private TextView nextButton;

    public static void start(Context context) {
        context.startActivity(new Intent(context, WriteThoughtsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_thoughts);
        initViews();
    }

    @Override
    protected void initViews() {
        thoughtInput = findViewById(R.id.thoughtInput);
        nextButton = findViewById(R.id.nextButton);
        View closeButton = findViewById(R.id.closeButton);
        if (closeButton != null) closeButton.setOnClickListener(v -> finish());
        if (nextButton != null) nextButton.setOnClickListener(v -> continueToEditor());
        if (thoughtInput != null) {
            thoughtInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateNextState(); }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        View longText = findViewById(R.id.longTextCard);
        if (longText != null) longText.setOnClickListener(v -> {
            if (thoughtInput != null) thoughtInput.setHint("写下你的长文内容...");
            Toast.makeText(this, "已切换长文输入模式", Toast.LENGTH_SHORT).show();
        });
        updateNextState();
    }

    private void updateNextState() {
        boolean hasText = thoughtInput != null && thoughtInput.getText() != null
                && !thoughtInput.getText().toString().trim().isEmpty();
        if (nextButton != null) {
            nextButton.setEnabled(hasText);
            nextButton.setBackgroundResource(hasText ? R.drawable.bg_write_next_enabled : R.drawable.bg_write_next_disabled);
        }
    }

    private void continueToEditor() {
        if (thoughtInput == null || thoughtInput.getText() == null
                || thoughtInput.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "先写点内容吧", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.putExtra(NoteEditActivity.KEY_INITIAL_CONTENT, thoughtInput.getText().toString().trim());
        startActivity(intent);
        finish();
    }
}
