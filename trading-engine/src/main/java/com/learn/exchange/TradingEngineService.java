package com.learn.exchange;

import com.learn.exchange.assets.Asset;
import com.learn.exchange.assets.AssetService;
import com.learn.exchange.assets.Transfer;
import com.learn.exchange.bean.OrderBookBean;
import com.learn.exchange.clearing.ClearingService;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.enums.UserType;
import com.learn.exchange.match.MatchDetailRecord;
import com.learn.exchange.match.MatchEngine;
import com.learn.exchange.match.MatchResult;
import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.message.event.OrderCancelEvent;
import com.learn.exchange.message.event.OrderRequestEvent;
import com.learn.exchange.message.event.TransferEvent;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.order.OrderService;
import com.learn.exchange.support.LoggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Component
public class TradingEngineService extends LoggerSupport {
    @Autowired(required = false)
    ZoneId zoneId = ZoneId.systemDefault();

    // @Value("#{}")
    boolean debugMode = false;
    // @Value("#{}")
    int orderBookDepth = 100;

    boolean fatalError = false;

    @Autowired
    AssetService assetService;
    @Autowired
    OrderService orderService;
    @Autowired
    MatchEngine matchEngine;
    @Autowired
    ClearingService clearingService;

    // 上一个处理的事件的 sequenceId
    private long lastSequenceId = 0;
    // orderBook 是否发生了变化
    private boolean orderBookChanged = false;

    // 保存的最新orderBook快照
    private OrderBookBean lastedOrderBook = null;

    private Queue<List<OrderEntity>> orderQueue = new ConcurrentLinkedQueue<>();


    private void panic() {
        logger.error("application panic, exit now....");
        this.fatalError = true;
        System.exit(1);
    }

    public void processMessages(List<AbstractEvent> messages) {
        this.orderBookChanged = false;
        for(AbstractEvent message : messages)
            processEvent(message);
        if(orderBookChanged) {
            // 保存最新的快照
            this.lastedOrderBook = this.matchEngine.getOrderBook(this.orderBookDepth);
        }
    }

