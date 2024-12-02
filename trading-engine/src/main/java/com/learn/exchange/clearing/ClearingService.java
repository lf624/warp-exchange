package com.learn.exchange.clearing;

import com.learn.exchange.assets.AssetService;
import com.learn.exchange.assets.Transfer;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.match.MatchDetailRecord;
import com.learn.exchange.match.MatchResult;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.order.OrderService;
import com.learn.exchange.support.LoggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ClearingService extends LoggerSupport {

    final AssetService assetService;
    final OrderService orderService;

    public ClearingService(@Autowired AssetService assetService, @Autowired OrderService orderService) {
        this.assetService = assetService;
        this.orderService = orderService;
    }

    public void clearMatchResult(MatchResult result) {
        OrderEntity taker = result.takerOrder;
        List<MatchDetailRecord> matchDetails = result.matchDetails;
        switch (taker.direction) {
            case BUY -> {
                for(MatchDetailRecord detail : matchDetails) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "clear buy matched detail: price = {}, quantity = {}, takerOrderId = {}, makerOrderId = {}, takerUserId = {}, makerUserId = {}",
                                detail.price(), detail.quantity(), detail.takerOrder().id, detail.makerOrder().id,
                                detail.takerOrder().userId, detail.makerOrder().userId);
                    }
                    OrderEntity maker = detail.makerOrder();
                    BigDecimal matched = detail.quantity();
                    if(maker.price.compareTo(taker.price) < 0) {
                        // 实际买入价比报价低，部分 USD 退回账户
                        assetService.unfreeze(taker.userId, AssetEnum.USD,
                                taker.price.subtract(maker.price).multiply(matched));
                        logger.debug("unfree extra unused quote {} back to taker user {}",
                                taker.price.subtract(maker.price).multiply(matched), taker.userId);
                    }
                    // 买方USD转入卖方账户
                    assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, taker.userId, maker.userId,
                            AssetEnum.USD, maker.price.multiply(matched));
                    // 卖方BTC转入买方账户
                    assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, maker.userId, taker.userId,
                            AssetEnum.BTC, matched);
                    // 删除完全成交的 maker
                    if(maker.unfilledQuantity.signum() == 0)
                        orderService.removeOrder(maker.id);
                }
                // 删除完全成交的 taker
                if(taker.unfilledQuantity.signum() == 0)
                    orderService.removeOrder(taker.id);
            }
            case SELL -> {
                for(MatchDetailRecord detail : matchDetails) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "clear sell matched detail: price = {}, quantity = {}, takerOrderId = {}, makerOrderId = {}, takerUserId = {}, makerUserId = {}",
                                detail.price(), detail.quantity(), detail.takerOrder().id, detail.makerOrder().id,
                                detail.takerOrder().userId, detail.makerOrder().userId);
                    }
                    OrderEntity maker = detail.makerOrder();
                    BigDecimal matched = detail.quantity();
                    // 卖方BTC转入买方账户
                    assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, taker.userId, maker.userId,
                            AssetEnum.BTC, matched);
                    // 买方USD转入卖方账户
                    assetService.transfer(Transfer.FROZEN_TO_AVAILABLE, maker.userId, taker.userId,
                            AssetEnum.USD, maker.price.multiply(matched));
                    if(maker.unfilledQuantity.signum() == 0)
                        orderService.removeOrder(maker.id);
                }
                if(taker.unfilledQuantity.signum() == 0)
                    orderService.removeOrder(taker.id);
            }
            default -> throw new IllegalArgumentException("No such direction: " + taker.direction);
        };
    }

    public void clearCancelResult(OrderEntity order) {
        switch (order.direction) {
            case BUY -> {
                // 解冻USD = 价格 x 未成交数量
                assetService.unfreeze(order.userId, AssetEnum.USD, order.price.multiply(order.unfilledQuantity));
            }
            case SELL -> {
                // 解冻BTC = 未成交数量
                assetService.unfreeze(order.userId, AssetEnum.BTC, order.unfilledQuantity);
            }
            default -> throw new IllegalArgumentException("Invalid direction: " + order.direction);
        }
        // 从OrderService中删除订单
        orderService.removeOrder(order.id);
    }
}
