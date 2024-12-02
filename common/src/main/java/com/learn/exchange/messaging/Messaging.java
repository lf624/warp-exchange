package com.learn.exchange.messaging;

public interface Messaging {

    enum Topic {
        // to sequence
        SEQUENCE(1),
        // to/from trading-engine
        TRANSFER(1),
        // events to trading-engine
        TRADE(1),
        // tick to quotation for generate bars
        TICK(1);

        private final int concurrency;

        Topic(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getConcurrency() {
            return this.concurrency;
        }

        public int getPartitions() {
            return this.concurrency;
        }
    }
}
