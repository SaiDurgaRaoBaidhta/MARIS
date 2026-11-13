package com.bart.maris.middleware.domain.yardreports;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The yard reports module's beans. */
@Configuration(proxyBeanMethods = false)
public class YardReportsConfig {
    @Bean
    YardReportsService yardReportsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardReportsService(legacy, messages, screens);
    }
}
