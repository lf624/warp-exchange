package com.learn.exchange.db;

import java.util.ArrayList;
import java.util.List;

public final class OrderBy<T> extends CriteriaQuery<T> {

    OrderBy(Criteria<T> criteria, String prop) {
        super(criteria);
        orderBy(prop);
    }

    public OrderBy<T> orderBy(String prop) {
        if(criteria.orderBy == null)
            criteria.orderBy = new ArrayList<>();
        criteria.orderBy.add(prop);
        return this;
    }

    public OrderBy<T> desc() {
        int idx = criteria.orderBy.size() - 1;
        String s = criteria.orderBy.get(idx);
        if(!s.toUpperCase().endsWith(" DESC"))
            s = s + " DESC";
        criteria.orderBy.set(idx, s);
        return this;
    }

    public Limit<T> limit(int maxResults) {
        return limit(0, maxResults);
    }
    public Limit<T> limit(int offset, int maxResults) {
        return new Limit<>(this.criteria, offset, maxResults);
    }

    public List<T> list() {
        return this.criteria.list();
    }
    public T first() {
        return criteria.first();
    }
}