    public void processEvent(AbstractEvent event) {
        // 前置条件
        if(this.fatalError)
            return;
        if(event.sequenceId <= this.lastSequenceId) {
            // 事件发生重复
            logger.warn("skip duplicate event: " + event);
            return;
        }
        if(event.previousId > this.lastSequenceId) {
            // 事件发生丢失
            logger.warn("event lost: expected previous id {} but actual {} for event {}", this.lastSequenceId,
                    event.previousId, event);
            // 尝试恢复
            // ...
        }
        if(event.previousId != this.lastSequenceId) {
            logger.warn("bad event: expected previous id {} but actual {} for event {}", this.lastSequenceId,
                    event.previousId, event);
            panic();
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("process event {} -> {}: {}...", this.lastSequenceId, event.sequenceId, event);
        }
        try {
            switch (event) {
                case OrderRequestEvent requestEvent -> createOrder(requestEvent);
                case OrderCancelEvent cancelEvent -> cancelOrder(cancelEvent);
                case TransferEvent transferEvent -> transfer(transferEvent);
                default -> {
                    logger.error("unable to process event type: {}", event.getClass().getName());
                    panic();
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("process event error.", e);
            panic();
            return;
        }
        this.lastSequenceId = event.sequenceId;
        if (logger.isDebugEnabled()) {
            logger.debug("set last processed sequence id: {}...", this.lastSequenceId);
        }
        if(debugMode) {
            this.validate();
        }
    }

    void createOrder(OrderRequestEvent event) {
        ZonedDateTime zdt = Instant.ofEpochMilli(event.createdAt).atZone(this.zoneId);
        int year = zdt.getYear();
        int month = zdt.getMonth().getValue();
        long orderId = event.sequenceId * 10000 + (year * 100 + month);
        OrderEntity order = this.orderService.createOrder(event.sequenceId, event.createdAt,
                orderId, event.userId, event.direction, event.price, event.quantity);
        if(order == null) {
            logger.warn("create order failed.");
            // 推送失败结果...
            return;
        }
        MatchResult result = this.matchEngine.processOrder(event.sequenceId, order);
        this.clearingService.clearMatchResult(result);
        // 推送消息...

        // 收集已完成的 OrderEntity
        if(!result.matchDetails.isEmpty()) {
            List<OrderEntity> closedOrders = new ArrayList<>();
            // ...
            if(result.takerOrder.status.isFinalStatus) {
                closedOrders.add(result.takerOrder);
            }
            for(MatchDetailRecord detail : result.matchDetails) {
                OrderEntity maker = detail.makerOrder();
                if(maker.status.isFinalStatus) {
                    closedOrders.add(maker);
                }
                // ...
            }
            this.orderQueue.add(closedOrders);
            // ...
        }
    }

    void cancelOrder(OrderCancelEvent event) {
        OrderEntity order = this.orderService.getOrder(event.refOrderId);
        // 订单不存在或与用户不匹配
        if(order == null || order.userId.longValue() != event.userId.longValue()) {
            // 发送失败消息
            // ...
            return;
        }
        this.matchEngine.cancel(event.createdAt, order);
        this.clearingService.clearCancelResult(order);
        // ...
    }

    boolean transfer(TransferEvent event) {
        return this.assetService.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE,
                event.fromUserId, event.toUserId, event.asset, event.amount, event.sufficient);
    }

    // 验证完整性
    void validate() {
        logger.debug("start validate...");
        validateAssets();
        validateOrders();
        validateMatchEngine();
        logger.debug("validate done.");
    }

    void validateAssets() {
        BigDecimal totalUSD = BigDecimal.ZERO;
        BigDecimal totalBTC = BigDecimal.ZERO;
        for(Map.Entry<Long, ConcurrentMap<AssetEnum, Asset>> assstEntry : this.assetService.getUserAssets().entrySet()) {
            Long userId = assstEntry.getKey();
            ConcurrentMap<AssetEnum, Asset> assets = assstEntry.getValue();
            for(Map.Entry<AssetEnum, Asset> entry : assets.entrySet()) {
                AssetEnum assetId = entry.getKey();
                Asset asset = entry.getValue();
                if(userId == UserType.DEBT.getInternalUserId()) {
                    // 系统账户
                    require(asset.getAvailable().signum() <= 0, "Debt has positive available: " + asset);
                    require(asset.getFrozen().signum() == 0, "Debt has non-negative frozen: " + asset);
                } else {
                    // 普通账户非负
                    require(asset.getAvailable().signum() >= 0, "asset has negative available: " + asset);
                    require(asset.getFrozen().signum() >= 0, "asset has negative frozen: " + asset);
                }
                switch (assetId) {
                    case USD -> totalUSD = totalUSD.add(asset.getTotal());
                    case BTC -> totalBTC = totalBTC.add(asset.getTotal());
                    default -> require(false, "Invalid asset type: " + assetId);
                }
            }
        }
        require(totalUSD.signum() == 0, "total USD non-zero");
        require(totalBTC.signum() == 0, "total BTC non-zero");
    }

    void validateOrders() {
        Map<Long, Map<AssetEnum, BigDecimal>> userOrderFrozen = new HashMap<>();
        // 验证所有活跃订单
        for(Map.Entry<Long, OrderEntity> entry : this.orderService.getActiveOrders().entrySet()) {
            OrderEntity order = entry.getValue();
            // 活跃订单的未完成量必须大于0
            require(order.unfilledQuantity.signum() > 0,
                    "active order must have positive unfilled quantity: " + order);
            switch (order.direction) {
                case BUY -> {
                    // 活跃订单需在 matchEngine 中
                    require(this.matchEngine.buyBook.exist(order), "order not found in buy book: " + order);
                    // 累计冻结的 USD
                    userOrderFrozen.putIfAbsent(order.userId, new HashMap<>());
                    Map<AssetEnum, BigDecimal> frozenUSD = userOrderFrozen.get(order.userId);
                    frozenUSD.putIfAbsent(AssetEnum.USD, BigDecimal.ZERO);
                    frozenUSD.computeIfPresent(AssetEnum.USD,
                            (k, frozen) -> frozen.add(order.price.multiply(order.unfilledQuantity)));
                }
                case SELL -> {
                    // 活跃订单需在 matchEngine 中
                    require(this.matchEngine.sellBook.exist(order), "order not found in sell book: " + order);
                    // 累计冻结的 BTC
                    userOrderFrozen.putIfAbsent(order.userId, new HashMap<>());
                    Map<AssetEnum, BigDecimal> frozenBTC = userOrderFrozen.get(order.userId);
                    frozenBTC.putIfAbsent(AssetEnum.BTC, BigDecimal.ZERO);
                    frozenBTC.computeIfPresent(AssetEnum.BTC,
                            (k, frozen) -> frozen.add(order.unfilledQuantity));
                }
                default -> require(false, "Unexpected order direction: " + order.direction);
            }
        }
        // 冻结金额是否与 asset 冻结资产一致
        for(Map.Entry<Long, ConcurrentMap<AssetEnum, Asset>> userEntity : this.assetService.getUserAssets().entrySet()) {
            Long userId = userEntity.getKey();
            ConcurrentMap<AssetEnum, Asset> assets = userEntity.getValue();
            for(Map.Entry<AssetEnum, Asset> entry : assets.entrySet()) {
                AssetEnum assetId = entry.getKey();
                Asset asset = entry.getValue();
                if(asset.getFrozen().signum() > 0) {
                    Map<AssetEnum, BigDecimal> orderFrozen = userOrderFrozen.get(userId);
                    require(orderFrozen != null, "No order frozen found for user: " +
                            userId + ", asset: " + asset);
                    BigDecimal frozen = orderFrozen.get(assetId);
                    require(frozen != null, "No order frozen found for asset: " + asset);
                    require(frozen.compareTo(asset.getFrozen()) == 0, "Order frozen " +
                            frozen + " is not equals to asset frozen " + asset);
                    // 删除已验证数据
                    orderFrozen.remove(assetId);
                }
            }
        }
        // 所有订单中的冻结均已经过验证
        for(Map.Entry<Long, Map<AssetEnum, BigDecimal>> userEntry : userOrderFrozen.entrySet()) {
            Long userId = userEntry.getKey();
            Map<AssetEnum, BigDecimal> frozenAssets = userEntry.getValue();
            require(frozenAssets.isEmpty(), "User " + userId + " has unexpected frozen asset: " + frozenAssets);
        }
    }

    void validateMatchEngine() {
        // 订单簿中的订单必须与 orderService 中的活跃订单一致
        Map<Long, OrderEntity> copyOfActiveOrders = new HashMap<>(this.orderService.getActiveOrders());
        for(OrderEntity buyOrder : this.matchEngine.buyBook.book.values()) {
            require(copyOfActiveOrders.remove(buyOrder.id) == buyOrder,
                    "Order in buy book is not in active orders: " + buyOrder);
        }
        for(OrderEntity sellOrder : this.matchEngine.sellBook.book.values()) {
            require(copyOfActiveOrders.remove(sellOrder.id) == sellOrder,
                    "Order in sell book is not in active orders: " + sellOrder);
        }
        require(copyOfActiveOrders.isEmpty(), "Not all active orders are in order book.");
    }

    void require(boolean condition, String errorMessage) {
        if(!condition) {
            logger.error("validated failed: {}", errorMessage);
            panic();
        }
    }
}
