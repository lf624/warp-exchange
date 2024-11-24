package com.learn.exchange.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class loggerSupport {
    // 子类可以直接使用 logger
    protected final Logger logger = LoggerFactory.getLogger(getClass());
}
