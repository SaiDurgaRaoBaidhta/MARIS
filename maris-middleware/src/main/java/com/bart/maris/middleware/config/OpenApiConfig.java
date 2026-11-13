package com.bart.maris.middleware.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean
    OpenAPI marisOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MARIS Middleware API")
                .version("v1")
                .description("JSON API for the new MARIS UI. Calls the existing EGL services unchanged."));
    }
}
