package com.xiaohongshu.network;

import java.io.IOException;

/** Exception returned by the optional Express backend. */
public class RemoteApiException extends IOException {
    private final int statusCode;

    public RemoteApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
