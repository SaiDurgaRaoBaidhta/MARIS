package com.bart.maris.middleware.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Real MRS_USER_GROUP rows from the local database (USER_TYPE, MENU_GUI_NAME). */
class UiRolesTest {
    @Test
    void rolesFollowUserTypeThenStartMenu() {
        assertThat(UiRoles.role("X", "YARD    ", "VHYT71")).isEqualTo("TOWER");   // CYTOWER
        assertThat(UiRoles.role("X", "SHOP", "VHYT61")).isEqualTo("SHOP");       // CYSHOP
        assertThat(UiRoles.role("X", "ALL", "MRS01I")).isEqualTo("ADMIN");
        assertThat(UiRoles.role("X", "", "VH001I")).isEqualTo("VTD");            // TRBLDESK, VEHSUPR
        assertThat(UiRoles.role("X", "", "VHMTOI")).isEqualTo("TRANS");          // CENTRAL, TRANS, TOWERS
        assertThat(UiRoles.role("X", "", "VHMVSI")).isEqualTo("SHOP");           // SHOPSUPR
        assertThat(UiRoles.role("X", "", "MRS01I")).isEqualTo("ADMIN");          // BRIGHTCO
        assertThat(UiRoles.role("X", null, "WS01I")).isEqualTo("TRANS");         // anything else: no update rights
        assertThat(UiRoles.role("TOWERS", "", "VHMTOI")).isEqualTo("TOWER");    // named exception
        assertThat(UiRoles.role("TRANS", "", "VHMTOI")).isEqualTo("TRANS");
    }

    @Test
    void onlyTowerShopAndAdminUpdate() {
        assertThat(UiRoles.canUpdate("TOWER")).isTrue();
        assertThat(UiRoles.canUpdate("ADMIN")).isTrue();
        assertThat(UiRoles.canUpdate("VTD")).isFalse();
        assertThat(UiRoles.canUpdate("TRANS")).isFalse();
    }
}
