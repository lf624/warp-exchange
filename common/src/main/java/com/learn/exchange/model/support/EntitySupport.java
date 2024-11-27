package com.learn.exchange.model.support;

public interface EntitySupport {

    // 定义数据库存储 big decimal 的精度: DECIMAL(PRECISION, SCALE)
    int PRECISION = 32;
    int SCALE = 18;

    int VAR_ENUM = 32;

    int VAR_CHAR_50 = 50;
    int VAR_CHAR_100 = 100;
    int VAR_CHAR_200 = 200;
    int VAR_CHAR_1000 = 1000;
    int VAR_CHAR_10000 = 10000;
}