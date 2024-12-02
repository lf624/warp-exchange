package com.learn.exchange.message;

import java.io.Serializable;

// base message object for extends
public class AbstractMessage implements Serializable {
    // 引用id，不用则为 null
    public String refId = null;
    // 消息创建时间
    public long createdAt;
}
