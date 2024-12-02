package com.learn.exchange.db;

import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import java.util.ArrayList;
import java.util.List;

// 持有条件查询信息
final class Criteria<T> {

    DbTemplate db;
    Class<T> clazz;
    Mapper<T> mapper;
    List<String> select = null;
    String where = null;
    List<Object> whereParams = null;
    List<String> orderBy = null;
    int offset = 0;
    int maxResults = 0;
    String table = null;
    // boolean distinct = false;


    Criteria(DbTemplate db) {
        this.db = db;
    }

    String sql() {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(select.isEmpty() ? "*" : String.join(", ", select));
        sql.append(" FROM ").append(this.mapper.tableName);
        if(where != null)
            sql.append(" WHERE ").append(where);
        if(orderBy != null)
            sql.append(" ORDER BY ").append(String.join(", ", orderBy));
        if(offset >= 0 && maxResults > 0)
            sql.append(" LIMIT ?, ?");
        return sql.toString();
    }

    Object[] params() {
        List<Object> params = new ArrayList<>();
        if(where != null) {
            for(Object obj : whereParams) {
                if(obj == null)
                    params.add(null);
                else
                    params.add(obj);
            }
        }
        if(offset >= 0 && maxResults > 0) {
            params.add(offset);
            params.add(maxResults);
        }
        return params.toArray();
    }

    List<T> list() {
        String selectSQL = sql();
        Object[] selectParams = params();
        return db.jdbcTemplate.query(selectSQL, mapper.resultSetExtractor, selectParams);
    }

    T first() {
        this.offset = 0;
        this.maxResults = 1;
        List<T> result = list();
        return result.isEmpty() ? null : result.get(0);
    }
    T unique() {
        this.offset = 0;
        this.maxResults = 2;
        List<T> result = list();
        if(result.isEmpty())
            throw new NoResultException("Expected unique row but noting found.");
        if(result.size() > 1)
            throw new NonUniqueResultException("Expected unique row but more than 1 rows found.");
        return result.get(0);
    }
}
