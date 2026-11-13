package com.bart.maris.middleware.engine.runtime;

import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One record variable of a screen: its layout and its bytes. Substructures overlay their parent's bytes,
 * so redefinitions (CON-NO / DASH-NO-C / DASH-NO-N) behave exactly as in the old runtime.
 */
public final class Rec {
    /** a subscript outside an array: reads give the empty value and writes are skipped, as the browser runtime did */
    public static final class IndexRangeException extends EngineException {
        public IndexRangeException(String m) { super(m); }
    }

    public final String var;              // variable name in the handler, e.g. VHIC31IW-TRIGGERS
    public final RecordLayout layout;
    public byte[] data;

    public Rec(String var, RecordLayout layout) {
        this.var = var;
        this.layout = layout;
        this.data = RecordCodec.blank(layout);
    }

    public Rec copyOf(String newVar) {
        Rec r = new Rec(newVar, layout);
        r.data = data.clone();
        return r;
    }

    /** path from the record to a field: the field and its ancestors, outermost first */
    public record Path(List<FieldLayout> chain) {
        public FieldLayout field() { return chain.get(chain.size() - 1); }
        public List<FieldLayout> dims() {
            List<FieldLayout> d = new ArrayList<>();
            for (FieldLayout f : chain) if (f.occurs() > 1) d.add(f);
            return d;
        }
    }

    private static final Map<RecordLayout, Map<String, Path>> PATHS = new ConcurrentHashMap<>();

    /** finds a field by name anywhere in the record (EGL: unique leaf name), or null */
    public Path find(String name) {
        return find(layout, name);
    }

    public static Path find(RecordLayout layout, String name) {
        Map<String, Path> m = PATHS.computeIfAbsent(layout, Rec::index);
        return m.get(name.toUpperCase(Locale.ROOT));
    }

    /** finds a field below a given field */
    public static Path findBelow(Path parent, String name) {
        List<FieldLayout> chain = new ArrayList<>(parent.chain());
        Path p = search(parent.field().children(), name.toUpperCase(Locale.ROOT), chain);
        return p;
    }

    private static Path search(List<FieldLayout> fields, String upper, List<FieldLayout> chain) {
        for (FieldLayout f : fields) {
            chain.add(f);
            if (f.name().toUpperCase(Locale.ROOT).equals(upper)) return new Path(List.copyOf(chain));
            Path p = search(f.children(), upper, chain);
            if (p != null) return p;
            chain.remove(chain.size() - 1);
        }
        return null;
    }

    private static Map<String, Path> index(RecordLayout layout) {
        Map<String, Path> m = new HashMap<>();
        walk(layout.fields(), new ArrayList<>(), m);
        return m;
    }

    private static void walk(List<FieldLayout> fields, List<FieldLayout> chain, Map<String, Path> m) {
        for (FieldLayout f : fields) {
            chain.add(f);
            if (!f.filler()) m.putIfAbsent(f.name().toUpperCase(Locale.ROOT), new Path(List.copyOf(chain)));
            walk(f.children(), chain, m);
            chain.remove(chain.size() - 1);
        }
    }

    /** byte offset of an element: one 1-based index per array dimension of the path (missing -> 1) */
    public static int offset(Path path, int[] idx) {
        int off = 0, d = 0;
        for (FieldLayout f : path.chain()) {
            off += f.offset();
            if (f.occurs() > 1) {
                int i = d < idx.length ? idx[d] : 1;
                d++;
                if (i < 1 || i > f.occurs()) throw new IndexRangeException("index " + i + " out of range for " + f.name() + "[" + f.occurs() + "]");
                off += (i - 1) * f.bytes();
            }
        }
        return off;
    }
}
