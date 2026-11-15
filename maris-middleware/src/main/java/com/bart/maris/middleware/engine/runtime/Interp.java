package com.bart.maris.middleware.engine.runtime;

import com.bart.maris.middleware.engine.egl.Ast;
import com.bart.maris.middleware.engine.egl.Ast.*;
import com.bart.maris.middleware.legacy.codec.FieldCodec;
import com.bart.maris.middleware.legacy.codec.RecordCodec;
import com.bart.maris.middleware.legacy.layout.FieldLayout;
import com.bart.maris.middleware.legacy.layout.FieldType;
import com.bart.maris.middleware.legacy.layout.RecordLayout;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs EGL statement groups against a screen's records. One interpreter per screen instance. */
public final class Interp {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Interp.class);

    /** what the interpreter needs from the screen it runs in */
    public interface Host {
        /** a record variable by name (handler variable, or a record type created on first use); null if none */
        Rec record(String name);

        /** every record of the screen, in declaration order, for unqualified field names */
        List<Rec> records();

        /** a function by (possibly qualified) name; null if none */
        Ast.Function function(String name);

        /** runs a backend program with the records as parameters; sets {@code sysVar.errorCode} through the interpreter */
        void callProgram(String program, List<Rec> args, Interp in);

        /** a field (or a whole record when path is null) was written */
        void written(Rec rec, Rec.Path path);

        /** the clock, for VGVar dates */
        Supplier<LocalDateTime> clock();
    }

    public String errorCode = "00000000";
    public int arrayIndex;
    private final Host host;
    private final Deque<Map<String, Object>> frames = new ArrayDeque<>();
    private final Map<String, Object> unqualified = new HashMap<>();
    private int depth;
    private long steps;
    private static final long MAX_STEPS = 20_000_000;

    public Interp(Host host) {
        this.host = host;
    }

    // ---- references ----------------------------------------------------------------------------------------

    /** a resolved place to read or write */
    public sealed interface Ref permits FieldRef, RecRef, LocalStr, LocalBool, SysRef {}

    /** a field element; idx has one entry per array dimension given; fewer than the dimensions = an array reference */
    public record FieldRef(Rec rec, Rec.Path path, int[] idx, Integer from, Integer to) implements Ref {
        public int dims() { return path.dims().size(); }
        public boolean isArray() { return idx.length < dims(); }
        public int offset() { return Rec.offset(path, idx); }
        public FieldLayout field() { return path.field(); }
    }

    public record RecRef(Rec rec) implements Ref {}

    public static final class LocalStr implements Ref { public String v = ""; }

    public static final class LocalBool implements Ref { public boolean v; }

    public record SysRef(String name) implements Ref {}

    // ---- running functions ---------------------------------------------------------------------------------

    public Object run(String function) {
        Ast.Function f = host.function(function);
        if (f == null) throw new EngineException("function " + function + " not found");
        return invoke(f, List.of());
    }

    public Object invoke(Ast.Function f, List<Object> args) {
        if (++depth > 200) throw new EngineException("functions call each other too deeply at " + f.name());
        Map<String, Object> frame = new HashMap<>();
        for (int i = 0; i < f.params().size(); i++) {
            Param p = f.params().get(i);
            Object a = i < args.size() ? args.get(i) : null;
            if (a instanceof RecRef rr && !p.type().isPrimitive()) { frame.put(key(p.name()), rr.rec); continue; }
            Ref local = newLocal(p.name(), p.type());
            frame.put(key(p.name()), local instanceof RecRef rr2 ? rr2.rec : local);
            if (a != null) store(local, a instanceof Ref r ? read(r) : a);
        }
        frames.push(frame);
        try {
            exec(f.body());
            return null;
        } catch (ReturnSignal r) {
            return r.value;
        } finally {
            frames.pop();
            depth--;
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object v) { super(null, null, false, false); value = v; }
    }

    private static final class ExitSignal extends RuntimeException {
        final String what;
        ExitSignal(String w) { super(null, null, false, false); what = w; }
    }

    /** a backend call failed in a way the screen cannot see through sysVar.errorCode */
    public static final class ProgramCallException extends EngineException {
        public ProgramCallException(String m, Throwable c) { super(m, c); }
    }

    private void exec(List<Stmt> stmts) {
        for (Stmt s : stmts) exec(s);
    }

    private void exec(Stmt s) {
        if (++steps > MAX_STEPS) throw new EngineException("the screen logic did not finish (loop)");
        switch (s) {
            case Assign a -> assign(resolve(a.target()), a.value(), a.target());
            case If i -> exec(Conv.truthy(eval(i.cond())) ? i.then() : i.otherwise());
            case While w -> {
                while (Conv.truthy(eval(w.cond()))) {
                    try { exec(w.body()); }
                    catch (ExitSignal e) { if (e.what.equals("continue")) continue; if (e.what.equals("while")) break; throw e; }
                }
            }
            case For f -> {
                Ref v = lookupLocal(f.var());
                if (v == null) {
                    Ref made = newLocal(f.var(), new TypeRef("int", 9, 0));
                    frames.peek().put(key(f.var()), made instanceof RecRef rr ? rr.rec : made);
                    v = lookupLocal(f.var());                       // the counter as a field (an INT), not as record text
                }
                BigDecimal by = f.by() == null ? BigDecimal.ONE : Conv.number(eval(f.by()));
                store(v, eval(f.from()));
                while (true) {
                    BigDecimal cur = Conv.number(read(v)), to = Conv.number(eval(f.to()));
                    if (by.signum() >= 0 ? cur.compareTo(to) > 0 : cur.compareTo(to) < 0) break;
                    try { exec(f.body()); }
                    catch (ExitSignal e) { if (e.what.equals("for")) break; if (!e.what.equals("continue")) throw e; }
                    store(v, Conv.number(read(v)).add(by));
                }
            }
            case CallProgram c -> {
                List<Rec> args = new ArrayList<>();
                for (Expr e : c.args()) {
                    Ref r = e instanceof Name n ? resolve(n) : null;
                    if (r instanceof RecRef rr) args.add(rr.rec);
                    else if (r instanceof FieldRef fr) args.add(fieldAsRecord(fr));
                    else args.add(textAsRecord(Conv.string(eval(e))));      // a literal for a service's string parameter
                }
                host.callProgram(c.program(), args, this);
                for (int i = 0; i < c.args().size(); i++) {               // copy back fields passed as records
                    if (!(c.args().get(i) instanceof Name)) continue;
                    Ref r = resolve((Name) c.args().get(i));
                    if (r instanceof FieldRef fr) {
                        System.arraycopy(args.get(i).data, 0, fr.rec.data, fr.offset(), fr.field().bytes());
                        host.written(fr.rec, fr.path);
                    } else if (r instanceof RecRef rr) {
                        host.written(rr.rec, null);
                    }
                }
            }
            case Try t -> {
                try { exec(t.body()); }
                catch (ProgramCallException e) { exec(t.onException()); }
            }
            case SetEmpty se -> { for (Name n : se.targets()) setEmpty(resolve(n)); }
            case Move m -> move(m);
            case Return r -> throw new ReturnSignal(r.value() == null ? null : eval(r.value()));
            case ExprStmt e -> eval(e.expr());
            case LocalDecl d -> {
                Ref v = newLocal(d.name(), d.type());
                frames.peek().put(key(d.name()), v instanceof RecRef rr ? rr.rec : v);
                if (d.init() != null) store(lookupLocal(d.name()), eval(d.init()));   // through its field, like every later write
            }
            case Exit x -> throw new ExitSignal(x.what().equals("function") ? "function" : x.what());
        }
    }

    // ---- locals --------------------------------------------------------------------------------------------

    private Ref newLocal(String name, TypeRef type) {
        String t = type.name().toUpperCase(Locale.ROOT);
        if (t.equals("STRING")) return new LocalStr();
        if (t.equals("BOOLEAN")) return new LocalBool();
        if (type.isPrimitive()) {
            FieldType ft = FieldType.valueOf(t.equals("NUMC") ? "NUMC" : t);
            int len = type.length() > 0 ? type.length() : switch (ft) { case SMALLINT -> 4; case INT -> 9; case BIGINT -> 18; default -> 1; };
            int bytes = switch (ft) {
                case CHAR, MBCHAR, NUM, NUMC -> len;
                case DECIMAL, PACF -> (len + 2) / 2;
                case SMALLINT -> 2; case INT -> 4; case BIGINT -> 8;
                case BIN -> len <= 4 ? 2 : len <= 9 ? 4 : 8;
                case HEX -> Math.max(1, len / 2);
                default -> len;
            };
            FieldLayout f = new FieldLayout(name, name, ft, len, type.decimals(), bytes, 1, 0, false, List.of());
            return new RecRef(new Rec(name, new RecordLayout(name, "local", bytes, List.of(f))));
        }
        Rec typed = host.record("type:" + type.name());
        if (typed == null) throw new EngineException("unknown type " + type.name());
        return new RecRef(typed);
    }

    private Ref lookupLocal(String name) {
        Map<String, Object> f = frames.peek();
        if (f == null) return null;
        Object o = f.get(key(name));
        if (o instanceof Rec r) return r.layout.pkg().equals("local") ? new FieldRef(r, r.find(r.var), new int[0], null, null) : new RecRef(r);
        return (Ref) o;
    }

    private static String key(String s) { return s.toUpperCase(Locale.ROOT); }

    // ---- name resolution -----------------------------------------------------------------------------------

    private static final Pattern MINUS_SUFFIX = Pattern.compile("^(.+)-(\\d+)$");

    public Ref resolve(Name n) {
        Ref r = tryResolve(n);
        if (r == null) throw new EngineException("unknown name " + n.dotted() + " (line " + n.line() + ")");
        return r;
    }

    private Ref tryResolve(Name n) {
        List<Segment> segs = n.segments();
        String first = segs.get(0).name();
        String up = key(first);
        // sysVar / VGVar
        if ((up.equals("SYSVAR") || up.equals("VGVAR")) && segs.size() == 2) return new SysRef(key(segs.get(1).name()));

        // a local or parameter
        Ref local = segs.get(0).subscripts().isEmpty() ? lookupLocal(first) : null;
        if (local == null && frames.peek() != null && frames.peek().get(up) instanceof Rec lr && !lr.layout.pkg().equals("local")) local = new RecRef(lr);
        if (local != null) {
            if (segs.size() == 1) return withSubstring(local, n);
            if (local instanceof RecRef rr) return field(rr.rec, segs, 1, n);
        }

        // a record variable (possibly package-qualified: MRSCommonCode.XXM0IT1.UMSGCODE)
        int start = 0;
        Rec rec = host.record(first);
        if (rec == null && segs.size() > 1 && Character.isUpperCase(first.charAt(0))) {
            Rec q = host.record(first + "." + segs.get(1).name());
            if (q != null) { rec = q; start = 1; }
        }
        if (rec != null) {
            if (segs.size() == start + 1) {
                if (!segs.get(start).subscripts().isEmpty()) throw new EngineException(first + " is a record, not an array");
                return withSubstring(new RecRef(rec), n);
            }
            return field(rec, segs, start + 1, n);
        }

        // an unqualified field of one of the screen's records
        Object cached = unqualified.get(up);
        if (cached == null) {
            for (Rec r : host.records()) {
                Rec.Path p = r.find(first);
                if (p != null) { cached = new Object[]{r, p}; break; }
            }
            if (cached != null) unqualified.put(up, cached);
        }
        if (cached instanceof Object[] arr) {
            Rec r = (Rec) arr[0];
            Rec.Path p = (Rec.Path) arr[1];
            Ref fr = fieldPath(r, p, segs, 0, n);
            return fr;
        }
        return null;
    }

    /** a field reached from a record: segs[from..] name nested fields */
    private Ref field(Rec rec, List<Segment> segs, int from, Name n) {
        Rec.Path p = rec.find(segs.get(from).name());
        if (p == null) throw new EngineException(segs.get(from).name() + " is not a field of " + rec.var + " (line " + n.line() + ")");
        return fieldPath(rec, p, segs, from, n);
    }

    private Ref fieldPath(Rec rec, Rec.Path p, List<Segment> segs, int from, Name n) {
        List<Integer> subs = new ArrayList<>();
        for (Expr e : segs.get(from).subscripts()) subs.add(Conv.number(eval(e)).intValue());
        for (int i = from + 1; i < segs.size(); i++) {
            Rec.Path below = Rec.findBelow(p, segs.get(i).name());
            if (below == null) throw new EngineException(segs.get(i).name() + " is not inside " + p.field().name() + " (line " + n.line() + ")");
            p = below;
            for (Expr e : segs.get(i).subscripts()) subs.add(Conv.number(eval(e)).intValue());
        }
        int dims = p.dims().size();
        if (subs.size() > dims) {
            // a subscript on a non-array char field is a one-character substring in VAGen style: X[3]
            if (dims == 0 && subs.size() == 1) {
                int at = subs.get(0);
                return new FieldRef(rec, p, new int[0], at, at);
            }
            throw new EngineException(n.dotted() + " has " + subs.size() + " subscripts but " + dims + " dimensions (line " + n.line() + ")");
        }
        int[] idx = subs.stream().mapToInt(Integer::intValue).toArray();
        Integer f = null, t = null;
        if (n.subFrom() != null) {
            f = Conv.number(eval(n.subFrom())).intValue();
            t = Conv.number(eval(n.subTo())).intValue();
        }
        return new FieldRef(rec, p, idx, f, t);
    }

    private Ref withSubstring(Ref r, Name n) {
        if (n.subFrom() == null) return r;
        int f = Conv.number(eval(n.subFrom())).intValue(), t = Conv.number(eval(n.subTo())).intValue();
        if (r instanceof FieldRef fr) return new FieldRef(fr.rec, fr.path, fr.idx, f, t);
        if (r instanceof LocalStr ls) { LocalStr x = new LocalStr(); String v = ls.v; x.v = v.substring(Math.min(v.length(), f - 1), Math.min(v.length(), t)); return x; }
        return r;
    }

    // ---- reading and writing -------------------------------------------------------------------------------

    public Object read(Ref r) {
        try {
            return readChecked(r);
        } catch (Rec.IndexRangeException e) {
            log.debug("{} (read as empty)", e.getMessage());
            return r instanceof FieldRef fr && !isText(fr.field()) ? BigDecimal.ZERO : "";
        }
    }

    private Object readChecked(Ref r) {
        return switch (r) {
            case FieldRef fr -> {
                FieldLayout f = fr.field();
                int off = fr.offset();
                if (fr.from != null) {
                    String all = Conv.text(fr.rec.data, off, f.bytes());
                    int a = Math.max(1, fr.from), b = Math.min(all.length(), fr.to);
                    yield a > b ? "" : all.substring(a - 1, b);
                }
                yield Conv.read(f, fr.rec.data, off);
            }
            case RecRef rr -> Conv.text(rr.rec.data, 0, rr.rec.data.length);
            case LocalStr s -> s.v;
            case LocalBool b -> b.v;
            case SysRef s -> sys(s.name());
        };
    }

    private Object sys(String name) {
        LocalDateTime now = host.clock().get();
        return switch (name) {
            case "ERRORCODE" -> errorCode;
            case "ARRAYINDEX" -> BigDecimal.valueOf(arrayIndex);
            case "CURRENTFORMATTEDGREGORIANDATE" -> now.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
            case "CURRENTGREGORIANDATE" -> new BigDecimal(now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            case "CURRENTSHORTGREGORIANDATE" -> new BigDecimal(now.format(DateTimeFormatter.ofPattern("yyMMdd")));
            case "CURRENTJULIANDATE" -> new BigDecimal(now.format(DateTimeFormatter.ofPattern("yyyyDDD")));
            case "CURRENTSHORTJULIANDATE" -> new BigDecimal(now.format(DateTimeFormatter.ofPattern("yyDDD")));
            case "CURRENTFORMATTEDJULIANDATE" -> now.format(DateTimeFormatter.ofPattern("yyyy-DDD"));
            case "CURRENTFORMATTEDTIME" -> now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            case "CURRENTTIME" -> new BigDecimal(now.format(DateTimeFormatter.ofPattern("HHmmss")));
            case "OVERFLOWINDICATOR", "SQLCODE", "RETURNCODE" -> BigDecimal.ZERO;
            default -> throw new EngineException("unsupported system variable " + name);
        };
    }

    /** stores a value with EGL assignment semantics */
    public void store(Ref r, Object v) {
        switch (r) {
            case FieldRef fr -> {
                if (fr.isArray() && fr.from == null) {          // whole array (e.g. after "set empty" style use): every element
                    forEachElement(fr, e -> storeElement(e, v));
                } else {
                    storeElement(fr, v);
                }
            }
            case RecRef rr -> {
                if (v instanceof byte[] bytes) copyBytes(bytes, rr.rec.data, 0, rr.rec.data.length);
                else Conv.writeText(Conv.string(v), rr.rec.data, 0, rr.rec.data.length);
                host.written(rr.rec, null);
            }
            case LocalStr s -> s.v = v instanceof byte[] b ? new String(b, java.nio.charset.StandardCharsets.ISO_8859_1) : Conv.string(v);
            case LocalBool b -> b.v = Conv.truthy(v);
            case SysRef s -> {
                if (s.name().equals("ERRORCODE")) errorCode = Conv.string(v);
                else if (s.name().equals("ARRAYINDEX")) arrayIndex = Conv.number(v).intValue();
                else throw new EngineException("cannot assign system variable " + s.name());
            }
        }
    }

    private void storeElement(FieldRef fr, Object v) {
        FieldLayout f = fr.field();
        int off;
        try {
            off = fr.offset();
        } catch (Rec.IndexRangeException e) {
            log.debug("{} (write skipped)", e.getMessage());
            return;
        }
        if (fr.from != null) {
            int a = Math.max(1, fr.from), b = Math.min(f.bytes(), fr.to);
            if (a <= b) Conv.writeText(Conv.string(v), fr.rec.data, off + a - 1, b - a + 1);
        } else if (v instanceof byte[] bytes) {
            copyBytes(bytes, fr.rec.data, off, f.bytes());
        } else {
            Conv.write(f, v, fr.rec.data, off);
        }
        host.written(fr.rec, fr.path);
    }

    private static void copyBytes(byte[] src, byte[] dst, int off, int len) {
        int n = Math.min(src.length, len);
        System.arraycopy(src, 0, dst, off, n);
        for (int i = n; i < len; i++) dst[off + i] = ' ';
    }

    /** assignment: groups and records copy their bytes; a number field moved to text keeps its digits */
    private void assign(Ref target, Expr value, Name targetName) {
        Object v = valueFor(target, value);
        store(target, v);
    }

    private Object valueFor(Ref target, Expr value) {
        if (value instanceof Name n) {
            Ref src = resolve(n);
            boolean targetText = target instanceof RecRef || target instanceof FieldRef tf && (isText(tf.field()) || tf.from != null);
            if (src instanceof RecRef rr) return targetText ? rr.rec.data.clone() : read(src);
            if (src instanceof FieldRef sf && sf.from == null) {
                FieldLayout f = sf.field();
                if (targetText && (f.isGroup() || f.type().isZoned())) {
                    int off;
                    try { off = sf.offset(); } catch (Rec.IndexRangeException e) { return read(src); }
                    byte[] b = new byte[f.bytes()];
                    System.arraycopy(sf.rec.data, off, b, 0, b.length);
                    if (f.type().isZoned() && !f.isGroup()) return Conv.text(b, 0, b.length).replaceAll("[\\x70-\\x79]$", "");
                    return b;
                }
            }
            return read(src);
        }
        return eval(value);
    }

    private static boolean isText(FieldLayout f) {
        return f.type() == FieldType.CHAR || f.type() == FieldType.MBCHAR || f.type() == FieldType.GROUP || f.type() == FieldType.HEX;
    }

    private void setEmpty(Ref r) {
        switch (r) {
            case RecRef rr -> {
                byte[] blank = RecordCodec.blank(rr.rec.layout);
                System.arraycopy(blank, 0, rr.rec.data, 0, Math.min(blank.length, rr.rec.data.length));   // in place: storage may be shared
                host.written(rr.rec, null);
            }
            case FieldRef fr -> {
                if (fr.isArray()) forEachElement(fr, this::blankElement);
                else blankElement(fr);
                host.written(fr.rec, fr.path);
            }
            case LocalStr s -> s.v = "";
            case LocalBool b -> b.v = false;
            case SysRef s -> { }
        }
    }

    private void blankElement(FieldRef fr) {
        int off = fr.offset();
        FieldCodec.writeDefault(fr.field(), fr.rec.data, off);
        blankChildren(fr.field().children(), fr.rec.data, off);
    }

    private static void blankChildren(List<FieldLayout> fields, byte[] buf, int base) {
        for (FieldLayout f : fields) {
            for (int i = 0; i < f.occurs(); i++) {
                int pos = base + f.offset() + i * f.bytes();
                FieldCodec.writeDefault(f, buf, pos);
                blankChildren(f.children(), buf, pos);
            }
        }
    }

    /** every element of an array reference (the missing subscripts run over their dimension) */
    public void forEachElement(FieldRef fr, java.util.function.Consumer<FieldRef> each) {
        List<FieldLayout> dims = fr.path.dims();
        int[] idx = new int[dims.size()];
        System.arraycopy(fr.idx, 0, idx, 0, fr.idx.length);
        iterate(fr, dims, idx, fr.idx.length, each);
    }

    private void iterate(FieldRef fr, List<FieldLayout> dims, int[] idx, int d, java.util.function.Consumer<FieldRef> each) {
        if (d == dims.size()) { each.accept(new FieldRef(fr.rec, fr.path, idx.clone(), null, null)); return; }
        for (int i = 1; i <= dims.get(d).occurs(); i++) { idx[d] = i; iterate(fr, dims, idx, d + 1, each); }
    }

    /** a text passed to a program's string parameter (e.g. call "printDnC"(..., "CT", ...)) */
    private static Rec textAsRecord(String text) {
        int n = Math.max(1, text.length());
        FieldLayout f = new FieldLayout("ARG", "arg", com.bart.maris.middleware.legacy.layout.FieldType.CHAR, n, 0, n, 1, 0, false, List.of());
        Rec r = new Rec("ARG", new RecordLayout("ARG", "field", n, List.of(f)));
        byte[] b = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(b, 0, r.data, 0, b.length);
        return r;
    }

    /** the bytes of a field passed to a program as if it were a record */
    private Rec fieldAsRecord(FieldRef fr) {
        FieldLayout f = fr.field();
        List<FieldLayout> fields = f.children().isEmpty()                    // a single field starts the record (its offset is the parent's)
                ? List.of(new FieldLayout(f.name(), f.json(), f.type(), f.length(), f.decimals(), f.bytes(), 1, 0, f.filler(), List.of()))
                : f.children();
        Rec r = new Rec(f.name(), new RecordLayout(f.name(), "field", f.bytes(), fields));
        System.arraycopy(fr.rec.data, fr.offset(), r.data, 0, f.bytes());
        return r;
    }

    // ---- move ----------------------------------------------------------------------------------------------

    private void move(Move m) {
        Ref to = resolve(m.to());
        switch (m.mode()) {
            case "all", "count" -> {
                if (!(to instanceof FieldRef tf)) throw new EngineException("move for all: target is not an array (line " + m.line() + ")");
                List<FieldRef> targets = elementsFrom(tf);
                List<Object> values = new ArrayList<>();
                Ref src = m.from() instanceof Name n ? resolve(n) : null;
                if (src instanceof FieldRef sf && (sf.isArray() || sf.dims() > 0)) {
                    FieldRef start = sf.isArray() ? sf : sf;
                    for (FieldRef e : elementsFrom(start)) values.add(elementValue(e, tf.field()));
                } else {
                    Object v = src == null ? eval(m.from()) : valueFor(tf, m.from());
                    for (int i = 0; i < targets.size(); i++) values.add(v);
                }
                int n = Math.min(targets.size(), values.size());
                if (m.mode().equals("count")) n = Math.min(n, Conv.number(eval(m.count())).intValue());
                for (int i = 0; i < n; i++) storeElement(targets.get(i), values.get(i));
            }
            case "byname" -> {
                Ref src = resolve((Name) m.from());
                Rec s = src instanceof RecRef rr ? rr.rec : null;
                Rec t = to instanceof RecRef rr ? rr.rec : null;
                if (s == null || t == null) throw new EngineException("move byName needs records (line " + m.line() + ")");
                byName(s, t);
            }
            default -> assign(to, m.from(), m.to());   // withV60Compat, byPosition, plain
        }
    }

    private Object elementValue(FieldRef e, FieldLayout targetField) {
        try { e.offset(); } catch (Rec.IndexRangeException x) { return read(e); }
        if (isText(targetField) && (e.field().isGroup() || e.field().type().isZoned())) {
            byte[] b = new byte[e.field().bytes()];
            System.arraycopy(e.rec.data, e.offset(), b, 0, b.length);
            return b;
        }
        return read(e);
    }

    /** elements from the given index (or 1) to the end of the innermost given/missing dimension */
    private List<FieldRef> elementsFrom(FieldRef fr) {
        List<FieldLayout> dims = fr.path.dims();
        if (dims.isEmpty()) return List.of(fr);
        int d = Math.min(fr.idx.length, dims.size()) - (fr.idx.length >= dims.size() ? 1 : 0);
        if (d < 0) d = 0;
        int[] base = new int[dims.size()];
        for (int i = 0; i < base.length; i++) base[i] = i < fr.idx.length ? fr.idx[i] : 1;
        List<FieldRef> out = new ArrayList<>();
        for (int i = Math.max(1, base[d]); i <= dims.get(d).occurs(); i++) {
            int[] idx = base.clone();
            idx[d] = i;
            out.add(new FieldRef(fr.rec, fr.path, idx, null, null));
        }
        return out;
    }

    private void byName(Rec s, Rec t) {
        for (FieldLayout f : t.layout.fields()) {
            Rec.Path sp = s.find(f.name()), tp = t.find(f.name());
            if (sp == null || tp == null || f.filler()) continue;
            FieldRef sf = new FieldRef(s, sp, new int[0], null, null), tf = new FieldRef(t, tp, new int[0], null, null);
            if (sf.dims() > 0 || tf.dims() > 0) {
                List<FieldRef> a = elementsFrom(sf), b = elementsFrom(tf);
                for (int i = 0; i < Math.min(a.size(), b.size()); i++) storeElement(b.get(i), elementValue(a.get(i), tf.field()));
            } else {
                storeElement(tf, elementValue(sf, tf.field()));
            }
        }
    }

    // ---- expressions ---------------------------------------------------------------------------------------

    public Object eval(Expr e) {
        return switch (e) {
            case Str s -> s.value();
            case Num n -> n.value();
            case Bool b -> b.value();
            case Name n -> {
                Ref r = tryResolve(n);
                if (r == null && n.segments().size() == 1 && n.first().equalsIgnoreCase("null")) yield "";
                if (r == null) {
                    // CNT-1 written without spaces: the hyphen was read as part of the name
                    Matcher m = n.segments().size() == 1 ? MINUS_SUFFIX.matcher(n.first()) : null;
                    if (m != null && m.matches()) {
                        Name base = new Name(List.of(new Segment(m.group(1), n.segments().get(0).subscripts())), null, null, n.line());
                        yield Conv.number(eval(base)).subtract(new BigDecimal(m.group(2)));
                    }
                    throw new EngineException("unknown name " + n.dotted() + " (line " + n.line() + ")");
                }
                yield read(r);
            }
            case Call c -> call(c);
            case Unary u -> u.op().equals("!") ? (Object) !Conv.truthy(eval(u.operand())) : Conv.number(eval(u.operand())).negate();
            case Binary b -> binary(b);
            case IsTest t -> {
                boolean res = switch (t.what()) {
                    case "numeric" -> isNumeric(t.operand());
                    case "blanks" -> Conv.rtrim(Conv.string(eval(t.operand()))).isEmpty();
                    default -> false;                                   // null, modified, cursor ...
                };
                yield t.negated() != res;
            }
            case InTest in -> inArray(in);
        };
    }

    private boolean isNumeric(Expr operand) {
        if (operand instanceof Name n && resolve(n) instanceof FieldRef fr && fr.from == null && !fr.field().isGroup()) {
            FieldLayout f = fr.field();
            if (f.type().isZoned()) {
                String s = Conv.text(fr.rec.data, fr.offset(), f.bytes());
                return s.chars().allMatch(Character::isDigit);
            }
            if (f.type().isBinary() || f.type().isPacked()) return true;
        }
        String s = Conv.rtrim(Conv.string(eval(operand)));
        return !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    private Object inArray(InTest in) {
        Object v = eval(in.value());
        Ref r = resolve(in.array());
        if (!(r instanceof FieldRef fr) || fr.dims() == 0) throw new EngineException(in.array().dotted() + " is not an array");
        int from = in.from() == null ? 1 : Conv.number(eval(in.from())).intValue();
        FieldRef start = fr.isArray() ? new FieldRef(fr.rec, fr.path, append(fr.idx, from), null, null) : fr;
        int i = from;
        for (FieldRef e : elementsFrom(start)) {
            if (Conv.compare(read(e), v) == 0) { arrayIndex = i; return true; }
            i++;
        }
        arrayIndex = 0;
        return false;
    }

    private static int[] append(int[] a, int v) {
        int[] r = new int[a.length + 1];
        System.arraycopy(a, 0, r, 0, a.length);
        r[a.length] = v;
        return r;
    }

    private Object binary(Binary b) {
        String op = b.op();
        if (op.equals("&&")) return Conv.truthy(eval(b.left())) && Conv.truthy(eval(b.right()));
        if (op.equals("||")) return Conv.truthy(eval(b.left())) || Conv.truthy(eval(b.right()));
        Object l = eval(b.left()), r = eval(b.right());
        switch (op) {
            case "==": return Conv.compare(l, r) == 0;
            case "!=": return Conv.compare(l, r) != 0;
            case "<": return Conv.compare(l, r) < 0;
            case ">": return Conv.compare(l, r) > 0;
            case "<=": return Conv.compare(l, r) <= 0;
            case ">=": return Conv.compare(l, r) >= 0;
            case "::": return Conv.string(l) + Conv.string(r);
            case "+":
                if (l instanceof String || r instanceof String) return Conv.string(l) + Conv.string(r);
                return Conv.number(l).add(Conv.number(r));
            case "-": return Conv.number(l).subtract(Conv.number(r));
            case "*": return Conv.number(l).multiply(Conv.number(r));
            case "/": {
                BigDecimal d = Conv.number(r);
                if (d.signum() == 0) return BigDecimal.ZERO;
                return Conv.number(l).divide(d, MathContext.DECIMAL64);
            }
            case "%": {
                BigDecimal d = Conv.number(r);
                return d.signum() == 0 ? BigDecimal.ZERO : Conv.number(l).remainder(d);
            }
            default: throw new EngineException("operator " + op);
        }
    }

    // ---- calls ---------------------------------------------------------------------------------------------

    private Object call(Call c) {
        String n = c.name();
        String lower = n.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "strlib.clip", "clip": return Conv.rtrim(Conv.string(eval(c.args().get(0))));
            case "strlib.characterlen", "characterlen": return BigDecimal.valueOf(Conv.rtrim(Conv.string(eval(c.args().get(0)))).length());
            case "strlib.indexof", "indexof": {
                String s = Conv.string(eval(c.args().get(0))), sub = Conv.string(eval(c.args().get(1)));
                int from = c.args().size() > 2 ? Conv.number(eval(c.args().get(2))).intValue() : 1;
                return BigDecimal.valueOf(s.indexOf(sub, Math.max(0, from - 1)) + 1);
            }
            case "strlib.lowercase": return Conv.string(eval(c.args().get(0))).toLowerCase(Locale.ROOT);
            case "strlib.uppercase": return Conv.string(eval(c.args().get(0))).toUpperCase(Locale.ROOT);
            case "strlib.formatnumber": return formatNumber(Conv.number(eval(c.args().get(0))), Conv.string(eval(c.args().get(1))));
            case "vglib.findstr": return findStr(c);
            case "sysLib.writestderr", "syslib.writestdout", "syslib.writestderr": return null;
            default: break;
        }
        Ast.Function f = host.function(n);
        if (f == null) throw new EngineException("function " + n + " not found (line " + c.line() + ")");
        List<Object> args = new ArrayList<>();
        for (Expr a : c.args()) {
            if (a instanceof Name nm) {
                Ref r = tryResolve(nm);
                args.add(r instanceof RecRef ? r : r != null ? read(r) : eval(a));
            } else {
                args.add(eval(a));
            }
        }
        return invoke(f, args);
    }

    /** vgLib.findStr(source, sourceIndex, sourceLength, searchString): 0 found (index updated), 1 not found */
    private Object findStr(Call c) {
        Ref src = resolve((Name) c.args().get(0));
        Ref idxRef = resolve((Name) c.args().get(1));
        int len = Conv.number(eval(c.args().get(2))).intValue();
        String search = Conv.rtrim(Conv.string(eval(c.args().get(3))));
        int start = Conv.number(read(idxRef)).intValue();
        if (src instanceof FieldRef fr && fr.dims() > 0) {
            List<FieldRef> els = elementsFrom(new FieldRef(fr.rec, fr.path, fr.isArray() ? append(fr.idx, start) : fr.idx, null, null));
            for (int i = 0; i < Math.min(len, els.size()); i++) {
                if (Conv.compare(read(els.get(i)), search) == 0) { store(idxRef, BigDecimal.valueOf(start + i)); return BigDecimal.ZERO; }
            }
            return BigDecimal.ONE;
        }
        String s = Conv.string(read(src));
        int at = s.indexOf(search, Math.max(0, start - 1));
        if (at >= 0 && at < start - 1 + len) { store(idxRef, BigDecimal.valueOf(at + 1)); return BigDecimal.ZERO; }
        return BigDecimal.ONE;
    }

    private static String formatNumber(BigDecimal v, String pattern) {
        try {
            return new java.text.DecimalFormat(pattern.replace('*', '#')).format(v);
        } catch (IllegalArgumentException e) {
            return Conv.plain(v);
        }
    }

    /** reads a field of a record by name, for the screen runtime */
    public FieldRef fieldRef(Rec rec, String fieldName) {
        Rec.Path p = rec.find(fieldName);
        if (p == null) return null;
        return new FieldRef(rec, p, new int[0], null, null);
    }
}
