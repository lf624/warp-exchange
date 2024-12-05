package com.learn.exchange.redis;

import com.learn.exchange.util.ClassPathUtil;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.support.ConnectionPoolSupport;
import jakarta.annotation.PreDestroy;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;

@Component
public class RedisService {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final RedisClient redisClient;

    final GenericObjectPool<StatefulRedisConnection<String, String>> redisConnectionPool;

    public RedisService(@Autowired RedisConfiguration redisConfig) {
        RedisURI uri = RedisURI.Builder.redis(redisConfig.getHost(), redisConfig.getPort())
                .withPassword(redisConfig.getPassword().toCharArray())
                .withDatabase(redisConfig.getDatabase())
                .build();
        this.redisClient = RedisClient.create(uri);

        GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(5);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        this.redisConnectionPool = ConnectionPoolSupport.createGenericObjectPool(
                () -> redisClient.connect(), poolConfig);
    }

    @PreDestroy
    public void shutdown() {
        this.redisConnectionPool.close();
        this.redisClient.shutdown();
    }

    public <T> T executeSync(SyncCommandCallback<T> callback) {
        try(StatefulRedisConnection<String, String> conn = redisConnectionPool.borrowObject()) {
            conn.setAutoFlushCommands(true);
            RedisCommands<String, String> commands = conn.sync();
            return callback.doInConnection(commands);
        }catch (Exception e) {
            logger.warn("executeSync redis failed.", e);
            throw new RuntimeException(e);
        }
    }

    // Load Lua script from classpath file and return SHA as string.
    public String loadScriptFromClasspath(String classPathFile) {
        String sha = executeSync(commands -> {
            try {
                return commands.scriptLoad(ClassPathUtil.readFile(classPathFile));
            }catch (IOException e) {
                throw new UncheckedIOException("load from classpath failed: " + classPathFile, e);
            }
        });
        if(logger.isInfoEnabled())
            logger.info("load script {} from {}", sha, classPathFile);
        return sha;
    }

    // Load Lua script and return SHA as string.
    String loadScript(String scriptContent) {
        return executeSync(commands -> commands.scriptLoad(scriptContent));
    }

    public Boolean executeScriptReturnBoolean(String sha, String[] keys, String[] values) {
        return executeSync(commands ->
                commands.evalsha(sha, ScriptOutputType.BOOLEAN, keys, values));
    }

    public String executeScriptReturnString(String sha, String[] keys, String[] values) {
        return executeSync(commands ->
                commands.evalsha(sha, ScriptOutputType.VALUE, keys, values));
    }

    public String get(String key) {
        return executeSync(commands -> commands.get(key));
    }
    public void publish(String topic, String data) {
        executeSync(commands -> commands.publish(topic, data));
    }
    public List<String> lrange(String key, long start, long end) {
        return executeSync(commands ->
                commands.lrange(key, start, end));
    }
    public List<String> zrangeByScore(String key, long start, long end) {
        return executeSync(commands ->
                commands.zrangebyscore(key, Range.create(start, end)));
    }

    public void subscribe(String channel, Consumer<String> listener) {
        StatefulRedisPubSubConnection<String, String> conn = this.redisClient.connectPubSub();
        conn.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                listener.accept(message);
            }
        });
        conn.sync().subscribe(channel);
    }
}
