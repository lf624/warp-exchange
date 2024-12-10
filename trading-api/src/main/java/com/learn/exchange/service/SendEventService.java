package com.learn.exchange.service;

import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.messaging.MessageProducer;
import com.learn.exchange.messaging.Messaging;
import com.learn.exchange.messaging.MessagingFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SendEventService {
    @Autowired
    private MessagingFactory messagingFactory;

    private MessageProducer<AbstractEvent> messageProducer;

    @PostConstruct
    public void init() {
        // 写入 Sequence 分区
        this.messageProducer = messagingFactory.createMessageProducer(
                Messaging.Topic.SEQUENCE, AbstractEvent.class);
    }

    public void sendMessage(AbstractEvent message) {
        this.messageProducer.sendMessage(message);
    }
}
