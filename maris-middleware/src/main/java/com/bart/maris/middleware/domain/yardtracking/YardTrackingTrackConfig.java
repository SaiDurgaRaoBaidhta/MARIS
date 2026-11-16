package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The track windows' beans (old VHYT08I, VHYT02I, VHYT06I, VHYT14I); the terminal id is maris.engine.terminal-id. */
@Configuration(proxyBeanMethods = false)
public class YardTrackingTrackConfig {
    @Bean
    YardTrackingTrackService yardTrackingTrackService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                      MarisProperties props) {
        return new YardTrackingTrackService(legacy, messages, screens, clock, props.engine().terminalId());
    }

    @Bean
    YardTrackingTrackReportService yardTrackingTrackReportService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingTrackReportService(legacy, messages, screens);
    }

    @Bean
    YardTrackingTrackReasonService yardTrackingTrackReasonService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                  MarisProperties props) {
        return new YardTrackingTrackReasonService(legacy, messages, screens, props.engine().terminalId());
    }

    @Bean
    YardTrackingTrackFixOrderService yardTrackingTrackFixOrderService(LegacyProgramService legacy, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new YardTrackingTrackFixOrderService(legacy, screens, clock, props.engine().terminalId());
    }
}
