package com.bart.maris.middleware.domain.vehiclequeries;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class VehicleQueriesConfig {
    @Bean
    VehicleQueriesBatchReportService vehicleQueriesBatchReportService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog, ScreenBundle screens,
                                                                       Clock clock) {
        return new VehicleQueriesBatchReportService(legacy, messages, catalog, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesCarStatusService vehicleQueriesCarStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new VehicleQueriesCarStatusService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesHomeYardFleetService vehicleQueriesHomeYardFleetService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesHomeYardFleetService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesCarHoursService vehicleQueriesCarHoursService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new VehicleQueriesCarHoursService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesFleetReportService vehicleQueriesFleetReportService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesFleetReportService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesRepeaterCarsService vehicleQueriesRepeaterCarsService(LegacyProgramService legacy, MessageResolver messages, LegacyCatalog catalog,
                                                                        ScreenBundle screens) {
        return new VehicleQueriesRepeaterCarsService(legacy, messages, catalog, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesHomeYardStatusService vehicleQueriesHomeYardStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesHomeYardStatusService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesYardStatusService vehicleQueriesYardStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesYardStatusService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    VehicleQueriesVehicleStatusService vehicleQueriesVehicleStatusService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new VehicleQueriesVehicleStatusService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    VehicleQueriesCarInquiryService vehicleQueriesCarInquiryService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new VehicleQueriesCarInquiryService(legacy, messages, new EglTexts(screens));
    }
}
