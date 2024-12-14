package com.learn.exchange.push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// 禁用数据库自动配置（无DataSource, JdbcTemplate...）
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class PushApplication {
    public static void main(String[] args) {
        System.setProperty("vertx.disableFileCPResolving", "true");
        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");
        SpringApplication app = new SpringApplication(PushApplication.class);
        // 禁用Spring的Web
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }
}
