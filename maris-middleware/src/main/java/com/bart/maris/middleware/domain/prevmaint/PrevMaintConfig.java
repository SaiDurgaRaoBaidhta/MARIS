package com.bart.maris.middleware.domain.prevmaint;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The preventive maintenance module's beans. */
@Configuration(proxyBeanMethods = false)
public class PrevMaintConfig {
    @Bean
    PrevMaintService prevMaintService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new PrevMaintService(legacy, messages, new EglTexts(screens));
    }

    /** PM scheduling (old VHPMS01I); updates are recorded with the configured terminal id, like the old menu passed it */
    @Bean
    PrevMaintScheduleService prevMaintScheduleService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, LegacyCatalog catalog,
                                                      Clock clock, MarisProperties props) {
        return new PrevMaintScheduleService(legacy, messages, new EglTexts(screens), new PrevMaintRecordText(catalog), clock, props.engine().terminalId());
    }
}
