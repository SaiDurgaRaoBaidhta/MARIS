package com.bart.maris.middleware.domain.incidents;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The vehicle incident screens' beans (VHINC08I, VHINC09I, VHINC10I); updates record the engine's terminal id. */
@Configuration(proxyBeanMethods = false)
public class IncidentsVehicleConfig {
    @Bean
    IncidentsVehicleCreateService incidentsVehicleCreateService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                                MarisProperties props) {
        return new IncidentsVehicleCreateService(legacy, messages, screens, clock, props.engine().terminalId());
    }

    @Bean
    IncidentsVehicleUpdateService incidentsVehicleUpdateService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new IncidentsVehicleUpdateService(legacy, messages, screens, props.engine().terminalId());
    }

    @Bean
    IncidentsVehicleMaintainService incidentsVehicleMaintainService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                    MarisProperties props) {
        return new IncidentsVehicleMaintainService(legacy, messages, screens, props.engine().terminalId());
    }
}
