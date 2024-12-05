package com.learn.exchange.web.api;

import com.learn.exchange.assets.Asset;
import com.learn.exchange.assets.AssetService;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.message.event.TransferEvent;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.order.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

@RestController
@RequestMapping("/internal")
public class InternalTradingEngineApiController {

    @Autowired
    OrderService orderService;
    @Autowired
    AssetService assetService;

    @GetMapping("/{userId}/assets")
    public Map<AssetEnum, Asset> getUserAssets(@PathVariable("userId") Long userId) {
        return assetService.getAssets(userId);
    }

    @GetMapping("/{userId}/orders")
    public List<OrderEntity> getUserOrders(@PathVariable("userId") Long userId) {
        ConcurrentMap<Long, OrderEntity> orders = orderService.getUserOrders(userId);
        if(orders == null || orders.isEmpty())
            return List.of();
        List<OrderEntity> result = new ArrayList<>();
        for(OrderEntity order : orders.values()) {
            OrderEntity copy = null;
            while(copy == null) {
                copy = order.copy();
            }
            result.add(copy);
        }
        return result;
    }

    @GetMapping("/{userId}/orders/{orderId}")
    public OrderEntity getUserOrder(@PathVariable("userId") Long userId, @PathVariable("orderId") Long orderId) {
        OrderEntity order = orderService.getOrder(orderId);
        if(order == null || order.userId.longValue() != userId.longValue()) {
            return null;
        }
        return order.copy();
    }

}
