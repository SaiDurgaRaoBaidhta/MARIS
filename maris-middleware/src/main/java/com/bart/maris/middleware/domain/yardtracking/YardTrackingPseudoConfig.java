package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The pseudo train feature's beans: pseudo status train and its printer window (old VHYT09I, VHYT01I), the Hayward yard
 * map (Vhyt07i), verify / reset a pseudo train (VHYT12I; the terminal id is maris.engine.terminal-id).
 */
@Configuration(proxyBeanMethods = false)
public class YardTrackingPseudoConfig {
    @Bean
    YardTrackingPseudoTrainService yardTrackingPseudoTrainService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingPseudoTrainService(legacy, messages, screens);
    }

    @Bean
    YardTrackingHaywardMapService yardTrackingHaywardMapService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingHaywardMapService(legacy, messages, screens);
    }

    @Bean
    YardTrackingConsistVerifyService yardTrackingConsistVerifyService(ScreenBundle screens, LegacyProgramService legacy, Clock clock, MarisProperties props) {
        return new YardTrackingConsistVerifyService(screens, legacy, clock, props.engine().terminalId());
    }
}
