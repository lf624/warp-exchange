package com.learn.exchange.match;

import com.learn.exchange.model.trade.OrderEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MatchResult {
    public final OrderEntity takerOrder;
    public final List<MatchDetailRecord> matchDetails = new ArrayList<>();

    public MatchResult(OrderEntity takerOrder) {
        this.takerOrder = takerOrder;
    }

    public void add(BigDecimal price, BigDecimal matchedQuantity, OrderEntity makerOrder) {
        this.matchDetails.add(new MatchDetailRecord(price, matchedQuantity, this.takerOrder, makerOrder));
    }

    @Override
    public String toString() {
        if(matchDetails.isEmpty())
            return "No matched.";
        return matchDetails.size() + " matched: " +
                String.join(", ", matchDetails.stream()
                        .map(MatchDetailRecord::toString)
                        .toArray(String[]::new));
    }
}
