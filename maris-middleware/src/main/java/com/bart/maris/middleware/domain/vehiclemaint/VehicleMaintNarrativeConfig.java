package com.bart.maris.middleware.domain.vehiclemaint;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.engine.ScreenEngine;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The narrative window's bean (old VHNAR01I); narratives are recorded with the terminal id the screens use. */
@Configuration(proxyBeanMethods = false)
public class VehicleMaintNarrativeConfig {
    @Bean
    VehicleMaintNarrativeService vehicleMaintNarrativeService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, ScreenEngine engine) {
        return new VehicleMaintNarrativeService(legacy, messages, new EglTexts(screens), engine.terminalId());
    }
}
