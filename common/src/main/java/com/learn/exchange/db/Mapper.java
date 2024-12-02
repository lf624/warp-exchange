package com.learn.exchange.db;

import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

import static java.lang.String.join;

final class Mapper<T> {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final Class<T> entityClass;
    final Constructor<T> constructor;
    final String tableName;

    final AccessibleProperty id;
    final List<AccessibleProperty> allProperties;
    final Map<String, AccessibleProperty> allPropertiesMap;
    final List<AccessibleProperty> insertableProperties;
    final List<AccessibleProperty> updatableProperties;
    final Map<String, AccessibleProperty> updatablePropertiesMap;

    final ResultSetExtractor<List<T>> resultSetExtractor;

    final String selectSQL;
    final String insertSQL;
    final String insertIgnoreSQL;
    final String updateSQL;
    final String deleteSQL;

    public Mapper(Class<T> clazz) throws NoSuchMethodException {
        List<AccessibleProperty> all = getProperties(clazz);
        AccessibleProperty[] ids = all.stream().filter(AccessibleProperty::isId)
                .toArray(AccessibleProperty[]::new);
        if(ids.length != 1)
            throw new RuntimeException("Require unique @Id class: " + clazz);
        this.id = ids[0];
        this.allProperties = all;
        this.allPropertiesMap = buildPropertiesMap(all);
        this.insertableProperties = all.stream().filter(AccessibleProperty::isInsertable)
                .toList(); // 生成的 List 不可变
                // .collect(Collectors.toList()); // 直接 toList() 效率更高，但生成的 List 是不可修改的
        this.updatableProperties = all.stream().filter(AccessibleProperty::isUpdatable).toList();
        this.updatablePropertiesMap = buildPropertiesMap(this.updatableProperties);
        this.entityClass = clazz;
        this.constructor = clazz.getConstructor();
        this.tableName = getTableName(clazz);
        this.selectSQL = "SELECT * FROM " + this.tableName + " WHERE " + this.id.propertyName + " = ?";
        this.insertSQL = "INSERT INTO " + this.tableName + " (" + join(", ",
                this.insertableProperties.stream().map(p -> p.propertyName).toArray(String[]::new)) +
                ") VALUES (" + numOfQuestions(this.insertableProperties.size()) + ")";
        this.insertIgnoreSQL = this.insertSQL.replace("INSERT INTO", "INSERT IGNORE INTO");
        this.updateSQL = "UPDATE " + this.tableName + " SET " + String.join(", ",
                this.updatableProperties.stream().map(p -> p.propertyName + " = ?").toArray(String[]::new)) +
                " WHERE " + this.id.propertyName + " = ?";
        this.deleteSQL = "DELETE FROM " + this.tableName + " WHERE " + this.id.propertyName + " = ?";
        this.resultSetExtractor = new ResultSetExtractor<>() {
            @Override
            public List<T> extractData(ResultSet rs) throws SQLException, DataAccessException {
                final List<T> result = new ArrayList<>();
                final ResultSetMetaData m = rs.getMetaData();
                final int cols = m.getColumnCount();
                final String[] names = new String[cols];
                for(int i = 0; i < cols; i++)
                    names[i] = m.getColumnLabel(i + 1); // the first column is 1
                try {
                    while(rs.next()) {
                        // 获取当前实例，并给字段赋值
                        T bean = newInstance();
                        for(int i = 0; i < cols; i++) {
                            AccessibleProperty p = allPropertiesMap.get(names[i]);
                            if(p != null) {
                                p.set(bean, rs.getObject(i + 1));
                            }
                        }
                        result.add(bean);
                    }
                }catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                return result;
            }
        };
    }

    public T newInstance() throws ReflectiveOperationException {
        return this.constructor.newInstance();
    }

    // 从 Class 获得所有表属性
    private List<AccessibleProperty> getProperties(Class<T> clazz) {
        List<AccessibleProperty> properties = new ArrayList<>();
        for(Field f : clazz.getFields()) {
            if(Modifier.isStatic(f.getModifiers()))
                continue;
            if(f.isAnnotationPresent(Transient.class))
                continue;
            var p = new AccessibleProperty(f);
            logger.debug("found accessible property: {}", p);
            properties.add(p);
        }
        return properties;
    }

