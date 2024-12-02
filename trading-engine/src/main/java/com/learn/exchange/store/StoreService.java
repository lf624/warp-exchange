package com.learn.exchange.store;

import com.learn.exchange.messaging.MessageTypes;
import com.learn.exchange.support.LoggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
public class StoreService extends LoggerSupport {
    @Autowired
    MessageTypes messageTypes;

}
