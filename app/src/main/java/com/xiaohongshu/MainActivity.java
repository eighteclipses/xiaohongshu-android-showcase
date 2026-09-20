package com.xiaohongshu;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xiaohongshu.databinding.ActivityMainBinding;
import com.xiaohongshu.ui.publish.NoteEditActivity;

public class MainActivity extends AppCompatActivity {
    
    private ActivityMainBinding binding;
    private boolean publishIconSet = false;
    
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 会话滑动续期：启动时用当前 token 换新，避免用户在使用中被判定过期（后台静默执行）
        new Thread(() -> com.xiaohongshu.ui.login.LoginDataRepository
                .getInstance(getApplicationContext()).refreshSessionToken()).start();
        
        androidx.navigation.NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);
        
        BottomNavigationView navView = binding.navView;
        android.view.Menu menu = navView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            android.view.MenuItem item = menu.getItem(i);
            if (item.getItemId() == R.id.navigation_publish) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    item.setIconTintList(null);
                }
            }
        }
        
        Runnable setPublishIcon = new Runnable() {
            @Override
            public void run() {
                if (publishIconSet) {
                    return; // 已经设置过，避免重复执行
                }
                try {
                    android.view.MenuItem publishItem = menu.findItem(R.id.navigation_publish);
                    if (publishItem != null) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            publishItem.setIconTintList(null);
                        }
                    }
                    
                    ViewGroup menuView = (ViewGroup) navView.getChildAt(0);
                    if (menuView != null && menuView.getChildCount() > 2) {
                        View publishView = menuView.getChildAt(2);
                        if (publishView != null) {
                            ImageView iconView = findImageView(publishView);
                            if (iconView != null) {
                                iconView.clearColorFilter();
                                iconView.setColorFilter(null);
                                
                                android.graphics.drawable.Drawable icon = getResources().getDrawable(R.drawable.icon_plus_red_new, getTheme());
                                if (icon != null) {
                                    iconView.setImageDrawable(icon);
                                    publishIconSet = true; // 标记已设置成功
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        
        // 只使用一次post，避免多次延迟调用
        navView.post(() -> {
            setPublishIcon.run();
            // 如果第一次设置失败，再尝试一次（布局可能还未完成）
            if (!publishIconSet) {
                navView.postDelayed(setPublishIcon, 200);
            }
        });
        
        // 优化OnGlobalLayoutListener，只在布局完成且图标未设置时执行一次
        android.view.ViewTreeObserver observer = navView.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (publishIconSet) {
                    // 已设置成功，移除监听器避免重复回调
                    navView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    return;
                }
                setPublishIcon.run();
                if (publishIconSet) {
                    navView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });
        
        navView.setItemOnTouchListener(R.id.navigation_publish, new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    showPublishOptionsDialog();
                }
                return true;
            }
        });
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        refreshMessageBadge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMessageBadge();
    }

    /**
     * 查询未读消息数并更新底部导航红点
     */
    public void refreshMessageBadge() {
        com.xiaohongshu.ui.message.MessageDataRepository repository =
                com.xiaohongshu.ui.message.MessageDataRepository.getInstance(this);
        repository.getUnreadCount(new com.xiaohongshu.ui.message.MessageDataRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (count != null && count > 0) {
                        binding.navView.getOrCreateBadge(R.id.navigation_message).setNumber(count);
                    } else {
                        binding.navView.removeBadge(R.id.navigation_message);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                // 查询失败时保持现状
            }
        });
    }

    /**
     * 清除底部导航消息红点（进入消息页后调用）
     */
    public void clearMessageBadge() {
        binding.navView.removeBadge(R.id.navigation_message);
    }

    private void showPublishOptionsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_publish_options, null);
        dialog.setContentView(dialogView);

        View cameraOption = dialogView.findViewById(R.id.cameraOption);
        View textOption = dialogView.findViewById(R.id.textOption);
        View cancelButton = dialogView.findViewById(R.id.cancelButton);

        if (cameraOption != null) {
            cameraOption.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
                startActivity(intent);
            });
        }

        if (textOption != null) {
            textOption.setOnClickListener(v -> {
                dialog.dismiss();
                startActivity(new Intent(MainActivity.this, com.xiaohongshu.ui.publish.WriteThoughtsActivity.class));
            });
        }

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
    
    private ImageView findImageView(View view) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ImageView imageView = findImageView(group.getChildAt(i));
                if (imageView != null) {
                    return imageView;
                }
            }
        }
        return null;
    }
}
