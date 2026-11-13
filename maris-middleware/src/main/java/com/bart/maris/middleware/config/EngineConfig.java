package com.bart.maris.middleware.config;

import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.time.Clock;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The screen engine: the old screens' logic, run against the existing backend services. */
@Configuration(proxyBeanMethods = false)
public class EngineConfig {
    @Bean
    ScreenBundle screenBundle(MarisProperties props) {
        return ScreenBundle.load(props.engine().location());
    }

    @Bean
    ScreenEngine screenEngine(ScreenBundle bundle, LegacyCatalog catalog, LegacyServiceClient client, Clock clock, MarisProperties props) {
        return new ScreenEngine(bundle, catalog, client, clock,
                props.engine().blockedPrograms().stream().map(p -> p.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()),
                props.engine().allowedPrograms().stream().map(p -> p.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()))
                .terminalId(props.engine().terminalId());
    }
}
