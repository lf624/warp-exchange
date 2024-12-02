package com.learn.exchange.message.event;

import com.learn.exchange.enums.AssetEnum;

import java.math.BigDecimal;

// 资产转移事件
public class TransferEvent extends AbstractEvent{

    public Long fromUserId;
    public Long toUserId;
    public AssetEnum asset;
    public BigDecimal amount;
    // 资产是否充足
    public boolean sufficient;

    @Override
    public String toString() {
        return "TransferEvent [sequenceId=" + sequenceId + ", previousId=" + previousId + ", uniqueId=" + uniqueId
                + ", refId=" + refId + ", createdAt=" + createdAt + ", fromUserId=" + fromUserId + ", toUserId="
                + toUserId + ", asset=" + asset + ", amount=" + amount + ", sufficient=" + sufficient + "]";
    }
}
