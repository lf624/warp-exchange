package com.learn.exchange.db;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Stream;

// 对于 JdbcTemplate 的简单封装
@Component
public class DbTemplate {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final JdbcTemplate jdbcTemplate;

    // Entity class --> Mapper
    private Map<Class<?>, Mapper<?>> classMapping;

    public DbTemplate(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        String pkg = getClass().getPackageName();
        int pos = pkg.lastIndexOf(".");
        String basePackage = pkg.substring(0, pos) + ".model"; // com.learn.exchange.model
        List<Class<?>> entities = scanEntities(basePackage);
        Map<Class<?>, Mapper<?>> classMapping = new HashMap<>();
        try {
            for(Class<?> clazz : entities) {
                logger.info("Found class: " + clazz.getName());
                Mapper<?> mapper = new Mapper<>(clazz);
                classMapping.put(clazz, mapper);
            }
        }catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        this.classMapping = classMapping;
    }

    public JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    public String getTable(Class<?> clazz) {
        Mapper<?> mapper = this.classMapping.get(clazz);
        if(mapper == null)
            throw new RuntimeException("Entity not registered: " + clazz.getSimpleName());
        return mapper.tableName;
    }

    // 根据 id 获得实例，没有返回 null
    public <T> T fetch(Class<T> clazz, Object id) {
        Mapper<T> mapper = getMapper(clazz);
        if(logger.isDebugEnabled())
            logger.debug("SQL: {}", mapper.selectSQL);
        List<T> list = jdbcTemplate.query(mapper.selectSQL, mapper.resultSetExtractor, id);
        if (list.isEmpty())
            return null;
        return list.get(0);
    }

    // 根据 id 获得实例，没有抛出异常
    public <T> T get(Class<T> clazz, Object id) {
        T t = fetch(clazz, id);
        if(t == null)
            throw new EntityNotFoundException(clazz.getSimpleName());
        return t;
    }

    public <T> void delete(Class<T> clazz, Object id) {
        Mapper<T> mapper = getMapper(clazz);
        if(logger.isDebugEnabled())
            logger.debug("SQL: {}", mapper.deleteSQL);
        jdbcTemplate.update(mapper.deleteSQL, id);
    }

    public <T> void delete(T bean) {
        try {
            Mapper<?> mapper = getMapper(bean.getClass());
            delete(bean.getClass(), mapper.getIdValue(bean));
        }catch (ReflectiveOperationException e) {
            throw new PersistenceException(e);
        }
    }

    public <T> void update(T bean) {
        try {
            Mapper<?> mapper = getMapper(bean.getClass());
            Object[] args = new Object[mapper.updatableProperties.size() + 1];
            int n = 0;
            for(AccessibleProperty p : mapper.updatableProperties) {
                args[n++] = p.get(bean);
            }
            args[n] = mapper.getIdValue(bean);
            if(logger.isDebugEnabled())
                logger.debug("SQL: {}", mapper.updateSQL);
            jdbcTemplate.update(mapper.updateSQL, args);
        }catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> void insert(T bean) {
        doInsert(bean, false);
    }
    public <T> void insertIgnore(T bean) {
        doInsert(bean, true);
    }
    public <T> void insert(List<T> beans) {
        for(T bean : beans)
            doInsert(bean, false);
    }
    public <T> void insertIgnore(List<T> beans) {
        for(T bean : beans)
            doInsert(bean, true);
    }
    public <T> void insert(Stream<T> beans) {
        beans.forEach(bean -> doInsert(bean, false));
    }
    public <T> void insertIgnore(Stream<T> beans) {
        beans.forEach(bean -> doInsert(bean, true));
    }

    <T> void doInsert(T bean, boolean isIgnore) {
        try {
            int rows = 0;
            final Mapper<?> mapper = getMapper(bean.getClass());
            Object[] args = new Object[mapper.insertableProperties.size()];
            int n = 0;
            for(AccessibleProperty p : mapper.insertableProperties)
                args[n++] = p.get(bean);
            if(logger.isDebugEnabled())
                logger.debug("SQL: {}", isIgnore ? mapper.insertIgnoreSQL : mapper.insertSQL);
            if(mapper.id.isIdentityId()) {
                // 使用 identityId
                KeyHolder keyHolder = new GeneratedKeyHolder();
                rows = jdbcTemplate.update(new PreparedStatementCreator() {
                    @Override
                    public PreparedStatement createPreparedStatement(Connection conn) throws SQLException {
                        PreparedStatement ps = conn.prepareStatement(
                                isIgnore ? mapper.insertIgnoreSQL : mapper.insertSQL, Statement.RETURN_GENERATED_KEYS);
                        for(int i = 0; i < args.length; i++)
                            ps.setObject(i + 1, args[i]);
                        return ps;
                    }
                }, keyHolder);
                if(rows == 1) {
                    Number num = keyHolder.getKey();
                    if(num instanceof BigInteger key)
                        num = key.longValueExact();
                    mapper.id.set(bean, num);
                }
            } else {
                // id 已指定
                rows = jdbcTemplate.update(isIgnore ? mapper.insertIgnoreSQL : mapper.insertSQL, args);
            }
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException(e);
        }
    }

    // 由 class 获得 mapper
    @SuppressWarnings("unchecked")
    <T> Mapper<T> getMapper(Class<T> clazz) {
        Mapper<T> mapper = (Mapper<T>) this.classMapping.get(clazz);
        if(mapper == null)
            throw new RuntimeException("Target class is not a registered entity: " + clazz.getName());
        return mapper;
    }

    private static List<Class<?>> scanEntities(String basePackage) {
        var provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> entities = new ArrayList<>();
        Set<BeanDefinition> beans = provider.findCandidateComponents(basePackage);
        for(BeanDefinition bean : beans) {
            try {
                entities.add(Class.forName(bean.getBeanClassName()));
            }catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return entities;
    }

    public String exportDDL() {
        return String.join("\n\n", this.classMapping.values()
                .stream()
                .map(Mapper::ddl)
                .sorted()
                .toArray(String[]::new));
    }

    // 链式查询
    @SuppressWarnings("rawtypes")
    public Select select(String... selectField) {
        return new Select(new Criteria(this), selectField);
    }

    public <T> From<T> from(Class<T> entityClass) {
        Mapper<T> mapper = getMapper(entityClass);
        return new From<>(new Criteria<>(this), mapper);
    }
}
