package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The car move windows' beans (old VHYT11I, VHYT13I, VHYT24I); the terminal id is maris.engine.terminal-id. */
@Configuration(proxyBeanMethods = false)
public class YardTrackingMoveConfig {
    @Bean
    YardTrackingMoveService yardTrackingMoveService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                    MarisProperties props) {
        return new YardTrackingMoveService(legacy, messages, screens, clock, props.engine().terminalId());
    }

    @Bean
    YardTrackingOrientationService yardTrackingOrientationService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                  Clock clock, MarisProperties props) {
        return new YardTrackingOrientationService(legacy, messages, screens, clock, props.engine().terminalId());
    }

    @Bean
    YardTrackingYardPrintService yardTrackingYardPrintService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new YardTrackingYardPrintService(legacy, messages, screens, clock);
    }
}
