package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** beans of the consist log (VHCL01I, VHCL02I) and the daily vehicle status (VHDVS1I .. VHDVS4I) */
@Configuration(proxyBeanMethods = false)
public class VehicleQueriesConsistLogConfig {
    @Bean
    VehicleQueriesConsistLogService vehicleQueriesConsistLogService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog,
                                                                     ScreenBundle screens, Clock clock) {
        return new VehicleQueriesConsistLogService(legacy, messages, catalog, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesDailyStatusService vehicleQueriesDailyStatusService(ScreenBundle screens, LegacyProgramService legacy, MessageResolver messages, Clock clock) {
        return new VehicleQueriesDailyStatusService(screens, legacy, messages, new EglTexts(screens), clock);
    }
}
