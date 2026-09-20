package com.xiaohongshu.ui.login.viewmodel;

import com.xiaohongshu.bean.UserBean;

/**
 * Login state sealed class equivalent
 */
public abstract class LoginState {
    public static class Idle extends LoginState {}
    public static class Loading extends LoginState {}
    public static class Success extends LoginState {
        private final UserBean user;
        public Success(UserBean user) {
            this.user = user;
        }
        public UserBean getUser() {
            return user;
        }
    }
    public static class Error extends LoginState {
        private final String message;
        public Error(String message) {
            this.message = message;
        }
        public String getMessage() {
            return message;
        }
    }
}

