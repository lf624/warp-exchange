package com.learn.exchange.order;

import com.learn.exchange.assets.AssetService;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.model.trade.OrderEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OrderService {
    final AssetService assetService;

    public OrderService(@Autowired AssetService assetService) {
        this.assetService = assetService;
    }

    // 跟踪所有活动订单: Order ID => OrderEntity
    final ConcurrentMap<Long, OrderEntity> activeOrders = new ConcurrentHashMap<>();
    // 跟踪用户活动订单: User ID => Map(Order ID => OrderEntity)
    final ConcurrentMap<Long, ConcurrentMap<Long, OrderEntity>> userOrders =
            new ConcurrentHashMap<>();

    // 创建订单，失败返回 null
    public OrderEntity createOrder(long sequenceId, long ts, Long orderId, Long userId, Direction direction,
                                   BigDecimal price, BigDecimal quantity) {
        switch (direction) {
            case BUY -> {
                if(!assetService.tryFreeze(userId, AssetEnum.USD, price.multiply(quantity)))
                    return null;
            }
            case SELL -> {
                if(!assetService.tryFreeze(userId, AssetEnum.BTC, quantity))
                    return null;
            }
            default -> throw new IllegalArgumentException("Invalid Direction type.");
        };
        OrderEntity order = new OrderEntity();
        order.id = orderId;
        order.sequenceId = sequenceId;
        order.userId = userId;
        order.price = price;
        order.direction = direction;
        order.quantity = quantity;
        order.unfilledQuantity = quantity;
        order.createdAt = order.updatedAt = ts;
        // 添加到 ActiveOrders
        this.activeOrders.put(orderId, order);
        // 添加到 UserOrders
        ConcurrentMap<Long, OrderEntity> uOrders = this.userOrders.computeIfAbsent(userId,
                k -> new ConcurrentHashMap<>());
        uOrders.put(orderId, order);
        return order;
    }
    // 删除订单
    public void removeOrder(Long orderId) {
        OrderEntity removed = this.activeOrders.remove(orderId);
        if(removed == null)
            throw new IllegalArgumentException("Order '" + orderId + "' not found in active orders.");
        ConcurrentMap<Long, OrderEntity> uOrders = this.userOrders.get(removed.userId);
        if(uOrders == null)
            throw new IllegalArgumentException("User orders not found by userId: " + removed.userId);
        if(uOrders.remove(orderId) == null)
            throw new IllegalArgumentException("Order not found by orderId in user orders: " + orderId);
    }

    // 查询所有活动订单
    public ConcurrentMap<Long, OrderEntity> getActiveOrders() {
        return this.activeOrders;
    }
    // 根据订单ID查询Order，不存在返回null
    public OrderEntity getOrder(Long orderId) {
        return this.activeOrders.get(orderId);
    }
    // 根据用户ID查询用户所有活动Order，不存在返回null
    public ConcurrentMap<Long, OrderEntity> getUserOrders(Long userId) {
        return this.userOrders.get(userId);
    }
}
