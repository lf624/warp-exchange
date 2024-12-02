package com.learn.exchange.message.event;

import com.learn.exchange.enums.Direction;

import java.math.BigDecimal;

// 订单请求事件
public class OrderRequestEvent extends AbstractEvent{

    public Long userId;
    public Direction direction;
    public BigDecimal price;
    public BigDecimal quantity;

    @Override
    public String toString() {
        return "OrderRequestEvent [sequenceId=" + sequenceId + ", previousId=" + previousId + ", uniqueId=" + uniqueId
                + ", refId=" + refId + ", createdAt=" + createdAt + ", userId=" + userId + ", direction=" + direction
                + ", price=" + price + ", quantity=" + quantity + "]";
    }
}
