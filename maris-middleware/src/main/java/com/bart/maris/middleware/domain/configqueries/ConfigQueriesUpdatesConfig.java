package com.bart.maris.middleware.domain.configqueries;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The configuration tracking queries module's update screens (VHCF21I, VHCF06I, VHCF35I, VHCF37I). */
@Configuration(proxyBeanMethods = false)
public class ConfigQueriesUpdatesConfig {
    @Bean
    ConfigQueriesRuleScreens configQueriesRuleScreens(ScreenBundle screens, LegacyProgramService legacy, MessageResolver messages, Clock clock, MarisProperties props) {
        return new ConfigQueriesRuleScreens(screens, legacy, messages, clock, props.engine().terminalId());
    }

    @Bean
    ConfigQueriesComponentAddService configQueriesComponentAddService(ConfigQueriesRuleScreens configQueriesRuleScreens) {
        return new ConfigQueriesComponentAddService(configQueriesRuleScreens);
    }

    @Bean
    ConfigQueriesTruckService configQueriesTruckService(ConfigQueriesRuleScreens configQueriesRuleScreens) {
        return new ConfigQueriesTruckService(configQueriesRuleScreens);
    }

    @Bean
    ConfigQueriesAxleService configQueriesAxleService(ConfigQueriesRuleScreens configQueriesRuleScreens) {
        return new ConfigQueriesAxleService(configQueriesRuleScreens);
    }
}
