package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The yard tracking maps' beans (VHYT40I, VHYT41I, VHYT50I, VHYT51I, VHYT04I). */
@Configuration(proxyBeanMethods = false)
public class YardTrackingMapsConfig {
    @Bean
    YardTrackingMapsService yardTrackingMapsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingMapsService(legacy, messages, screens);
    }
}
