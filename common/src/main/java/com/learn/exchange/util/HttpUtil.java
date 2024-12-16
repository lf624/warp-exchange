package com.learn.exchange.util;

import jakarta.servlet.http.HttpServletRequest;

public class HttpUtil {

    public static boolean isSecure(HttpServletRequest request) {
        // 该请求头指示客户端与代理服务器之间的协议
        String forwarded = request.getHeader("x-forwarded-proto");
        if(forwarded != null) {
            return "https".equals(forwarded);
        }
        return "https".equals(request.getScheme());
    }
}
