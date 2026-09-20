package com.xiaohongshu.ui.wallet;

import android.content.Context;

import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.dao.CoinDao;
import com.xiaohongshu.database.entity.CoinTransactionEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.List;
import java.util.UUID;

/**
 * 薯币账本仓库。
 * 余额以服务端 users.coins 为权威源（RemoteApiClient.getWalletCoins）；
 * 本表记录充值 / 送出礼物 / 收到礼物收益，用于钱包明细展示与断网兜底。
 * 所有方法涉及数据库读写，必须在后台线程调用。
 */
public final class WalletRepository {

    private WalletRepository() {
    }

    /** 当前登录用户的本地 userId；未登录返回 null */
    public static String currentLocalUserId(Context context) {
        UserBean current = LoginDataRepository.getInstance(context).getCurrentUser();
        if (current == null) return null;
        com.xiaohongshu.database.entity.UserEntity entity =
                AppApplication.getDatabase().userDao().getUserByUsername(current.getUsername());
        return entity == null ? null : entity.id;
    }

    /** 账本合计（断网兜底余额） */
    public static int ledgerBalance(String userId) {
        if (userId == null) return 0;
        return AppApplication.getDatabase().coinDao().getLedgerSumSync(userId);
    }

    /** 最近账目（明细页） */
    public static List<CoinTransactionEntity> recentTransactions(String userId, int limit) {
        if (userId == null) return java.util.Collections.emptyList();
        return AppApplication.getDatabase().coinDao().getRecentByUserSync(userId, limit);
    }

    /** 记一笔充值入账 */
    public static void recordRecharge(String userId, int amount) {
        insert(userId, CoinTransactionEntity.TYPE_RECHARGE, amount, "充值",
                "recharge_" + System.currentTimeMillis());
    }

    /** 记一笔送出礼物（负向），messageId 作幂等键 */
    public static void recordGiftOut(String userId, int price, String giftLabel, String messageId) {
        insert(userId, CoinTransactionEntity.TYPE_GIFT_OUT, -price,
                "送出礼物 " + giftLabel, messageId == null ? "giftout_" + UUID.randomUUID() : messageId);
    }

    /** 记一笔收到礼物收益（正向），按 messageId 幂等，已入账则跳过 */
    public static void recordGiftInIfAbsent(String userId, int price, String giftLabel, String messageId) {
        if (userId == null || messageId == null || messageId.isEmpty()) return;
        CoinDao dao = AppApplication.getDatabase().coinDao();
        if (dao.countByRelatedId(userId, messageId) > 0) return;
        insert(userId, CoinTransactionEntity.TYPE_GIFT_IN, price,
                "收到礼物收益 " + giftLabel, messageId);
    }

    private static void insert(String userId, int type, int amount, String title, String relatedId) {
        if (userId == null) return;
        CoinTransactionEntity entity = new CoinTransactionEntity();
        entity.id = UUID.randomUUID().toString();
        entity.userId = userId;
        entity.type = type;
        entity.amount = amount;
        entity.title = title;
        entity.relatedId = relatedId;
        entity.createTime = System.currentTimeMillis();
        AppApplication.getDatabase().coinDao().insert(entity);
    }
}
