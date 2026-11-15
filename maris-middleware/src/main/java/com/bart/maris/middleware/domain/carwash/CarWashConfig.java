package com.bart.maris.middleware.domain.carwash;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The car wash module's beans. */
@Configuration(proxyBeanMethods = false)
public class CarWashConfig {
    @Bean
    CarWashService carWashService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new CarWashService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    CarWashConsistWashService carWashConsistWashService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new CarWashConsistWashService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }

    @Bean
    CarWashCarsWashService carWashCarsWashService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new CarWashCarsWashService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }
}
