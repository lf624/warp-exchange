package com.learn.exchange.ctx;

import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import jakarta.annotation.Nullable;

// Holds user context in thread-local
public class UserContext implements AutoCloseable {
    static final ThreadLocal<Long> THREAD_LOCAL_CTX = new ThreadLocal<>();

    public UserContext(Long userId) {
        THREAD_LOCAL_CTX.set(userId);
    }

    public static Long getRequiredUserId() {
        Long userId = getUserId();
        if(userId == null)
            throw new ApiException(ApiError.AUTH_SIGNIN_REQUIRED, null, "need sign in first.");
        return userId;
    }

    @Nullable
    public static Long getUserId() {
        return THREAD_LOCAL_CTX.get();
    }

    @Override
    public void close() {
        THREAD_LOCAL_CTX.remove();
    }
}
