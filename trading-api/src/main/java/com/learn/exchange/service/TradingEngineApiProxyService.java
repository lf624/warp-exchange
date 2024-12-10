package com.learn.exchange.service;

import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.support.LoggerSupport;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// 访问 trading-engine-api 的 httpclient
@Component
public class TradingEngineApiProxyService extends LoggerSupport {

    @Value("#{exchangeConfiguration.apiEndpoints.tradingEngineApi}")
    private String tradingEngineInternalApiEndpoint;

    private OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(20, 60, TimeUnit.SECONDS))
            .retryOnConnectionFailure(false)
            .build();

    public String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(tradingEngineInternalApiEndpoint + url)
                .header("Accept", "*/*")
                .build();
        try(Response response = okHttpClient.newCall(request).execute()) {
            if(response.code() != 200) {
                logger.error("Internal api failed with code {}: {}", response.code(), url);
                throw new ApiException(ApiError.OPERATION_TIMEOUT, "operation timeout.");
            }
            try(ResponseBody body = response.body()) {
                String json = body != null ? body.string() : null;
                if(json == null || json.isEmpty()) {
                    logger.error("Internal api failed with code 200 but empty response: {}", json);
                    throw new ApiException(ApiError.INTERNAL_SERVER_ERROR, null, "response is empty.");
                }
                return json;
            }
        }
    }
}
