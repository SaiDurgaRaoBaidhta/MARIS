package com.bart.maris.middleware.legacy.control;

import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import java.util.List;

/** Turns a MARIS message code plus inserts into the text the old screens show (XXM0IT1 table, ~1..~3 markers). */
public class MessageResolver {
    private final LegacyCatalog catalog;

    public MessageResolver(LegacyCatalog catalog) {
        this.catalog = catalog;
    }

    public record Message(String code, String type, String text) {}

    public Message resolve(String code, List<String> inserts) {
        if (code == null || code.isBlank()) return null;
        return catalog.message(code)
                .map(m -> new Message(code.strip(), m.type(), fill(m.text(), inserts)))
                .orElse(new Message(code.strip(), null, null));
    }

    static String fill(String text, List<String> inserts) {
        String t = text;
        for (int i = 0; i < 3; i++) {
            t = t.replace("~" + (i + 1), i < inserts.size() ? inserts.get(i) : "");
        }
        return t.replaceAll(" {2,}", " ").strip();
    }
}
