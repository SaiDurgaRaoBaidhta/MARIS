package com.bart.maris.middleware.security;

import java.io.Serializable;
import java.time.Instant;

/**
 * The signed-on MARIS user group, kept in the HTTP session. The password is never stored.
 *
 * @param role     the new UI's role (VTD, TRANS, TOWER, SHOP, ADMIN), see {@code domain.auth.UiRoles}
 * @param signedAt when the group signed on
 */
public record MarisUser(String groupId, String groupDescription, String menu, String userType, String yard,
                        String incidentPrefix, String role, Instant signedAt) implements Serializable {}
