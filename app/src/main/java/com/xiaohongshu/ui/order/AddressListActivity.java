package com.xiaohongshu.ui.order;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.AddressEntity;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 地址列表Activity
 * 基于Room的收货地址管理：新增、编辑、删除、设默认
 */
public class AddressListActivity extends BaseActivity {
    public static final String KEY_SELECTED_ADDRESS = "key_selected_address";
    private static final int REQUEST_CODE_SELECT_ADDRESS = 1001;

    private RecyclerView addressRecyclerView;
    private TextView emptyView;
    private AddressAdapter addressAdapter;
    private final List<AddressEntity> addressList = new ArrayList<>();
    private boolean isSelectMode = false;
    private String currentUserId = "";
    private AppDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void start(Context context) {
        Intent intent = new Intent(context, AddressListActivity.class);
        context.startActivity(intent);
    }

    public static void startForSelect(Context context) {
        Intent intent = new Intent(context, AddressListActivity.class);
        intent.putExtra("select_mode", true);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_address_list);

        isSelectMode = getIntent().getBooleanExtra("select_mode", false);
        database = AppApplication.getDatabase();

        initViews();
        resolveUserIdAndLoad();
    }

    @Override
    protected void initViews() {
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("收货地址");
        }

        addressRecyclerView = findViewById(R.id.addressRecyclerView);
        addressRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        addressAdapter = new AddressAdapter(addressList, new AddressAdapter.OnAddressActionListener() {
            @Override
            public void onAddressClick(AddressEntity address) {
                if (isSelectMode) {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra(KEY_SELECTED_ADDRESS, formatAddress(address));
                    setResult(RESULT_OK, resultIntent);
                    finish();
                }
            }

            @Override
            public void onEditClick(AddressEntity address) {
                showEditDialog(address);
            }

            @Override
            public void onDeleteClick(AddressEntity address) {
                confirmDelete(address);
            }

            @Override
            public void onSetDefaultClick(AddressEntity address) {
                setDefault(address);
            }
        });
        addressRecyclerView.setAdapter(addressAdapter);

        // 添加地址按钮
        TextView addAddressButton = findViewById(R.id.addAddressButton);
        if (addAddressButton != null) {
            addAddressButton.setOnClickListener(v -> showEditDialog(null));
        }

        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        emptyView = findViewById(R.id.emptyView);
    }

    private void resolveUserIdAndLoad() {
        com.xiaohongshu.bean.UserBean currentUser =
                LoginDataRepository.getInstance(this).getCurrentUser();
        final String username = currentUser != null ? currentUser.getUsername() : "";
        executor.execute(() -> {
            UserEntity userEntity = username.isEmpty() ? null
                    : database.userDao().getUserByUsername(username);
            currentUserId = userEntity != null ? userEntity.id : "";
            loadAddresses();
        });
    }

    private void loadAddresses() {
        executor.execute(() -> {
            List<AddressEntity> addresses = currentUserId.isEmpty()
                    ? new ArrayList<>() : database.addressDao().getByUserSync(currentUserId);
            runOnUiThread(() -> {
                addressList.clear();
                addressList.addAll(addresses);
                addressAdapter.notifyDataSetChanged();
                updateEmptyState();
            });
        });
    }

    private void updateEmptyState() {
        if (emptyView != null) {
            emptyView.setVisibility(addressList.isEmpty() ? View.VISIBLE : View.GONE);
        }
        addressRecyclerView.setVisibility(addressList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static String formatAddress(AddressEntity address) {
        return address.receiverName + " " + address.receiverPhone + " " + address.detail;
    }

    /**
     * 新增（address为null）或编辑地址对话框
     */
    private void showEditDialog(final AddressEntity address) {
        boolean isEdit = address != null;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "编辑地址" : "添加地址");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("收件人姓名");
        if (isEdit) nameInput.setText(address.receiverName);
        container.addView(nameInput);

        final EditText phoneInput = new EditText(this);
        phoneInput.setHint("联系电话");
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        if (isEdit) phoneInput.setText(address.receiverPhone);
        container.addView(phoneInput);

        final EditText detailInput = new EditText(this);
        detailInput.setHint("详细地址（省市区街道门牌号）");
        if (isEdit) detailInput.setText(address.detail);
        container.addView(detailInput);

        builder.setView(container);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();
            String detail = detailInput.getText().toString().trim();
            if (name.isEmpty() || phone.isEmpty() || detail.isEmpty()) {
                Toast.makeText(this, "请完整填写收件人、电话和地址", Toast.LENGTH_SHORT).show();
                return;
            }
            saveAddress(address, name, phone, detail);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveAddress(final AddressEntity original, String name, String phone, String detail) {
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "请先登录后再管理地址", Toast.LENGTH_SHORT).show();
            return;
        }
        final AddressEntity target = original != null ? original : new AddressEntity();
        target.userId = currentUserId;
        target.receiverName = name;
        target.receiverPhone = phone;
        target.detail = detail;
        final boolean shouldBeDefault = original != null && original.isDefault
                || (original == null && addressList.isEmpty());
        executor.execute(() -> {
            if (shouldBeDefault) {
                database.addressDao().clearDefault(currentUserId);
                target.isDefault = true;
            }
            if (original != null) {
                database.addressDao().update(target);
            } else {
                target.createTime = System.currentTimeMillis();
                database.addressDao().insert(target);
            }
            loadAddresses();
        });
    }

    private void confirmDelete(final AddressEntity address) {
        new AlertDialog.Builder(this)
                .setTitle("删除地址")
                .setMessage("确定删除该收货地址吗？")
                .setPositiveButton("删除", (dialog, which) -> executor.execute(() -> {
                    database.addressDao().delete(address);
                    loadAddresses();
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void setDefault(final AddressEntity address) {
        executor.execute(() -> {
            database.addressDao().clearDefault(currentUserId);
            address.isDefault = true;
            database.addressDao().update(address);
            loadAddresses();
        });
    }

    private static class AddressAdapter extends RecyclerView.Adapter<AddressAdapter.ViewHolder> {
        private final List<AddressEntity> addresses;
        private final OnAddressActionListener listener;

        public interface OnAddressActionListener {
            void onAddressClick(AddressEntity address);
            void onEditClick(AddressEntity address);
            void onDeleteClick(AddressEntity address);
            void onSetDefaultClick(AddressEntity address);
        }

        public AddressAdapter(List<AddressEntity> addresses, OnAddressActionListener listener) {
            this.addresses = addresses;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_address, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AddressEntity address = addresses.get(position);
            holder.receiverText.setText(address.receiverName + "  " + address.receiverPhone);
            holder.addressText.setText(address.detail);
            holder.defaultTag.setVisibility(address.isDefault ? View.VISIBLE : View.GONE);
            holder.setDefaultButton.setVisibility(address.isDefault ? View.GONE : View.VISIBLE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddressClick(address);
            });
            holder.editButton.setOnClickListener(v -> {
                if (listener != null) listener.onEditClick(address);
            });
            holder.deleteButton.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteClick(address);
            });
            holder.setDefaultButton.setOnClickListener(v -> {
                if (listener != null) listener.onSetDefaultClick(address);
            });
        }

        @Override
        public int getItemCount() {
            return addresses != null ? addresses.size() : 0;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView receiverText;
            TextView addressText;
            TextView defaultTag;
            TextView editButton;
            TextView deleteButton;
            TextView setDefaultButton;

            public ViewHolder(View itemView) {
                super(itemView);
                receiverText = itemView.findViewById(R.id.receiverText);
                addressText = itemView.findViewById(R.id.addressText);
                defaultTag = itemView.findViewById(R.id.defaultTag);
                editButton = itemView.findViewById(R.id.editButton);
                deleteButton = itemView.findViewById(R.id.deleteButton);
                setDefaultButton = itemView.findViewById(R.id.setDefaultButton);
            }
        }
    }
}
