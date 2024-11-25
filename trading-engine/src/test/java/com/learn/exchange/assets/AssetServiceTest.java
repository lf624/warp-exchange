package com.learn.exchange.assets;

import com.learn.exchange.enums.AssetEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class AssetServiceTest {
    static final Long DEBT = 1L;
    static final Long USER_A = 2000L;
    static final Long USER_B = 3000L;
    static final Long USER_C = 4000L;

    AssetService service;

    @BeforeEach
    public void setup() {
        service = new AssetService();
        init();
    }

    @AfterEach
    public void tearDown() {
        verify();
    }

    @Test
    void tryTransfer() {
        // A -> B, USD, 12000. ok
        service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, USER_A, USER_B,
                AssetEnum.USD, BigDecimal.valueOf(12000), true);
        assertBDEquals(300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(12000 + 45600, service.getAsset(USER_B, AssetEnum.USD).available);

        // A -> B, USD, 301. fail
        assertFalse(service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, USER_A, USER_B,
                AssetEnum.USD, BigDecimal.valueOf(301), true));
        assertBDEquals(300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(12000 + 45600, service.getAsset(USER_B, AssetEnum.USD).available);
    }

    @Test
    void tryFreeze() {
        // freeze A USD 12000 ok
        service.tryFreeze(USER_A, AssetEnum.USD, BigDecimal.valueOf(12000));
        assertBDEquals(300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(12000, service.getAsset(USER_A, AssetEnum.USD).frozen);

        // freeze A USD 301 false
        assertFalse(service.tryFreeze(USER_A, AssetEnum.USD, BigDecimal.valueOf(301)));
        assertBDEquals(300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(12000, service.getAsset(USER_A, AssetEnum.USD).frozen);
    }

    @Test
    void unfreeze() {
        // freeze 12000 ok:
        service.tryFreeze(USER_A, AssetEnum.USD, new BigDecimal("12000"));
        assertBDEquals(300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(12000, service.getAsset(USER_A, AssetEnum.USD).frozen);

        // unfreeze 9000 ok:
        service.unfreeze(USER_A, AssetEnum.USD, new BigDecimal("9000"));
        assertBDEquals(9300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(3000, service.getAsset(USER_A, AssetEnum.USD).frozen);

        // unfreeze 3001 failed:
        assertThrows(RuntimeException.class, () -> {
            service.unfreeze(USER_A, AssetEnum.USD, new BigDecimal("3001"));
        });
    }

    @Test
    void transfer() {
        // A available -> A frozen 9000
        service.transfer(Transfer.AVAILABLE_TO_FROZEN, USER_A, USER_A, AssetEnum.USD, BigDecimal.valueOf(9000));
        assertBDEquals(3300, service.getAsset(USER_A, AssetEnum.USD).available);
        assertBDEquals(9000, service.getAsset(USER_A, AssetEnum.USD).frozen);
        // A frozen -> C available 8000
        service.transfer(Transfer.FROZEN_TO_AVAILABLE, USER_A, USER_C, AssetEnum.USD, BigDecimal.valueOf(8000));
        assertBDEquals(1000, service.getAsset(USER_A, AssetEnum.USD).frozen);
        assertBDEquals(8000, service.getAsset(USER_C, AssetEnum.USD).available);
        // A frozen -> B available 1001 failed
        assertThrows(RuntimeException.class, () -> {
            service.transfer(Transfer.FROZEN_TO_AVAILABLE, USER_A, USER_C, AssetEnum.USD, BigDecimal.valueOf(1001));
        });
    }

    // A: USD=12300, BTC=12, B: USD=45600, C: BTC=34
    void init() {
        service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, DEBT, USER_A,
                AssetEnum.USD, BigDecimal.valueOf(12300), false);
        service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, DEBT, USER_A,
                AssetEnum.BTC, BigDecimal.valueOf(12), false);
        service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, DEBT, USER_B,
                AssetEnum.USD, BigDecimal.valueOf(45600), false);
        service.tryTransfer(Transfer.AVAILABLE_TO_AVAILABLE, DEBT, USER_A,
                AssetEnum.BTC, BigDecimal.valueOf(34), false);

        assertBDEquals(-57900, service.getAsset(DEBT, AssetEnum.USD).available);
        assertBDEquals(-46, service.getAsset(DEBT, AssetEnum.BTC).available);
    }

    // 所有资产总和应为 0
    void verify() {
        BigDecimal totalUSD = BigDecimal.ZERO;
        BigDecimal totalBTC = BigDecimal.ZERO;
        for(Long userId : service.userAssets.keySet()) {
            Asset assetUSD = service.getAsset(userId, AssetEnum.USD);
            if(assetUSD != null)
                totalUSD = totalUSD.add(assetUSD.available).add(assetUSD.frozen);
            Asset assetBTC = service.getAsset(userId, AssetEnum.BTC);
            if(assetBTC != null)
                totalBTC = totalBTC.add(assetBTC.available).add(assetBTC.frozen);
        }
        assertBDEquals(0, totalUSD);
        assertBDEquals(0, totalBTC);
    }

    void assertBDEquals(long value, BigDecimal bd) {
        assertBDEquals(String.valueOf(value), bd);
    }

    void assertBDEquals(String value, BigDecimal bd) {
        assertEquals(0, new BigDecimal(value).compareTo(bd),
                String.format("Expected %s but actual %s.", value, bd.toPlainString()));
    }
}
