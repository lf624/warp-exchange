package com.learn.exchange.redis;

import io.lettuce.core.api.sync.RedisCommands;

public interface SyncCommandCallback<T> {

    T doInConnection(RedisCommands<String, String> commands);
}
