package com.bart.maris.middleware.domain.incidents;

import static com.bart.maris.middleware.domain.common.Programs.number;
import static com.bart.maris.middleware.domain.common.Programs.text;

import com.bart.maris.middleware.domain.InvalidRequestException;
import com.bart.maris.middleware.domain.common.Programs;
import com.bart.maris.middleware.legacy.ProgramResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The checks the incident entry screens share: the user group's access (VH01A02), the symptom code (VHINC1A), the speed. */
final class IncidentCalls {
    private final Programs programs;

    IncidentCalls(Programs programs) {
        this.programs = programs;
    }

    /** VH001IS-INIT-Y + ACCE-*: refused when the group's access row for the button part says N */
    void access(String userGroup, String gui, String part, String denied) {
        Map<String, Object> crit = new LinkedHashMap<>();
        crit.put("userGrp", userGroup);
        crit.put("guiName", gui);
        ProgramResult r = programs.run("VH01A02", Map.of("VH01A02_CRIT", crit));
        Map<String, Object> d = r.record("VH01A02_DATA");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = d != null && d.get("userGroupAccess") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        for (Map<String, Object> row : rows) {
            Object p = row.get("partName");
            if (p != null && p.toString().strip().equals(part)) {
                if ("N".equals(text(row, "accessAllow"))) throw new InvalidRequestException(denied);
                return;
            }
        }
    }

    /** VHINC1A: the symptom code exists (a program error counts as found, as on the old screens) */
    boolean symptomExists(String symptom, Map<String, Object> crit) {
        crit.put("vagenCount", 0);
        crit.put("sympCd", symptom);
        ProgramResult r = programs.attempt("VHINC1A", Map.of("VHINC1A_CRIT", crit)).result();
        int urc = r.control() == null ? 0 : r.control().returnCode();
        return !(urc == 0 || urc == 4) || number(r.record("VHINC1A_CRIT"), "vagenCount") != 0;
    }

    /** a speed other than ALL must be digits (blanks allowed between) */
    static boolean badSpeed(String speed, String all) {
        if (speed.equals(all)) return false;
        for (char ch : IncidentScreens.fit(speed, 3).toCharArray()) if (ch != ' ' && !Character.isDigit(ch)) return true;
        return false;
    }
}
