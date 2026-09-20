package com.xiaohongshu.mock;

import com.xiaohongshu.R;
import com.xiaohongshu.ui.home.bean.UserInfoBean;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * User mock data generator
 */
public class UserMock {
    private static final List<String> nameList = Arrays.asList(
            "Alice", "李华", "Bob", "王明", "Charlie", "张伟",
            "David", "赵丽", "Emma", "刘洋", "Frank", "陈静",
            "Grace", "李娜", "Henry", "王刚", "Isabella", "杨柳",
            "Jack", "周杰", "Karen", "吴敏", "Leo", "郑涛",
            "Megan", "陈强", "Nicholas", "林琳", "Olivia", "黄勇",
            "Peter", "孙莉", "Quinn", "朱军", "Rachel", "李梅",
            "Samuel", "王红", "Tiffany", "周丽", "Uma", "赵刚",
            "Victor", "陈丽", "Wendy", "李杰", "Xavier", "王芳",
            "Yara", "张强", "Zachary", "刘梅", "Amelia", "陈涛",
            "Benjamin", "李芳", "Chloe", "王杰", "Daniel", "赵敏",
            "Evelyn", "刘刚", "Finn", "陈丽", "Georgia", "李勇",
            "Harrison", "王梅", "Ivy", "张丽", "Jacob", "刘涛"
    );

    private static final List<Integer> imageList = Arrays.asList(
            R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
            R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
            R.drawable.p9, R.drawable.p10, R.drawable.p11,
            R.drawable.p12, R.drawable.p13, R.drawable.p14,
            R.drawable.p15, R.drawable.p16
    );

    private static final List<UserInfoBean> userInfoList = Arrays.asList(
            new UserInfoBean(18, 1, "北京市天安门"),
            new UserInfoBean(22, 1, "上海市陆家嘴"),
            new UserInfoBean(23, 0, "杭州市"),
            new UserInfoBean(20, 1, "宝盖山体育馆"),
            new UserInfoBean(24, 0, "泉州市石狮黄金海岸"),
            new UserInfoBean(19, 1, "石狮市民宿")
    );

    private static final Random random = new Random();

    public static String getRandomName() {
        return nameList.get(random.nextInt(nameList.size()));
    }

    public static int getRandomImage() {
        return imageList.get(random.nextInt(imageList.size()));
    }

    public static UserInfoBean getRandomUserInfo() {
        return userInfoList.get(random.nextInt(userInfoList.size()));
    }
}

