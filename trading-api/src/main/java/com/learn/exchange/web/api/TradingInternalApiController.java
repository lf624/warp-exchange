package com.learn.exchange.web.api;

import com.learn.exchange.bean.TransferRequestBean;
import com.learn.exchange.enums.UserType;
import com.learn.exchange.message.event.TransferEvent;
import com.learn.exchange.service.SendEventService;
import com.learn.exchange.support.AbstractApiController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal")
public class TradingInternalApiController extends AbstractApiController {
    @Autowired
    SendEventService sendEventService;

    // 处理一个转账请求，可重复调用，重复发送消息，根据uniqueId去重，仅定序一次
    @PostMapping("/transfer")
    public Map<String, Boolean> transferIn(@RequestBody TransferRequestBean transferRequest) {
        logger.info("transfer request: transferId={}, fromUserId={}, toUserId={}, asset={}, amount={}",
                transferRequest.transferId, transferRequest.fromUserId, transferRequest.toUserId,
                transferRequest.asset, transferRequest.amount);
        transferRequest.validate();

        var msg = new TransferEvent();
        // 设置uniqueId以确保消息只被排序一次
        msg.uniqueId = transferRequest.transferId;
        msg.fromUserId = transferRequest.fromUserId;
        msg.toUserId = transferRequest.toUserId;
        msg.asset = transferRequest.asset;
        msg.amount = transferRequest.amount;
        msg.sufficient = transferRequest.fromUserId.longValue() != UserType.DEBT.getInternalUserId();
        this.sendEventService.sendMessage(msg);
        logger.info("transfer event sent: {}", msg);
        return Map.of("result", Boolean.TRUE);
    }
}
