package com.bart.maris.middleware.domain.secondaryrepair;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The bean of the secondary repair "out the door" update (old VHSR04I). */
@Configuration(proxyBeanMethods = false)
public class SecondaryRepairOutTheDoorConfig {
    @Bean
    SecondaryRepairOutTheDoorService secondaryRepairOutTheDoorService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new SecondaryRepairOutTheDoorService(legacy, messages, new EglTexts(screens), screens, clock);
    }
}
