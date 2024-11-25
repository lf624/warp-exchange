package com.learn.exchange.enums;

public enum OrderStatus {
    // 等待成交 (unfilledQuantity == quantity)
    PENDING(false),

    // 完全成交 (unfilledQuantity = 0)
    FULLY_FILLED(true),

    // 部分成交 (quantity > unfilledQuantity > 0)
    PARTIAL_FILLED(false),

    // 部分成交后取消 (quantity > unfilledQuantity > 0)
    PARTIAL_CANCELLED(true),

    // 完全取消 (unfilledQuantity == quantity)
    FULLY_CANCELLED(true);

    // 订单是否处理完成
    public final boolean isFinalStatus;

    OrderStatus(boolean status) {
        this.isFinalStatus = status;
    }
}
