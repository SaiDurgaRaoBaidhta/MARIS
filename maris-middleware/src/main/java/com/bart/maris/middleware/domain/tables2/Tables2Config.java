package com.bart.maris.middleware.domain.tables2;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The table maintenance 2 module's beans (MRS messages received: VHCCS04I, 05I, 06I, 07I, 18I). */
@Configuration(proxyBeanMethods = false)
public class Tables2Config {
    @Bean
    Tables2MessagesService tables2MessagesService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new Tables2MessagesService(legacy, messages, screens, clock);
    }

    @Bean
    Tables2MessageDetailsService tables2MessageDetailsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new Tables2MessageDetailsService(legacy, messages, screens);
    }
}
