package com.bart.maris.middleware.legacy.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.bart.maris.middleware.support.TestCatalog;
import org.junit.jupiter.api.Test;

class LegacyCatalogTest {
    private final LegacyCatalog catalog = TestCatalog.get();

    @Test
    void containsEveryServiceOfTheBackend() {
        // 639 service files; one of them (VHDC01IService) has two methods
        assertThat(catalog.services()).hasSize(640);
        assertThat(catalog.services().stream().map(ServiceDefinition::service).distinct()).hasSize(639);
    }

    @Test
    void signonServiceMatchesTheEglSource() {
        ServiceDefinition s = catalog.function("mrs02a").orElseThrow();
        assertThat(s.service()).isEqualTo("MRS02AService");
        assertThat(s.params()).extracting(ServiceDefinition.Param::name)
                .containsExactly("XDCONTROL", "MRS02A_CRIT", "MRS02A_DATA", "PROGRAM_RETURN_REC");
        assertThat(catalog.record("MRSMainMenu.MRS02A_CRIT").length()).isEqualTo(14);
        assertThat(catalog.record("MRSMainMenu.MRS02A_DATA").length()).isEqualTo(51);
        assertThat(catalog.record("MRSCommonCode.XDCONTROL").length()).isEqualTo(122);
    }

    @Test
    void messagesComeFromTheXxm0it1Table() {
        assertThat(catalog.message("WS00I003")).get().extracting(MessageDefinition::text)
                .isEqualTo("User Group ~1 is invalid. Please reenter the user group or contact your system administrator.");
    }
}
