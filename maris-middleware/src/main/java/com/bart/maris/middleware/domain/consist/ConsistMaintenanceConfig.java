package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The consist maintenance screens' beans (make train, combine, clear, change, swap, split, move a car). */
@Configuration(proxyBeanMethods = false)
public class ConsistMaintenanceConfig {
    @Bean
    ConsistRuleScreens consistRuleScreens(ScreenBundle screens, LegacyProgramService legacy, MessageResolver messages, Clock clock, MarisProperties props) {
        return new ConsistRuleScreens(screens, legacy, messages, clock, props.engine().terminalId());
    }

    @Bean
    ConsistMaintenanceService consistMaintenanceService(ConsistRuleScreens consistRuleScreens) {
        return new ConsistMaintenanceService(consistRuleScreens);
    }

    @Bean
    ConsistCarLocationService consistCarLocationService(ConsistRuleScreens consistRuleScreens) {
        return new ConsistCarLocationService(consistRuleScreens);
    }
}
