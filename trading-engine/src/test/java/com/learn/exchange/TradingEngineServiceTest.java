package com.learn.exchange;

import com.learn.exchange.assets.AssetService;
import com.learn.exchange.clearing.ClearingService;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.UserType;
import com.learn.exchange.match.MatchEngine;
import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.message.event.OrderCancelEvent;
import com.learn.exchange.message.event.OrderRequestEvent;
import com.learn.exchange.message.event.TransferEvent;
import com.learn.exchange.order.OrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TradingEngineServiceTest {

    static final Long USER_A = 11111L;
    static final Long USER_B = 22222L;
    static final Long USER_C = 33333L;
    static final Long USER_D = 44444L;

    static final Long[] USERS = { USER_A, USER_B, USER_C, USER_D };

    @Test
    public void testTradingEngine() {
        var engine = createTradingEngineService();

        engine.processEvent(depositEvent(USER_A, AssetEnum.USD, bd("58000")));
        engine.processEvent(depositEvent(USER_B, AssetEnum.USD, bd("126700")));
        engine.processEvent(depositEvent(USER_C, AssetEnum.BTC, bd("5.5")));
        engine.processEvent(depositEvent(USER_D, AssetEnum.BTC, bd("8.6")));

        engine.validate();

        engine.processEvent(orderRequestEvent(USER_A, Direction.BUY, bd("2207.33"), bd("1.2")));
        engine.processEvent(orderRequestEvent(USER_C, Direction.SELL, bd("2215.6"), bd("0.8")));
        engine.processEvent(orderRequestEvent(USER_C, Direction.SELL, bd("2921.1"), bd("0.3")));

        engine.validate();

        engine.processEvent(orderRequestEvent(USER_D, Direction.SELL, bd("2206"), bd("0.3")));

        engine.validate();

        engine.processEvent(orderRequestEvent(USER_B, Direction.BUY, bd("2219.6"), bd("2.4")));

        engine.validate();

        engine.processEvent(orderCancelEvent(USER_A, 1L));

        engine.validate();
    }

    @Test
    public void testRandom() {
        var engine = createTradingEngineService();
        var r = new Random(123456789);
        for (Long user : USERS) {
            engine.processEvent(depositEvent(user, AssetEnum.USD, random(r, 1000_0000, 2000_0000)));
            engine.processEvent(depositEvent(user, AssetEnum.BTC, random(r, 1000, 2000)));
        }
        engine.validate();

        int low = 20000;
        int high = 40000;
        for (int i = 0; i < 100; i++) {
            Long user = USERS[i % USERS.length];
            engine.processEvent(orderRequestEvent(user, Direction.BUY, random(r, low, high), random(r, 1, 5)));
            engine.validate();

            engine.processEvent(orderRequestEvent(user, Direction.SELL, random(r, low, high), random(r, 1, 5)));
            engine.validate();
        }
        assertEquals("35216.4", engine.matchEngine.marketPrice.stripTrailingZeros().toPlainString());
    }

    BigDecimal random(Random random, int low, int high) {
        int n = random.nextInt(low, high);
        int m = random.nextInt(100);
        return new BigDecimal(n + "." + m);
    }

    TradingEngineService createTradingEngineService() {
        var assetService = new AssetService();
        var orderService = new OrderService(assetService);
        var matchEngine = new MatchEngine();
        var clearingService = new ClearingService(assetService, orderService);
        var engine = new TradingEngineService();
        engine.assetService = assetService;
        engine.orderService = orderService;
        engine.matchEngine = matchEngine;
        engine.clearingService = clearingService;
        return engine;
    }

    OrderRequestEvent orderRequestEvent(Long userId, Direction direction,
                                        BigDecimal price, BigDecimal quantity) {
        var event = createEvent(OrderRequestEvent.class);
        event.userId = userId;
        event.direction = direction;
        event.price = price;
        event.quantity = quantity;
        return event;
    }

    OrderCancelEvent orderCancelEvent(Long userId, Long refOrderId) {
        var event = createEvent(OrderCancelEvent.class);
        event.userId = userId;
        event.refOrderId = refOrderId;
        return event;
    }

    TransferEvent depositEvent(Long userId, AssetEnum asset, BigDecimal amount) {
        var event = createEvent(TransferEvent.class);
        event.fromUserId = UserType.DEBT.getInternalUserId();
        event.toUserId = userId;
        event.asset = asset;
        event.amount = amount;
        event.sufficient = false;
        return event;
    }

    private long currentSequenceId = 0;

    <T extends AbstractEvent> T createEvent(Class<T> clazz) {
        T event;
        try {
            event = clazz.getConstructor().newInstance();
        }catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        event.previousId = this.currentSequenceId;
        this.currentSequenceId++;
        event.sequenceId = this.currentSequenceId;
        event.createdAt = LocalDateTime.parse("2024-12-05T20:23:23")
                .atZone(ZoneId.of("Z"))
                .toEpochSecond() * 1000 + this.currentSequenceId;
        return event;
    }

    BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
