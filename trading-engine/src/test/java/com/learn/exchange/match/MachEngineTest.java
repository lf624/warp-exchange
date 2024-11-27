package com.learn.exchange.match;

import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.OrderStatus;
import com.learn.exchange.model.trade.OrderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MachEngineTest {
    final Long USER_A = 1234L;
    long sequenceId = 0;
    MatchEngine engine;

    @BeforeEach
    void init() {
        this.engine = new MatchEngine();
    }

    @Test
    void processTest() {
        List<OrderEntity> orders = List.of( //
                createOrder(Direction.BUY, "12300.21", "1.02"), // 0
                createOrder(Direction.BUY, "12305.39", "0.33"), // 1
                createOrder(Direction.SELL, "12305.39", "0.11"), // 2
                createOrder(Direction.SELL, "12300.01", "0.33"), // 3
                createOrder(Direction.SELL, "12400.00", "0.10"), // 4
                createOrder(Direction.SELL, "12400.00", "0.20"), // 5
                createOrder(Direction.SELL, "12390.00", "0.15"), // 6
                createOrder(Direction.BUY, "12400.01", "0.55"), // 7
                createOrder(Direction.BUY, "12300.00", "0.77"));
        List<MatchDetailRecord> matches = new ArrayList<>();
        for(OrderEntity order : orders) {
            MatchResult res = this.engine.processOrder(order.sequenceId, order);
            matches.addAll(res.matchDetails);
        }
        assertArrayEquals(new MatchDetailRecord[] { //
                new MatchDetailRecord(bd("12305.39"), bd("0.11"), orders.get(2), orders.get(1)), //
                new MatchDetailRecord(bd("12305.39"), bd("0.22"), orders.get(3), orders.get(1)), //
                new MatchDetailRecord(bd("12300.21"), bd("0.11"), orders.get(3), orders.get(0)), //
                new MatchDetailRecord(bd("12390.00"), bd("0.15"), orders.get(7), orders.get(6)), //
                new MatchDetailRecord(bd("12400.00"), bd("0.10"), orders.get(7), orders.get(4)), //
                new MatchDetailRecord(bd("12400.00"), bd("0.20"), orders.get(7), orders.get(5)), //
        }, matches.toArray(MatchDetailRecord[]::new));
        assertTrue(bd("12400.00").compareTo(engine.marketPrice) == 0);


    }

    OrderEntity createOrder(Direction direction, String price, String quantity) {
        this.sequenceId++;
        OrderEntity entity = new OrderEntity();
        entity.id = sequenceId << 4;
        entity.sequenceId = sequenceId;
        entity.userId = USER_A;
        entity.direction = direction;
        entity.price = bd(price);
        entity.quantity = entity.unfilledQuantity = bd(quantity);
        entity.status = OrderStatus.PENDING;
        entity.createdAt = entity.updatedAt = 1234567890000L + this.sequenceId;
        return entity;
    }

    BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // 测试买卖盘是否与教程一致
    @Test
    void testBook() {
        List<OrderEntity> orders = createByString("""
                                            buy  2082.34 1
                                            sell 2087.6  2
                                            buy  2087.8  1
                                            buy  2085.01 5
                                            sell 2088.02 3
                                            sell 2087.60 6
                                            buy  2081.11 7
                                            buy  2086.0  3
                                            buy  2088.33 1
                                            sell 2086.54 2
                                            sell 2086.55 5
                                            buy  2086.55 3""");
        List<MatchDetailRecord> matches = new ArrayList<>();
        for(OrderEntity order : orders) {
            MatchResult res = this.engine.processOrder(order.sequenceId, order);
            matches.addAll(res.matchDetails);
        }
        List<OrderEntity> sellOrders = new ArrayList<>(engine.sellBook.book.values().stream().toList());
        Collections.reverse(sellOrders);
        sellOrders.forEach(order ->
                System.out.println(order.price + " " + order.unfilledQuantity));
        System.out.println("---------");
        System.out.println(engine.marketPrice);
        System.out.println("---------");
        engine.buyBook.book.values()
                .forEach(order ->
                        System.out.println(order.price + " " + order.unfilledQuantity));
    }

    List<OrderEntity> createByString(String s) {
        return s.lines().map(v -> {
            String[] item = v.split(" +"); // 匹配一个或多个空格
            Direction direction = item[0].trim().equals("buy") ? Direction.BUY : Direction.SELL;
            String price = item[1].trim();
            String quantity = item[2].trim();
            return createOrder(direction, price, quantity);
        }).toList();
    }
}
