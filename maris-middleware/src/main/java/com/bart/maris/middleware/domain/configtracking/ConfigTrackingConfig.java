package com.bart.maris.middleware.domain.configtracking;

import com.bart.maris.middleware.domain.configqueries.ConfigQueriesRuleScreens;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The configuration tracking module's beans (VHCF01I car configuration, VFCF05I its report printer window). */
@Configuration(proxyBeanMethods = false)
public class ConfigTrackingConfig {
    @Bean
    ConfigTrackingCarService configTrackingCarService(ConfigQueriesRuleScreens configQueriesRuleScreens) {
        return new ConfigTrackingCarService(configQueriesRuleScreens);
    }

    @Bean
    ConfigTrackingReportService configTrackingReportService(LegacyProgramService legacy, MessageResolver messages) {
        return new ConfigTrackingReportService(legacy, messages);
    }
}
