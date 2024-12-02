package com.learn.exchange.db;

import java.util.Arrays;
import java.util.List;

public final class Where<T> extends CriteriaQuery<T> {

    Where(Criteria<T> criteria, String clause, Object... args) {
        super(criteria);
        this.criteria.where = clause;
        this.criteria.whereParams.addAll(Arrays.asList(args));
    }

    public Limit<T> limit(int maxResults) {
        return limit(0, maxResults);
    }
    public Limit<T> limit(int offset, int maxResults) {
        return new Limit<>(this.criteria, offset, maxResults);
    }

    public OrderBy<T> orderBy(String orderBy) {
        return new OrderBy<>(this.criteria, orderBy);
    }

    public List<T> list() {
        return this.criteria.list();
    }
    public T first() {
        return criteria.first();
    }
    T unique() {
        return criteria.unique();
    }
}
