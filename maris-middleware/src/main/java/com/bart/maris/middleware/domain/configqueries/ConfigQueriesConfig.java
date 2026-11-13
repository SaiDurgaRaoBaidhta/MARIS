package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The configuration tracking queries module's beans. */
@Configuration(proxyBeanMethods = false)
public class ConfigQueriesConfig {
    @Bean
    ConfigQueriesComponentsService configQueriesComponentsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new ConfigQueriesComponentsService(legacy, messages, screens);
    }
}
