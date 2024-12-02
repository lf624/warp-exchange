package com.learn.exchange.db;

import java.util.Arrays;

@SuppressWarnings("rawtypes")
public final class Select extends CriteriaQuery {

    @SuppressWarnings("unchecked")
    Select(Criteria criteria, String... selectParam) {
        super(criteria);
        if(selectParam.length > 0)
            this.criteria.select = Arrays.asList(selectParam);
    }

    @SuppressWarnings("unchecked")
    public <T> From<T> from(Class<T> entityClass) {
        return new From<>(this.criteria, this.criteria.db.getMapper(entityClass));
    }
}
