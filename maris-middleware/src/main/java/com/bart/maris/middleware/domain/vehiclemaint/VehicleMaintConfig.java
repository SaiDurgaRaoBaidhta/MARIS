package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The vehicle maintenance module's beans. */
@Configuration(proxyBeanMethods = false)
public class VehicleMaintConfig {
    @Bean
    VehicleMaintReportsService vehicleMaintReportsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleMaintReportsService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleMaintCarService vehicleMaintCarService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock,
                                                  MarisProperties props) {
        return new VehicleMaintCarService(legacy, messages, new EglTexts(screens), screens, clock, props.engine().terminalId());
    }

    @Bean
    VehicleMaintStatusHistoryService vehicleMaintStatusHistoryService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                      Clock clock, MarisProperties props) {
        return new VehicleMaintStatusHistoryService(legacy, messages, new EglTexts(screens), clock, props.engine().terminalId());
    }
}
