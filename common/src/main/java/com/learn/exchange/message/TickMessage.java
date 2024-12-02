package com.learn.exchange.message;

import com.learn.exchange.model.quotation.TickEntity;

import java.util.List;

public class TickMessage extends AbstractMessage{
    public long sequenceId;

    public List<TickEntity> ticks;
}
