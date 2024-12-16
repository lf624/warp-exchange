package com.learn.exchange.ui.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.bean.AuthToken;
import com.learn.exchange.client.RestClient;
import com.learn.exchange.ctx.UserContext;
import com.learn.exchange.support.AbstractFilter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;

// 将 `/api/*` 请求转发给 trading-api
@Component
public class ProxyFilterRegistrationBean extends FilterRegistrationBean<Filter> {
    @Autowired
    RestClient tradingApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Value("#{exchangeConfiguration.hmacKey}")
    String hmacKey;

    @PostConstruct
    public void init() {
        ProxyFilter filter = new ProxyFilter();
        setFilter(filter);
        addUrlPatterns("/api/*");
        setName(filter.getClass().getSimpleName());
        setOrder(200);
    }

    class ProxyFilter extends AbstractFilter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            String path = req.getRequestURI();
            logger.info("process {} {}...", req.getMethod(), path);
            Long userId = UserContext.getUserId();
            logger.info("process with userId={}...", userId);
            proxyForward(req, resp, userId);
        }

        public void proxyForward(HttpServletRequest req, HttpServletResponse resp, Long userId) throws IOException{
            String authToken = null;
            if(userId != null) {
                AuthToken token = new AuthToken(userId, System.currentTimeMillis() + 60_000);
                authToken = "Bearer " + token.toSecureString(hmacKey);
            }
            String responseJson = null;
            try {
                if("GET".equals(req.getMethod())) {
                    Map<String, String[]> params = req.getParameterMap();
                    Map<String, String> query = params.isEmpty() ? null : convertParams(params);
                    responseJson = tradingApiClient.get(String.class, req.getRequestURI(), authToken, query);
                } else if ("POST".equals(req.getMethod())) {
                    responseJson = tradingApiClient.post(String.class, req.getRequestURI(), authToken, readBody(req));
                }
                resp.setContentType("application/json;charset=UTF-8");
                PrintWriter pw = resp.getWriter();
                pw.write(responseJson);
                pw.flush();
            } catch (ApiException e) {
                logger.warn(e.getMessage(), e);
                writeApiException(req, resp, e);
            } catch (Exception e) {
                logger.warn(e.getMessage(), e);
                writeApiException(req, resp, new ApiException(ApiError.INTERNAL_SERVER_ERROR, null, e.getMessage()));
            }
        }

        private Map<String, String> convertParams(Map<String, String[]> params) {
            Map<String, String> map = new HashMap<>();
            params.forEach((key, value) -> {
                map.put(key, value[0]);
            });
            return map;
        }

        private String readBody(HttpServletRequest req) throws IOException{
            StringBuilder sb = new StringBuilder(2048);
            char[] buffer = new char[256];
            BufferedReader reader = req.getReader();
            for(;;) {
                int n = reader.read(buffer);
                if(n == (-1))
                    break;
                sb.append(buffer, 0, n);
            }
            return sb.toString();
        }

        private void writeApiException(HttpServletRequest req, HttpServletResponse resp, ApiException e)
                throws IOException{
            resp.setStatus(400);
            resp.setContentType("application/json;charset=UTF-8");
            PrintWriter pw= resp.getWriter();
            pw.write(objectMapper.writeValueAsString(e.error));
            pw.flush();
        }
    }
}
