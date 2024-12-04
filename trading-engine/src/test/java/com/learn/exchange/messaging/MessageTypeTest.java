package com.learn.exchange.messaging;

import com.learn.exchange.message.AbstractMessage;
import com.learn.exchange.message.event.AbstractEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageTypeTest {
    MessageTypes messageTypes;

    @BeforeEach
    void init() {
        messageTypes = new MessageTypes();
        messageTypes.init();
    }

    @Test
    void testMessageType() {
        assertEquals("com.learn.exchange.message", messageTypes.messagePackage);
        for(Map.Entry<String, Class<? extends AbstractMessage>> entry : messageTypes.messageTypes.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().getSimpleName());
        }
        AbstractEvent abstractEvent = new AbstractEvent();
        abstractEvent.previousId = 1000L;
        abstractEvent.sequenceId = 1001L;
        abstractEvent.uniqueId = "abstract";
        String serialize = messageTypes.serialize(abstractEvent);
        System.out.println(serialize);
    }
}
