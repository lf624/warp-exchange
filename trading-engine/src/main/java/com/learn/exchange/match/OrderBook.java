package com.learn.exchange.match;

import com.learn.exchange.bean.OrderBookItemBean;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.model.trade.OrderEntity;

import java.util.*;

public class OrderBook {
    public final Direction direction;
    public final TreeMap<OrderKey, OrderEntity> book;

    public OrderBook(Direction direction) {
        this.direction = direction;
        this.book = new TreeMap<>(this.direction == Direction.BUY ? SORT_BUY : SORT_SELL);
    }

    public OrderEntity getFirst() {
        return book.isEmpty() ? null : book.firstEntry().getValue();
    }
    public boolean remove(OrderEntity order) {
        return this.book.remove(new OrderKey(order.sequenceId, order.price)) != null;
    }
    public boolean add(OrderEntity order) {
        return this.book.put(new OrderKey(order.sequenceId, order.price), order) == null;
    }
    public boolean exist(OrderEntity order) {
        return this.book.containsKey(new OrderKey(order.sequenceId, order.price));
    }
    public int size() { return this.book.size();}

    // 对买卖盘中相同价格的订单合并显示
    public List<OrderBookItemBean> getOrderBook(int maxDepth) {
        List<OrderBookItemBean> items = new ArrayList<>(maxDepth);
        OrderBookItemBean prevItem = null;
        for(OrderEntity order : this.book.values()) {
            if(prevItem == null) {
                prevItem = new OrderBookItemBean(order.price, order.unfilledQuantity);
                items.add(prevItem);
            } else {
                // order是有顺序的，相同价格的会在一起
                if (prevItem.price.compareTo(order.price) == 0)
                    prevItem.addQuantity(order.unfilledQuantity);
                else {
                    if(items.size() >= maxDepth)
                        break;
                    // 相同价格的 order 已合并完毕，找下一个
                    prevItem = new OrderBookItemBean(order.price, order.unfilledQuantity);
                    items.add(prevItem);
                }
            }
        }
        return items;
    }

    @Override
    public String toString() {
        if(this.book.isEmpty())
            return "(empty)";
        List<String> orders = new ArrayList<>(10);
        for(Map.Entry<OrderKey, OrderEntity> entry : this.book.entrySet()) {
            OrderEntity order = entry.getValue();
            orders.add(" " + order.price + " " + order.unfilledQuantity + " " + order);
        }
        if(direction == Direction.SELL)
            Collections.reverse(orders);
        return String.join("\n", orders);
    }

    // 定义买卖盘的订单排序规则
    private static final Comparator<OrderKey> SORT_SELL = new Comparator<OrderKey>() {
        @Override
        public int compare(OrderKey o1, OrderKey o2) {
            // 卖方价格低优先
            int cmp = o1.price().compareTo(o2.price());
            // 时间早在前
            return cmp == 0 ? Long.compare(o1.sequenceId(), o2.sequenceId()) : cmp;
        }
    };
    private static final Comparator<OrderKey> SORT_BUY = new Comparator<OrderKey>() {
        @Override
        public int compare(OrderKey o1, OrderKey o2) {
            // 买方价格高优先
            int cmp = o2.price().compareTo(o1.price());
            // 时间早在前
            return cmp == 0 ? Long.compare(o1.sequenceId(), o2.sequenceId()) : cmp;
        }
    };
}