    Map<String, AccessibleProperty> buildPropertiesMap(List<AccessibleProperty> props) {
        Map<String, AccessibleProperty> map = new HashMap<>();
        for(AccessibleProperty prop : props)
            map.put(prop.propertyName, prop);
        return map;
    }
    // 获得表名
    private String getTableName(Class<T> clazz) {
        Table table = clazz.getAnnotation(Table.class);
        if(table != null && !table.name().isEmpty())
            return table.name();
        String name = clazz.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    // 给定个数 n 返回 (?,?,....)
    private String numOfQuestions(int n) {
        String[] qs = new String[n];
        return join(", ", Arrays.stream(qs)
                .map(s -> "?")
                .toArray(String[]::new));
    }

    Object getIdValue(Object bean) throws ReflectiveOperationException{
        return this.id.get(bean);
    }

    public String ddl() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("CREATE TABLE ").append(this.tableName).append(" (\n");
        sb.append(String.join(",\n", this.allProperties.stream().sorted((o1, o2) -> {
            if(o1.isId())
                return -1;
            if(o2.isId())
                return 1;
            return o1.propertyName.compareTo(o2.propertyName);
        }).map(p -> "  " + p.propertyName + " " + p.columnDefinition).toArray(String[]::new)));
        sb.append(",\n");
        // 唯一约束
        sb.append(getUniqueKey());
        // 索引
        sb.append(getIndex());
        // 主键
        sb.append("  PRIMARY KEY(").append(this.id.propertyName).append(")\n");
        sb.append(") CHARACTER SET utf8 COLLATE utf8_general_ci AUTO_INCREMENT = 1000;\n");
        return sb.toString();
    }

    String getUniqueKey() {
        Table table = this.entityClass.getAnnotation(Table.class);
        if(table != null) {
            StringBuilder sb = new StringBuilder();
            var uniques = table.uniqueConstraints();
            if(uniques.length > 0) {
                for(UniqueConstraint uni : uniques) {
                    sb.append("  CONSTRAINT ");
                    if(uni.name() == null)
                        sb.append("UNI").append(Arrays.toString(Arrays.stream(uni.columnNames())
                                .map(c -> "_" + c).toArray(String[]::new)));
                    else
                        sb.append(uni.name());
                    sb.append(" UNIQUE (").append(String.join(", ",
                            Arrays.stream(uni.columnNames()).toArray(String[]::new)));
                    sb.append("),\n");
                }
                return sb.toString();
            }
        }
        return "";
    }

    String getIndex() {
        Table table = this.entityClass.getAnnotation(Table.class);
        if(table != null) {
            return Arrays.stream(table.indexes()).map(c -> {
                if(c.unique()) {
                    // 唯一索引
                    String name = c.name().isEmpty() ? "UNI_" + c.columnList().replace(" ", "")
                            .replace(",", "_") : c.name();
                    return "  CONSTRAINT " + name + " UNIQUE (" + c.columnList() + "),\n";
                } else {
                    // 普通索引
                    String name = c.name().isEmpty() ? "IDX_" + c.columnList().replace(" ", "")
                            .replace(",", "_") : c.name();
                    return "  INDEX " + name + " (" + c.columnList() + "),\n";
                }
            }).reduce("", (acc, s) -> acc + s);
        }
        return "";
    }

    static List<String> columnDefinitionSortBy = Arrays.asList("BIT", "BOOL", "TINYINT", "SMALLINT", "MEDIUMINT", "INT",
            "INTEGER", "BIGINT", "FLOAT", "REAL", "DOUBLE", "DECIMAL", "YEAR", "DATE", "TIME", "DATETIME", "TIMESTAMP",
            "VARCHAR", "CHAR", "BLOB", "TEXT", "MEDIUMTEXT");
    static int columnDefinitionSortIndex(String definition) {
        int pos = definition.indexOf("(");
        if(pos > 0)
            definition = definition.substring(0, pos);
        int index = columnDefinitionSortBy.indexOf(definition.toUpperCase());
        return index == (-1) ? Integer.MAX_VALUE : index;
    }
}
