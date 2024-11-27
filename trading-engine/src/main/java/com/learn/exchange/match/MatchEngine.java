package com.learn.exchange.match;

import com.learn.exchange.bean.OrderBookBean;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.OrderStatus;
import com.learn.exchange.model.trade.OrderEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MatchEngine {
    public final OrderBook buyBook = new OrderBook(Direction.BUY);
    public final OrderBook sellBook = new OrderBook(Direction.SELL);
    public BigDecimal marketPrice = BigDecimal.ZERO; // 最新市场价
    private long sequenceId; // 上次处理的Sequence ID

    public MatchResult processOrder(long sequenceId, OrderEntity order) {
        return switch (order.direction) {
            case BUY -> processOrder(sequenceId, order, this.sellBook, this.buyBook);
            case SELL -> processOrder(sequenceId, order, this.buyBook, this.sellBook);
            default -> throw new IllegalArgumentException("Invalid direction.");
        };
    }

    private MatchResult processOrder(long sequenceId, OrderEntity takerOrder,
                                     OrderBook makerBook, OrderBook anotherBook) {
        this.sequenceId = sequenceId;
        long ts = takerOrder.createdAt;
        MatchResult matchResult = new MatchResult(takerOrder);
        BigDecimal takerUnfilledQuantity = takerOrder.quantity; // 待处理的订单剩余的数量
        for(;;) {
            OrderEntity makerOrder = makerBook.getFirst();
            if(makerOrder == null)
                break; // 没有对手盘
            if(takerOrder.direction == Direction.BUY && takerOrder.price.compareTo(makerOrder.price) < 0)
                break; // 买入订单价格比卖盘第一档价格低
            if(takerOrder.direction == Direction.SELL && takerOrder.price.compareTo(makerOrder.price) > 0)
                break; // 卖出订单价格比买盘第一档价格高
            // 以Maker价格成交
            this.marketPrice = makerOrder.price;
            // 待成交数量为两者较小值
            BigDecimal matchedQuantity = takerUnfilledQuantity.min(makerOrder.unfilledQuantity);
            // 成交记录
            matchResult.add(makerOrder.price, matchedQuantity, makerOrder);
            // 更新剩余订单数量
            takerUnfilledQuantity = takerUnfilledQuantity.subtract(matchedQuantity);
            BigDecimal markerUnfilledQuantity = makerOrder.unfilledQuantity.subtract(matchedQuantity);
            if(markerUnfilledQuantity.signum() == 0) {
                // 对手盘完全成交后，从订单簿删除
                makerOrder.updateOrder(markerUnfilledQuantity, OrderStatus.FULLY_FILLED, ts);
                makerBook.remove(makerOrder);
            } else {
                // 对手盘部分成交
                makerOrder.updateOrder(markerUnfilledQuantity, OrderStatus.PARTIAL_FILLED, ts);
            }
            if(takerUnfilledQuantity.signum() == 0) {
                // Taker 订单完全成交，退出循环
                takerOrder.updateOrder(takerUnfilledQuantity, OrderStatus.FULLY_FILLED, ts);
                break;
            }
        }
        if(takerUnfilledQuantity.signum() > 0) {
            // Taker 订单部分成交，放入订单簿
            takerOrder.updateOrder(takerUnfilledQuantity,
                    takerUnfilledQuantity.compareTo(takerOrder.quantity) == 0 // 不要使用 equals()
                    ? OrderStatus.PENDING : OrderStatus.PARTIAL_FILLED, ts);
            anotherBook.add(takerOrder);
        }
        return matchResult;
    }

    public void cancel(long ts, OrderEntity order) {
        OrderBook book = order.direction == Direction.BUY ? this.buyBook : this.sellBook;
        if(!book.remove(order))
            throw new IllegalArgumentException("Order not found in order book: " + order);
        OrderStatus status = order.unfilledQuantity.compareTo(order.quantity) == 0 ?
                OrderStatus.FULLY_CANCELLED : OrderStatus.PARTIAL_CANCELLED;
        order.updateOrder(order.unfilledQuantity, status, ts);
    }

    public OrderBookBean getOrderBook(int maxDepth) {
        return new OrderBookBean(this.sequenceId, this.marketPrice,
                this.buyBook.getOrderBook(maxDepth), this.sellBook.getOrderBook(maxDepth));
    }
}
