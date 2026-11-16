package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Consist tracking (old VHST01I .. VHST05I) */
@Configuration(proxyBeanMethods = false)
public class VehicleQueriesConsistTrackingConfig {
    @Bean
    VehicleQueriesConsistTrackingService vehicleQueriesConsistTrackingService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesConsistTrackingService(legacy, messages, new EglTexts(screens), screens);
    }
}
