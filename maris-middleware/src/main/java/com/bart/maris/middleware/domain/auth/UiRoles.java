package com.bart.maris.middleware.domain.auth;

import java.util.Map;
import java.util.Set;

/**
 * The new UI groups users into five roles (VTD, TRANS, TOWER, SHOP, ADMIN). MARIS has no such column; the role is
 * derived from MRS_USER_GROUP.USER_TYPE and the group's start menu (MENU_GUI_NAME), as returned by MRS02A, with a
 * few named groups whose MARIS data does not tell them apart (TOWERS has the Transportation menu, but the UI treats it
 * as a tower). Checked against every user ID in maris-ui src/features/auth/userGroups.json.
 * Anything not recognised gets TRANS, the role without update rights.
 */
public final class UiRoles {
    private static final Map<String, String> BY_GROUP = Map.of("TOWERS", "TOWER");
    private static final Map<String, String> BY_USER_TYPE = Map.of("ALL", "ADMIN", "YARD", "TOWER", "SHOP", "SHOP");
    private static final Map<String, String> BY_MENU = Map.of(
            "MRS01I", "ADMIN",     // MARIS main menu: every application
            "VH001I", "VTD",       // Trouble Desk main menu
            "VHMTOI", "TRANS",     // Transportation menu
            "VHMVSI", "SHOP",      // Vehicle shop supervisor menu
            "VHMSHI", "SHOP");
    private static final Set<String> UPDATE = Set.of("TOWER", "SHOP", "ADMIN");

    private UiRoles() {}

    public static String role(String group, String userType, String menu) {
        String byGroup = BY_GROUP.get(group == null ? "" : group.strip());
        if (byGroup != null) return byGroup;
        String byType = BY_USER_TYPE.get(userType == null ? "" : userType.strip());
        if (byType != null) return byType;
        return BY_MENU.getOrDefault(menu == null ? "" : menu.strip(), "TRANS");
    }

    /** Roles whose yard and shop maps open in Update mode (the UI's rule). */
    public static boolean canUpdate(String role) {
        return UPDATE.contains(role);
    }
}
