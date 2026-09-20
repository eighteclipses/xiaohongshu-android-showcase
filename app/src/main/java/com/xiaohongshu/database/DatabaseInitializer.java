package com.xiaohongshu.database;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaohongshu.R;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.database.entity.*;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.publish.model.NoteModel;
import com.xiaohongshu.ui.publish.TextToImageConverter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 数据库初始化工具
 * 负责数据迁移和初始化基础数据
 */
public class DatabaseInitializer {
    private static final String PREF_MIGRATION_FLAG = "database_migrated";
    private static final String PREF_NAME_USER = "user_prefs";
    private static final String PREF_NAME_NOTE = "note_prefs";
    private static final String KEY_REGISTERED_USERS = "registered_users";
    private static final String KEY_DRAFTS = "note_drafts";
    private static final String KEY_PUBLISHED_NOTES = "note_published";

    /** 初始化完成信号：详情页/首页读取 Room 前必须等待，避免读到空的示例数据 */
    private static final CountDownLatch READY_LATCH = new CountDownLatch(1);

    private final Context context;
    private final AppDatabase database;
    private final ExecutorService executorService;
    private final Gson gson;

    public DatabaseInitializer(Context context) {
        this.context = context.getApplicationContext();
        this.database = AppDatabase.getInstance(context);
        this.executorService = Executors.newSingleThreadExecutor();
        this.gson = new Gson();
    }

