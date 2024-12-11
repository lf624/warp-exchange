package com.learn.exchange.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.exchange.ApiError;
import com.learn.exchange.ApiErrorResponse;
import com.learn.exchange.ApiException;
import com.learn.exchange.bean.OrderBookBean;
import com.learn.exchange.bean.OrderRequestBean;
import com.learn.exchange.bean.SimpleMatchDetailRecord;
import com.learn.exchange.ctx.UserContext;
import com.learn.exchange.message.ApiResultMessage;
import com.learn.exchange.message.event.OrderCancelEvent;
import com.learn.exchange.message.event.OrderRequestEvent;
import com.learn.exchange.model.trade.OrderEntity;
import com.learn.exchange.redis.RedisCache;
import com.learn.exchange.redis.RedisService;
import com.learn.exchange.service.HistoryService;
import com.learn.exchange.service.SendEventService;
import com.learn.exchange.service.TradingEngineApiProxyService;
import com.learn.exchange.support.AbstractApiController;
import com.learn.exchange.util.IdUtil;
import com.learn.exchange.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class TradingApiController extends AbstractApiController {
    @Autowired
    private HistoryService historyService;
    @Autowired
    private SendEventService sendEventService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradingEngineApiProxyService tradingEngineApiProxyService;

    private Long asyncTimeout = Long.valueOf(500);
    private String timeoutJson = null;

    Map<String, DeferredResult<ResponseEntity<String>>> deferredResultMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        this.redisService.subscribe(RedisCache.Topic.TRADING_API_RESULT, this::onApiResultMessage);
    }

    @GetMapping("timestamp")
    public Map<String, Long> timestamp() {
        return Map.of("timestamp", System.currentTimeMillis());
    }

    @ResponseBody
    @GetMapping(value = "/orderBook", produces = "application/json")
    public String getOrderBook() {
        String data = redisService.get(RedisCache.Key.ORDER_BOOK);
        return data == null ? OrderBookBean.EMPTY : data;
    }

    @ResponseBody
    @GetMapping(value = "/ticks", produces = "application/json")
    public String getRecentTicks() {
        List<String> data = redisService.lrange(RedisCache.Key.RECENT_TICKS, 0, -1);
        if(data == null || data.isEmpty())
            return "[]";
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for(String d : data)
            sj.add(d);
        return sj.toString();
    }

    @GetMapping(value = "/history/orders")
    public List<OrderEntity> getHistoryOrders(
            @RequestParam(value = "maxResults", defaultValue = "100") int maxResults) {
        if(maxResults < 1 || maxResults > 1000)
            throw new ApiException(ApiError.PARAMETER_INVALID, "maxResults", "Invalid maxResults value.");
        return historyService.getHistoryOrders(UserContext.getRequiredUserId(), maxResults);
    }

    @GetMapping(value = "/history/orders/{orderId}/matches")
    public List<SimpleMatchDetailRecord> getOrderMatchDetails(@PathVariable("orderId") Long orderId)
            throws IOException{
        Long userId = UserContext.getRequiredUserId();
        // 查找活动的 order
        String strOpenOrder = tradingEngineApiProxyService.get("/internal/" + userId + "/orders/" + orderId);
        if(strOpenOrder.equals("null")) {
            // 查找历史order
            OrderEntity orderEntity = historyService.getHistoryOrder(userId, orderId);
            if(orderEntity == null)
                // 该 order 未找到
                throw new ApiException(ApiError.ORDER_NOT_FOUND, orderId.toString(), "order not found.");
        }
        return historyService.getHistoryDetailRecord(orderId);
    }

    // cancel an order
    @PostMapping(value = "/orders/{orderId}/cancel", produces = "application/json")
    @ResponseBody
    public DeferredResult<ResponseEntity<String>> cancelOrder(@PathVariable("orderId") Long orderId)
            throws IOException {
        Long userId = UserContext.getRequiredUserId();
        String orderStr = tradingEngineApiProxyService.get("/internal/" + userId + "/orders/" + orderId);
        if(orderStr.equals("null")) {
            throw new ApiException(ApiError.ORDER_NOT_FOUND, orderId.toString(), "Active order not found,");
        }
        // 创建订单取消消息(事件)
        final String refId = IdUtil.generateUniqueId();
        var event = new OrderCancelEvent();
        event.refId = refId;
        event.userId = userId;
        event.refOrderId = orderId;
        event.createdAt = System.currentTimeMillis();

        // 与下面创建订单类似
        // 超时的返回
        ResponseEntity<String> timeout = new ResponseEntity<>(getTimeoutJson(), HttpStatus.BAD_REQUEST);
        // 正常的异步返回
        DeferredResult<ResponseEntity<String>> deferred = new DeferredResult<>(this.asyncTimeout, timeout);
        deferred.onTimeout(() -> {
            logger.warn("deferred order {} cancel request refId={}, timeout.", orderId, refId);
            this.deferredResultMap.remove(refId);
        });
        // 跟踪 deferred
        this.deferredResultMap.put(refId, deferred);
        this.sendEventService.sendMessage(event);
        return deferred;
    }

    // create a new order
    @PostMapping(value = "/orders", produces = "application/json")
    @ResponseBody
    public DeferredResult<ResponseEntity<String>> createOrder(@RequestBody OrderRequestBean orderRequest)
            throws IOException{
        final Long userId = UserContext.getRequiredUserId();
        orderRequest.validate();
        // 消息的Reference ID
        final String refId = IdUtil.generateUniqueId();
        // 创建订单请求消息
        var event = new OrderRequestEvent();
        event.refId = refId;
        event.userId = userId;
        event.direction = orderRequest.direction;
        event.price = orderRequest.price;
        event.quantity = orderRequest.quantity;
        event.createdAt = System.currentTimeMillis();

        // 超时的返回
        ResponseEntity<String> timeout = new ResponseEntity<>(getTimeoutJson(), HttpStatus.BAD_REQUEST);
        // 正常的异步返回
        DeferredResult<ResponseEntity<String>> deferred = new DeferredResult<>(this.asyncTimeout, timeout);
        deferred.onTimeout(() -> {
            logger.warn("deferred order request refId: {}", event.refId);
            this.deferredResultMap.remove(event.refId);
        });
        // 跟踪 deferred
        this.deferredResultMap.put(event.refId, deferred);
        // 发送到下游定序
        this.sendEventService.sendMessage(event);
        // 延迟响应，在 onApiResultMessage() 中返回响应
        return deferred;
    }

    // message callback
    // 收到Redis的消息结果推送
    public void onApiResultMessage(String msg) {
        logger.info("on subscribe message: {}", msg);
        try {
            ApiResultMessage message = objectMapper.readValue(msg, ApiResultMessage.class);
            if(message.refId != null) {
                // 根据 refId 找 DeferredResult
                DeferredResult<ResponseEntity<String>> deferred = this.deferredResultMap.remove(message.refId);
                if(deferred != null) {
                    if(message.error != null) {
                        String error = objectMapper.writeValueAsString(message.error);
                        ResponseEntity<String> resp = new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
                        deferred.setResult(resp);
                    } else {
                        // 找到 DeferredResult 后设置响应结果
                        ResponseEntity<String> resp = new ResponseEntity<>(
                                JsonUtil.writeJson(message.result), HttpStatus.OK);
                        deferred.setResult(resp); // 这时才真正返回响应
                    }
                }
            }
        }catch (Exception e) {
            logger.error("Invalid ApiResultMessage: " + msg, e);
        }
    }

    private String getTimeoutJson() throws IOException {
        if(timeoutJson == null)
            this.timeoutJson = this.objectMapper.writeValueAsString(
                    new ApiErrorResponse(ApiError.OPERATION_TIMEOUT, null, ""));
        return timeoutJson;
    }

    @ResponseBody
    @GetMapping(value = "/assets", produces = "application/json")
    public String getAssets() throws IOException {
        return tradingEngineApiProxyService.get("/internal/" + UserContext.getRequiredUserId() + "/assets");
    }

    @ResponseBody
    @GetMapping(value = "/orders/{orderId}", produces = "application/json")
    public String getOrder(@PathVariable("orderId") Long orderId) throws IOException{
        final Long userId = UserContext.getRequiredUserId();
        return tradingEngineApiProxyService.get("/internal/" + userId + "/orders/" + orderId);
    }

    @ResponseBody
    @GetMapping(value = "/orders", produces = "application/json")
    public String getOrders() throws IOException {
        return tradingEngineApiProxyService.get("/internal/" + UserContext.getRequiredUserId() + "/orders");
    }

    @ResponseBody
    @GetMapping(value = "/bars/day", produces = "application/json")
    public String getDayBars() {
        long end = System.currentTimeMillis();
        long start = end - 366L * 86400_000;
        return getBars(RedisCache.Key.DAY_BARS, start, end);
    }

    @ResponseBody
    @GetMapping(value = "/bars/hour", produces = "application/json")
    public String getHourBars() {
        long end = System.currentTimeMillis();
        long start = end - 720L * 3600_000;
        return getBars(RedisCache.Key.HOUR_BARS, start, end);
    }

    @ResponseBody
    @GetMapping(value = "/bars/min", produces = "application/json")
    public String getMinBars() {
        long end = System.currentTimeMillis();
        long start = end - 1440 * 60_000;
        return getBars(RedisCache.Key.MIN_BARS, start, end);
    }

    @ResponseBody
    @GetMapping(value = "/bars/sec", produces = "application/json")
    public String getSecBars() {
        long end = System.currentTimeMillis();
        long start = end - 3600 * 1_000;
        return getBars(RedisCache.Key.SEC_BARS, start, end);
    }

    private String getBars(String key, long start, long end) {
        List<String> data = redisService.zrangeByScore(key, start, end);
        if(data == null || data.isEmpty())
            return "[]";
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for(String d :data)
            sj.add(d);
        return sj.toString();
    }
}
