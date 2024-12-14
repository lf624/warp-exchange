package com.learn.exchange.push;

import com.learn.exchange.redis.RedisCache;
import com.learn.exchange.support.LoggerSupport;
import io.vertx.core.Vertx;
import io.vertx.redis.client.*;
import io.vertx.redis.client.impl.types.BulkType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PushService extends LoggerSupport {

    @Value("${server.port}")
    private int serverPort;

    @Value("${exchange.config.hmac-key}")
    String hmacKey;

    @Value("${spring.redis.standalone.host:localhost}")
    private String redisHost;
    @Value("${spring.redis.standalone.port:6379}")
    private int redisPort;
    @Value("${spring.redis.standalone.password:}")
    private String redisPassword;
    @Value("${spring.redis.standalone.database:0}")
    private int redisDatabase = 0;

    private Vertx vertx;

    @PostConstruct
    public void startVertx() {
        logger.info("start vertx...");
        // 启动 Vert.x
        this.vertx = Vertx.vertx();

        // 创建一个Vert.x Verticle组件
        var push = new PushVerticle(this.hmacKey, this.serverPort);
        vertx.deployVerticle(push);

        String url = "redis://" + (redisPassword.isEmpty() ? "" : ":" + redisPassword + "@") +
                redisHost + ":" + redisPort + "/" + redisDatabase;
        logger.info("create redis client: {}", url);
        // 创建 redis 客户端
        Redis redis = Redis.createClient(vertx, url);

        // 与 redis 建立连接
        redis.connect().onSuccess(conn -> {
            logger.info("connect to redis ok.");
            // 设置一个消息处理器，捕获来自 Redis 服务器的 push 消息
            conn.handler(response -> {
                if(response.type() == ResponseType.PUSH) {
                    int size = response.size();
                    if(size == 3) { // 典型的消息结构 ["message", "channel_name", "message_content"]
                        Response type = response.get(2);
                        if(type instanceof BulkType) { // Redis Bulk String 数据类型
                            String msg = type.toString();
                            if(logger.isDebugEnabled())
                                logger.debug("received push message: {}", msg);
                            // 由push verticle组件处理该通知
                            push.broadcast(msg);
                        }
                    }
                }
            });
            logger.info("try subscribe...");
            // 发送订阅命令，订阅频道 "notification"
            conn.send(Request.cmd(Command.SUBSCRIBE).arg(RedisCache.Topic.NOTIFICATION))
                    .onSuccess(resp -> logger.info("subscribe ok."))
                    .onFailure(err -> {
                        logger.error("subscribe failed.", err);
                        System.exit(1);
                    });
        }).onFailure(err -> {
            logger.error("connect to redis failed.", err);
            System.exit(1);
        });
    }

    void exit(int exitCode) {
        this.vertx.close();
        System.exit(exitCode);
    }
}
