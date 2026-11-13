package com.bart.maris.middleware.domain.yardreports;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The yard reports module's beans. */
@Configuration(proxyBeanMethods = false)
public class YardReportsConfig {
    @Bean
    YardReportsService yardReportsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardReportsService(legacy, messages, screens);
    }

    /** the terminal id the old screens record on updates (maris.engine.terminal-id) */
    @Bean
    YardReportsMoveService yardReportsMoveService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new YardReportsMoveService(legacy, messages, screens, props.engine().terminalId());
    }

    @Bean
    YardReportsPseudoService yardReportsPseudoService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new YardReportsPseudoService(legacy, messages, screens, props.engine().terminalId());
    }
}
