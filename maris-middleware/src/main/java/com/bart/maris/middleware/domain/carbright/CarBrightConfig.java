package com.bart.maris.middleware.domain.carbright;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The car bright module's beans. */
@Configuration(proxyBeanMethods = false)
public class CarBrightConfig {
    @Bean
    CarBrightService carBrightService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new CarBrightService(legacy, messages, new EglTexts(screens));
    }

    /** adding thorough cleanings (old VHCBP1I); recorded with the configured terminal id, like the old menu passed it */
    @Bean
    CarBrightThoroughCleanService carBrightThoroughCleanService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                                MarisProperties props) {
        return new CarBrightThoroughCleanService(legacy, messages, new EglTexts(screens), clock, props.engine().terminalId());
    }
}
