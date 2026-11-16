package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The Hayward yard update map (old VHYT05I). */
@Configuration(proxyBeanMethods = false)
public class YardTrackingHaywardConfig {
    @Bean
    YardTrackingHaywardService yardTrackingHaywardService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingHaywardService(legacy, messages, screens, Clock.systemDefaultZone());
    }
}
