package com.bart.maris.middleware.domain.incidents;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The incidents module's beans; updates are recorded with the terminal id the screens use. */
@Configuration(proxyBeanMethods = false)
public class IncidentsConfig {
    @Bean
    IncidentScreens incidentScreens(ScreenBundle screens) {
        return new IncidentScreens(screens);
    }

    @Bean
    IncidentsTrainService incidentsTrainService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, IncidentScreens incidentScreens,
                                                Clock clock, ScreenEngine engine) {
        return new IncidentsTrainService(legacy, messages, new EglTexts(screens), incidentScreens, clock, engine.terminalId());
    }

    @Bean
    IncidentsRevenueService incidentsRevenueService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, IncidentScreens incidentScreens,
                                                    IncidentsTrainService train, Clock clock, ScreenEngine engine) {
        return new IncidentsRevenueService(legacy, messages, new EglTexts(screens), incidentScreens, train, clock, engine.terminalId());
    }

    @Bean
    IncidentsCarService incidentsCarService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, IncidentScreens incidentScreens,
                                            Clock clock, ScreenEngine engine) {
        return new IncidentsCarService(legacy, messages, new EglTexts(screens), incidentScreens, clock, engine.terminalId());
    }
}
