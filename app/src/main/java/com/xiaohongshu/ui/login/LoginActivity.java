package com.xiaohongshu.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.xiaohongshu.MainActivity;
import java.lang.reflect.Method;
import com.xiaohongshu.R;
import com.xiaohongshu.base.BaseActivity;
import com.xiaohongshu.ui.login.viewmodel.LoginState;
import com.xiaohongshu.ui.login.viewmodel.LoginViewModel;

public class LoginActivity extends BaseActivity {
    private LoginViewModel viewModel;
    private LoginDataRepository repository;
    private boolean isLoginMode = true;
    
    // Login views
    private TextInputLayout usernameLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText usernameEdit;
    private TextInputEditText passwordEdit;
    private TextView errorText;
    private MaterialButton loginButton;
    private TextView registerLink;
    private ProgressBar loadingProgress;
    
    // Register views
    private TextInputLayout registerUsernameLayout;
    private TextInputLayout registerPasswordLayout;
    private TextInputLayout confirmPasswordLayout;
    private TextInputEditText registerUsernameEdit;
    private TextInputEditText registerPasswordEdit;
    private TextInputEditText confirmPasswordEdit;
    private TextView registerErrorText;
    private MaterialButton registerButton;
    private TextView loginLink;
    private ProgressBar registerLoadingProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        repository = LoginDataRepository.getInstance(this);

        // 登录态自动恢复：本地已保存有效 token 时直接进入主页
        com.xiaohongshu.bean.UserBean savedUser = repository.getCurrentUser();
        if (savedUser != null && savedUser.getToken() != null && !savedUser.getToken().isEmpty()) {
            jumpToMainActivity();
            finish();
            return;
        }

