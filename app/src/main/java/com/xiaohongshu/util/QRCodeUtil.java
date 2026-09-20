package com.xiaohongshu.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具（基于 zxing，配合扫一扫实现加好友闭环）
 */
public final class QRCodeUtil {
    /** 用户主页二维码协议前缀，与 ScanActivity 的路由规则对应 */
    public static final String USER_SCHEME_PREFIX = "xhs://user/";

    private QRCodeUtil() { }

    /**
     * 生成二维码位图，白色背景带静区
     *
     * @param content 编码内容
     * @param size    边长（像素）
     * @return 生成失败时返回 null
     */
    public static Bitmap create(String content, int size) {
        if (content == null || content.trim().isEmpty() || size <= 0) return null;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException error) {
            return null;
        }
    }
}
