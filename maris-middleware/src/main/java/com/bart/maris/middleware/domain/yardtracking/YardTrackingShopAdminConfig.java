package com.bart.maris.middleware.domain.yardtracking;

import com.bart.maris.middleware.config.MarisProperties;
import com.bart.maris.middleware.engine.ScreenBundle;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The shop administration maps (VHYT10I, VHYT95I) and the shop car move (VHYT03I with its reason check VHYT42I). */
@Configuration(proxyBeanMethods = false)
public class YardTrackingShopAdminConfig {
    @Bean
    YardTrackingShopAdminService yardTrackingShopAdminService(LegacyProgramService legacy, MessageResolver messages, ScreenBundle screens) {
        return new YardTrackingShopAdminService(legacy, messages, screens, Clock.systemDefaultZone());
    }

    @Bean
    YardTrackingShopMoveService yardTrackingShopMoveService(LegacyProgramService legacy, ScreenBundle screens, MarisProperties props) {
        return new YardTrackingShopMoveService(legacy, screens, Clock.systemDefaultZone(), props.engine().terminalId());
    }
}
