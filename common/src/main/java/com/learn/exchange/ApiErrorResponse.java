package com.learn.exchange;

public record ApiErrorResponse(ApiError error, String data, String message) {
}
