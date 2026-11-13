package com.bart.maris.middleware.domain.screens;

import com.bart.maris.middleware.legacy.control.XdControl;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * What a screen button returns, as the UI expects it (maris-ui src/api/openapi.yaml, schema ActionResult).
 *
 * @param fields  values to write back into the screen, by the screen's field name (data-w)
 * @param rows    table (list box) name -> rows, each row the table's columns in order
 * @param message text for the user; shown as an error when {@code control.URC} is not 0
 */
public record ActionResult(Control control, Map<String, String> fields, Map<String, List<List<String>>> rows, String message) {

    /** The EGL XDCONTROL record, with the field names the UI uses. */
    public record Control(@JsonProperty("URC") int urc, @JsonProperty("UMSGCODE") String msgCode,
                          @JsonProperty("UMSGINS") String msgIns, @JsonProperty("UUP") String up,
                          @JsonProperty("UDOWN") String down) {
        public static Control ok() {
            return new Control(0, "", "", "", "");
        }

        public static Control of(XdControl c) {
            return new Control(c.returnCode(), c.messageCode(), String.join(" ", c.messageInserts()),
                    c.moreUp() ? "Y" : "", c.moreDown() ? "Y" : "");
        }

        /** A check made by the screen itself before calling any program (the old screens' own messages). */
        public static Control screenError() {
            return new Control(8, "", "", "", "");
        }
    }
}
