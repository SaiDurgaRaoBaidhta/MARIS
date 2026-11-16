package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans of the vehicle maintenance update screens VHVM08I, VHVM10I and VHVM19I (they record the terminal id the screens record). */
@Configuration(proxyBeanMethods = false)
public class VehicleMaintUpdatesConfig {
    @Bean
    VehicleMaintCarHoursService vehicleMaintCarHoursService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new VehicleMaintCarHoursService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }

    @Bean
    VehicleMaintConsistStatusService vehicleMaintConsistStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                      MarisProperties props) {
        return new VehicleMaintConsistStatusService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }

    @Bean
    VehicleMaintConsistHistoryService vehicleMaintConsistHistoryService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens,
                                                                        MarisProperties props) {
        return new VehicleMaintConsistHistoryService(legacy, messages, new EglTexts(screens), props.engine().terminalId());
    }
}
