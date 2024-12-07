package com.learn.exchange.sequencer;

import com.learn.exchange.message.event.AbstractEvent;
import com.learn.exchange.messaging.*;
import com.learn.exchange.support.LoggerSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SequenceService extends LoggerSupport implements CommonErrorHandler {
    private static final String GROUP_ID = "SequenceGroup";

    @Autowired
    SequenceHandler sequenceHandler;
    @Autowired
    MessagingFactory messagingFactory;
    @Autowired
    MessageTypes messageTypes;

    private AtomicLong sequence;

    private MessageProducer<AbstractEvent> messageProducer;

    private Thread jobThread;

    private boolean running;
    private boolean crash = false;

    @PostConstruct
    public void init() {
        Thread thread = new Thread(() -> {
            logger.info("start sequence job...");
            this.messageProducer = this.messagingFactory.createMessageProducer(
                    Messaging.Topic.TRADE, AbstractEvent.class);
            // 找到最大的 event id
            this.sequence = new AtomicLong(this.sequenceHandler.getMaxSequenceId());
            logger.info("create message consumer for {}...", getClass().getName());
            // 创建 consumer
            MessageConsumer consumer = this.messagingFactory.createBatchMessageListener(Messaging.Topic.SEQUENCE,
                    GROUP_ID, this::processMessages, this);
            this.running = true;
            while(running) {
                try{
                    Thread.sleep(1000);
                }catch (InterruptedException e) {
                    break;
                }
            }
            // 关闭 message consumer
            logger.info("close message consumer for {}...", getClass().getName());
            consumer.stop();
            System.exit(1);
        });
        this.jobThread = thread;
        this.jobThread.start();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("shutdown sequence service...");
        this.running = false;
        if(jobThread != null) {
            jobThread.interrupt();
            try {
                jobThread.join(500);
            }catch (InterruptedException e) {
                logger.error("interrupt jobThread failed.", e);
            }
            jobThread = null;
        }
    }

    private void sendMessages(List<AbstractEvent> messages) {
        this.messageProducer.sendMessages(messages);
    }

    private synchronized void processMessages(List<AbstractEvent> messages) {
        if(!running || crash) {
            panic();
            return;
        }
        if(logger.isInfoEnabled())
            logger.info("do sequence for {} messages...", messages.size());
        long start = System.currentTimeMillis();
        List<AbstractEvent> sequenced = null;
        try {
            sequenced = this.sequenceHandler.sequenceMessages(this.messageTypes, this.sequence, messages);
        } catch (Throwable e) {
            logger.error("exception when do sequence.", e);
            shutdown();
            panic();
            throw new Error(e);
        }
        if(logger.isInfoEnabled()) {
            long end = System.currentTimeMillis();
            logger.info("sequenced {} messages in {} ms. current sequence id: {}", messages.size(),
                    (end - start), this.sequence.get());
        }
        sendMessages(sequenced);
    }

    @Override
    public void handleBatch(Exception thrownException, ConsumerRecords<?, ?> data, Consumer<?, ?> consumer,
                            MessageListenerContainer container, Runnable invokeListener) {
        logger.error("batch error!", thrownException);
        panic();
    }

    private void panic() {
        this.crash = true;
        this.running = false;
        System.exit(1);
    }
}
