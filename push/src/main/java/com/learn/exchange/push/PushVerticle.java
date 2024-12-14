package com.learn.exchange.push;

import com.learn.exchange.bean.AuthToken;
import com.learn.exchange.message.NotificationMessage;
import com.learn.exchange.util.JsonUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PushVerticle extends AbstractVerticle {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String hmacKey;

    private final int serverPort;

    // 所有 Handler
    private final Map<String, Boolean> handlerMap = new ConcurrentHashMap<>(1000);
    // 用户ID -> Handlers
    private final Map<Long, Set<String>> userToHandlersMap = new ConcurrentHashMap<>(1000);
    // Handler -> 用户ID
    private final Map<String, Long> handlerToUserMap = new ConcurrentHashMap<>(1000);

    public PushVerticle(String hmacKey, int serverPort) {
        this.hmacKey = hmacKey;
        this.serverPort = serverPort;
    }

    @Override
    public void start() throws Exception {
        // 创建VertX HttpServer
        HttpServer server = vertx.createHttpServer();

        // 创建路由
        Router router = Router.router(vertx);

        // 处理 GET 请求 `/notification`
        router.get("/notification").handler(requestHandler -> {
            HttpServerRequest request = requestHandler.request();
            // 解析请求参数 token 并获取用户 ID
            Supplier<Long> supplier = () -> {
                String tokenStr = request.getParam("token");
                if(tokenStr != null && !tokenStr.isEmpty()) {
                    AuthToken auth = AuthToken.fromSecureString(tokenStr, this.hmacKey);
                    if(!auth.isExpired())
                        return auth.userId();
                }
                return null;
            };
            final Long userId = supplier.get();
            logger.info("parse user id from token: {}", userId);
            // 将 HTTP 请求升级为 WebSocket 连接
            request.toWebSocket(ar -> {
                if(ar.succeeded())
                    initWebSocket(ar.result(), userId);
            });
        });

        router.get("/actuator/health").respond(ctx -> ctx.response()
                .putHeader("Content-Type", "application/json")
                .end("{\"status\":\"UP\"}"));

        // 其他未匹配请求返回 404 错误
        router.get().respond(ctx -> ctx.response()
                .setStatusCode(404)
                .setStatusMessage("No Route Found")
                .end());

        // 绑定路由，启动 HTTP 服务器并监听端口
        server.requestHandler(router).listen(this.serverPort, request -> {
            // 异步回调操作
            if(request.succeeded())
                logger.info("Vertx started on port(s): {} (http) with context path ''", this.serverPort);
            else {
                logger.error("Start http server failed on port " + this.serverPort, request.cause());
                vertx.close();
                System.exit(1);
            }
        });
    }

    void initWebSocket(ServerWebSocket webSocket, Long userId) {
        String handlerId = webSocket.textHandlerID();
        logger.info("WebSocket accept userId: {}, handlerId: {}", userId, handlerId);
        // 设置消息处理器
        webSocket.textMessageHandler(str -> logger.info("text message: {}", str));
        // 设置异常处理器
        webSocket.exceptionHandler(t -> {
            logger.error("webSocket error: " + t.getMessage(), t);
        });
        // 设置关闭处理器
        webSocket.closeHandler(e -> {
            unsubscribeClient(handlerId);
            unsubscribeUser(handlerId, userId);
            logger.info("websocket closed: {}", handlerId);
        });
        // 建立映射关系
        subscribeClient(handlerId);
        subscribeUser(handlerId, userId);
        // 发送欢迎消息
        if(userId == null)
            webSocket.writeTextMessage(
                    "{\"type\":\"status\",\"status\":\"connected\",\"message\":\"connected as anonymous user\"}");
        else
            webSocket.writeTextMessage("{\"type\":\"status\",\"status\":\"connected\",\"message\":\"connected as user\",\"userId\":"
                    + userId + "}");
    }

    void subscribeClient(String handlerId) {
        this.handlerMap.put(handlerId, true);
    }
    void unsubscribeClient(String handlerId) {
        this.handlerMap.remove(handlerId);
    }
    void subscribeUser(String handlerId, Long userId) {
        if(userId == null)
            return;
        handlerToUserMap.put(handlerId, userId);
//        Set<String> set = userToHandlersMap.get(userId);
//        if (set == null) {
//            set = new HashSet<>();
//            userToHandlersMap.put(userId, set);
//        }
//        set.add(handlerId);
        // 用更简洁的computeIfAbsent()替代
        userToHandlersMap.computeIfAbsent(userId, k -> new HashSet<>())
                .add(handlerId);
        logger.info("subscribe user {} {} ok.", userId, handlerId);
    }
    void unsubscribeUser(String handlerId, Long userId) {
        if(userId == null)
            return;
        handlerToUserMap.remove(handlerId);
//        Set<String> set = userToHandlersMap.get(userId);
//        if (set != null) {
//            set.remove(handlerId);
//        }
        // 简化上述代码
        userToHandlersMap.computeIfPresent(userId, (k, s) -> {
            s.remove(handlerId);
            if(s.isEmpty()) {
                logger.info("unsubscribe user {} {} ok: cleared.",k, handlerId);
                return null;
            } else {
                logger.info("unsubscribe user {} {} ok: but still others online.", k, handlerId);
                return s;
            }
        });
    }

    // 将 notification 消息推送到 websocket 客户端
    public void broadcast(String text) {
        NotificationMessage message = null;
        try {
            message = JsonUtil.readJson(text, NotificationMessage.class);
        } catch (Exception e) {
            logger.info("invalid message format: {}", text);
            return;
        }
        if(message.userId == null) {
            if(logger.isInfoEnabled())
                logger.info("try broadcast message to all: {}", text);
            // 没有用户ID时，推送给所有连接
            EventBus eb = vertx.eventBus();
            for(String handler : this.handlerMap.keySet()) {
                eb.send(handler, text);
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("try broadcast message to user {}: {}", message.userId, text);
            }
            // 推送给指定用户
            Set<String> handlers = this.userToHandlersMap.get(message.userId);
            if(handlers != null) {
                EventBus eb = vertx.eventBus();
                for(String handler : handlers) {
                    eb.send(handler, text);
                }
            }
        }
    }
}
