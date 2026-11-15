package com.bart.maris.middleware.domain.secondaryrepair;

import com.bart.maris.middleware.domain.common.EglTexts;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The secondary repair module's beans. */
@Configuration(proxyBeanMethods = false)
public class SecondaryRepairConfig {
    @Bean
    SecondaryRepairService secondaryRepairService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new SecondaryRepairService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    SecondaryRepairLaborService secondaryRepairLaborService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new SecondaryRepairLaborService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    SecondaryRepairRepairDetailService secondaryRepairRepairDetailService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new SecondaryRepairRepairDetailService(legacy, messages, new EglTexts(screens));
    }

    @Bean
    SecondaryRepairJobSplitService secondaryRepairJobSplitService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new SecondaryRepairJobSplitService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    SecondaryRepairInDoorService secondaryRepairInDoorService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens, Clock clock) {
        return new SecondaryRepairInDoorService(legacy, messages, new EglTexts(screens), clock);
    }

    @Bean
    SecondaryRepairCommentsService secondaryRepairCommentsService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new SecondaryRepairCommentsService(legacy, messages, new EglTexts(screens));
    }
}
