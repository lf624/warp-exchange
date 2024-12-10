package com.learn.exchange.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.exchange.ApiError;
import com.learn.exchange.ApiErrorResponse;
import com.learn.exchange.ApiException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

// 访问 REST API 的 Http client
public class RestClient {
    final Logger logger = LoggerFactory.getLogger(getClass());

    final String endpoint;
    final String host;
    final ObjectMapper objectMapper;
    OkHttpClient client;

    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    static final ApiException ERROR_UNKNOWN = new ApiException(ApiError.INTERNAL_SERVER_ERROR, "api",
            "Api failed without error code.");

    RestClient(String endpoint, String host, ObjectMapper objectMapper, OkHttpClient client) {
        this.endpoint = endpoint;
        this.host = host;
        this.objectMapper = objectMapper;
        this.client = client;
    }

    public static class Builder {
        final Logger logger = LoggerFactory.getLogger(getClass());

        String scheme;
        String host;
        int port;

        int connectTimeout = 3;
        int readTimeout = 3;
        int keepAlive = 30;

        // api endpoint: http://localhost:8002
        public Builder(String apiEndpoint) {
            try {
                URI uri = new URI(apiEndpoint);
                if(!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
                    throw new IllegalArgumentException("Invalid API endpoint: " + apiEndpoint);
                if(uri.getPath() != null && !uri.getPath().isEmpty())
                    throw new IllegalArgumentException("Invalid API endpoint: " + apiEndpoint);
                this.scheme = uri.getScheme();
                this.host = uri.getHost().toLowerCase();
                this.port = uri.getPort();
            }catch (URISyntaxException e) {
                throw new IllegalArgumentException("Invalid API endpoint: " + apiEndpoint, e);
            }
        }

        public Builder connectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }
        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }
        public Builder keepAlive(int keepAliveInSeconds) {
            this.keepAlive = keepAliveInSeconds;
            return this;
        }

        public RestClient build(ObjectMapper objectMapper) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(this.connectTimeout, TimeUnit.SECONDS)
                    .readTimeout(this.readTimeout, TimeUnit.SECONDS)
                    .connectionPool(new ConnectionPool(0, this.keepAlive, TimeUnit.SECONDS))
                    .retryOnConnectionFailure(false)
                    .build();
            String endpoint = this.scheme + "://" + this.host;
            if(this.port != (-1))
                endpoint = endpoint + ":" + port;
            return new RestClient(endpoint, this.host, objectMapper, client);
        }
    }

    public <T> T get(Class<T> clazz, String path, String authHeader, Map<String, String> query) {
        Objects.requireNonNull(clazz);
        return request(clazz, null, "GET", path, authHeader, query, null);
    }
    public <T> T get(TypeReference<T> ref, String path, String authHeader, Map<String, String> query) {
        Objects.requireNonNull(ref);
        return request(null, ref, "GET", path, authHeader, query, null);
    }
    public <T> T post(Class<T> clazz, String path, String authHeader, Object body) {
        Objects.requireNonNull(clazz);
        return request(clazz, null, "POST", path, authHeader, null, body);
    }
    public <T> T post(TypeReference<T> ref, String path, String authHeader, Object body) {
        Objects.requireNonNull(ref);
        return request(null, ref, "POST", path, authHeader, null, body);
    }

    <T> T request(Class<T> clazz, TypeReference<T> ref, String method, String path, String authHeader,
                  Map<String, String> query, Object body) {
        if(!path.startsWith("/"))
            throw new IllegalArgumentException("Invalid path: " + path);
        // 构造 query
        String queryString = null;
        if(query != null) {
            List<String> paramList = new ArrayList<>();
            for(Map.Entry<String, String> entry : query.entrySet()) {
                paramList.add(entry.getKey() + "=" + entry.getValue());
            }
            queryString = String.join("&", paramList);
        }
        StringBuilder urlBuilder = new StringBuilder(64).append(this.endpoint).append(path);
        if(queryString != null)
            urlBuilder.append("?").append(queryString);
        final String url = urlBuilder.toString();
        // 构造 json body
        String jsonBody;
        try {
            jsonBody = body == null ? "" : (body instanceof String ? (String) body :
                    objectMapper.writeValueAsString(body));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 构造 request
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if(authHeader != null)
            requestBuilder.addHeader("Authorization", authHeader);
        if("POST".equals(method))
            requestBuilder.put(RequestBody.create(jsonBody, JSON));
        Request request = requestBuilder.build();
        try {
            return execute(clazz, ref, request);
        }catch (IOException e) {
            logger.warn("IOException", e);
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    <T> T execute(Class<T> clazz, TypeReference<T> ref, Request request) throws IOException{
        try(Response response = this.client.newCall(request).execute()) {
            if(response.code() == 200) {
                try(ResponseBody body = response.body()) {
                    String json = body != null ? body.string() : null;
                    if(json == null)
                        return null;
                    if(clazz == null)
                        return objectMapper.readValue(json, ref);
                    if(clazz == String.class)
                        return (T) json;
                    return objectMapper.readValue(json, clazz);
                }
            } else if(response.code() == 400) {
                try(ResponseBody body = response.body()) {
                    String bodyString = body != null ? body.string() : "";
                    logger.warn("response 400. Error: " + bodyString);
                    ApiErrorResponse err = objectMapper.readValue(bodyString, ApiErrorResponse.class);
                    if(err == null || err.error() == null)
                        throw ERROR_UNKNOWN;
                    throw new ApiException(err.error(), err.data(), err.message());
                }
            } else {
                throw new ApiException(ApiError.INTERNAL_SERVER_ERROR, null, "Http error " + response.code());
            }
        }
    }
}
