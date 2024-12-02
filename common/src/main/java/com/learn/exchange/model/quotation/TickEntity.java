package com.learn.exchange.model.quotation;

import com.learn.exchange.model.support.EntitySupport;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ticks", uniqueConstraints = @UniqueConstraint(
        name = "UNI_T_M", columnNames = {"takerUserId", "makerUserId"}),
        indexes = @Index(name = "IDX_CAT", columnList = "createdAt"))
public class TickEntity implements EntitySupport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    public long id;

    @Column(nullable = false, updatable = false)
    public long sequenceId;

    @Column(nullable = false, updatable = false)
    public Long takerUserId;

    @Column(nullable = false, updatable = false)
    public Long makerUserId;

    @Column(nullable = false, updatable = false)
    public boolean takerDirection;

    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal price;

    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal quantity;

    @Column(nullable = false, updatable = false)
    public long createdAt;

    public String toJson() {
        return "[" + createdAt + "," + (takerDirection ? 1 : 0) +
                "," + price + "," + quantity + "]";
    }
}