    /**
     * 等待数据库初始化完成。在后台线程调用；超时或中断时返回 false，调用方按本地数据兜底。
     */
    public static boolean awaitReady(long timeoutMs) {
        try {
            return READY_LATCH.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 初始化数据库（迁移数据并初始化基础数据）
     */
    public void initialize() {
        executorService.execute(() -> {
            try {
                SharedPreferences sp = context.getSharedPreferences(PREF_MIGRATION_FLAG, Context.MODE_PRIVATE);
                boolean migrated = sp.getBoolean(PREF_MIGRATION_FLAG, false);

                if (!migrated) {
                    // 迁移用户数据
                    migrateUsers();

                    // 迁移笔记数据
                    migrateNotes();

                    // 标记为已迁移
                    sp.edit().putBoolean(PREF_MIGRATION_FLAG, true).apply();
                }

                // 初始化或更新商品数据（每次都执行，确保数据最新）
                initializeGoods();

                // 初始化示例笔记（每次启动都检查，确保有初始数据）
                initializeSampleNotes();

                // 为历史纯文字笔记补齐文字海报，避免首页使用 Logo 占位图。
                ensureTextNoteImages();

                // 初始化消息数据（每次启动都检查，确保有初始数据）
                initializeMessages();
            } finally {
                READY_LATCH.countDown();
            }
        });
    }
    
    /**
     * 迁移用户数据
     */
    private void migrateUsers() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME_USER, Context.MODE_PRIVATE);
        String usersJson = sp.getString(KEY_REGISTERED_USERS, "[]");
        
        try {
            Type listType = new TypeToken<List<UserBean>>(){}.getType();
            List<UserBean> users = gson.fromJson(usersJson, listType);
            
            if (users != null && !users.isEmpty()) {
                List<UserEntity> entities = new ArrayList<>();
                for (UserBean user : users) {
                    // 检查用户是否已存在
                    UserEntity existing = database.userDao().getUserByUsername(user.getUsername());
                    if (existing == null) {
                        UserEntity entity = new UserEntity();
                        entity.id = String.valueOf(System.currentTimeMillis() + entities.size());
                        entity.username = user.getUsername();
                        // 迁移历史 SP 数据时不再写入明文密码
                        // 使用真实头像 p1.jpeg 到 p11.jpeg
                        int avatarIndex = entities.size() % 16 + 1;
                        entity.avatar = getAvatarResource(avatarIndex);
                        entity.nickname = user.getUsername();
                        entity.bio = "";
                        entity.createTime = System.currentTimeMillis();
                        entities.add(entity);
                    }
                }
                
                if (!entities.isEmpty()) {
                    database.userDao().insertAll(entities);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 迁移笔记数据
     */
    private void migrateNotes() {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME_NOTE, Context.MODE_PRIVATE);
        
        // 迁移草稿
        String draftsJson = sp.getString(KEY_DRAFTS, "[]");
        migrateNotesFromJson(draftsJson, true);
        
        // 迁移已发布笔记
        String publishedJson = sp.getString(KEY_PUBLISHED_NOTES, "[]");
        migrateNotesFromJson(publishedJson, false);
    }
    
    /**
     * 从JSON迁移笔记
     */
    private void migrateNotesFromJson(String json, boolean isDraft) {
        try {
            Type listType = new TypeToken<List<NoteModel>>(){}.getType();
            List<NoteModel> notes = gson.fromJson(json, listType);
            
            if (notes != null && !notes.isEmpty()) {
                // 获取当前登录用户ID
                String userId = getCurrentUserId();
                if (userId == null || userId.isEmpty()) {
                    return;
                }
                
                List<NoteEntity> entities = new ArrayList<>();
                for (NoteModel note : notes) {
                    NoteEntity entity = new NoteEntity();
                    entity.id = note.getId();
                    entity.title = note.getTitle();
                    entity.content = note.getContent();
                    entity.imageUris = note.getImageUris() != null ? note.getImageUris() : new ArrayList<>();
                    entity.topics = note.getTopics() != null ? note.getTopics() : new ArrayList<>();
                    entity.location = note.getLocation();
                    entity.isPublic = note.isPublic();
                    entity.isDraft = isDraft;
                    entity.userId = userId;
                    entity.createTime = note.getCreateTime() > 0 ? note.getCreateTime() : System.currentTimeMillis();
                    entity.updateTime = note.getUpdateTime() > 0 ? note.getUpdateTime() : System.currentTimeMillis();
                    entities.add(entity);
                }
                
                if (!entities.isEmpty()) {
                    database.noteDao().insertAll(entities);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        UserBean currentUser = LoginDataRepository.getInstance(context).getCurrentUser();
        if (currentUser != null) {
            UserEntity userEntity = database.userDao().getUserByUsername(currentUser.getUsername());
            if (userEntity != null) {
                return userEntity.id;
            }
        }
        return null;
    }
    
    /**
     * 初始化或更新商品数据
     */
    private void initializeGoods() {
        // 初始化商品数据（使用真实的商品图片 goods_1.jpg 到 goods_20.jpg）
        List<GoodsEntity> goodsList = new ArrayList<>();
        
        android.util.Log.d("DatabaseInitializer", "Initializing or updating goods");
        
        String[] goodsTitles = {
            "RED电子雾化器", "智能手机", "真无线蓝牙耳机", "防水电动剃须刀", "头戴式无线耳机",
            "GUVEE音频一分二线", "TYPE-C接口设备", "iPhone X手机", "漫步者B&O音响系统", "电动剃须刀",
            "华硕笔记本电脑", "办公笔记本", "ps5游戏手柄", "华为手机", "机械革命笔记本",
            "数码相机", "机械键盘", "大众汽车", "智能手环", "智能摄像头"
        };
        
        double[] goodsPrices = {
            89.0, 4999.0, 399.0, 299.0, 899.0,
            59.9, 129.0, 5999.0, 2999.0, 399.0,
            5999.0, 3999.0, 499.0, 3999.0, 4999.0,
            5999.0, 299.0, 199999.0, 199.0, 299.0
        };
        
        double[] goodsDiscounts = {
            0.85, 0.9, 0.8, 0.95, 0.75,
            0.9, 0.85, 0.95, 0.75, 0.8,
            0.85, 0.9, 0.8, 0.85, 0.9,
            0.8, 0.85, 0.9, 0.85, 0.8
        };
        
        String[] goodsDescriptions = {
            "RED电子雾化器，高品质雾化体验", "智能手机，高性能处理器，流畅体验", "真无线蓝牙耳机，舒适佩戴，高清音质", "防水电动剃须刀，持久续航，锋利剃须", "头戴式无线耳机，降噪效果出色，舒适佩戴",
            "GUVEE音频一分二线，高品质音效传输，双设备共享", "TYPE-C接口设备，便捷充电，高效传输", "iPhone X手机，全面屏设计，强劲性能", "漫步者B&O音响系统，极致音效，完美享受", "电动剃须刀，高效剃须，持久续航",
            "华硕笔记本电脑，高性能配置，轻薄便携", "办公笔记本，商务办公首选", "PS5游戏手柄，精准操控，沉浸式体验", "华为手机，拍照旗舰，性能强劲", "机械革命笔记本，游戏性能出色",
            "数码相机，高清拍照，专业性能", "机械键盘，舒适手感，炫酷灯光", "大众汽车，品质可靠，性能稳定", "智能手环，健康监测，多功能设计", "智能摄像头，高清监控，远程查看"
        };
        
        int[] goodsStock = {
            100, 50, 80, 30, 60,
            40, 70, 90, 50, 60,
            80, 45, 35, 55, 25,
            40, 70, 50, 85, 20
        };
        
        // 创建或更新20个商品，使用 goods_1 到 goods_20 的真实图片
        for (int i = 0; i < 20; i++) {
            String goodsId = "goods_" + (i + 1);
            
            // 检查商品是否已存在
            GoodsEntity existing = database.goodsDao().getGoodsById(goodsId);
            
            // 使用对应的图片资源 (1-20)
            int imageRes = getGoodsImageResource(i + 1);
            
            if (existing != null) {
                // 商品已存在，更新所有信息
                existing.title = goodsTitles[i];
                existing.image = imageRes;
                existing.price = goodsPrices[i];
                existing.discount = goodsDiscounts[i];
                existing.description = goodsDescriptions[i];
                existing.stock = goodsStock[i];
                database.goodsDao().update(existing);
                android.util.Log.d("DatabaseInitializer", "Updated goods: " + goodsId);
            } else {
                // 商品不存在，添加新商品
                goodsList.add(new GoodsEntity(
                    goodsId,
                    goodsTitles[i],
                    imageRes,
                    goodsPrices[i],
                    goodsDiscounts[i],
                    goodsDescriptions[i],
                    goodsStock[i],
                    System.currentTimeMillis() - (20 - i) * 1000
                ));
            }
        }
        
        if (!goodsList.isEmpty()) {
            database.goodsDao().insertAll(goodsList);
            android.util.Log.d("DatabaseInitializer", "Inserted " + goodsList.size() + " new goods");
        }
        
        // 验证最终的商品数量
        List<GoodsEntity> finalGoods = database.goodsDao().getAllGoodsSync();
        android.util.Log.d("DatabaseInitializer", "Final goods count: " + (finalGoods != null ? finalGoods.size() : 0));
    }
    
    /**
     * 获取头像资源ID（p1.jpeg 到 p11.jpeg）
     */
    private int getAvatarResource(int index) {
        switch (index) {
            case 1: return R.drawable.p1;
            case 2: return R.drawable.p2;
            case 3: return R.drawable.p3;
            case 4: return R.drawable.p4;
            case 5: return R.drawable.p5;
            case 6: return R.drawable.p6;
            case 7: return R.drawable.p7;
            case 8: return R.drawable.p8;
            case 9: return R.drawable.p9;
            case 10: return R.drawable.p10;
            case 11: return R.drawable.p11;
            case 12: return R.drawable.p12;
            case 13: return R.drawable.p13;
            case 14: return R.drawable.p14;
            case 15: return R.drawable.p15;
            case 16: return R.drawable.p16;
            default: return R.drawable.p1;
        }
    }
    
    /**
     * 获取商品图片资源ID（goods_1.jpg 到 goods_20.jpg）
     */
    private int getGoodsImageResource(int index) {
        switch (index) {
            case 1: return R.drawable.goods_1;
            case 2: return R.drawable.goods_2;
            case 3: return R.drawable.goods_3;
            case 4: return R.drawable.goods_4;
            case 5: return R.drawable.goods_5;
            case 6: return R.drawable.goods_6;
            case 7: return R.drawable.goods_7;
            case 8: return R.drawable.goods_8;
            case 9: return R.drawable.goods_9;
            case 10: return R.drawable.goods_10;
            case 11: return R.drawable.goods_11;
            case 12: return R.drawable.goods_12;
            case 13: return R.drawable.goods_13;
            case 14: return R.drawable.goods_14;
            case 15: return R.drawable.goods_15;
            case 16: return R.drawable.goods_16;
            case 17: return R.drawable.goods_17;
            case 18: return R.drawable.goods_18;
            case 19: return R.drawable.goods_19;
            case 20: return R.drawable.goods_20;
            default: return R.drawable.goods_1;
        }
    }
    
    /**
     * 初始化示例笔记数据
     * 创建一些示例公开笔记，并为其添加点赞、收藏、评论数据
     */
    private void initializeSampleNotes() {
        // 逐条检查 sample_note_*：缺失则创建，旧版本种子（无真实配图/话题）则升级。
        // 所有写入路径都按 noteId 幂等，可安全地每次启动执行。

        // 创建示例用户（如果不存在）
        List<UserEntity> sampleUsers = createSampleUsers();
        if (sampleUsers.isEmpty()) {
            return;
        }

        // 创建示例笔记（已存在但需升级的会在内部直接更新）
        List<NoteEntity> sampleNotes = createSampleNotes(sampleUsers);
        if (sampleNotes.isEmpty()) {
            return;
        }
        
        // 插入笔记
        database.noteDao().insertAll(sampleNotes);
        
        // 为每个笔记添加点赞、收藏、评论数据
        for (NoteEntity note : sampleNotes) {
            addLikesForNote(note.id, sampleUsers);
            addCollectionsForNote(note.id, sampleUsers);
            addCommentsForNote(note.id, sampleUsers);
        }
    }

    private void ensureTextNoteImages() {
        List<NoteEntity> notes = database.noteDao().getAllPublicNotesSync();
        if (notes == null) return;
        for (NoteEntity note : notes) {
            if (note == null || note.imageUris == null || !note.imageUris.isEmpty()) continue;
            android.net.Uri image = TextToImageConverter.createIfNeeded(
                    context, note.title, note.content, note.imageUris);
            if (image != null) {
                note.imageUris = new ArrayList<>();
                note.imageUris.add(image.toString());
                database.noteDao().update(note);
            }
        }
    }
    
    /**
     * 创建示例用户
     */
    private List<UserEntity> createSampleUsers() {
        List<UserEntity> users = new ArrayList<>();
        String[] nicknames = {
            "时尚达人", "美食家", "旅行者", "摄影师", "设计师",
            "生活家", "美妆师", "健身教练", "读书人", "音乐人",
            "艺术家", "程序员", "教师", "医生", "学生"
        };
        
        for (int i = 0; i < nicknames.length; i++) {
            String userId = "sample_user_" + (i + 1);
            UserEntity existing = database.userDao().getUserById(userId);
            if (existing == null) {
                UserEntity user = new UserEntity();
                user.id = userId;
                user.username = "user" + (i + 1);
                user.password = "123456";
                user.avatar = getAvatarResource((i % 16) + 1);
                user.nickname = nicknames[i];
                user.bio = "分享生活中的美好";
                user.createTime = System.currentTimeMillis() - (15 - i) * 86400000L; // 不同时间创建
                users.add(user);
            } else {
                users.add(existing);
            }
        }
        
        if (!users.isEmpty()) {
            // 只插入不存在的用户
            List<UserEntity> toInsert = new ArrayList<>();
            for (UserEntity user : users) {
                if (database.userDao().getUserById(user.id) == null) {
                    toInsert.add(user);
                }
            }
            if (!toInsert.isEmpty()) {
                database.userDao().insertAll(toInsert);
            }
        }
        
        return users;
    }
    
    /**
     * 创建示例笔记
     */
    private List<NoteEntity> createSampleNotes(List<UserEntity> users) {
        List<NoteEntity> notes = new ArrayList<>();
        
        String[] titles = {
            "春日穿搭分享，温柔又时尚",
            "今天做了超好吃的抹茶蛋糕",
            "云南旅行vlog，风景太美了",
            "摄影技巧分享：如何拍出好照片",
            "我的房间改造计划",
            "日常护肤routine分享",
            "健身房打卡第30天",
            "最近在读的几本好书推荐",
            "周末音乐会，现场太棒了",
            "手绘插画作品集",
            "编程学习心得分享",
            "教师节礼物推荐",
            "健康饮食小贴士",
            "医学生的日常",
            "大学生活vlog"
        };
        
        String[] contents = {
            "春天来了，分享几套温柔又时尚的穿搭，希望大家喜欢～",
            "第一次做抹茶蛋糕，虽然有点小瑕疵，但味道还不错！",
            "云南真的太美了，每一帧都是风景，强烈推荐大家去！",
            "分享一些摄影小技巧，希望能帮助到喜欢拍照的朋友",
            "终于把房间改造完成了，满满的成就感！",
            "坚持护肤一个月，皮肤真的变好了很多",
            "健身30天，虽然累但很充实，继续加油！",
            "最近读了几本很棒的书，推荐给大家",
            "周末去听了音乐会，现场氛围太棒了",
            "最近画的一些插画，希望大家喜欢",
            "学习编程的心得体会，分享给同样在路上的朋友",
            "教师节快到了，推荐一些适合送老师的礼物",
            "健康饮食真的很重要，分享一些小心得",
            "医学生的日常，虽然累但很充实",
            "记录一下大学生活的点点滴滴"
        };
        
        String[] locations = {
            "北京", "上海", "广州", "深圳", "杭州",
            "成都", "重庆", "西安", "南京", "武汉",
            "长沙", "厦门", "青岛", "大连", "苏州"
        };

        String[] topics = {
            "穿搭", "美食", "旅行", "摄影", "家居",
            "护肤", "健身", "读书", "音乐", "插画",
            "编程", "教师节", "健康饮食", "医学生", "校园"
        };

        for (int i = 0; i < titles.length; i++) {
            String noteId = "sample_note_" + (i + 1);
            List<String> images = buildSampleNoteImages(i);
            NoteEntity existing = database.noteDao().getNoteById(noteId);
            if (existing == null) {
                NoteEntity note = new NoteEntity();
                note.id = noteId;
                note.title = titles[i];
                note.content = contents[i];
                note.imageUris = images;
                note.topics = new ArrayList<>();
                note.topics.add(topics[i]);
                note.location = locations[i];
                note.isPublic = true;
                note.isDraft = false;
                note.userId = users.get(i % users.size()).id;
                note.createTime = System.currentTimeMillis() - (15 - i) * 3600000L; // 不同时间创建
                note.updateTime = note.createTime;
                notes.add(note);
            } else if (upgradeLegacySampleNote(existing, images, topics[i])) {
                database.noteDao().update(existing);
            }
        }

        return notes;
    }

    /**
     * 示例笔记配图：前 12 篇为图文笔记（1~3 张 drawable-nodpi 真实素材图），
     * 后 3 篇保持纯文字（由文字海报兜底，作为纯文字形态的示例）。
     * 使用 android.resource://包名/drawable/名称 形式，跨构建稳定。
     */
    private List<String> buildSampleNoteImages(int index) {
        List<String> images = new ArrayList<>();
        if (index >= 12) return images;
        int count = (index % 3) + 1;
        for (int k = 0; k < count; k++) {
            int resId = context.getResources().getIdentifier(
                    "image_" + (((index + k * 5) % 15) + 1), "drawable", context.getPackageName());
            if (resId != 0) {
                images.add("android.resource://" + context.getPackageName() + "/drawable/image_"
                        + (((index + k * 5) % 15) + 1));
            }
        }
        return images;
    }

    /**
     * 升级历史版本的示例笔记：旧种子全是空图片列表（或仅有一张文字海报），
     * 补上真实配图与话题。只处理 sample_note_* 数据，不碰用户自己的笔记。
     *
     * @return true 表示有字段被更新，需要写回数据库
     */
    private boolean upgradeLegacySampleNote(NoteEntity existing, List<String> images, String topic) {
        boolean changed = false;
        boolean missingRealImage = existing.imageUris == null || existing.imageUris.isEmpty()
                || (existing.imageUris.size() == 1
                        && TextToImageConverter.isGeneratedTextImage(existing.imageUris.get(0)));
        if (!images.isEmpty() && missingRealImage) {
            existing.imageUris = images;
            changed = true;
        }
        if (topic != null && (existing.topics == null || existing.topics.isEmpty())) {
            existing.topics = new ArrayList<>();
            existing.topics.add(topic);
            changed = true;
        }
        if (changed) {
            existing.updateTime = System.currentTimeMillis();
        }
        return changed;
    }
    
    /**
     * 为笔记添加点赞数据
     */
    private void addLikesForNote(String noteId, List<UserEntity> users) {
        // 检查是否已有点赞数据
        int existingLikes = database.likeDao().getLikeCountSync(noteId);
        if (existingLikes > 0) {
            return; // 已有点赞数据，不重复添加
        }
        
        // 随机生成5-500个点赞
        int likeCount = 5 + (int)(Math.random() * 495);
        List<LikeEntity> likes = new ArrayList<>();
        
        for (int i = 0; i < likeCount && i < users.size() * 3; i++) {
            UserEntity user = users.get((int)(Math.random() * users.size()));
            LikeEntity like = new LikeEntity(
                noteId,
                user.id,
                System.currentTimeMillis() - (long)(Math.random() * 86400000 * 7) // 过去7天内
            );
            likes.add(like);
        }
        
        if (!likes.isEmpty()) {
            database.likeDao().insertAll(likes);
        }
    }
    
    /**
     * 为笔记添加收藏数据
     */
    private void addCollectionsForNote(String noteId, List<UserEntity> users) {
        // 检查是否已有收藏数据
        int existingCollections = database.collectionDao().getCollectionCountSync(noteId);
        if (existingCollections > 0) {
            return; // 已有收藏数据，不重复添加
        }
        
        // 随机生成3-200个收藏
        int collectionCount = 3 + (int)(Math.random() * 197);
        List<CollectionEntity> collections = new ArrayList<>();
        
        for (int i = 0; i < collectionCount && i < users.size() * 2; i++) {
            UserEntity user = users.get((int)(Math.random() * users.size()));
            CollectionEntity collection = new CollectionEntity(
                noteId,
                user.id,
                System.currentTimeMillis() - (long)(Math.random() * 86400000 * 7) // 过去7天内
            );
            collections.add(collection);
        }
        
        if (!collections.isEmpty()) {
            database.collectionDao().insertAll(collections);
        }
    }
    
    /**
     * 为笔记添加评论数据
     */
    private void addCommentsForNote(String noteId, List<UserEntity> users) {
        // 检查是否已有评论数据
        List<CommentEntity> existingComments = database.commentDao().getCommentsByNoteIdSync(noteId);
        if (existingComments != null && !existingComments.isEmpty()) {
            return; // 已有评论数据，不重复添加
        }
        
        // 随机生成2-10条评论
        int commentCount = 2 + (int)(Math.random() * 8);
        String[] commentContents = {
            "太棒了！",
            "学到了，谢谢分享",
            "好喜欢这个风格",
            "收藏了",
            "期待更多分享",
            "真的很实用",
            "我也要去试试",
            "太美了",
            "赞赞赞",
            "支持一下"
        };
        
        List<CommentEntity> comments = new ArrayList<>();
        
        for (int i = 0; i < commentCount; i++) {
            UserEntity user = users.get((int)(Math.random() * users.size()));
            String commentId = noteId + "_comment_" + (i + 1);
            String content = commentContents[(int)(Math.random() * commentContents.length)];
            
            CommentEntity comment = new CommentEntity(
                commentId,
                noteId,
                user.id,
                content,
                null, // 暂时不支持回复
                System.currentTimeMillis() - (long)(Math.random() * 86400000 * 7) // 过去7天内
            );
            comments.add(comment);
        }
        
        if (!comments.isEmpty()) {
            database.commentDao().insertAll(comments);
        }
    }
    
    /**
     * 初始化消息数据
     * 为当前用户创建一些初始消息（点赞、收藏、评论、关注）
     */
    private void initializeMessages() {
        // 获取当前登录用户
        String currentUserId = getCurrentUserId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            return; // 没有登录用户，不初始化消息
        }
        
        // 检查是否已有消息数据
        List<MessageEntity> existingMessages = database.messageDao().getMessagesByUserIdSync(currentUserId);
        if (existingMessages != null && existingMessages.size() >= 10) {
            return; // 已有足够的消息，不重复初始化
        }
        
        // 获取示例笔记
        List<NoteEntity> sampleNotes = database.noteDao().getAllPublicNotesSync();
        if (sampleNotes == null || sampleNotes.isEmpty()) {
            return; // 没有笔记，无法创建消息
        }
        
        // 获取示例用户（排除当前用户）
        List<UserEntity> allUsers = database.userDao().getAllUsersSync();
        List<UserEntity> otherUsers = new ArrayList<>();
        for (UserEntity user : allUsers) {
            if (!user.id.equals(currentUserId)) {
                otherUsers.add(user);
            }
        }
        
        if (otherUsers.isEmpty()) {
            return; // 没有其他用户，无法创建消息
        }
        
        // 创建消息列表
        List<MessageEntity> messages = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        // 创建点赞消息（5-10条）
        int likeCount = 5 + (int)(Math.random() * 6);
        for (int i = 0; i < likeCount && i < sampleNotes.size(); i++) {
            NoteEntity note = sampleNotes.get((int)(Math.random() * sampleNotes.size()));
            if (note.userId.equals(currentUserId)) {
                UserEntity fromUser = otherUsers.get((int)(Math.random() * otherUsers.size()));
                MessageEntity message = new MessageEntity(
                    "msg_like_" + currentTime + "_" + i,
                    fromUser.id,
                    currentUserId,
                    0, // 点赞消息
                    "点赞了你的笔记",
                    note.id,
                    Math.random() < 0.3, // 30%已读
                    currentTime - (long)(Math.random() * 86400000 * 7) // 过去7天内
                );
                messages.add(message);
            }
        }
        
        // 创建收藏消息（3-8条）
        int collectionCount = 3 + (int)(Math.random() * 6);
        for (int i = 0; i < collectionCount && i < sampleNotes.size(); i++) {
            NoteEntity note = sampleNotes.get((int)(Math.random() * sampleNotes.size()));
            if (note.userId.equals(currentUserId)) {
                UserEntity fromUser = otherUsers.get((int)(Math.random() * otherUsers.size()));
                MessageEntity message = new MessageEntity(
                    "msg_collection_" + currentTime + "_" + i,
                    fromUser.id,
                    currentUserId,
                    1, // 收藏消息
                    "收藏了你的笔记",
                    note.id,
                    Math.random() < 0.3, // 30%已读
                    currentTime - (long)(Math.random() * 86400000 * 7) // 过去7天内
                );
                messages.add(message);
            }
        }
        
        // 创建评论消息（5-10条）
        int commentCount = 5 + (int)(Math.random() * 6);
        String[] commentContents = {
            "太棒了！",
            "学到了，谢谢分享",
            "好喜欢这个风格",
            "期待更多分享",
            "真的很实用",
            "我也要去试试",
            "太美了",
            "支持一下"
        };
        for (int i = 0; i < commentCount && i < sampleNotes.size(); i++) {
            NoteEntity note = sampleNotes.get((int)(Math.random() * sampleNotes.size()));
            if (note.userId.equals(currentUserId)) {
                UserEntity fromUser = otherUsers.get((int)(Math.random() * otherUsers.size()));
                String commentContent = commentContents[(int)(Math.random() * commentContents.length)];
                MessageEntity message = new MessageEntity(
                    "msg_comment_" + currentTime + "_" + i,
                    fromUser.id,
                    currentUserId,
                    2, // 评论消息
                    "评论了你的笔记：" + commentContent,
                    note.id,
                    Math.random() < 0.3, // 30%已读
                    currentTime - (long)(Math.random() * 86400000 * 7) // 过去7天内
                );
                messages.add(message);
            }
        }
        
        // 创建关注消息（2-5条）
        int followCount = 2 + (int)(Math.random() * 4);
        for (int i = 0; i < followCount && i < otherUsers.size(); i++) {
            UserEntity fromUser = otherUsers.get((int)(Math.random() * otherUsers.size()));
            MessageEntity message = new MessageEntity(
                "msg_follow_" + currentTime + "_" + i,
                fromUser.id,
                currentUserId,
                3, // 关注消息
                "关注了你",
                null,
                Math.random() < 0.3, // 30%已读
                currentTime - (long)(Math.random() * 86400000 * 7) // 过去7天内
            );
            messages.add(message);
        }
        
        // 插入消息
        if (!messages.isEmpty()) {
            database.messageDao().insertAll(messages);
        }
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
    }
}
