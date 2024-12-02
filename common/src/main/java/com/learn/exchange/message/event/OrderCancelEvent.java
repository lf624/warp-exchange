package com.learn.exchange.message.event;

// 取消订单事件
public class OrderCancelEvent extends AbstractEvent {
    // 用户id
    public Long userId;
    // 引用订单id，应属于该用户
    public Long refOrderId;

    @Override
    public String toString() {
        return "OrderCancelEvent [sequenceId=" + sequenceId + ", previousId=" + previousId + ", uniqueId=" + uniqueId
                + ", refId=" + refId + ", createdAt=" + createdAt + ", userId=" + userId + ", refOrderId=" + refOrderId
                + "]";
    }
}
