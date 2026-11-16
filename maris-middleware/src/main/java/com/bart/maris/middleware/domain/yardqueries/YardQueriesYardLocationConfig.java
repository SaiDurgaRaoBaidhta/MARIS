package com.bart.maris.middleware.domain.yardqueries;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans of the yard location (old VHYT26I) and reason out of service (VHYT32I) reports; terminal id = maris.engine.terminal-id. */
@Configuration(proxyBeanMethods = false)
public class YardQueriesYardLocationConfig {
    @Bean
    YardQueriesYardLocationService yardQueriesYardLocationService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new YardQueriesYardLocationService(legacy, screens, clock, props.engine().terminalId());
    }

    @Bean
    YardQueriesOutOfServiceService yardQueriesOutOfServiceService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new YardQueriesOutOfServiceService(legacy, screens, clock, props.engine().terminalId());
    }
}
