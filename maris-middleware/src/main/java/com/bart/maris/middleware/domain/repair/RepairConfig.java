package com.bart.maris.middleware.domain.repair;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The repair module's beans. */
@Configuration(proxyBeanMethods = false)
public class RepairConfig {
    @Bean
    RepairService repairService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock, MarisProperties props) {
        return new RepairService(legacy, messages, new EglTexts(screens), clock, props.engine().terminalId());
    }
}
