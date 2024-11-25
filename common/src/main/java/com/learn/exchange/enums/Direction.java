package com.learn.exchange.enums;

public enum Direction {
    // 买入：USD -> BTC，出售：BTC -> USD
    BUY(1), SELL(0);

    public final int value;

    Direction(int value) {
        this.value = value;
    }

    // get negate direction
    public Direction negate() {
        return this == BUY ? SELL : BUY;
    }

    public Direction of(int intValue) {
        if(intValue == 1)
            return BUY;
        if(intValue == 0)
            return SELL;
        throw new IllegalArgumentException("Invalid Direction value.");
    }
}
