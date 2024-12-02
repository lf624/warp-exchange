package com.learn.exchange.enums;

public enum UserType {
    DEBT(1),
    TAKER(0);

    private final long userId;

    public long getInternalUserId() {
        return this.userId;
    }

    UserType(long userId) {
        this.userId = userId;
    }
}
