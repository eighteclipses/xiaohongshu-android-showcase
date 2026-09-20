package com.xiaohongshu.database.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.xiaohongshu.database.entity.CoinTransactionEntity;

import java.util.List;

/**
 * 薯币账目数据访问对象
 */
@Dao
public interface CoinDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CoinTransactionEntity transaction);

    /** 按时间倒序取最近账目（明细页） */
    @Query("SELECT * FROM coin_transactions WHERE userId = :userId ORDER BY createTime DESC LIMIT :limit")
    List<CoinTransactionEntity> getRecentByUserSync(String userId, int limit);

    /** 账本合计（断网时兜底余额） */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_transactions WHERE userId = :userId")
    int getLedgerSumSync(String userId);

    /** 幂等检查：同一 relatedId 是否已入账 */
    @Query("SELECT COUNT(*) FROM coin_transactions WHERE userId = :userId AND relatedId = :relatedId")
    int countByRelatedId(String userId, String relatedId);

    @Query("DELETE FROM coin_transactions WHERE userId = :userId")
    void deleteByUser(String userId);
}
