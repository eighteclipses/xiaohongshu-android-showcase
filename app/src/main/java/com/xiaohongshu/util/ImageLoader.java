package com.xiaohongshu.util;

import android.content.Context;
import android.widget.ImageView;

import coil.request.ImageRequest;

/**
 * Image loader facade for server-hosted note images.
 * Internally backed by Coil (memory + disk cache, sampled decoding); public API unchanged.
 */
public final class ImageLoader {
    private static volatile coil.ImageLoader loader;

    private ImageLoader() { }

    public static void load(ImageView target, String url, int placeholder) {
        load(target, url, placeholder, null);
    }

    /** 带图片尺寸回调的加载：成功后回调 图片宽,图片高（源图固有尺寸）。 */
    public static void load(ImageView target, String url, int placeholder, SizeListener sizeListener) {
        if (target == null || url == null || url.trim().isEmpty()) {
            if (target != null && placeholder != 0) target.setImageResource(placeholder);
            if (sizeListener != null) sizeListener.onSize(0, 0);
            return;
        }
        String key = url.trim();
        // 相对路径（如服务器脏数据 /uploads/...）补全为后台完整地址
        if (key.startsWith("/") && !key.startsWith("//")) {
            try {
                String base = target.getContext().getString(com.xiaohongshu.R.string.backend_base_url);
                String host = base.endsWith("/api") ? base.substring(0, base.length() - "/api".length()) : base;
                key = host + key;
            } catch (Exception ignored) {
            }
        }
        Context context = target.getContext();
        ImageRequest.Builder builder = new ImageRequest.Builder(context)
                .data(key)
                .crossfade(true)
                .listener(request -> kotlin.Unit.INSTANCE,
                        request -> kotlin.Unit.INSTANCE,
                        (request, result) -> { if (sizeListener != null) sizeListener.onSize(0, 0); return kotlin.Unit.INSTANCE; },
                        (request, result) -> {
                            if (sizeListener != null && result.getDrawable() != null) {
                                sizeListener.onSize(result.getDrawable().getIntrinsicWidth(),
                                        result.getDrawable().getIntrinsicHeight());
                            }
                            return kotlin.Unit.INSTANCE;
                        });
        if (placeholder != 0) {
            builder.placeholder(placeholder).error(placeholder);
        }
        builder.target(target);
        loader(context).enqueue(builder.build());
    }

    /** 图片加载尺寸回调 */
    public interface SizeListener {
        void onSize(int width, int height);
    }

    private static coil.ImageLoader loader(Context context) {
        coil.ImageLoader local = loader;
        if (local == null) {
            synchronized (ImageLoader.class) {
                if (loader == null) {
                    loader = new coil.ImageLoader.Builder(context.getApplicationContext())
                            .crossfade(true)
                            .build();
                }
                local = loader;
            }
        }
        return local;
    }
}
