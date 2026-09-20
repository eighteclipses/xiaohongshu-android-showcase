package com.xiaohongshu.ui.publish;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.text.Layout;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 文字转图片工具类
 * 参照正版小红书文字笔记样式：柔和纯色底 + 大引号装饰 + 大字粗体排版
 * + 荧光笔高亮 + 右下角小红书水印
 */
public class TextToImageConverter {
    private static final int IMAGE_WIDTH = 1080; // 图片宽度
    private static final int IMAGE_HEIGHT = 1440; // 图片高度，适配首页 3:4 卡片
    private static final int CONTENT_PADDING = 120; // 正文左右留白
    private static final int TOP_MARGIN = 330; // 引号下方正文起点
    private static final int BOTTOM_RESERVE = 250; // 底部水印区
    private static final float TEXT_SIZE_LONG = 56f; // 长文字号
    private static final float TEXT_SIZE_MID = 68f; // 中等文字号
    private static final float TEXT_SIZE_SHORT = 84f; // 短文字号（对齐正版观感）
    private static final float LINE_SPACING_MULTIPLIER = 1.45f;
    private static final int TEXT_COLOR = 0xFF3D4144; // 正文深灰
    private static final int HIGHLIGHT_COLOR = 0xCCFF9EC2; // 粉色荧光笔
    private static final int MAX_LINES = 12; // 最大行数

    /** 马卡龙配色：{背景色, 同色系加深装饰色} */
    private static final int[][] PALETTES = {
        {0xFFCBEDE2, 0xFF9AD4BE}, // 浅青（对齐正版截图）
        {0xFFFFE1EB, 0xFFF6AFC8}, // 浅粉
        {0xFFFFF1C7, 0xFFF3D488}, // 浅黄
        {0xFFE7E1F8, 0xFFC6B8EA}, // 浅紫
        {0xFFD9EAFA, 0xFFA9CCEE}, // 浅蓝
        {0xFFE2F3D6, 0xFFB5DA9C}, // 浅绿
    };

