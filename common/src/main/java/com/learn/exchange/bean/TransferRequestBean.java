package com.learn.exchange.bean;

import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.enums.AssetEnum;
import com.learn.exchange.util.IdUtil;

import java.math.BigDecimal;

public class TransferRequestBean implements ValidatableBean {
    public String transferId;
    public Long fromUserId;
    public Long toUserId;
    public AssetEnum asset;
    public BigDecimal amount;

    @Override
    public void validate() {
        if(!IdUtil.isValidStringId(transferId))
            throw new ApiException(ApiError.PARAMETER_INVALID, "transferId", "Must specify a unique transferId.");
        if(fromUserId == null || fromUserId <= 0)
            throw new ApiException(ApiError.PARAMETER_INVALID, "fromUserId", "Must specify fromUserId.");
        if(toUserId == null || toUserId <= 0)
            throw new ApiException(ApiError.PARAMETER_INVALID, "toUserId", "Must specify toUserId.");
        if(fromUserId.longValue() == toUserId.longValue())
            throw new ApiException(ApiError.PARAMETER_INVALID, "toUserId", "Must be different with fromUserId.");
        if(asset == null)
            throw new ApiException(ApiError.PARAMETER_INVALID, "asset", "Must specify asset.");
        if(amount == null)
            throw new ApiException(ApiError.PARAMETER_INVALID, "amount", "Must specify amount.");
        amount = amount.setScale(AssetEnum.SCALE);
        if(amount.signum() <= 0)
            throw new ApiException(ApiError.PARAMETER_INVALID, "amount", "Must specify positive amount.");
    }
}
