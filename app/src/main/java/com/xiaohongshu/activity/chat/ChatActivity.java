package com.xiaohongshu.activity.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonObject;
import com.xiaohongshu.R;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 私信会话页：气泡对话 + 礼物面板。
 * 规则与真实小红书对齐：对方未关注时只能发一条私信，送礼物（消耗薯币）解锁无限私信。
 * 薯币余额以服务端为准（users.coins），送礼前校验余额、服务端扣费，本地账本记录明细。
 * 私信数据走服务端（需联网），本地不缓存。
 */
public class ChatActivity extends AppCompatActivity {

    private RemoteApiClient remoteApiClient;
    private ChatAdapter chatAdapter;
    private ExecutorService executorService;
    private String toId = "";
    private String token = "";
    private RecyclerView chatList;
    private TextView coinText;
    /** 最近一次从服务端获取的薯币余额；-1 表示未知（离线/未登录） */
    private volatile int coinBalance = -1;
    /** 我在服务端的用户 id（气泡左右与收礼入账判断） */
    private volatile String myIdValue = "";

    public static void start(android.content.Context context, String targetUserId) {
        android.content.Intent intent = new android.content.Intent(context, ChatActivity.class);
        intent.putExtra("key_to_id", targetUserId);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        toId = getIntent().getStringExtra("key_to_id");
        remoteApiClient = new RemoteApiClient(this);
        executorService = Executors.newSingleThreadExecutor();

        com.xiaohongshu.bean.UserBean me =
                LoginDataRepository.getInstance(this).getCurrentUser();
        token = me == null || me.getToken() == null ? "" : me.getToken();

        chatList = findViewById(R.id.chatList);
        chatList.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(null);
        chatList.setAdapter(chatAdapter);

        coinText = findViewById(R.id.coinText);
        EditText input = findViewById(R.id.chatInput);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.sendButton).setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            sendMessage(text);
        });
        findViewById(R.id.giftButton).setOnClickListener(v -> showGiftSheet());

        // 会话与我的用户 id（气泡左右判断需要服务器身份）
        executorService.execute(() -> {
            try {
                JsonObject thread = remoteApiClient.getDmThread(toId, token);
                JsonObject peer = thread.has("user") ? thread.getAsJsonObject("user") : new JsonObject();
                String peerName = peer.has("nickname") && !peer.get("nickname").isJsonNull()
                        ? peer.get("nickname").getAsString() : "私信";
                boolean canSend = thread.has("can_send") && thread.get("can_send").getAsBoolean();
                List<JsonObject> messages = new ArrayList<>();
                if (thread.has("messages") && thread.get("messages").isJsonArray()) {
                    thread.getAsJsonArray("messages").forEach(element -> {
                        if (element.isJsonObject()) messages.add(element.getAsJsonObject());
                    });
                }
                String myId = "";
                com.xiaohongshu.bean.UserBean meUser =
                        LoginDataRepository.getInstance(this).getCurrentUser();
                if (meUser != null) {
                    try {
                        JsonObject myProfile = remoteApiClient.getUserProfile(meUser.getUsername(), token);
                        myId = myProfile.has("id") && !myProfile.get("id").isJsonNull()
                                ? myProfile.get("id").getAsString() : "";
                    } catch (Exception ignored) {
                    }
                }
                // 右上角展示薯币余额（服务端权威源）
                myIdValue = myId;
                refreshCoinBalance();
                // 收到的礼物按 1:1 折算薯币收益入账本（幂等）
                creditIncomingGifts(messages);
                final String finalMyId = myId;
                final String title = peerName;
                runOnUiThread(() -> {
                    chatAdapter = new ChatAdapter(finalMyId);
                    chatAdapter.submit(messages);
                    chatList.setAdapter(chatAdapter);
                    if (!messages.isEmpty()) chatList.scrollToPosition(messages.size() - 1);
                    TextView chatTitle = findViewById(R.id.chatTitle);
                    if (chatTitle != null) chatTitle.setText(title);
                    updateCoinText();
                    if (!canSend) {
                        Toast.makeText(this, "对方关注你之前只能发一条私信，点礼物按钮送一份解锁", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "私信服务暂不可用，请稍后再试", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从钱包充值返回后刷新余额展示
        refreshCoinBalance();
    }

    /** 拉取服务端薯币余额并更新角标显示 */
    private void refreshCoinBalance() {
        executorService.execute(() -> {
            try {
                coinBalance = remoteApiClient.getWalletCoins(token);
                runOnUiThread(this::updateCoinText);
            } catch (Exception ignored) {
                // 离线时保留上次余额；送礼仍会由服务端兜底校验
            }
        });
    }

    private void updateCoinText() {
        if (coinText != null) {
            coinText.setText(coinBalance >= 0
                    ? "薯币 " + coinBalance
                    : "薯币 --");
        }
    }

    /** 会话里收到的礼物按消息 id 幂等记入本地薯币账本（收益与余额在服务端已结算） */
    private void creditIncomingGifts(List<JsonObject> messages) {
        String localUserId = com.xiaohongshu.ui.wallet.WalletRepository
                .currentLocalUserId(this);
        if (localUserId == null) return;
        for (JsonObject message : messages) {
            try {
                if (!message.has("gift") || message.get("gift").isJsonNull()) continue;
                if (!message.has("toId") || message.get("toId").isJsonNull()) continue;
                if (!myIdValue.equals(message.get("toId").getAsString())) continue;
                JsonObject gift = message.getAsJsonObject("gift");
                int price = gift.has("price") ? gift.get("price").getAsInt() : 0;
                String label = (gift.has("emoji") ? gift.get("emoji").getAsString() : "") +
                        (gift.has("name") ? gift.get("name").getAsString() : "");
                com.xiaohongshu.ui.wallet.WalletRepository.recordGiftInIfAbsent(
                        localUserId, price, label, message.get("id").getAsString());
            } catch (Exception ignored) {
                // 单条消息解析失败不影响其余入账
            }
        }
    }

    private void sendMessage(String content) {
        executorService.execute(() -> {
            try {
                // 本地拉黑拦截（服务端 dmAllowed 亦会拦截，双保险）
                com.xiaohongshu.bean.UserBean meUser =
                        LoginDataRepository.getInstance(this).getCurrentUser();
                if (meUser != null) {
                    com.xiaohongshu.database.entity.UserEntity me =
                            com.xiaohongshu.app.AppApplication.getDatabase()
                                    .userDao().getUserByUsername(meUser.getUsername());
                    if (me != null && com.xiaohongshu.app.AppApplication.getDatabase()
                            .blockDao().checkBlock(me.id, toId) != null) {
                        runOnUiThread(() -> Toast.makeText(this,
                                "已拉黑该用户，无法发送私信", Toast.LENGTH_SHORT).show());
                        return;
                    }
                }
                remoteApiClient.sendDm(toId, content, token);
                JsonObject thread = remoteApiClient.getDmThread(toId, token);
                List<JsonObject> messages = new ArrayList<>();
                if (thread.has("messages") && thread.get("messages").isJsonArray()) {
                    thread.getAsJsonArray("messages").forEach(element -> {
                        if (element.isJsonObject()) messages.add(element.getAsJsonObject());
                    });
                }
                runOnUiThread(() -> chatAdapter.submit(messages));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        error.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    /** 礼物面板：底部弹层网格，按对方创作者等级定价，赠送后刷新会话。 */
    private void showGiftSheet() {
        android.app.Dialog sheet = new android.app.Dialog(this);
        sheet.setContentView(R.layout.sheet_gifts);
        android.view.Window window = sheet.getWindow();
        if (window != null) {
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.45f));
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        RecyclerView giftGrid = sheet.findViewById(R.id.giftGrid);
        TextView coinLabel = sheet.findViewById(R.id.giftSheetCoins);
        // 4 列网格展示全部礼物，可上下滚动
        giftGrid.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 4));

        executorService.execute(() -> {
            try {
                JsonObject data = remoteApiClient.getGifts(toId, token);
                List<JsonObject> gifts = new ArrayList<>();
                if (data.has("gifts") && data.get("gifts").isJsonArray()) {
                    data.getAsJsonArray("gifts").forEach(element -> {
                        if (element.isJsonObject()) gifts.add(element.getAsJsonObject());
                    });
                }
                if (coinBalance < 0) {
                    try {
                        coinBalance = remoteApiClient.getWalletCoins(token);
                        runOnUiThread(this::updateCoinText);
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(() -> {
                    coinLabel.setText(coinBalance >= 0 ? "薯币 " + coinBalance : "薯币 --");
                    giftGrid.setAdapter(new RecyclerView.Adapter<GiftViewHolder>() {
                        @NonNull
                        @Override
                        public GiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View view = LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.item_gift, parent, false);
                            return new GiftViewHolder(view);
                        }

                        @Override
                        public void onBindViewHolder(@NonNull GiftViewHolder holder, int position) {
                            JsonObject gift = gifts.get(position);
                            holder.icon.setText(gift.get("emoji").getAsString());
                            holder.name.setText(gift.get("name").getAsString());
                            holder.price.setText(gift.get("price").getAsInt() + "币");
                            holder.itemView.setOnClickListener(v -> {
                                String giftId = gift.get("id").getAsString();
                                String giftLabel = gift.get("emoji").getAsString() + " " + gift.get("name").getAsString();
                                int price = gift.get("price").getAsInt();
                                // 余额预检：不足直接引导充值（服务端仍有最终校验）
                                if (coinBalance >= 0 && coinBalance < price) {
                                    showInsufficientDialog(price);
                                    return;
                                }
                                executorService.execute(() -> {
                                    try {
                                        JsonObject result = remoteApiClient.sendGift(toId, giftId, token);
                                        // 服务端扣费成功：记录账本并同步余额
                                        if (result.has("balance") && !result.get("balance").isJsonNull()) {
                                            coinBalance = result.get("balance").getAsInt();
                                        }
                                        String messageId = result.has("message") && result.getAsJsonObject("message").has("id")
                                                ? result.getAsJsonObject("message").get("id").getAsString() : null;
                                        com.xiaohongshu.ui.wallet.WalletRepository.recordGiftOut(
                                                com.xiaohongshu.ui.wallet.WalletRepository.currentLocalUserId(ChatActivity.this),
                                                price, giftLabel, messageId);
                                        JsonObject refreshed = remoteApiClient.getDmThread(toId, token);
                                        List<JsonObject> messages = new ArrayList<>();
                                        if (refreshed.has("messages") && refreshed.get("messages").isJsonArray()) {
                                            refreshed.getAsJsonArray("messages").forEach(element -> {
                                                if (element.isJsonObject()) messages.add(element.getAsJsonObject());
                                            });
                                        }
                                        runOnUiThread(() -> {
                                            Toast.makeText(ChatActivity.this,
                                                    "已送出 " + gift.get("name").getAsString() + "，解锁无限私信",
                                                    Toast.LENGTH_SHORT).show();
                                            updateCoinText();
                                            sheet.dismiss();
                                            chatAdapter.submit(messages);
                                            if (!messages.isEmpty()) chatList.scrollToPosition(messages.size() - 1);
                                        });
                                    } catch (Exception error) {
                                        runOnUiThread(() -> Toast.makeText(ChatActivity.this,
                                                error.getMessage(), Toast.LENGTH_LONG).show());
                                    }
                                });
                            });
                        }

                        @Override
                        public int getItemCount() {
                            return gifts.size();
                        }
                    });
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "礼物加载失败：" + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        sheet.show();
    }

    /** 薯币不足：提示差额并引导去钱包充值（模拟支付） */
    private void showInsufficientDialog(int price) {
        int shortage = coinBalance < 0 ? price : price - coinBalance;
        new android.app.AlertDialog.Builder(this)
                .setTitle("薯币余额不足")
                .setMessage("送出这份礼物需要 " + price + " 薯币，当前余额 "
                        + (coinBalance < 0 ? 0 : coinBalance) + "，还差 " + shortage + " 薯币。\n可先去钱包充值（学习版为模拟支付，不产生真实扣款）。")
                .setPositiveButton("去充值", (dialog, which) ->
                        com.xiaohongshu.ui.wallet.WalletActivity.start(this))
                .setNegativeButton(com.xiaohongshu.R.string.cancel, null)
                .show();
    }

    static class GiftViewHolder extends RecyclerView.ViewHolder {
        final TextView icon;
        final TextView name;
        final TextView price;

        GiftViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.giftIcon);
            name = itemView.findViewById(R.id.giftName);
            price = itemView.findViewById(R.id.giftPrice);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) executorService.shutdown();
    }
}
