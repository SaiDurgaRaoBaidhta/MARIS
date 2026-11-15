package com.bart.maris.middleware.domain.incidents;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans of the vehicle tracking change incident screens VHCI02I and VHCI03I (they record the terminal id the screens use). */
@Configuration(proxyBeanMethods = false)
public class IncidentsTrackingChangeConfig {
    @Bean
    IncidentsNeverGoService incidentsNeverGoService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock, ScreenEngine engine) {
        return new IncidentsNeverGoService(legacy, messages, new EglTexts(screens), clock, engine.terminalId());
    }

    /** VHCI02I's programs that write (the screen inventory's list): not called again when a change rebuilds the list */
    static final Set<String> VHCI02I_WRITERS = Set.of("VHCI01F", "VHCI02D", "VHCIP", "VHCIQ", "VHCIR", "VHCIS", "VHCIT", "VHCIU", "VHCIW", "VHCIX", "VHCIY",
            "VHCIZ", "VHINVA", "VHINVB", "VHINXB", "VHINXD");

    @Bean
    IncidentsTrackingChangeService incidentsTrackingChangeService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                                  ScreenEngine engine) {
        return new IncidentsTrackingChangeService(screens, legacy, messages, new EglTexts(screens), clock, engine.terminalId(), VHCI02I_WRITERS);
    }
}
