package com.xiaohongshu.ui.login.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.xiaohongshu.bean.UserBean;
import com.xiaohongshu.ui.login.LoginDataRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginViewModel extends ViewModel {
    private final MutableLiveData<LoginState> loginState = new MutableLiveData<>(new LoginState.Idle());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private LoginDataRepository repository;

    public LiveData<LoginState> getLoginState() {
        return loginState;
    }

    public void setRepository(LoginDataRepository repository) {
        this.repository = repository;
    }

    public void login(String username, String password, LoginDataRepository repository) {
        loginState.postValue(new LoginState.Loading());
        executorService.execute(() -> {
            try {
                UserBean user = repository.login(username, password);
                loginState.postValue(new LoginState.Success(user));
            } catch (Exception e) {
                loginState.postValue(new LoginState.Error(e.getMessage() != null ? e.getMessage() : "登录失败"));
            }
        });
    }

    public void register(String username, String password, String confirmPassword, LoginDataRepository repository) {
        loginState.postValue(new LoginState.Loading());
        executorService.execute(() -> {
            try {
                UserBean user = repository.register(username, password, confirmPassword);
                loginState.postValue(new LoginState.Success(user));
            } catch (Exception e) {
                loginState.postValue(new LoginState.Error(e.getMessage() != null ? e.getMessage() : "注册失败"));
            }
        });
    }

    public void resetLoginState() {
        loginState.postValue(new LoginState.Idle());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}

