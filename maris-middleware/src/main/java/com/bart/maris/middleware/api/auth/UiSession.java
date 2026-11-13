package com.bart.maris.middleware.api.auth;

import com.bart.maris.middleware.domain.auth.UiRoles;
import com.bart.maris.middleware.security.MarisUser;
import java.time.Instant;

/** The session as the UI expects it (maris-ui src/api/openapi.yaml, schema Session). */
public record UiSession(String user, String role, String group, boolean update, Instant signedAt,
                        String menu, String yard) {
    static UiSession of(MarisUser u) {
        return new UiSession(u.groupId(), u.role(), u.groupDescription(), UiRoles.canUpdate(u.role()), u.signedAt(),
                u.menu(), u.yard());
    }
}
