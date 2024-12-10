package com.learn.exchange.model.ui;

import com.learn.exchange.enums.UserType;
import com.learn.exchange.model.support.EntitySupport;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity implements EntitySupport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, updatable = false)
    public Long id;

    @Column(nullable = false, updatable = false, length = VAR_ENUM)
    public UserType type;

    @Column(nullable = false, updatable = false)
    public long createdAt;

    @Override
    public String toString() {
        return "UserEntity [id=" + id + ", type=" + type + ", createdAt=" + createdAt + "]";
    }
}
