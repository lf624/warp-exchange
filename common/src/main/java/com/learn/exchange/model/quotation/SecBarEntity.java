package com.learn.exchange.model.quotation;

import com.learn.exchange.model.support.AbstractBarEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sec_bars")
public class SecBarEntity extends AbstractBarEntity {
}
