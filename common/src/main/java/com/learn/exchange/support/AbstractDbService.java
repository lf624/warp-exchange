package com.learn.exchange.support;

import com.learn.exchange.db.DbTemplate;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractDbService extends LoggerSupport{
    @Autowired
    protected DbTemplate db;
}
