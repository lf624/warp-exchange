package com.learn.exchange;

import com.learn.exchange.assets.Asset;
import com.learn.exchange.assets.AssetService;
import com.learn.exchange.assets.Transfer;
import com.learn.exchange.bean.OrderBookBean;
import com.learn.exchange.clearing.ClearingService;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.MatchType;
import com.learn.exchange.enums.UserType;
import com.learn.exchange.match.MatchDetailRecord;
import com.learn.exchange.match.MatchEngine;
import com.learn.exchange.match.MatchResult;
import com.learn.exchange.message.ApiResultMessage;
import com.learn.exchange.message.NotificationMessage;
import com.learn.exchange.message.TickMessage;
import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.message.event.OrderCancelEvent;
import com.learn.exchange.message.event.OrderRequestEvent;
import com.learn.exchange.message.event.TransferEvent;
import com.learn.exchange.messaging.MessageConsumer;
import com.learn.exchange.messaging.MessageProducer;
import com.learn.exchange.messaging.Messaging;
import com.learn.exchange.messaging.MessagingFactory;
import com.learn.exchange.model.quotation.TickEntity;
import com.learn.exchange.model.trade.MatchDetailEntity;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.order.OrderService;
import com.learn.exchange.redis.RedisCache;
import com.learn.exchange.redis.RedisService;
import com.learn.exchange.store.StoreService;
import com.learn.exchange.support.LoggerSupport;
import com.learn.exchange.util.IpUtil;
import com.learn.exchange.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

    @Value("#{exchangeConfiguration.debugMode}")
    boolean debugMode = false;
    @Value("#{exchangeConfiguration.orderBookDepth}")
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

    @Autowired
    MessagingFactory messagingFactory;
    @Autowired
    StoreService storeService;
    @Autowired
    RedisService redisService;

    private MessageConsumer consumer;
    private MessageProducer<TickMessage> producer;

    private String shaUpdateOrderBookLua;

    // 上一个处理的事件的 sequenceId
    private long lastSequenceId = 0;
    // orderBook 是否发生了变化
    private boolean orderBookChanged = false;

    private Thread tickThread;
    private Thread notifyThread;
    private Thread apiResultThread;
    private Thread orderBookThread;
    private Thread dbThread;

    // 保存的最新orderBook快照
    private OrderBookBean lastedOrderBook = null;

    private Queue<List<OrderEntity>> orderQueue = new ConcurrentLinkedQueue<>();
    private Queue<List<MatchDetailEntity>> matchQueue = new ConcurrentLinkedQueue<>();
    private Queue<TickMessage> tickQueue = new ConcurrentLinkedQueue<>();
    private Queue<NotificationMessage> notificationQueue = new ConcurrentLinkedQueue<>();
    private Queue<ApiResultMessage> apiResultQueue = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void init() {
        this.shaUpdateOrderBookLua = this.redisService.loadScriptFromClasspath("/redis/update-orderbook.lua");
        this.consumer = this.messagingFactory.createBatchMessageListener(Messaging.Topic.TRADE,
                IpUtil.getHostId(), this::processMessages);
        this.producer = this.messagingFactory.createMessageProducer(Messaging.Topic.TICK, TickMessage.class);
        this.tickThread = new Thread(this::runTickThread, "async-tick");
        this.tickThread.start();
        this.notifyThread = new Thread(this::runNotifyThread, "async-notify");
        this.notifyThread.start();
        this.apiResultThread = new Thread(this::runApiResultThread, "async-api-result");
        this.apiResultThread.start();
        this.orderBookThread = new Thread(this::runOrderBookThread, "async-orderBook");
        this.orderBookThread.start();
        this.dbThread = new Thread(this::runDbThread, "async-db");
        this.dbThread.start();
    }

    @PreDestroy
    public void destroy() {
        this.consumer.stop();
        this.orderBookThread.interrupt();
        this.dbThread.interrupt();
    }

    private void runTickThread() {
        logger.info("start tick thread...");
        for(;;) {
            List<TickMessage> msgs = new ArrayList<>();
            for(;;) {
                TickMessage message = tickQueue.poll();
                if(message != null) {
                    msgs.add(message);
                    if(msgs.size() >= 1000)
                        break;
                } else {
                    break;
                }
            }
            if(!msgs.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("send {} tick messages...", msgs.size());
                }
                this.producer.sendMessages(msgs);
            } else {
                // 无 TickMessage，暂停 1ms
                try {
                    Thread.sleep(1);
                }catch (InterruptedException e) {
                    logger.warn("{} was interrupted.", Thread.currentThread().getName());
                    break;
                }
            }
        }
    }
    private void runNotifyThread() {
        logger.info("start publish notify to redis...");
        for(;;) {
            NotificationMessage msg = notificationQueue.poll();
            if(msg != null) {
                redisService.publish(RedisCache.Topic.NOTIFICATION, JsonUtil.writeJson(msg));
            } else {
                try {
                    Thread.sleep(1);
                }catch (InterruptedException e) {
                    logger.warn("{} was interrupted.", Thread.currentThread().getName());
                    break;
                }
            }
        }
    }
    private void runApiResultThread() {
        logger.info("start publish api result to redis...");
        for(;;) {
            ApiResultMessage msg = apiResultQueue.poll();
            if(msg != null) {
                // 发送 pub 事件 TRADING_API_RESULT，TradingApiController 订阅该事件并处理
                redisService.publish(RedisCache.Topic.TRADING_API_RESULT, JsonUtil.writeJson(msg));
            } else {
                try {
                    Thread.sleep(1);
                }catch (InterruptedException e) {
                    logger.warn("{} was interrupted.", Thread.currentThread().getName());
                    break;
                }
            }
        }
    }
    private void runOrderBookThread() {
        logger.info("start update orderBook snapshot to redis...");
        long lastSequenceId = 0;
        for(;;) {
            // 获取OrderBookBean的引用，确保后续操作针对局部变量而非成员变量
            final OrderBookBean orderBook = this.lastedOrderBook;
            // 仅在OrderBookBean更新后刷新Redis
            if(orderBook != null && orderBook.sequenceId > lastSequenceId) {
                if(logger.isDebugEnabled())
                    logger.debug("update orderBook snapshot at sequenceId {}...", orderBook.sequenceId);
                redisService.executeScriptReturnBoolean(this.shaUpdateOrderBookLua,
                        // key: [cache key]
                        new String[] {RedisCache.Key.ORDER_BOOK},
                        // args: [sequenceId, json-data]
                        new String[] {String.valueOf(orderBook.sequenceId), JsonUtil.writeJson(orderBook)});
                lastSequenceId = orderBook.sequenceId;
            } else {
                try {
                    Thread.sleep(1);
                }catch (InterruptedException e) {
                    logger.warn("{} was interrupted.", Thread.currentThread().getName());
                    break;
                }
            }
        }
    }
    private void runDbThread() {
        logger.info("start batch insert to db...");
        for(;;) {
            try {
                saveToDb();
            }catch (InterruptedException e) {
                logger.warn("{} was interrupted.", Thread.currentThread().getName());
                break;
            }
        }
    }
    private void saveToDb() throws InterruptedException{
        if(!matchQueue.isEmpty()) {
            List<MatchDetailEntity> batch = new ArrayList<>(1000);
            for(;;) {
                List<MatchDetailEntity> matches = matchQueue.poll();
                if(matches != null) {
                    batch.addAll(matches);
                    if(batch.size() >= 1000)
                        break;
                } else {
                    break;
                }
            }
            batch.sort(MatchDetailEntity::compareTo);
            if(logger.isDebugEnabled())
                logger.debug("batch insert {} match details.", batch.size());
            this.storeService.insertIgnore(batch);
        }
        if(!orderQueue.isEmpty()) {
            List<OrderEntity> batch = new ArrayList<>(1000);
            for(;;) {
                List<OrderEntity> orders = orderQueue.poll();
                if(orders != null) {
                    batch.addAll(orders);
                    if(batch.size() >= 1000)
                        break;
                } else {
                    break;
                }
            }
            batch.sort(OrderEntity::compareTo);
            if(logger.isDebugEnabled())
                logger.debug("batch insert {} orders.", batch.size());
            this.storeService.insertIgnore(batch);
        }
        if(matchQueue.isEmpty())
            Thread.sleep(1);
    }

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
            List<AbstractEvent> events = this.storeService.loadEventsFromBd(this.lastSequenceId);
            if(events.isEmpty()) {
                logger.error("can't read lost events from db.");
                panic();
                return;
            }
            for(AbstractEvent e : events)
                this.processEvent(e);
            return;
        }
        // 判断当前消息是否指向上一条消息
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
        // 生成 orderId
        long orderId = event.sequenceId * 10000 + (year * 100 + month);
        // 创建Order
        OrderEntity order = this.orderService.createOrder(event.sequenceId, event.createdAt,
                orderId, event.userId, event.direction, event.price, event.quantity);
        if(order == null) {
            logger.warn("create order failed.");
            // 推送失败结果
            this.apiResultQueue.add(ApiResultMessage.createOrderFailed(event.refId, event.createdAt));
            return;
        }
        // 撮合
        MatchResult result = this.matchEngine.processOrder(event.sequenceId, order);
        // 清算
        this.clearingService.clearMatchResult(result);
        // 推送成功结果
        // 必须复制一份OrderEntity，因为将异步序列化
        this.apiResultQueue.add(ApiResultMessage.orderSuccess(event.refId, order.copy(), event.createdAt));
        this.orderBookChanged = true;
        // 收集 Notification
        List<NotificationMessage> notifications = new ArrayList<>();
        notifications.add(createNotification(event.createdAt, "order_matched", order.userId, order.copy()));
        // 收集已完成的 OrderEntity
        if(!result.matchDetails.isEmpty()) {
            List<OrderEntity> closedOrders = new ArrayList<>();
            List<MatchDetailEntity> matchDetails = new ArrayList<>();
            List<TickEntity> ticks = new ArrayList<>();
            if(result.takerOrder.status.isFinalStatus) {
                closedOrders.add(result.takerOrder);
            }
            for(MatchDetailRecord detail : result.matchDetails) {
                OrderEntity maker = detail.makerOrder();
                if(maker.status.isFinalStatus) {
                    closedOrders.add(maker);
                }
                MatchDetailEntity takerDetail = generateMatchDetailEntity(event.sequenceId, event.createdAt, detail, true);
                MatchDetailEntity makerDetail = generateMatchDetailEntity(event.sequenceId, event.createdAt, detail, false);
                matchDetails.add(takerDetail);
                matchDetails.add(makerDetail);
                TickEntity tick = new TickEntity();
                tick.sequenceId = event.sequenceId;
                tick.takerUserId = detail.takerOrder().userId;
                tick.makerUserId = detail.makerOrder().userId;
                tick.price = detail.price();
                tick.quantity = detail.quantity();
                tick.takerDirection = detail.takerOrder().direction == Direction.BUY;
                tick.createdAt = event.createdAt;
                ticks.add(tick);
            }
            // 异步写入db
            this.orderQueue.add(closedOrders);
            this.matchQueue.add(matchDetails);
            // 异步发送 tick 消息
            TickMessage msg = new TickMessage();
            msg.sequenceId = event.sequenceId;
            msg.ticks = ticks;
            this.tickQueue.add(msg);
            // 异步通知orderMatch
            this.notificationQueue.addAll(notifications);
        }
    }

    void cancelOrder(OrderCancelEvent event) {
        OrderEntity order = this.orderService.getOrder(event.refOrderId);
        // 订单不存在或与用户不匹配
        if(order == null || order.userId.longValue() != event.userId.longValue()) {
            // 发送失败消息
            this.apiResultQueue.add(ApiResultMessage.createOrderFailed(event.refId, event.createdAt));
            return;
        }
        this.matchEngine.cancel(event.createdAt, order);
        this.clearingService.clearCancelResult(order);
        this.orderBookChanged = true;
        // 发送取消成功消息(这里不用复制)
        this.apiResultQueue.add(ApiResultMessage.orderSuccess(event.refId, order, event.createdAt));
        this.notificationQueue.add(createNotification(event.createdAt, "order_canceled", event.userId, order));
    }

    boolean transfer(TransferEvent event) {
        return this.assetService.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE,
                event.fromUserId, event.toUserId, event.asset, event.amount, event.sufficient);
    }

    private NotificationMessage createNotification(long ts, String type, Long userId, Object data) {
        NotificationMessage msg = new NotificationMessage();
        msg.createdAt = ts;
        msg.type = type;
        msg.userId = userId;
        msg.data = data;
        return msg;
    }

    MatchDetailEntity generateMatchDetailEntity(long sequenceId, long ts, MatchDetailRecord detail, boolean forTaker) {
        MatchDetailEntity d = new MatchDetailEntity();
        d.sequenceId = sequenceId;
        d.orderId = forTaker ? detail.takerOrder().id : detail.makerOrder().id;
        d.counterOrderId = forTaker ? detail.makerOrder().id : detail.takerOrder().id;
        d.direction = forTaker ? detail.takerOrder().direction : detail.makerOrder().direction;
        d.price = detail.price();
        d.quantity = detail.quantity();
        d.type = forTaker ? MatchType.TAKER : MatchType.MAKER;
        d.userId = forTaker ? detail.takerOrder().userId : detail.makerOrder().userId;
        d.counterUserId = forTaker ? detail.makerOrder().userId : detail.takerOrder().userId;
        d.createdAt = ts;
        return d;
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
