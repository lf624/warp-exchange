package com.learn.exchange.model.quotation;

import com.learn.exchange.model.support.AbstractBarEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Store bars of minute.
 */
@Entity
@Table(name = "min_bars")
public class MinBarEntity extends AbstractBarEntity {

}