    /**
     * 将文字转换为图片
     * @param context 上下文
     * @param text 要转换的文字
     * @return 生成的图片URI，如果失败返回null
     */
    public static Uri convertTextToImage(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        try {
            String body = text.trim();
            Bitmap bitmap = renderPoster(context, body);
            Uri imageUri = saveBitmapToFile(context, bitmap);
            bitmap.recycle();
            return imageUri;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 为无图笔记生成/复用仿真文字海报（按笔记 id 落盘，已存在直接复用）。
     * 供远程纯文字笔记在首页/详情页展示时调用，观感与本地文字笔记一致。
     */
    public static Uri createPosterForNote(Context context, String noteId, String title, String content) {
        if (noteId == null || noteId.trim().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) text.append(title.trim());
        if (content != null && !content.trim().isEmpty()) {
            if (text.length() > 0) text.append("\n\n");
            text.append(content.trim());
        }
        if (text.length() == 0) {
            return null;
        }
        try {
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "text_images");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String safeId = noteId.replaceAll("[^a-zA-Z0-9_-]", "_");
            File poster = new File(dir, "poster_" + safeId + ".png");
            if (poster.exists() && poster.length() > 0) {
                return Uri.fromFile(poster);
            }
            Bitmap bitmap = renderPoster(context, text.toString());
            FileOutputStream fos = new FileOutputStream(poster);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();
            return Uri.fromFile(poster);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 绘制仿真实小红书样式的文字海报位图（柔和底色 + 大引号 + 荧光笔高亮 + 水印）。 */
    private static Bitmap renderPoster(Context context, String body) {
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int seed = body.hashCode();
        int[] palette = PALETTES[Math.floorMod(seed, PALETTES.length)];

        // 1. 柔和纯色背景
        canvas.drawColor(palette[0]);

        // 2. 左上角大引号装饰（背景加深色）
        Paint quotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        quotePaint.setColor(palette[1]);
        quotePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        quotePaint.setTextSize(300f);
        canvas.drawText("\u201C", 96f, 330f, quotePaint);

        // 3. 正文：大字粗体 + 荧光笔高亮
        SpannableString spannable = new SpannableString(body);
        applyHighlight(spannable, seed);

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(TEXT_COLOR);
        textPaint.setTextSize(pickTextSize(body));
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        int availableWidth = IMAGE_WIDTH - CONTENT_PADDING * 2;
        StaticLayout layout = buildLayout(spannable, textPaint, availableWidth);

        // 超高时按行截断并加省略号
        int maxHeight = IMAGE_HEIGHT - TOP_MARGIN - BOTTOM_RESERVE;
        if (layout.getHeight() > maxHeight) {
            int maxLineCount = Math.min(layout.getLineCount(), MAX_LINES);
            int end = layout.getLineEnd(maxLineCount - 1);
            String truncated = body.substring(0, Math.min(end, body.length())) + "…";
            SpannableString truncatedSpannable = new SpannableString(truncated);
            applyHighlight(truncatedSpannable, seed);
            layout = buildLayout(truncatedSpannable, textPaint, availableWidth);
        }

        // 短文字在引号下方区域垂直居中，长文字从固定起点向下排
        int startY = TOP_MARGIN + Math.max(0, (maxHeight - layout.getHeight()) / 2);
        canvas.save();
        canvas.translate(CONTENT_PADDING, startY);
        layout.draw(canvas);
        canvas.restore();

        // 4. 右下角小色块装饰（背景加深色）
        Paint decoPaint = new Paint();
        decoPaint.setColor(palette[1]);
        canvas.drawRect(IMAGE_WIDTH - 236f, IMAGE_HEIGHT - 158f, IMAGE_WIDTH - 148f, IMAGE_HEIGHT - 136f, decoPaint);

        // 5. 右下角小红书水印
        drawWatermark(canvas, context);
        return bitmap;
    }

    private static StaticLayout buildLayout(CharSequence text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE_SPACING_MULTIPLIER)
                .setIncludePad(false)
                .build();
    }

    /** 根据文字长度选择字号，避免长文被过度截断 */
    private static float pickTextSize(String body) {
        if (body.length() <= 60) return TEXT_SIZE_SHORT;
        if (body.length() <= 160) return TEXT_SIZE_MID;
        return TEXT_SIZE_LONG;
    }

    /**
     * 荧光笔高亮：确定性随机选 1~3 个词，垫粉色圆角矩形。
     * 使用正文 hash 作随机种子，同一笔记重新生成结果一致。
     * 按下标递增采样，循环次数有上界；token 由 splitTokens 保证互不重叠。
     */
    private static void applyHighlight(SpannableString spannable, int seed) {
        List<int[]> tokens = splitTokens(spannable.toString());
        if (tokens.isEmpty()) return;
        Random random = new Random(seed);
        int count = Math.min(tokens.size(), 1 + random.nextInt(3));
        int gap = Math.max(1, tokens.size() / (count + 1));
        int applied = 0;
        int index = random.nextInt(Math.min(tokens.size(), gap));
        while (index < tokens.size() && applied < count) {
            int[] token = tokens.get(index);
            spannable.setSpan(new HighlightSpan(HIGHLIGHT_COLOR), token[0], token[1],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            applied++;
            index += gap;
        }
    }

    /** 切词：连续文字按 2~6 字切窗，中英混合时按空白/标点切 */
    private static List<int[]> splitTokens(String text) {
        List<int[]> tokens = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean wordChar = !Character.isWhitespace(c) && !isPunctuation(c);
            if (wordChar && start < 0) {
                start = i;
            } else if (!wordChar && start >= 0) {
                addSplit(tokens, text, start, i);
                start = -1;
            }
        }
        if (start >= 0) addSplit(tokens, text, start, text.length());
        return tokens;
    }

    private static void addSplit(List<int[]> tokens, String text, int start, int end) {
        // 每段再按最多 4 字细分，保证高亮是局部的
        int piece = 4;
        for (int i = start; i < end; i += piece) {
            int stop = Math.min(i + piece, end);
            if (stop - i >= 2 || stop == end) {
                tokens.add(new int[]{i, stop});
            }
        }
    }

    private static boolean isPunctuation(char c) {
        return !Character.isLetterOrDigit(c) && !isCJK(c);
    }

    private static boolean isCJK(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /** 荧光笔高亮 Span：垫粉色圆角矩形后按原样式绘制文字 */
    private static class HighlightSpan extends ReplacementSpan {
        private final Paint backgroundPaint;

        HighlightSpan(int color) {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(color);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) paint.measureText(text, start, end);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            RectF rect = new RectF(x - 8f, top + bottom * 0.08f,
                    x + paint.measureText(text, start, end) + 8f, bottom - bottom * 0.06f);
            canvas.drawRoundRect(rect, 18f, 18f, backgroundPaint);
            canvas.drawText(text, start, end, x, y, paint);
        }
    }

    /** 右下角水印：白色胶囊红字 + 下方半透明小红书号 */
    private static void drawWatermark(Canvas canvas, Context context) {
        String username = "";
        try {
            com.xiaohongshu.bean.UserBean user =
                    LoginDataRepository.getInstance(context).getCurrentUser();
            if (user != null && user.getUsername() != null) username = user.getUsername();
        } catch (Exception ignored) {
        }

        Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillPaint.setColor(Color.WHITE);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xFFFF2442);
        labelPaint.setTextSize(34f);
        labelPaint.setFakeBoldText(true);
        String label = "小红书";
        float labelWidth = labelPaint.measureText(label);
        float pillWidth = labelWidth + 52f;
        float pillHeight = 66f;
        float pillRight = IMAGE_WIDTH - 76f;
        float pillTop = IMAGE_HEIGHT - BOTTOM_RESERVE + 46f;
        canvas.drawRoundRect(pillRight - pillWidth, pillTop, pillRight, pillTop + pillHeight,
                pillHeight / 2f, pillHeight / 2f, pillPaint);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        float labelBaseline = pillTop + pillHeight / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(label, pillRight - pillWidth + 26f, labelBaseline, labelPaint);

        Paint idPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        idPaint.setColor(0xB3FFFFFF);
        idPaint.setTextSize(30f);
        String accountId = "小红书号 " + Math.abs(username.isEmpty() ? 5541459619L : username.hashCode());
        canvas.drawText(accountId, pillRight - idPaint.measureText(accountId), pillTop + pillHeight + 46f, idPaint);
    }

    public static boolean isGeneratedTextImage(String uri) {
        return uri != null && (uri.contains("text_image_") || uri.contains("poster_"));
    }

    public static Uri createIfNeeded(Context context, String title, String content, List<String> currentImages) {
        if (currentImages != null && !currentImages.isEmpty()) {
            if (currentImages.size() != 1 || !isGeneratedTextImage(currentImages.get(0))) return null;
        }
        StringBuilder text = new StringBuilder();
        if (title != null && !title.trim().isEmpty()) text.append(title.trim());
        if (content != null && !content.trim().isEmpty()) {
            if (text.length() > 0) text.append("\n\n");
            text.append(content.trim());
        }
        return text.length() == 0 ? null : convertTextToImage(context, text.toString());
    }

    /**
     * 将Bitmap保存到文件
     * @param context 上下文
     * @param bitmap 要保存的Bitmap
     * @return 保存图片的URI
     */
    private static Uri saveBitmapToFile(Context context, Bitmap bitmap) throws IOException {
        // 创建文件目录
        File imagesDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "text_images");
        if (!imagesDir.exists()) {
            imagesDir.mkdirs();
        }

        // 文件名带毫秒 + 随机后缀：同一秒生成多张海报时避免文件名冲突互相覆盖
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date())
                + "_" + (int) (Math.random() * 9000 + 1000);
        String fileName = "text_image_" + timeStamp + ".png";
        File imageFile = new File(imagesDir, fileName);

        // 保存图片
        FileOutputStream fos = new FileOutputStream(imageFile);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();

        // 返回URI
        return Uri.fromFile(imageFile);
    }
}
