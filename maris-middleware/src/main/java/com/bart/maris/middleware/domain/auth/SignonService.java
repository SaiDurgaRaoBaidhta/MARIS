package com.bart.maris.middleware.domain.auth;

import com.bart.maris.middleware.domain.LegacyBusinessException;
import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.ProgramResult;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Sign-on through legacy program MRS02A, following the old screen MRS02I (RUIFunctions.egl, MRS02IS-CHECK_ID):
 * group and password are upper-cased; URC 0 or 4 means the lookup worked; then USER_GRP_EXISTS and
 * SIGNON_IS_VALID decide; failures use messages WS00I002 / WS00I003.
 */
@Service
public class SignonService {
    static final String PROGRAM = "MRS02A";

    private final LegacyProgramService legacy;
    private final MessageResolver messages;

    public SignonService(LegacyProgramService legacy, MessageResolver messages) {
        this.legacy = legacy;
        this.messages = messages;
    }

    public SignonResult signon(String groupId, String password) {
        String group = groupId.strip().toUpperCase(Locale.ROOT);
        String pw = password.toUpperCase(Locale.ROOT);
        ProgramResult r = legacy.execute(PROGRAM, Map.of("MRS02A_CRIT", Map.of("userGrp", group, "userPassword", pw)), true);

        XdControl c = r.control();
        if (c != null && c.outcome() == XdControl.Outcome.ERROR) {
            throw new LegacyBusinessException(PROGRAM, c, messages.resolve(c.messageCode(), c.messageInserts()));
        }
        Map<String, Object> d = r.record("MRS02A_DATA");
        if (!"Y".equals(d.get("userGrpExists"))) {
            return failed(SignonResult.Status.UNKNOWN_GROUP, group, "WS00I003");
        }
        if (!"Y".equals(d.get("signonIsValid"))) {
            return failed(SignonResult.Status.WRONG_PASSWORD, group, "WS00I002");
        }
        return new SignonResult(SignonResult.Status.VALID, group, s(d.get("userGrpDesc")), s(d.get("menuGuiName")),
                s(d.get("userType")), s(d.get("yrdTrkgYrd")), s(d.get("incdPfx")), null, null);
    }

    private SignonResult failed(SignonResult.Status status, String group, String code) {
        MessageResolver.Message m = messages.resolve(code, List.of(group));
        return new SignonResult(status, group, null, null, null, null, null, code, m == null ? null : m.text());
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString().strip();
    }
}
