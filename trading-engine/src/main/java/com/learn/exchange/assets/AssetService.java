package com.learn.exchange.assets;

import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.support.LoggerSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AssetService extends LoggerSupport{
    // UserId -> Map(AssetEnum -> Asset(available/frozen))
    final ConcurrentMap<Long, ConcurrentMap<AssetEnum, Asset>> userAssets = new ConcurrentHashMap<>();

    public Asset getAsset(Long userId, AssetEnum assetId) {
        ConcurrentMap<AssetEnum, Asset> asset = userAssets.get(userId);
        if(asset == null)
            return null;
        return asset.get(assetId);
    }

    public Map<AssetEnum, Asset> getAssets(Long userId) {
        Map<AssetEnum, Asset> assets = userAssets.get(userId);
        if(assets == null)
            return Map.of();
        return assets;
    }

    public ConcurrentMap<Long, ConcurrentMap<AssetEnum, Asset>> getUserAssets() {
        return this.userAssets;
    }

    // 除用户存入资产操作，其他常规操作均需检查余额 checkBalance
    public void transfer(Transfer type, Long fromUser, Long toUser, AssetEnum assetId,
                         BigDecimal amount) {
        if(!tryTransfer(type, fromUser, toUser, assetId, amount,true))
            throw new RuntimeException("Transfer failed for type: " + type +
                    ", form user " + fromUser + ", to user " + toUser +
                    ", asset = " + assetId + ", amount = " + amount);
        if(logger.isDebugEnabled())
            logger.debug("transfer asset {} from {} ==> {}, amount={}", assetId, fromUser, toUser, amount);
    }

    public boolean tryFreeze(Long userId, AssetEnum assetId, BigDecimal amount) {
        boolean ok = tryTransfer(Transfer.AVAILABLE_TO_FROZEN, userId, userId, assetId, amount, true);
        if(ok && logger.isDebugEnabled())
            logger.debug("freeze user {}, asset {}, amount {}", userId, assetId, amount);
        return ok;
    }

    public void unfreeze(Long userId, AssetEnum assetId, BigDecimal amount) {
        if(!tryTransfer(Transfer.FROZEN_TO_AVAILABLE, userId, userId, assetId, amount, true))
            throw new RuntimeException("unfreeze failed for user " + userId +
                    ", asset " + assetId + ", amount=" + amount);
        if (logger.isDebugEnabled()) {
            logger.debug("unfreezed user {}, asset {}, amount {}", userId, assetId, amount);
        }
    }

    public boolean tryTransfer(Transfer type, Long fromUser, Long toUser, AssetEnum assetId,
                               BigDecimal amount, boolean checkBalance) {
        if(amount.signum() == 0)
            return true;
        if(amount.signum() < 0)
            throw new IllegalArgumentException("Negative amount.");
        // 获取源用户资产
        Asset fromAsset = getAsset(fromUser, assetId);
        if(fromAsset == null)
            // 资产不存在时初始化用户资产
            fromAsset = initAsset(fromUser, assetId);
        // 目标用户资产
        Asset toAsset = getAsset(toUser, assetId);
        if(toAsset == null)
            toAsset = initAsset(toUser, assetId);
        return switch (type) {
            case AVAILABLE_TO_AVAILABLE -> {
                // 需要检查余额且余额不足
                if(checkBalance && fromAsset.available.compareTo(amount) < 0)
                    yield false;
                fromAsset.available = fromAsset.available.subtract(amount);
                toAsset.available = toAsset.available.add(amount);
                yield true;
            }
            case AVAILABLE_TO_FROZEN -> {
                if(checkBalance && fromAsset.available.compareTo(amount) < 0)
                    yield false;
                fromAsset.available = fromAsset.available.subtract(amount);
                toAsset.frozen = toAsset.frozen.add(amount);
                yield true;
            }
            case FROZEN_TO_AVAILABLE -> {
                if(checkBalance && fromAsset.frozen.compareTo(amount) < 0)
                    yield false;
                fromAsset.frozen = fromAsset.frozen.subtract(amount);
                toAsset.available = toAsset.available.add(amount);
                yield true;
            }
            // 若枚举情况全覆盖，则不需要default
            default -> throw new IllegalArgumentException("Invalid transfer type: " + type);
        };
    }

    private Asset initAsset(Long userId, AssetEnum assetId) {
        ConcurrentMap<AssetEnum, Asset> map = userAssets.computeIfAbsent(userId,
                k -> new ConcurrentHashMap<>());
        Asset zeroAsset = new Asset();
        map.put(assetId, zeroAsset);
        return zeroAsset;
    }
}
