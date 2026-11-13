package com.bart.maris.middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MarisMiddlewareApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarisMiddlewareApplication.class, args);
    }
}
