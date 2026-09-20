package com.xiaohongshu.ui.publish;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaohongshu.R;
import com.xiaohongshu.ui.login.LoginDataRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Only sends user-confirmed writing material to our backend; no model credentials in the app. */
public final class AiNoteAssistant {
    public interface Listener { void onApply(String title, String content, List<String> topics); }
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(55, TimeUnit.SECONDS)
            .callTimeout(55, TimeUnit.SECONDS).build();

    public static AlertDialog show(Activity activity, String initialIdea, Listener listener) {
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        TextView intro = new TextView(activity);
        intro.setText("写下想法，让 AI 帮你整理成笔记。点击生成后，内容将发送给 DeepSeek；核对后再填入编辑器。");
        intro.setTextSize(12);
        intro.setTextColor(activity.getColor(R.color.polish_muted));
        intro.setLineSpacing(dp(activity, 3), 1);
        intro.setPadding(0, 0, 0, dp(activity, 16));
        body.addView(intro);
        EditText idea = new EditText(activity);
        idea.setHint("例如：整理适合大学生日常使用的学习计划，语气自然，不编造经历");
        idea.setMinLines(3);
        idea.setMaxLines(5);
        idea.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        idea.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        idea.setText(initialIdea);
        idea.setTextSize(14);
        idea.setTextColor(activity.getColor(R.color.polish_ink));
        idea.setHintTextColor(activity.getColor(R.color.polish_muted));
        idea.setBackgroundResource(R.drawable.bg_polish_field);
        idea.setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 14), dp(activity, 14));
        body.addView(idea);
        Spinner style = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
                new String[]{"生活记录", "实用攻略", "体验分享"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        style.setAdapter(adapter);
        LinearLayout.LayoutParams styleLayout = new LinearLayout.LayoutParams(-1, dp(activity, 52));
        styleLayout.topMargin = dp(activity, 12);
        style.setLayoutParams(styleLayout);
        body.addView(style);
        Button generate = new Button(activity);
        generate.setText("生成标题、正文和话题");
        generate.setTextSize(14);
        generate.setTextColor(android.graphics.Color.WHITE);
        generate.setAllCaps(false);
        generate.setBackgroundResource(R.drawable.bg_polish_ai_primary);
        generate.setBackgroundTintList(null);
        generate.setStateListAnimator(null);
        LinearLayout.LayoutParams generateLayout = new LinearLayout.LayoutParams(-1, dp(activity, 48));
        generateLayout.topMargin = dp(activity, 10);
        generate.setLayoutParams(generateLayout);
        body.addView(generate);
        TextView status = new TextView(activity);
        status.setTextSize(12);
        status.setTextColor(activity.getColor(R.color.polish_violet));
        status.setPadding(0, dp(activity, 14), 0, dp(activity, 10));
        body.addView(status);
        TextView preview = new TextView(activity);
        preview.setTextSize(15);
        preview.setTextColor(activity.getColor(R.color.polish_ink));
        preview.setLineSpacing(dp(activity, 5), 1);
        preview.setBackgroundResource(R.drawable.bg_polish_field);
        preview.setVisibility(View.GONE);
        preview.setTextIsSelectable(true);
        preview.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        body.addView(preview);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        heading.setPadding(padding, padding, padding, dp(activity, 14));
        android.widget.ImageView sparkle = new android.widget.ImageView(activity);
        sparkle.setImageResource(R.drawable.ic_polish_sparkle);
        heading.addView(sparkle, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        LinearLayout headingCopy = new LinearLayout(activity);
        headingCopy.setOrientation(LinearLayout.VERTICAL);
        headingCopy.setPadding(dp(activity, 12), 0, 0, 0);
        TextView title = new TextView(activity);
        title.setText("AI 笔记助手"); title.setTextSize(21);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        title.setTextColor(activity.getColor(R.color.polish_ink));
        headingCopy.addView(title);
        TextView subtitle = new TextView(activity);
        subtitle.setText("灵感成稿 · 由 DeepSeek 提供支持"); subtitle.setTextSize(11);
        subtitle.setTextColor(activity.getColor(R.color.polish_muted));
        headingCopy.addView(subtitle);
        heading.addView(headingCopy);
        AlertDialog dialog = new AlertDialog.Builder(activity).setCustomTitle(heading)
                .setView(scroll).setNegativeButton("取消", null).setPositiveButton("填入编辑器", null).create();
        Call[] request = new Call[1];
        JsonObject[] result = new JsonObject[1];
        dialog.setOnDismissListener(ignored -> { if (request[0] != null) request[0].cancel(); });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_polish_surface);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_enabled}, new int[]{}},
                    new int[]{activity.getColor(R.color.polish_violet), activity.getColor(R.color.polish_muted)}));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(activity.getColor(R.color.polish_muted));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (result[0] == null) return;
                JsonObject data = result[0];
                List<String> topics = new ArrayList<>();
                data.getAsJsonArray("topics").forEach(topic -> topics.add(topic.getAsString()));
                listener.onApply(data.get("title").getAsString(), data.get("content").getAsString(), topics);
                dialog.dismiss();
            });
            generate.setOnClickListener(v -> {
                String material = idea.getText().toString().trim();
                if (material.isEmpty()) { idea.setError("先写下你的创作想法"); return; }
                com.xiaohongshu.bean.UserBean user = LoginDataRepository.getInstance(activity).getCurrentUser();
                if (user == null || user.getToken() == null || user.getToken().isEmpty()) {
                    status.setText("请先登录账号，再使用 AI 创作助手"); return;
                }
                JsonObject payload = new JsonObject();
                payload.addProperty("idea", material);
                payload.addProperty("style", new String[]{"daily", "guide", "review"}[style.getSelectedItemPosition()]);
                String base = activity.getString(R.string.backend_base_url).replaceAll("/+$", "");
                Request http = new Request.Builder().url(base + "/ai/note-assistant")
                        .header("Authorization", "Bearer " + user.getToken())
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString())).build();
                generate.setEnabled(false); idea.setEnabled(false); style.setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                status.setText("DeepSeek 正在整理你的灵感，请稍候…");
                preview.setVisibility(View.GONE);
                result[0] = null; preview.setText("");
                request[0] = CLIENT.newCall(http);
                request[0].enqueue(new Callback() {
                    private void complete(JsonObject data, String message) {
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                            generate.setEnabled(true); idea.setEnabled(true); style.setEnabled(true);
                            status.setText(message);
                            result[0] = data;
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(data != null);
                            if (data != null) {
                                preview.setVisibility(View.VISIBLE);
                                StringBuilder text = new StringBuilder(data.get("title").getAsString()).append("\n\n")
                                        .append(data.get("content").getAsString()).append("\n\n");
                                data.getAsJsonArray("topics").forEach(topic -> text.append('#').append(topic.getAsString()).append(' '));
                                preview.setText(text);
                            }
                        });
                    }
                    @Override public void onFailure(Call call, IOException error) {
                        if (!call.isCanceled()) complete(null, "连接失败或生成超时，请检查后台后重试");
                    }
                    @Override public void onResponse(Call call, Response response) {
                        try (Response ignored = response) {
                            JsonObject envelope = JsonParser.parseString(response.body().string()).getAsJsonObject();
                            if (!response.isSuccessful()) {
                                complete(null, envelope.has("message") ? envelope.get("message").getAsString() : "生成失败，请重试"); return;
                            }
                            JsonObject data = envelope.getAsJsonObject("data");
                            data.get("title").getAsString(); data.get("content").getAsString(); data.getAsJsonArray("topics");
                            complete(data, "由 DeepSeek 生成，请核实事实后采用。填入会替换当前标题和正文。");
                        } catch (Exception error) { complete(null, "生成结果无法读取，请重试"); }
                    }
                });
            });
        });
        dialog.show();
        return dialog;
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
    private AiNoteAssistant() { }
}
