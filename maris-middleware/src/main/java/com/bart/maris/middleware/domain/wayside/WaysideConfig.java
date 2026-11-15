package com.bart.maris.middleware.domain.wayside;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The wayside module's beans. */
@Configuration(proxyBeanMethods = false)
public class WaysideConfig {
    @Bean
    WaysideStockService waysideStockService(LegacyProgramService legacy, MessageResolver messages) {
        return new WaysideStockService(legacy, messages);
    }

    /** narratives (old WS13I); added narratives are recorded with the configured terminal id */
    @Bean
    WaysideNarrativeService waysideNarrativeService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new WaysideNarrativeService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }
}
