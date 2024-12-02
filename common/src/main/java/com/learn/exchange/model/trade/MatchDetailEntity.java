package com.learn.exchange.model.trade;

import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.MatchType;
import com.learn.exchange.model.support.EntitySupport;
import jakarta.persistence.*;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;


// 存储每个订单的只读的匹配详情
@Entity
@Table(name = "match_details", uniqueConstraints = @UniqueConstraint(name = "UNI_OID_COID",
        columnNames = {"orderId", "counterOrderId"}),
        indexes = @Index(name = "IDX_OID_CT", columnList = "orderId,createdAt"))
public class MatchDetailEntity implements EntitySupport, Comparable<MatchDetailEntity>{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    public long id;

    @Column(nullable = false, updatable = false)
    public long sequenceId;

    @Column(nullable = false, updatable = false)
    public Long orderId;

    @Column(nullable = false, updatable = false)
    public Long counterOrderId;

    @Column(nullable = false, updatable = false)
    public Long userId;

    @Column(nullable = false, updatable = false)
    public Long counterUserId;

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    public MatchType type;

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    public Direction direction;

    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal price;

    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal quantity;

    @Column(nullable = false, updatable = false)
    public long createdAt;

    // 按OrderId, CounterOrderId排序
    @Override
    public int compareTo(MatchDetailEntity o) {
        int cmp = Long.compare(this.orderId, o.orderId);
        if(cmp == 0) {
            cmp = Long.compare(this.counterOrderId, o.orderId);
        }
        return cmp;
    }
}
