package com.bart.maris.middleware.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "Today" for screen checks, in the server's time zone like the EGL programs (VGVar.currentGregorianDate). */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
