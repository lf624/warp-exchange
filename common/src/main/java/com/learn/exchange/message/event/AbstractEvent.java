package com.learn.exchange.message.event;

import com.learn.exchange.message.AbstractMessage;
import jakarta.annotation.Nullable;

public class AbstractEvent extends AbstractMessage {
    // 消息的定序id
    public long sequenceId;
    // 上一个消息的 sequenceId
    public long previousId;
    // 全局唯一 id，不用则为null
    @Nullable
    public String uniqueId;
}
