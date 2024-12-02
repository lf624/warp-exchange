package com.learn.exchange.messaging;

import com.learn.exchange.message.AbstractMessage;

import java.util.List;

@FunctionalInterface
public interface MessageProducer<T extends AbstractMessage> {

    void sendMessage(T message);

    default void sendMessage(List<T> messages) {
        for(T message: messages)
            sendMessage(message);
    }
}
