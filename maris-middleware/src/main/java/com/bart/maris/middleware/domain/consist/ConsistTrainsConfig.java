package com.bart.maris.middleware.domain.consist;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lay-up (old VHLT01I) and reconfigure (old VHTR01I / VHTR02I) beans; the terminal id the old screens record is maris.engine.terminal-id. */
@Configuration(proxyBeanMethods = false)
public class ConsistTrainsConfig {
    @Bean
    ConsistLayUpService consistTrainsLayUpService(ScreenBundle screens, LegacyProgramService legacy, MessageResolver messages, MarisProperties props) {
        return new ConsistLayUpService(screens, legacy, messages, Clock.systemDefaultZone(), props.engine().terminalId());
    }

    @Bean
    ConsistReconfigureService consistTrainsReconfigureService(ScreenBundle screens, LegacyProgramService legacy, MessageResolver messages, MarisProperties props) {
        return new ConsistReconfigureService(screens, legacy, messages, Clock.systemDefaultZone(), props.engine().terminalId());
    }
}
