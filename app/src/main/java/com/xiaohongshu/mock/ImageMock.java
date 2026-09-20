package com.xiaohongshu.mock;

import com.xiaohongshu.R;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Image mock data generator
 */
public class ImageMock {
    private static final List<Integer> imageList = Arrays.asList(
            R.drawable.image_1, R.drawable.image_2, R.drawable.image_3, R.drawable.image_4,
            R.drawable.image_5, R.drawable.image_6, R.drawable.image_7, R.drawable.image_8,
            R.drawable.image_9, R.drawable.image_10, R.drawable.image_11, R.drawable.image_12,
            R.drawable.image_13, R.drawable.image_14, R.drawable.image_15
    );

    private static final List<Integer> goodsList = Arrays.asList(
            R.drawable.goods_1, R.drawable.goods_2, R.drawable.goods_3, R.drawable.goods_4,
            R.drawable.goods_5, R.drawable.goods_6, R.drawable.goods_7, R.drawable.goods_8,
            R.drawable.goods_9, R.drawable.goods_10, R.drawable.goods_11, R.drawable.goods_12,
            R.drawable.goods_13, R.drawable.goods_14, R.drawable.goods_15, R.drawable.goods_16,
            R.drawable.goods_17, R.drawable.goods_18, R.drawable.goods_19, R.drawable.goods_20
    );

    private static final Random random = new Random();

    public static int getRandomImage() {
        return imageList.get(random.nextInt(imageList.size()));
    }

    public static int getRandomGoods() {
        return goodsList.get(random.nextInt(goodsList.size()));
    }
}

