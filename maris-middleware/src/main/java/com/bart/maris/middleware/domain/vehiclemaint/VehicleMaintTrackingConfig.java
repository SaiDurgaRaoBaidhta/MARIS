package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bean of the vehicle parts status / mod tracking screen VHVM11I (it records the terminal id the screens record). */
@Configuration(proxyBeanMethods = false)
public class VehicleMaintTrackingConfig {
    @Bean
    VehicleMaintTrackingService vehicleMaintTrackingService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, MarisProperties props) {
        return new VehicleMaintTrackingService(legacy, messages, new EglTexts(screens), screens, props.engine().terminalId());
    }
}
