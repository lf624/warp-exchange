package com.learn.exchange.match;

import java.math.BigDecimal;

// 订单的顺序由价格和 sequenceId 决定
public record OrderKey(long sequenceId, BigDecimal price) {
}
