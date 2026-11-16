package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Vehicle queries: consist orientation (VHCO01I), D and C sheet reprint (VHDC01I), SCRAM (VHSCM1I), car status change (VHCHG1I). */
@Configuration(proxyBeanMethods = false)
public class VehicleQueriesMiscConfig {
    @Bean
    VehicleQueriesConsistOrientationService vehicleQueriesMiscConsistOrientationService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesConsistOrientationService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesDncSheetService vehicleQueriesMiscDncSheetService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesDncSheetService(legacy, messages, new EglTexts(screens), screens);
    }

    @Bean
    VehicleQueriesScramService vehicleQueriesMiscScramService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new VehicleQueriesScramService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesCarStatusChangeService vehicleQueriesMiscCarStatusChangeService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesCarStatusChangeService(legacy, messages, new EglTexts(screens));
    }
}
