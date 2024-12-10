package com.learn.exchange.bean;

import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.enums.Direction;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderRequestBean implements ValidatableBean {

    public Direction direction;
    public BigDecimal price;
    public BigDecimal quantity;

    @Override
    public void validate() {
        if(this.direction == null)
            throw new ApiException(ApiError.PARAMETER_INVALID, "direction", "direction is required.");
        if(this.price == null)
            throw new ApiException(ApiError.PARAMETER_INVALID, "price", "price is required.");
        this.price = this.price.setScale(2, RoundingMode.DOWN);
        if(this.price.signum() <= 0)
            throw new ApiException(ApiError.PARAMETER_INVALID, "price", "price must be positive.");
        if (this.quantity == null)
            throw new ApiException(ApiError.PARAMETER_INVALID, "quantity", "quantity is required.");
        this.quantity = this.quantity.setScale(2, RoundingMode.DOWN);
        if (this.quantity.signum() <= 0)
            throw new ApiException(ApiError.PARAMETER_INVALID, "quantity", "quantity must be positive.");
    }
}
