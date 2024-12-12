package com.learn.exchange.model.quotation;

import com.learn.exchange.model.support.AbstractBarEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Store bars of hour.
 */
@Entity
@Table(name = "hour_bars")
public class HourBarEntity extends AbstractBarEntity {

}
