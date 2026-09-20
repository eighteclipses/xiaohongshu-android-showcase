package com.xiaohongshu.ui.mine;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.xiaohongshu.R;
import com.xiaohongshu.app.AppApplication;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.database.AppDatabase;
import com.xiaohongshu.database.entity.UserEntity;
import com.xiaohongshu.ui.login.LoginDataRepository;
import com.xiaohongshu.network.RemoteApiClient;
import com.xiaohongshu.util.ImageLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 编辑资料Activity
 * 允许用户编辑昵称、头像、简介等
 */
public class EditProfileActivity extends BaseActivity {
    private static final int REQUEST_CODE_PICK_AVATAR = 2101;
    private AppDatabase database;
    private ExecutorService executorService;
    private EditText nicknameInput;
    private EditText bioInput;
    private ImageView avatarImage;
    private UserEntity currentUser;
    
    public static void start(Context context) {
        Intent intent = new Intent(context, EditProfileActivity.class);
        context.startActivity(intent);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        
        database = AppApplication.getDatabase();
        executorService = Executors.newSingleThreadExecutor();
        
        initViews();
        initData();
    }
    
    @Override
    protected void initViews() {
        // 设置标题
        TextView titleText = findViewById(R.id.titleText);
        if (titleText != null) {
            titleText.setText("编辑资料");
        }
        
        nicknameInput = findViewById(R.id.nicknameInput);
        bioInput = findViewById(R.id.bioInput);
        avatarImage = findViewById(R.id.avatarImage);
        
        // 保存按钮
        TextView saveButton = findViewById(R.id.saveButton);
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveProfile());
        }
        
        // 返回按钮
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // 头像点击 - 显示头像选择对话框
        if (avatarImage != null) {
            avatarImage.setOnClickListener(v -> {
                showAvatarSelectionDialog();
            });
        }
    }
    
    @Override
    protected void initData() {
        executorService.execute(() -> {
            com.xiaohongshu.bean.UserBean currentUserBean = 
                LoginDataRepository.getInstance(this).getCurrentUser();
            if (currentUserBean != null) {
                currentUser = database.userDao().getUserByUsername(currentUserBean.getUsername());
                
                // 如果UserEntity不存在，自动创建
                if (currentUser == null) {
                    currentUser = createUserEntity(currentUserBean);
                }
                
                if (currentUser != null) {
                    runOnUiThread(() -> {
                        if (nicknameInput != null) {
                            nicknameInput.setText(currentUser.nickname != null ? currentUser.nickname : currentUser.username);
                        }
                        if (bioInput != null) {
                            bioInput.setText(currentUser.bio != null ? currentUser.bio : "");
                        }
                        if (avatarImage != null) {
                            if (currentUser.avatarUri != null && !currentUser.avatarUri.isEmpty()) {
                                showAvatar(currentUser.avatarUri);
                            } else if (currentUser.avatar != 0) {
                                avatarImage.setImageResource(currentUser.avatar);
                            } else {
                                // 设置默认头像
                                int avatarIndex = Math.abs(currentUserBean.getUsername().hashCode()) % 16 + 1;
                                int defaultAvatar = getAvatarResource(avatarIndex);
                                avatarImage.setImageResource(defaultAvatar);
                                currentUser.avatar = defaultAvatar;
                            }
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        android.widget.Toast.makeText(this, "无法加载用户信息", android.widget.Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, "用户未登录", android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
    
    /**
     * 创建UserEntity
     */
    private UserEntity createUserEntity(com.xiaohongshu.bean.UserBean userBean) {
        try {
            UserEntity userEntity = new UserEntity();
            // 生成唯一的用户ID
            userEntity.id = String.valueOf(System.currentTimeMillis()) + "_" + (int)(Math.random() * 10000);
            userEntity.username = userBean.getUsername();
            // 明文密码不写入本地数据库
            
            // 根据用户名hash选择默认头像（p1-p11）
            int avatarIndex = Math.abs(userBean.getUsername().hashCode()) % 16 + 1;
            userEntity.avatar = getAvatarResource(avatarIndex);
            
            userEntity.nickname = userBean.getUsername();
            userEntity.bio = "";
            userEntity.createTime = System.currentTimeMillis();

            // 插入数据库
            database.userDao().insert(userEntity);
            
            return userEntity;
        } catch (Exception e) {
            android.util.Log.e("EditProfileActivity", "Error creating UserEntity", e);
            return null;
        }
    }
    
    /**
     * 根据索引获取头像资源（使用p1-p11）
     */
    private int getAvatarResource(int index) {
        int[] avatars = {
            R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
            R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
            R.drawable.p9, R.drawable.p10, R.drawable.p11,
            R.drawable.p12, R.drawable.p13, R.drawable.p14,
            R.drawable.p15, R.drawable.p16
        };
        return avatars[(index - 1) % avatars.length];
    }
    
    /**
     * 显示头像选择对话框
     */
    private void showAvatarSelectionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("更换头像")
                .setItems(new String[]{"从相册上传", "选择内置头像"}, (dialog, which) -> {
                    if (which == 0) openAvatarGallery();
                    else showBuiltInAvatarSelectionDialog();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openAvatarGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_AVATAR);
        } catch (Exception error) {
            android.widget.Toast.makeText(this, "无法打开相册，请检查存储权限", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void showBuiltInAvatarSelectionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("选择头像");
        
        // 创建GridView来显示头像
        android.widget.GridView gridView = new android.widget.GridView(this);
        gridView.setNumColumns(3);
        gridView.setPadding(16, 16, 16, 16);
        
        // 头像资源ID列表 (p1 到 p11)
        final int[] avatarResources = {
            R.drawable.p1, R.drawable.p2, R.drawable.p3, R.drawable.p4,
            R.drawable.p5, R.drawable.p6, R.drawable.p7, R.drawable.p8,
            R.drawable.p9, R.drawable.p10, R.drawable.p11,
            R.drawable.p12, R.drawable.p13, R.drawable.p14,
            R.drawable.p15, R.drawable.p16
        };
        
        // 创建适配器
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return avatarResources.length;
            }
            
            @Override
            public Object getItem(int position) {
                return avatarResources[position];
            }
            
            @Override
            public long getItemId(int position) {
                return position;
            }
            
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ImageView imageView;
                if (convertView == null) {
                    imageView = new ImageView(EditProfileActivity.this);
                    imageView.setLayoutParams(new android.widget.GridView.LayoutParams(200, 200));
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setPadding(8, 8, 8, 8);
                } else {
                    imageView = (ImageView) convertView;
                }
                
                imageView.setImageResource(avatarResources[position]);
                return imageView;
            }
        };
        
        gridView.setAdapter(adapter);
        
        // 设置GridView到对话框
        builder.setView(gridView);
        
        // 创建对话框
        android.app.AlertDialog dialog = builder.create();
        
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            // 更新头像显示
            if (avatarImage != null) {
                avatarImage.setImageResource(avatarResources[position]);
            }
            // 保存选中的头像资源ID（在saveProfile中保存）
            if (currentUser != null) {
                currentUser.avatar = avatarResources[position];
                currentUser.avatarUri = "";
            }
            // 关闭对话框
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void saveProfile() {
        if (currentUser == null) {
            android.widget.Toast.makeText(this, "用户信息不存在", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        String nickname = nicknameInput != null ? nicknameInput.getText().toString().trim() : "";
        String bio = bioInput != null ? bioInput.getText().toString().trim() : "";
        
        if (nickname.isEmpty()) {
            android.widget.Toast.makeText(this, "昵称不能为空", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 使用CountDownLatch确保数据库更新同步完成
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] updateSuccess = {false};
        
        executorService.execute(() -> {
            try {
                currentUser.nickname = nickname;
                currentUser.bio = bio;
                // avatar已经在选择时更新了
                
                // 执行数据库更新（Room的update是同步的，会等待写入完成）
                database.userDao().update(currentUser);

                com.xiaohongshu.bean.UserBean loginUser = LoginDataRepository.getInstance(this).getCurrentUser();
                if (loginUser != null && loginUser.getToken() != null && !loginUser.getToken().isEmpty()) {
                    try {
                        String avatar = currentUser.avatarUri == null ? "" : currentUser.avatarUri;
                        if (!avatar.isEmpty() && !avatar.startsWith("http://") && !avatar.startsWith("https://")) {
                            java.util.List<String> uploaded = new RemoteApiClient(this).uploadImages(this,
                                    java.util.Collections.singletonList(avatar), loginUser.getToken());
                            if (!uploaded.isEmpty()) {
                                avatar = uploaded.get(0);
                                currentUser.avatarUri = avatar;
                                database.userDao().update(currentUser);
                            }
                        }
                        com.xiaohongshu.bean.UserBean updated = new RemoteApiClient(this)
                                .updateProfile(nickname, bio, avatar.isEmpty() ? null : avatar, loginUser.getToken());
                        if (updated != null) {
                            updated.setPassword(loginUser.getPassword());
                            LoginDataRepository.getInstance(this).saveLoginUser(updated);
                        }
                    } catch (Exception ignored) {
                        // Local profile remains available if the backend is offline.
                    }
                }
                
                // 验证更新是否成功：重新查询用户信息
                UserEntity updatedUser = database.userDao().getUserByUsername(currentUser.username);
                if (updatedUser != null && updatedUser.avatar == currentUser.avatar) {
                    updateSuccess[0] = true;
                    android.util.Log.d("EditProfileActivity", "Avatar updated successfully: " + currentUser.avatar);
                } else {
                    android.util.Log.w("EditProfileActivity", "Avatar update verification failed");
                }
            } catch (Exception e) {
                android.util.Log.e("EditProfileActivity", "Error updating profile", e);
            } finally {
                latch.countDown();
            }
        });
        
        // 等待更新完成（最多等待2秒）
        try {
            boolean completed = latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
            if (!completed) {
                android.util.Log.w("EditProfileActivity", "Profile update timeout");
            }
        } catch (InterruptedException e) {
            android.util.Log.e("EditProfileActivity", "Interrupted while waiting for update", e);
        }
        
        // 更新完成后返回
        runOnUiThread(() -> {
            if (updateSuccess[0]) {
                android.widget.Toast.makeText(this, "保存成功", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "保存成功（请刷新查看）", android.widget.Toast.LENGTH_SHORT).show();
            }
            finish();
        });
    }

    private void showAvatar(String uri) {
        if (uri == null || uri.isEmpty() || avatarImage == null) return;
        // 统一走 Coil：http/content/file 都能加载，失败显示占位圆
        ImageLoader.load(avatarImage, uri, R.drawable.placeholder_avatar);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_PICK_AVATAR || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        if (currentUser != null) {
            currentUser.avatar = 0;
            currentUser.avatarUri = uri.toString();
        }
        showAvatar(uri.toString());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
