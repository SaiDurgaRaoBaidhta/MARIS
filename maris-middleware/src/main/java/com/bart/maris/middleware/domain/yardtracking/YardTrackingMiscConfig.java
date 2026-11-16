package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for old VHYT22I, VHYT23I, VHYT31I (shop activity and its prints), VHYT52I, VHYT54I (in-service trains) and
 * VHYT34I (make / break overrides, module MrsYardTrackingQueries; it runs on this package's screen logic runner).
 */
@Configuration(proxyBeanMethods = false)
public class YardTrackingMiscConfig {
    @Bean
    YardTrackingShopActivityService yardTrackingShopActivityService(LegacyProgramService legacy, ScreenBundle screens, Clock clock) {
        return new YardTrackingShopActivityService(legacy, screens, clock);
    }

    @Bean
    YardTrackingOverridesService yardTrackingOverridesService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new YardTrackingOverridesService(legacy, screens, clock, props.engine().terminalId());
    }

    @Bean
    YardTrackingTrainsService yardTrackingTrainsService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new YardTrackingTrainsService(legacy, screens, clock, props.engine().terminalId());
    }
}
