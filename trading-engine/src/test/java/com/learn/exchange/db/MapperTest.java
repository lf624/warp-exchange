package com.learn.exchange.db;

import com.learn.exchange.model.trade.MatchDetailEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MapperTest {
    @Test
    void testDDL() throws NoSuchMethodException {
        Mapper<MatchDetailEntity> mapper = new Mapper<>(MatchDetailEntity.class);
        assertEquals("""
                CREATE TABLE match_details (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  counterOrderId BIGINT NOT NULL,
                  counterUserId BIGINT NOT NULL,
                  createdAt BIGINT NOT NULL,
                  direction VARCHAR(32) NOT NULL,
                  orderId BIGINT NOT NULL,
                  price DECIMAL(36,18) NOT NULL,
                  quantity DECIMAL(36,18) NOT NULL,
                  sequenceId BIGINT NOT NULL,
                  type VARCHAR(32) NOT NULL,
                  userId BIGINT NOT NULL,
                  CONSTRAINT UNI_OID_COID UNIQUE (orderId, counterOrderId),
                  INDEX IDX_OID_CT (orderId,createdAt),
                  PRIMARY KEY(id)
                ) CHARACTER SET utf8 COLLATE utf8_general_ci AUTO_INCREMENT = 1000;
                """, mapper.ddl());
    }

    @Test
    void testOther() {
        assertTrue(Integer.valueOf(127) == Integer.valueOf(127));
    }
}
