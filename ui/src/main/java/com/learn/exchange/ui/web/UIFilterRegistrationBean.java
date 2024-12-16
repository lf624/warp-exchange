package com.learn.exchange.ui.web;

import com.learn.exchange.bean.AuthToken;
import com.learn.exchange.ctx.UserContext;
import com.learn.exchange.support.AbstractFilter;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 将cookie中的用户信息放入UserContext
@Component
public class UIFilterRegistrationBean extends FilterRegistrationBean<Filter> {

    @Autowired
    CookieService cookieService;

    @PostConstruct
    public void init() {
        UIFilter filter = new UIFilter();
        setFilter(filter);
        addUrlPatterns("/*");
        setName(filter.getClass().getSimpleName());
        setOrder(100);
    }

    class UIFilter extends AbstractFilter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse resp = (HttpServletResponse) response;
            String path = req.getRequestURI();
            if (logger.isDebugEnabled()) {
                logger.debug("process {} {}...", req.getMethod(), path);
            }
            req.setCharacterEncoding("UTF-8");
            resp.setCharacterEncoding("UTF-8");
            resp.setContentType("text/html;charset=UTF-8");
            // try parse user
            AuthToken auth = cookieService.findSessionCookie(req);
            if(auth != null && auth.isAboutToExpire()) {
                logger.info("refresh session cookie...");
                cookieService.setSessionCookie(req, resp, auth.refresh());
            }
            Long userId = auth == null ? null : auth.userId();
            if (logger.isDebugEnabled()) {
                logger.debug("parsed user {} from session cookie.", userId);
            }
            try(UserContext ctx = new UserContext(userId)) {
                chain.doFilter(request, response);
            }
        }
    }
}
