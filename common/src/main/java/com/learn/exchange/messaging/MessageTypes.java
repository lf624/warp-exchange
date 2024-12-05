package com.learn.exchange.messaging;

import com.learn.exchange.message.AbstractMessage;
import com.learn.exchange.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

// 用反射获得所有 message 类型，并提供序列化和反序列化方法
@Component
public class MessageTypes {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final String messagePackage = AbstractMessage.class.getPackageName();

    final Map<String, Class<? extends AbstractMessage>> messageTypes = new HashMap<>();

    private static final char SEP = '#'; // separator

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        logger.info("find message classes...");
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new TypeFilter() {
            @Override
            public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                    throws IOException {
                String name = metadataReader.getClassMetadata().getClassName();
                Class<?> clazz = null;
                try {
                    clazz = Class.forName(name);
                }catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                return AbstractMessage.class.isAssignableFrom(clazz);
            }
        });
        Set<BeanDefinition> beans = provider.findCandidateComponents(messagePackage);
        for(BeanDefinition bean : beans) {
            try {
                Class<?> clazz = Class.forName(bean.getBeanClassName());
                logger.info("found message class: {}", clazz.getName());
                if(messageTypes.put(clazz.getName(), (Class<? extends AbstractMessage>) clazz) != null)
                    throw new RuntimeException("Duplicate message class name: " + clazz.getName());
            }catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String serialize(AbstractMessage message) {
        String type = message.getClass().getName();
        String json = JsonUtil.writeJson(message);
        return type + "#" + json;
    }

    public AbstractMessage deserialize(String data) {
        int pos = data.indexOf("#");
        if(pos == -1)
            throw new RuntimeException("Unable to handle message with data: " + data);
        String type = data.substring(0, pos);
        Class<? extends AbstractMessage> clazz = messageTypes.get(type);
        if(clazz == null)
            throw new RuntimeException("Unable to handle message with type: " + type);
        String json = data.substring(pos + 1);
        return JsonUtil.readJson(json, clazz);
    }

    public List<AbstractMessage> deserialize(List<String> dataList) {
        List<AbstractMessage> res = new ArrayList<>(dataList.size());
        for(String data : dataList)
            res.add(deserialize(data));
        return res;
    }

    public List<AbstractMessage> deserializeConsumerRecord(List<ConsumerRecord<String, String>> dataList) {
        List<AbstractMessage> res = new ArrayList<>(dataList.size());
        for(ConsumerRecord<String, String> data : dataList)
            res.add(deserialize(data.value()));
        return res;
    }
}
