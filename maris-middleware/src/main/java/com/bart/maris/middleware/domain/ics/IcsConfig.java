package com.bart.maris.middleware.domain.ics;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The ICS module's beans. Each module has its own config class in its own package (no shared config file to edit). */
@Configuration(proxyBeanMethods = false)
public class IcsConfig {
    @Bean
    IcsReportService icsReportService(LegacyProgramService legacy, MessageResolver messages, Clock clock, ScreenBundle screens) {
        return new IcsReportService(legacy, messages, clock, screens);
    }
}
