package com.learn.exchange.model.quotation;

import com.learn.exchange.model.support.AbstractBarEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Store bars of day.
 */
@Entity
@Table(name = "day_bars")
public class DayBarEntity extends AbstractBarEntity {

}
