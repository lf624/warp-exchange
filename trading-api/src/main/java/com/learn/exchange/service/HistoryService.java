package com.learn.exchange.service;

import com.learn.exchange.bean.SimpleMatchDetailRecord;
import com.learn.exchange.model.trade.MatchDetailEntity;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.support.AbstractDbService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// 从 db 查询历史订单
@Component
public class HistoryService extends AbstractDbService {
    public List<OrderEntity> getHistoryOrders(Long userId, int maxResults) {
        return db.from(OrderEntity.class)
                .where("userId = ?", userId)
                .limit(maxResults)
                .list();
    }

    public OrderEntity getHistoryOrder(Long userId, Long orderId) {
        OrderEntity entity = db.fetch(OrderEntity.class, orderId);
        if(entity == null || entity.userId.longValue() != userId.longValue()) {
            return null;
        }
        return entity;
    }

    public List<SimpleMatchDetailRecord> getHistoryDetailRecord(Long orderId) {
        List<MatchDetailEntity> details = db.select("price", "quantity", "type")
                .from(MatchDetailEntity.class)
                .where("orderId = ?", orderId)
                .orderBy("id").list();
        return details.stream().map(e -> new SimpleMatchDetailRecord(e.price, e.quantity, e.type))
                .collect(Collectors.toList());
    }
}
