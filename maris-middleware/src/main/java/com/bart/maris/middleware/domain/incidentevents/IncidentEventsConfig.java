package com.bart.maris.middleware.domain.incidentevents;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The incident events module's beans. */
@Configuration(proxyBeanMethods = false)
public class IncidentEventsConfig {
    @Bean
    IncidentEventsService incidentEventsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new IncidentEventsService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    IncidentEventsWorksheetService incidentEventsWorksheetService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new IncidentEventsWorksheetService(legacy, messages, new EglTexts(screens), screens);
    }

    @Bean
    IncidentEventsSymptomHistoryService incidentEventsSymptomHistoryService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new IncidentEventsSymptomHistoryService(legacy, messages, new EglTexts(screens));
    }
}
