package com.learn.exchange.model.trade;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learn.exchange.enums.Direction;
import com.learn.exchange.enums.OrderStatus;
import com.learn.exchange.model.support.EntitySupport;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class OrderEntity implements EntitySupport, Comparable<OrderEntity> {
    // 订单ID / 定序ID / 用户ID
    @Id
    @Column(nullable = false, updatable = false)
    public Long id;

    @Column(nullable = false, updatable = false)
    public long sequenceId;

    @Column(nullable = false, updatable = false)
    public Long userId;

    // 价格 / 方向 / 状态
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal price;

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    public Direction direction;

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    public OrderStatus status;

    // 订单数量 / 未完成数量
    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal quantity;

    @Column(nullable = false, updatable = false, precision = PRECISION, scale = SCALE)
    public BigDecimal unfilledQuantity;

    // 创建和更新时间
    @Column(nullable = false, updatable = false)
    public long createdAt;

    @Column(nullable = false, updatable = false)
    public long updatedAt;

    private int version;

    public void updateOrder(BigDecimal unfilledQuantity, OrderStatus status, long updatedAt) {
        this.version++;
        this.unfilledQuantity = unfilledQuantity;
        this.status = status;
        this.updatedAt = updatedAt;
        this.version++;
    }

    @Transient
    @JsonIgnore
    public int getVersion() {
        return this.version;
    }

    @Nullable
    public OrderEntity copy() {
        OrderEntity entity = new OrderEntity();
        int ver = this.version;
        entity.unfilledQuantity = this.unfilledQuantity;
        entity.status = this.status;
        entity.updatedAt = this.updatedAt;
        if(ver != this.version) {
            return null;
        }

        entity.createdAt = this.createdAt;
        entity.direction = this.direction;
        entity.id = this.id;
        entity.price = this.price;
        entity.quantity = this.quantity;
        entity.sequenceId = this.sequenceId;
        entity.userId = this.userId;
        return entity;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o)
            return true;
        if(o instanceof OrderEntity e) {
            return this.id.longValue() == e.id.longValue();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "OrderEntity [" + "id=" + id +
                ", sequenceId=" + sequenceId +
                ", userId=" + userId + ", price=" + price +
                ", direction=" + direction + ", status=" + status +
                ", quantity=" + quantity +
                ", unfilledQuantity=" + unfilledQuantity +
                ", createdAt=" + createdAt + ", updatedAt=" + updatedAt +
                ", version=" + version + ']';
    }

    @Override
    public int compareTo(OrderEntity o) {
        return Long.compare(this.id, o.id);
    }
}
