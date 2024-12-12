package com.learn.exchange.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;

public class JsonUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapperForInternal();

    public static ObjectMapper createObjectMapperForInternal() {
        final ObjectMapper mapper = new ObjectMapper();
        // 确保序列化过程中包含所有字段
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        // 关闭一些特性
        // 忽略反序列化过程中遇到的未知字段
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 允许序列化空对象
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // 使用 ISO-8601 格式序列化日期
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    // ....

    public static String writeJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        }catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
    //...

    public static <T> T readJson(String str, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(str, clazz);
        } catch (JsonProcessingException e) {
            logger.warn("can't read json: " + str, e);
            throw new RuntimeException(e);
        }
    }

    public static  <T> T readJson(String str, TypeReference<T> ref) {
        try {
            return OBJECT_MAPPER.readValue(str, ref);
        } catch (JsonProcessingException e) {
            logger.warn("can't read json: " + str, e);
            throw new RuntimeException(e);
        }
    }

    // ...
}
