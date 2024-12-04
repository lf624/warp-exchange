package com.learn.exchange.store;

import com.learn.exchange.db.DbTemplate;
import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.messaging.MessageTypes;
import com.learn.exchange.model.support.EntitySupport;
import com.learn.exchange.model.trade.EventEntity;
import com.learn.exchange.support.LoggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Transactional
@Component
public class StoreService extends LoggerSupport {
    @Autowired
    MessageTypes messageTypes;
    @Autowired
    DbTemplate dbTemplate;

    public List<AbstractEvent> loadEventsFromBd(long lastSequenceId) {
        List<EventEntity> events = dbTemplate.from(EventEntity.class).where("sequence > ?", lastSequenceId)
                .orderBy("sequenceId").limit(100000).list();
        return events.stream().map(event -> (AbstractEvent)messageTypes.deserialize(event.data))
                .collect(Collectors.toList());
    }

    public void insertIgnore(List<? extends EntitySupport> list) {
        dbTemplate.insertIgnore(list);
    }
}