        setupLoginViews();
        observeViewModel();
    }

    @Override
    protected void initViews() {
        // Views are set up in onCreate
    }

    @Override
    protected void initData() {
        // Data initialization done in onCreate
    }

    private void setupLoginViews() {
        setContentView(R.layout.activity_login);
        
        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        usernameEdit = findViewById(R.id.usernameEdit);
        passwordEdit = findViewById(R.id.passwordEdit);
        errorText = findViewById(R.id.errorText);
        loginButton = findViewById(R.id.loginButton);
        registerLink = findViewById(R.id.registerLink);
        loadingProgress = findViewById(R.id.loadingProgress);
        
        // Setup input fields with red border and rounded corners
        // Use post to ensure views are fully laid out
        usernameLayout.post(() -> setupInputField(usernameLayout, usernameEdit));
        passwordLayout.post(() -> setupInputField(passwordLayout, passwordEdit));

        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                String username = usernameEdit != null && usernameEdit.getText() != null 
                    ? usernameEdit.getText().toString() : "";
                String password = passwordEdit != null && passwordEdit.getText() != null 
                    ? passwordEdit.getText().toString() : "";
                viewModel.login(username, password, repository);
            });
        }

        if (registerLink != null) {
            registerLink.setOnClickListener(v -> {
                isLoginMode = false;
                setupRegisterViews();
            });
        }
    }

    private void setupRegisterViews() {
        setContentView(R.layout.activity_register);

        registerUsernameLayout = findViewById(R.id.usernameLayout);
        registerPasswordLayout = findViewById(R.id.passwordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        registerUsernameEdit = findViewById(R.id.usernameEdit);
        registerPasswordEdit = findViewById(R.id.passwordEdit);
        confirmPasswordEdit = findViewById(R.id.confirmPasswordEdit);
        registerErrorText = findViewById(R.id.errorText);
        registerButton = findViewById(R.id.registerButton);
        loginLink = findViewById(R.id.loginLink);
        registerLoadingProgress = findViewById(R.id.loadingProgress);
        
        // Setup input fields with red border and rounded corners
        // Use post to ensure views are fully laid out
        registerUsernameLayout.post(() -> setupInputField(registerUsernameLayout, registerUsernameEdit));
        registerPasswordLayout.post(() -> setupInputField(registerPasswordLayout, registerPasswordEdit));
        confirmPasswordLayout.post(() -> setupInputField(confirmPasswordLayout, confirmPasswordEdit));

        if (registerButton != null) {
            registerButton.setOnClickListener(v -> {
                String username = registerUsernameEdit != null && registerUsernameEdit.getText() != null 
                    ? registerUsernameEdit.getText().toString() : "";
                String password = registerPasswordEdit != null && registerPasswordEdit.getText() != null 
                    ? registerPasswordEdit.getText().toString() : "";
                String confirmPassword = confirmPasswordEdit != null && confirmPasswordEdit.getText() != null 
                    ? confirmPasswordEdit.getText().toString() : "";
                viewModel.register(username, password, confirmPassword, repository);
            });
        }

        if (loginLink != null) {
            loginLink.setOnClickListener(v -> {
                isLoginMode = true;
                setupLoginViews();
            });
        }
    }

    private void observeViewModel() {
        viewModel.getLoginState().observe(this, state -> {
            if (state == null) return;

            if (isLoginMode) {
                handleLoginState(state);
            } else {
                handleRegisterState(state);
            }
        });
    }

    private void handleLoginState(LoginState state) {
        if (state instanceof LoginState.Loading) {
            if (errorText != null) errorText.setVisibility(View.GONE);
            if (loginButton != null) {
                loginButton.setEnabled(false);
                loginButton.setText("");
            }
            if (loadingProgress != null) loadingProgress.setVisibility(View.VISIBLE);
        } else if (state instanceof LoginState.Error) {
            String message = ((LoginState.Error) state).getMessage();
            if (errorText != null) {
                errorText.setText(message);
                errorText.setVisibility(View.VISIBLE);
            }
            if (loginButton != null) {
                loginButton.setEnabled(true);
                loginButton.setText(R.string.login);
            }
            if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
        } else if (state instanceof LoginState.Success) {
            if (errorText != null) errorText.setVisibility(View.GONE);
            if (loginButton != null) {
                loginButton.setEnabled(true);
                loginButton.setText(R.string.login);
            }
            if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
            jumpToMainActivity();
        } else {
            if (errorText != null) errorText.setVisibility(View.GONE);
            if (loginButton != null) {
                loginButton.setEnabled(true);
                loginButton.setText(R.string.login);
            }
            if (loadingProgress != null) loadingProgress.setVisibility(View.GONE);
        }
    }

    private void handleRegisterState(LoginState state) {
        if (state instanceof LoginState.Loading) {
            if (registerErrorText != null) registerErrorText.setVisibility(View.GONE);
            if (registerButton != null) {
                registerButton.setEnabled(false);
                registerButton.setText("");
            }
            if (registerLoadingProgress != null) registerLoadingProgress.setVisibility(View.VISIBLE);
        } else if (state instanceof LoginState.Error) {
            String message = ((LoginState.Error) state).getMessage();
            if (registerErrorText != null) {
                registerErrorText.setText(message);
                registerErrorText.setVisibility(View.VISIBLE);
            }
            if (registerButton != null) {
                registerButton.setEnabled(true);
                registerButton.setText(R.string.register);
            }
            if (registerLoadingProgress != null) registerLoadingProgress.setVisibility(View.GONE);
        } else if (state instanceof LoginState.Success) {
            if (registerErrorText != null) registerErrorText.setVisibility(View.GONE);
            if (registerButton != null) {
                registerButton.setEnabled(true);
                registerButton.setText(R.string.register);
            }
            if (registerLoadingProgress != null) registerLoadingProgress.setVisibility(View.GONE);
            // Switch to login mode after successful registration
            isLoginMode = true;
            setupLoginViews();
        } else {
            if (registerErrorText != null) registerErrorText.setVisibility(View.GONE);
            if (registerButton != null) {
                registerButton.setEnabled(true);
                registerButton.setText(R.string.register);
            }
            if (registerLoadingProgress != null) registerLoadingProgress.setVisibility(View.GONE);
        }
    }

    private void jumpToMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
    
    private void updateBorderColor(TextInputLayout layout, TextInputEditText editText, int redColor, int pinkColor) {
        if (layout == null || editText == null) return;
        
        boolean hasFocus = editText.hasFocus() || editText.isFocused();
        int targetColor = hasFocus ? pinkColor : redColor;
        
        // Set border color
        layout.setBoxStrokeColor(targetColor);
        
        // Force multiple updates to ensure it takes effect
        layout.post(() -> {
            layout.setBoxStrokeColor(targetColor);
            layout.invalidate();
            layout.requestLayout();
        });
        
        // Also update after a short delay to override any system updates
        layout.postDelayed(() -> {
            layout.setBoxStrokeColor(targetColor);
            layout.invalidate();
        }, 100);
        
        Log.d("LoginActivity", "Border color updated - hasFocus: " + hasFocus + ", color: " + Integer.toHexString(targetColor));
    }
    
    private void setupInputField(TextInputLayout layout, TextInputEditText editText) {
        if (layout == null || editText == null) return;
        
        int redColor = ContextCompat.getColor(this, R.color.xhs_red);
        int pinkColor = ContextCompat.getColor(this, R.color.xhs_pink);
        
        // Force boxBackgroundMode to outline
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        // Set initial border color to red
        layout.setBoxStrokeColor(redColor);
        layout.setBoxStrokeWidth(2);
        layout.setBoxStrokeWidthFocused(2);
        
        // Adjust hint text size - use reflection to access internal collapsedTextHeight field
        try {
            // Try to set collapsed text height using reflection
            java.lang.reflect.Field collapsedTextHeightField = TextInputLayout.class.getDeclaredField("collapsedTextHeight");
            collapsedTextHeightField.setAccessible(true);
            // Set to a larger value (in pixels) - 16sp = about 24px on most devices
            float textSize = getResources().getDisplayMetrics().scaledDensity * 16;
            collapsedTextHeightField.setInt(layout, (int) textSize);
            Log.d("LoginActivity", "Set collapsedTextHeight to: " + textSize);
        } catch (NoSuchFieldException e) {
            // Try alternative field name
            try {
                java.lang.reflect.Field collapsedTextHeightField = TextInputLayout.class.getDeclaredField("mCollapsedTextHeight");
                collapsedTextHeightField.setAccessible(true);
                float textSize = getResources().getDisplayMetrics().scaledDensity * 16;
                collapsedTextHeightField.setInt(layout, (int) textSize);
                Log.d("LoginActivity", "Set mCollapsedTextHeight to: " + textSize);
            } catch (Exception e2) {
                Log.w("LoginActivity", "Could not set collapsedTextHeight: " + e2.getMessage());
            }
        } catch (Exception e) {
            Log.w("LoginActivity", "Could not set collapsedTextHeight: " + e.getMessage());
        }
        
        // Set rounded corners
        try {
            layout.setBoxCornerRadii(12f, 12f, 12f, 12f);
        } catch (Exception e) {
            Log.w("LoginActivity", "Could not set corner radius: " + e.getMessage());
        }
        
        // Setup focus listener - this is the key to changing border color
        // Also listen on the layout itself for better reliability
        layout.setOnFocusChangeListener((v, hasFocus) -> {
            updateBorderColor(layout, editText, redColor, pinkColor);
        });
        
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            updateBorderColor(layout, editText, redColor, pinkColor);
        });
        
        // Also listen for click events
        editText.setOnClickListener(v -> {
            updateBorderColor(layout, editText, redColor, pinkColor);
        });
        
        // Also update when text changes
        editText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (editText.hasFocus()) {
                    layout.setBoxStrokeColor(pinkColor);
                } else {
                    layout.setBoxStrokeColor(redColor);
                }
            }
        });
    }
}

