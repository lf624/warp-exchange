package com.learn.exchange.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learn.exchange.ApiError;
import com.learn.exchange.ApiException;
import com.learn.exchange.bean.AuthToken;
import com.learn.exchange.ctx.UserContext;
import com.learn.exchange.model.ui.UserProfileEntity;
import com.learn.exchange.support.AbstractFilter;
import com.learn.exchange.user.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class ApiFilterRegistrationBean extends FilterRegistrationBean<Filter> {

    @Autowired
    UserService userService;
    @Autowired
    ObjectMapper objectMapper;

    @Value("#{exchangeConfiguration.hmacKey}")
    String hmacKey;

    @PostConstruct
    public void init() {
        ApiFilter filter = new ApiFilter();
        setFilter(filter);
        addUrlPatterns("/api/*");
        setName(filter.getClass().getName());
        setOrder(100);
    }

    class ApiFilter extends AbstractFilter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            String path = req.getRequestURI();
            logger.info("process api {} {} ...", req.getMethod(), path);
            req.setCharacterEncoding("UTF-8");
            resp.setCharacterEncoding("UTF-8");
            // 解析用户
            Long userId = null;
            try {
                userId = parseUserId(req);
            }catch (ApiException e) {
                sendErrorResponse(resp, e);
                return;
            }
            if(userId == null)
                // 匿名身份
                chain.doFilter(request, response);
            else {
                // 用户身份
                try(UserContext ctx = new UserContext(userId)) {
                    chain.doFilter(request, response);
                }
            }
        }

        Long parseUserId(HttpServletRequest req) {
            // 尝试通过 Authorization Header 认证
            String auth = req.getHeader("Authorization");
            if(auth != null)
                return parseUserFromAuthorization(auth);
            // 尝试通过 API key 认证
            String apiKey = req.getHeader("API-Key");
            String apiSignature = req.getHeader("API-Signature");
            if(apiKey != null && apiSignature != null)
                return parseUserFromApiKey(apiKey, apiSignature, req);
            return null;
        }

        Long parseUserFromAuthorization(String auth) {
            if(auth.startsWith("Basic ")) {
                // 用 Base64 解码
                String eap = new String(Base64.getDecoder().decode(auth.substring(6)),
                        StandardCharsets.UTF_8);
                // 分离 email:password
                int pos = eap.indexOf(':');
                if(pos < 1)
                    throw new ApiException(ApiError.AUTH_SIGNIN_FAILED, "Invalid email or password.");
                String email = eap.substring(0, pos);
                String passwd = eap.substring(pos + 1);
                // 验证
                UserProfileEntity p = userService.signin(email, passwd);
                Long userId = p.userId;
                if(logger.isDebugEnabled())
                    logger.debug("parse from basic authorization: {}", userId);
                return userId;
            }
            if(auth.startsWith("Bearer ")) {
                AuthToken token = AuthToken.fromSecureString(auth.substring(7), hmacKey);
                if(token.isExpired())
                    return null;
                if(logger.isDebugEnabled())
                    logger.debug("parse from bearer authorization: {}", token.userId());
                return token.userId();
            }
            throw new ApiException(ApiError.AUTH_SIGNIN_FAILED, "Invalid authorization header.");
        }

        Long parseUserFromApiKey(String apiKey, String apiSignature, HttpServletRequest request) {
            // TODO: 验证API-Key, API-Secret并返回userId
            return null;
        }

        void sendErrorResponse(HttpServletResponse resp, ApiException e) throws IOException {
            resp.sendError(400);
            resp.setContentType("application/json");
            PrintWriter pw = resp.getWriter();
            pw.write(objectMapper.writeValueAsString(e.error));
            pw.flush();
        }
    }
}
