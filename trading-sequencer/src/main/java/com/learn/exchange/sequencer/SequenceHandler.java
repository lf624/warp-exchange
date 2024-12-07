package com.learn.exchange.sequencer;

import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.messaging.MessageTypes;
import com.learn.exchange.model.trade.EventEntity;
import com.learn.exchange.model.trade.UniqueEventEntity;
import com.learn.exchange.support.AbstractDbService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Transactional(rollbackFor = Throwable.class)
public class SequenceHandler extends AbstractDbService {
    private long lastTimestamp = 0;

    public List<AbstractEvent> sequenceMessages(final MessageTypes messageTypes, final AtomicLong sequence,
                                                final List<AbstractEvent> messages) {
        final long t = System.currentTimeMillis();
        if(t < lastTimestamp) {
            logger.warn("[sequence] current time {} is turned back from {}", t, this.lastTimestamp);
        } else {
            this.lastTimestamp = t;
        }
        // 利用UniqueEventEntity去重
        List<UniqueEventEntity> uniques = null;
        Set<String> uniqueKeys = null;
        List<AbstractEvent> sequencedMessages = new ArrayList<>(messages.size());
        List<EventEntity> events = new ArrayList<>(messages.size());
        for(AbstractEvent message: messages) {
            UniqueEventEntity unique = null;
            final String uniqueId = message.uniqueId;
            // 检查 uniqueId 是否已存在
            if(uniqueId != null) {
                if((uniqueKeys != null && uniqueKeys.contains(uniqueId)) ||
                        db.fetch(UniqueEventEntity.class, uniqueId) != null) {
                    logger.warn("ignore processed unique message: {} ", message);
                }
                unique = new UniqueEventEntity();
                unique.uniqueId = uniqueId;
                unique.createdAt = message.createdAt;
                if(uniques == null)
                    uniques = new ArrayList<>();
                uniques.add(unique);
                if(uniqueKeys == null)
                    uniqueKeys = new HashSet<>();
                uniqueKeys.add(uniqueId);
            }
            // 上次定序 Id
            final long previousId = sequence.get();
            // 本次定序 Id
            final long currentId = sequence.incrementAndGet();
            // 先设置message的sequenceId / previouseId，再序列化并落库
            message.previousId = previousId;
            message.sequenceId = currentId;
            message.createdAt = this.lastTimestamp;
            // 若该消息关联了 UniqueEvent，给UniqueEvent加上相同的sequenceId
            if(unique != null)
                unique.sequenceId = message.sequenceId;
            // 准备写入数据库的 EventEntity
            EventEntity event = new EventEntity();
            event.previousId = previousId;
            event.sequenceId = currentId;
            event.data = messageTypes.serialize(message);
            event.createdAt = this.lastTimestamp;
            events.add(event);
            // 添加到结果集
            sequencedMessages.add(message);
        }
        // 落库
        if(uniques != null)
            db.insert(uniques);
        db.insert(events);
        // 返回定序后的消息
        return sequencedMessages;
    }

    public long getMaxSequenceId() {
        EventEntity last = db.from(EventEntity.class).orderBy("sequenceId").desc().first();
        if(last == null) {
            logger.info("no max sequenceId found, set max sequenceId = 0.");
            return 0;
        }
        this.lastTimestamp = last.createdAt;
        logger.info("find max sequenceId = {}, last timestamp = {}", last.sequenceId, this.lastTimestamp);
        return last.sequenceId;
    }
}
