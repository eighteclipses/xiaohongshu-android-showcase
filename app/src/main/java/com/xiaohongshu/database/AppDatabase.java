package com.xiaohongshu.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.xiaohongshu.database.converter.StringListConverter;
import com.xiaohongshu.database.dao.*;
import com.xiaohongshu.database.entity.*;

/**
 * 应用数据库
 * 使用Room数据库管理所有数据
 */
@Database(
    entities = {
        NoteEntity.class,
        UserEntity.class,
        LikeEntity.class,
        CollectionEntity.class,
        CommentEntity.class,
        FollowEntity.class,
        BlockEntity.class,
        GoodsEntity.class,
        OrderEntity.class,
        CartEntity.class,
        MessageEntity.class,
        AddressEntity.class,
        CoinTransactionEntity.class
    },
    version = 10,
    exportSchema = false
)
@TypeConverters({StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "xiaohongshu_db";
    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE notes ADD COLUMN moderationStatus TEXT DEFAULT 'approved'");
            db.execSQL("ALTER TABLE notes ADD COLUMN syncState TEXT DEFAULT 'synced'");
            db.execSQL("ALTER TABLE notes ADD COLUMN reviewNote TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE notes ADD COLUMN contentVersion INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE notes ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE notes ADD COLUMN serverKnown INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM likes WHERE id NOT IN (SELECT MIN(id) FROM likes GROUP BY noteId, userId)");
            database.execSQL("DELETE FROM collections WHERE id NOT IN (SELECT MIN(id) FROM collections GROUP BY noteId, userId)");
            database.execSQL("DELETE FROM follows WHERE id NOT IN (SELECT MIN(id) FROM follows GROUP BY followerId, followingId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_likes_noteId_userId ON likes(noteId, userId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_noteId_userId ON collections(noteId, userId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_follows_followerId_followingId ON follows(followerId, followingId)");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE users ADD COLUMN avatarUri TEXT NOT NULL DEFAULT ''");
        }
    };

    /**
     * 修复 2->3 迁移将 avatarUri 建为 NOT NULL 导致的 Room schema 校验失败。
     * SQLite 不支持直接修改列约束，因此通过临时表重建 users 并保留已有数据。
     */
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS users_new (" +
                "id TEXT NOT NULL, " +
                "username TEXT, " +
                "password TEXT, " +
                "avatar INTEGER NOT NULL, " +
                "avatarUri TEXT, " +
                "nickname TEXT, " +
                "bio TEXT, " +
                "createTime INTEGER NOT NULL, " +
                "PRIMARY KEY(id))");
            database.execSQL("INSERT INTO users_new " +
                "(id, username, password, avatar, avatarUri, nickname, bio, createTime) " +
                "SELECT id, username, password, avatar, avatarUri, nickname, bio, createTime FROM users");
            database.execSQL("DROP TABLE users");
            database.execSQL("ALTER TABLE users_new RENAME TO users");
        }
    };
    
    /**
     * 5：评论表增加冗余作者字段（支持远程评论回读展示），新建收货地址表。
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comments ADD COLUMN authorName TEXT");
            database.execSQL("ALTER TABLE comments ADD COLUMN authorAvatar TEXT");
            database.execSQL("CREATE TABLE IF NOT EXISTS addresses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "userId TEXT NOT NULL, " +
                "receiverName TEXT NOT NULL, " +
                "receiverPhone TEXT NOT NULL, " +
                "detail TEXT NOT NULL, " +
                "isDefault INTEGER NOT NULL, " +
                "createTime INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_addresses_userId ON addresses(userId)");
        }
    };

    /**
     * 6：用户表增加主页背景图字段。
     */
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE users ADD COLUMN background TEXT DEFAULT ''");
        }
    };

    /**
     * 7：新建拉黑关系表。
     */
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS blocks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "blockerId TEXT, " +
                "blockedId TEXT, " +
                "createTime INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_blocks_blockerId ON blocks(blockerId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_blocks_blockedId ON blocks(blockedId)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocks_blockerId_blockedId ON blocks(blockerId, blockedId)");
        }
    };

    /** 7→8：评论点赞数（本地维护，服务端暂无评论点赞接口） */
    private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE comments ADD COLUMN likes INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** 8→9：薯币账本（钱包明细与断网兜底余额；服务端 users.coins 为权威源） */
    private static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS coin_transactions (" +
                "id TEXT NOT NULL PRIMARY KEY, " +
                "userId TEXT, " +
                "type INTEGER NOT NULL, " +
                "amount INTEGER NOT NULL, " +
                "title TEXT, " +
                "relatedId TEXT, " +
                "createTime INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_coin_transactions_userId ON coin_transactions(userId)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_coin_transactions_relatedId ON coin_transactions(relatedId)");
        }
    };

    /**
     * 获取数据库实例（单例模式）
     */
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                context.getApplicationContext(),
                AppDatabase.class,
                DATABASE_NAME
            )
            .allowMainThreadQueries() // 允许在主线程查询（开发阶段，生产环境应使用后台线程）
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .build();
        }
        return instance;
    }
    
    /**
     * 获取笔记DAO
     */
    public abstract NoteDao noteDao();
    
    /**
     * 获取用户DAO
     */
    public abstract UserDao userDao();
    
    /**
     * 获取点赞DAO
     */
    public abstract LikeDao likeDao();
    
    /**
     * 获取收藏DAO
     */
    public abstract CollectionDao collectionDao();
    
    /**
     * 获取评论DAO
     */
    public abstract CommentDao commentDao();
    
    /**
     * 获取关注DAO
     */
    public abstract FollowDao followDao();

    /**
     * 获取拉黑DAO
     */
    public abstract BlockDao blockDao();
    
    /**
     * 获取商品DAO
     */
    public abstract GoodsDao goodsDao();
    
    /**
     * 获取订单DAO
     */
    public abstract OrderDao orderDao();
    
    /**
     * 获取购物车DAO
     */
    public abstract CartDao cartDao();
    
    /**
     * 获取消息DAO
     */
    public abstract MessageDao messageDao();

    /**
     * 获取收货地址DAO
     */
    public abstract AddressDao addressDao();

    /**
     * 获取薯币账目DAO
     */
    public abstract CoinDao coinDao();
}

