package com.bart.maris.middleware.domain.yardqueries;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The yard tracking queries module's beans. */
@Configuration(proxyBeanMethods = false)
public class YardQueriesConfig {
    @Bean
    YardQueriesShopStatusService yardQueriesShopStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardQueriesShopStatusService(legacy, messages, screens);
    }
}
