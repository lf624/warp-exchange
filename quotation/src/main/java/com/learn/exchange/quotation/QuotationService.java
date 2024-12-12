package com.learn.exchange.quotation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.learn.exchange.enums.BarType;
import com.learn.exchange.message.AbstractMessage;
import com.learn.exchange.message.TickMessage;
import com.learn.exchange.messaging.MessageConsumer;
import com.learn.exchange.messaging.Messaging;
import com.learn.exchange.messaging.MessagingFactory;
import com.learn.exchange.model.quotation.*;
import com.learn.exchange.model.support.AbstractBarEntity;
import com.learn.exchange.redis.RedisCache;
import com.learn.exchange.redis.RedisService;
import com.learn.exchange.support.LoggerSupport;
import com.learn.exchange.util.IpUtil;
import com.learn.exchange.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Supplier;

@Component
public class QuotationService extends LoggerSupport {

    @Autowired
    private ZoneId zoneId;
    @Autowired
    private RedisService redisService;
    @Autowired
    QuotationDbService quotationDbService;

    @Autowired
    private MessagingFactory messagingFactory;
    private MessageConsumer tickConsumer;

    private String shaUpdateRecentTicksLua = null;
    private String shaUpdateBarLua = null;

    private long sequenceId;

    @PostConstruct
    public void init() {
        // init redis
        this.shaUpdateRecentTicksLua = this.redisService.loadScriptFromClasspath("/redis/update-recent-ticks.lua");
        this.shaUpdateBarLua = this.redisService.loadScriptFromClasspath("/redis/update-bar.lua");
        // init mq
        String groupId = Messaging.Topic.TICK.name() + "_" + IpUtil.getHostId();
        this.tickConsumer = this.messagingFactory.createBatchMessageListener(Messaging.Topic.TICK,
                groupId, this::processMessages);
    }

    @PreDestroy
    public void shutdown() {
        if(this.tickConsumer != null) {
            this.tickConsumer.stop();
            this.tickConsumer = null;
        }
    }

    // 处理接收到的消息
    public void processMessages(List<AbstractMessage> messages) {
        for(AbstractMessage message : messages)
            processMessage((TickMessage) message);
    }

    // 处理一个 Tick 消息
    void processMessage(TickMessage message) {
        if(message.sequenceId < this.sequenceId)
            return;
        if(logger.isDebugEnabled())
            logger.debug("process ticks: sequenceId = {}, {} ticks...", message.sequenceId, message.ticks.size());
        this.sequenceId = message.sequenceId;
        // 对一个Tick消息中的多个Tick先进行合并
        final long cratedAt = message.createdAt;
        StringJoiner ticksStrJoiner = new StringJoiner(",", "[", "]");
        StringJoiner ticksJoiner = new StringJoiner(",", "[", "]");
        BigDecimal openPrice = BigDecimal.ZERO;
        BigDecimal closePrice = BigDecimal.ZERO;
        BigDecimal highPrice = BigDecimal.ZERO;
        BigDecimal lowPrice = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;
        for(TickEntity tick : message.ticks) {
            String json = tick.toJson();
            ticksStrJoiner.add("\"" + json + "\"");
            ticksJoiner.add(json);
            if(openPrice.signum() == 0) {
                openPrice = tick.price;
                closePrice = tick.price;
                highPrice = tick.price;
                lowPrice = tick.price;
            } else {
                closePrice = tick.price;
                highPrice = highPrice.max(tick.price);
                lowPrice = lowPrice.min(tick.price);
            }
            quantity = quantity.add(tick.quantity);
        }

        long sec = cratedAt / 1000; // 转换为以秒为单位
        long min = sec / 60;
        long hour = min / 60;
        // 确保每个时间粒度的开始时间是准确对齐的
        long secStartTime = sec * 1000; // 秒K的开始时间
        long minStartTime = min * 60 * 1000; // 分钟K的开始时间
        long hourStartTime = hour * 3600 * 1000; // 小时K的开始时间
        long dayStartTime = Instant.ofEpochMilli(hourStartTime)
                .atZone(zoneId)
                .withHour(0)
                .toEpochSecond() * 1000; // 日K的开始时间，与TimeZone相关
        String ticksData = ticksJoiner.toString();
        if(logger.isDebugEnabled())
            logger.debug("generate ticks data: {}", ticksData);
        // 写入缓存，更新 最近ticks 列表
        Boolean tickOk = this.redisService.executeScriptReturnBoolean(this.shaUpdateRecentTicksLua,
                new String[] {RedisCache.Key.RECENT_TICKS},
                new String[] {String.valueOf(this.sequenceId), ticksData, ticksStrJoiner.toString()});
        if(!tickOk) {
            logger.warn("ticks are ignored by Redis.");
            return;
        }
        // 保存Tick至数据库
        this.quotationDbService.saveTicks(message.ticks);

        // 更新各种类型的K线
        String strCreateBar = this.redisService.executeScriptReturnString(this.shaUpdateBarLua,
                new String[] {RedisCache.Key.SEC_BARS, RedisCache.Key.MIN_BARS, RedisCache.Key.HOUR_BARS, RedisCache.Key.DAY_BARS},
                new String[] {String.valueOf(this.sequenceId), String.valueOf(secStartTime), String.valueOf(minStartTime),
                    String.valueOf(hourStartTime), String.valueOf(dayStartTime), String.valueOf(openPrice),
                    String.valueOf(highPrice), String.valueOf(lowPrice), String.valueOf(closePrice),
                    String.valueOf(quantity)});
        logger.info("returned created bars: {}", strCreateBar);
        // 将Redis返回的K线保存至数据库
        Map<BarType, BigDecimal[]> barMap = JsonUtil.readJson(strCreateBar, TYPE_BARS);
        if(!barMap.isEmpty()) {
            SecBarEntity secBar = createBar(SecBarEntity::new, barMap.get(BarType.SEC));
            MinBarEntity minBar = createBar(MinBarEntity::new, barMap.get(BarType.MIN));
            HourBarEntity hourBar = createBar(HourBarEntity::new, barMap.get(BarType.HOUR));
            DayBarEntity dayBar = createBar(DayBarEntity::new, barMap.get(BarType.DAY));
            this.quotationDbService.saveBars(secBar, minBar, hourBar, dayBar);
        }
    }

    private static final TypeReference<Map<BarType, BigDecimal[]>> TYPE_BARS = new TypeReference<>() {
    };

    static <T extends AbstractBarEntity> T createBar(Supplier<T> fn, BigDecimal[] data) {
        if(data == null)
            return null;
        T t = fn.get();
        t.startTime = data[0].longValue();
        t.openPrice = data[1];
        t.highPrice = data[2];
        t.lowPrice = data[3];
        t.closePrice = data[4];
        t.quantity = data[5];
        return t;
    }
}
