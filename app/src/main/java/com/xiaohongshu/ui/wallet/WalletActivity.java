package com.xiaohongshu.ui.wallet;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.CoinTransactionEntity;
import com.xiaohongshu.database.entity.OrderEntity;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.ui.order.OrderListActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 钱包Activity
 * 薯币余额以服务端 users.coins 为权威源（断网时回退本地账本合计）；
 * 支持演示充值、薯币明细账本；私信送礼物会校验并扣除薯币。
 */
public class WalletActivity extends BaseActivity {

    /** 充值档位（与服务端白名单一致），1 元 = 1 薯币 */
    private static final int[] RECHARGE_PRESETS = {6, 30, 68, 128, 328, 648};

    private TextView coinBalanceText;
    private TextView orderPaidValue;
    private TextView coinHistoryEmpty;
    private RecyclerView coinHistoryList;
    private CoinHistoryAdapter historyAdapter;
    private ImageView eyeIcon;
    private boolean isBalanceVisible = true;
    private int coinBalance = 0;

    private AppDatabase database;
    private RemoteApiClient remoteApiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String localUserId;

    public static void start(Context context) {
        Intent intent = new Intent(context, WalletActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);
        database = AppApplication.getDatabase();
        remoteApiClient = new RemoteApiClient(this);

        initViews();
        initData();
    }

    @Override
    protected void initViews() {
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("我的钱包");
        }

        coinBalanceText = findViewById(R.id.coinBalanceText);
        orderPaidValue = findViewById(R.id.orderPaidValue);
        eyeIcon = findViewById(R.id.eyeIcon);
        coinHistoryEmpty = findViewById(R.id.coinHistoryEmpty);
        coinHistoryList = findViewById(R.id.coinHistoryList);

        coinHistoryList.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new CoinHistoryAdapter();
        coinHistoryList.setAdapter(historyAdapter);

        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (eyeIcon != null) {
            eyeIcon.setOnClickListener(v -> {
                isBalanceVisible = !isBalanceVisible;
                updateBalanceVisibility();
            });
        }

        // 充值：演示支付，选择档位后直接入账
        View rechargeButton = findViewById(R.id.rechargeButton);
        if (rechargeButton != null) {
            rechargeButton.setOnClickListener(v -> showRechargeDialog());
        }

        // 订单累计消费：跳订单列表
        View orderPaidItem = findViewById(R.id.orderPaidItem);
        if (orderPaidItem != null) {
            orderPaidItem.setOnClickListener(v ->
                    startActivity(new Intent(this, OrderListActivity.class)));
        }
    }

    @Override
    protected void initData() {
        updateBalanceVisibility();
        loadLocalData();

        // 服务端余额为权威源；失败（断网/未登录）时保留账本合计
        final String token = currentToken();
        executor.execute(() -> {
            int coins = -1;
            try {
                coins = remoteApiClient.getWalletCoins(token);
            } catch (Exception ignored) {
                // 服务端不可达：保留本地账本合计
            }
            final int serverCoins = coins;
            runOnUiThread(() -> {
                if (serverCoins >= 0) {
                    coinBalance = serverCoins;
                }
                updateBalanceVisibility();
            });
        });
    }

    private String currentToken() {
        LoginDataRepository repository = LoginDataRepository.getInstance(this);
        com.xiaohongshu.bean.UserBean user = repository.getCurrentUser();
        return user == null || user.getToken() == null ? "" : user.getToken();
    }

    /** 本地账本明细 + 订单累计消费 */
    private void loadLocalData() {
        executor.execute(() -> {
            localUserId = WalletRepository.currentLocalUserId(this);

            double totalPaid = 0.0;
            if (localUserId != null) {
                List<OrderEntity> orders = database.orderDao().getOrdersByUserIdSync(localUserId);
                if (orders != null) {
                    for (OrderEntity order : orders) {
                        if (order.status > 0) totalPaid += order.totalPrice;
                    }
                }
            }
            if (coinBalance == 0 && localUserId != null) {
                coinBalance = WalletRepository.ledgerBalance(localUserId);
            }

            final List<CoinTransactionEntity> transactions =
                    WalletRepository.recentTransactions(localUserId, 50);
            final double paidResult = totalPaid;
            runOnUiThread(() -> {
                if (orderPaidValue != null) {
                    orderPaidValue.setText(String.format(Locale.getDefault(), "¥ %.2f", paidResult));
                }
                historyAdapter.submit(transactions);
                boolean empty = transactions == null || transactions.isEmpty();
                coinHistoryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                coinHistoryList.setVisibility(empty ? View.GONE : View.VISIBLE);
                updateBalanceVisibility();
            });
        });
    }

    /** 充值档位对话框：模拟支付，不产生真实扣款 */
    private void showRechargeDialog() {
        String[] labels = new String[RECHARGE_PRESETS.length];
        for (int i = 0; i < RECHARGE_PRESETS.length; i++) {
            labels[i] = RECHARGE_PRESETS[i] + " 薯币（¥" + RECHARGE_PRESETS[i] + "）";
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("薯币充值")
                .setItems(labels, (dialog, which) -> recharge(RECHARGE_PRESETS[which]))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void recharge(int amount) {
        Toast.makeText(this, "模拟支付中…（学习版不产生真实扣款）", Toast.LENGTH_SHORT).show();
        final String token = currentToken();
        executor.execute(() -> {
            try {
                int newBalance = remoteApiClient.rechargeCoins(amount, token);
                WalletRepository.recordRecharge(localUserId, amount);
                runOnUiThread(() -> {
                    coinBalance = newBalance;
                    updateBalanceVisibility();
                    Toast.makeText(this, "充值成功，+" + amount + " 薯币", Toast.LENGTH_SHORT).show();
                    loadLocalData();
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "充值失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateBalanceVisibility() {
        if (coinBalanceText != null) {
            coinBalanceText.setText(isBalanceVisible
                    ? String.valueOf(coinBalance) : "****");
        }
    }

    /** 薯币明细列表 */
    private static class CoinHistoryAdapter extends RecyclerView.Adapter<CoinHistoryViewHolder> {
        private List<CoinTransactionEntity> items = java.util.Collections.emptyList();

        void submit(List<CoinTransactionEntity> transactions) {
            items = transactions == null ? java.util.Collections.emptyList() : transactions;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CoinHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_coin_transaction, parent, false);
            return new CoinHistoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CoinHistoryViewHolder holder, int position) {
            CoinTransactionEntity item = items.get(position);
            holder.title.setText(item.title == null ? "" : item.title);
            SimpleDateFormat format = new SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault());
            holder.time.setText(format.format(new Date(item.createTime)));
            boolean income = item.amount >= 0;
            holder.amount.setText(income ? "+" + item.amount : String.valueOf(item.amount));
            holder.amount.setTextColor(holder.amount.getResources().getColor(
                    income ? R.color.xhs_red : R.color.text_primary, null));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class CoinHistoryViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView time;
        final TextView amount;

        CoinHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.coinItemTitle);
            time = itemView.findViewById(R.id.coinItemTime);
            amount = itemView.findViewById(R.id.coinItemAmount);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
