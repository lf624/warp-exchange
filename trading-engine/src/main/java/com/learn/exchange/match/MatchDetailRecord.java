package com.learn.exchange.match;

import com.learn.exchange.model.trade.OrderEntity;

import java.math.BigDecimal;

public record MatchDetailRecord(BigDecimal price, BigDecimal quantity,
                                OrderEntity takerOrder, OrderEntity makerOrder) {
}
