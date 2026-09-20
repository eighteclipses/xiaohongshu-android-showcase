package com.xiaohongshu.mock;

import com.xiaohongshu.R;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Video mock data generator
 */
public class VideoMock {
    private static final List<Integer> videoList = Arrays.asList(
            R.raw.video_1, R.raw.video_2, R.raw.video_3, R.raw.video_4, R.raw.video_5
    );

    private static final Random random = new Random();

    public static int getRandomVideo() {
        return videoList.get(random.nextInt(videoList.size()));
    }
}

