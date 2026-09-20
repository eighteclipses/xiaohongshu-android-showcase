package com.xiaohongshu.database.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 薯币账目实体
 * 钱包明细的本地账本：充值 / 送出礼物 / 收到礼物收益。
 * 余额以服务端 users.coins 为权威源，本表用于明细展示与断网时兜底显示；
 * relatedId 存幂等键（充值单号或礼物消息 id），防止重复入账。
 */
@Entity(tableName = "coin_transactions",
        indices = {@Index("userId"), @Index("relatedId")})
public class CoinTransactionEntity {
    public static final int TYPE_RECHARGE = 0;   // 充值
    public static final int TYPE_GIFT_OUT = 1;   // 送出礼物
    public static final int TYPE_GIFT_IN = 2;    // 收到礼物收益

    @PrimaryKey
    @NonNull
    public String id = "";

    public String userId;
    public int type;
    /** 带符号金额：收入为正、支出为负 */
    public int amount;
    /** 展示文案，如「充值」「送出礼物 🌹 玫瑰」 */
    public String title;
    /** 幂等键 */
    public String relatedId;
    public long createTime;
}
