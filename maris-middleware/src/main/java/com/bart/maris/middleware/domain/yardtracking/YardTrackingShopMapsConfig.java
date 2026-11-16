package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The Concord and Richmond shop maps' beans (VHYT60I, VHYT96I). */
@Configuration(proxyBeanMethods = false)
public class YardTrackingShopMapsConfig {
    @Bean
    YardTrackingShopMapsService yardTrackingShopMapsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingShopMapsService(legacy, messages, screens);
    }
}
