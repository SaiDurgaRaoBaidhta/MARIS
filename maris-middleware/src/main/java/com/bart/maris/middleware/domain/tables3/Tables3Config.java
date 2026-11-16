package com.bart.maris.middleware.domain.tables3;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The table maintenance (app 3) module's beans: component serials (old VHCF49I, VHCF50I, VHCF51I, VHCF52I). */
@Configuration(proxyBeanMethods = false)
public class Tables3Config {
    @Bean
    Tables3SerialService tables3SerialService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new Tables3SerialService(legacy, messages, screens);
    }

    @Bean
    Tables3SerialDataService tables3SerialDataService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new Tables3SerialDataService(legacy, messages, screens);
    }
}
