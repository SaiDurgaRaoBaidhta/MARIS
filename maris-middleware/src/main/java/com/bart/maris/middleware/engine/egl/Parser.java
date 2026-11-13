package com.bart.maris.middleware.engine.egl;

import com.bart.maris.middleware.engine.egl.Ast.*;
import com.bart.maris.middleware.engine.egl.Lexer.Kind;
import com.bart.maris.middleware.engine.egl.Lexer.Token;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses a function file (RUIFunctions.egl / CommonFunctions.egl) into functions. */
public final class Parser {
    private final List<Token> t;
    private final String file;
    private int p;
    private String pkg = "";

    private Parser(List<Token> tokens, String file) {
        this.t = tokens;
        this.file = file;
    }

    public static List<Function> parseFile(String source, String file) {
        return new Parser(Lexer.lex(source), file).file();
    }

    /** for tests: one statement list */
    public static List<Stmt> parseStatements(String source) {
        Parser ps = new Parser(Lexer.lex(source), "test");
        return ps.block();
    }

    public static Expr parseExpression(String source) {
        return new Parser(Lexer.lex(source), "test").expr();
    }

    private List<Function> file() {
        List<Function> out = new ArrayList<>();
        while (peek().kind() != Kind.EOF) {
            if (peek().is("package")) { next(); pkg = qualified(); accept(";"); continue; }
            if (peek().is("import")) { next(); qualified(); accept(";"); continue; }
            if (peek().is("function")) { out.add(function()); continue; }
            next();                                   // anything else at top level (stray tokens) is skipped
        }
        return out;
    }

    private Function function() {
        expect("function");
        String name = next().text();
        expect("(");
        List<Param> params = new ArrayList<>();
        while (!peek().is(")")) {
            String pn = next().text();
            TypeRef type = type();
            while (peek().is("const") || peek().is("in") || peek().is("out") || peek().is("inout") || peek().is("field")
                    || peek().is("sqlNullable")) next();
            params.add(new Param(pn, type));
            accept(",");
        }
        expect(")");
        TypeRef ret = null;
        if (peek().is("returns")) {
            next();
            expect("(");
            ret = type();
            expect(")");
        }
        List<Stmt> body = block();
        expect("end");
        return new Function(pkg, name, params, ret, body, file);
    }

    private TypeRef type() {
        String n = qualified();
        int len = 0, dec = 0;
        if (peek().is("(")) {
            next();
            len = Integer.parseInt(next().text());
            if (accept(",")) dec = Integer.parseInt(next().text());
            expect(")");
        }
        if (peek().is("[")) {                         // array locals: not used by MARIS screens, read and ignore size
            next();
            while (!peek().is("]")) next();
            next();
        }
        if (peek().is("?")) next();
        return new TypeRef(n, len, dec);
    }

    private String qualified() {
        StringBuilder sb = new StringBuilder(next().text());
        while (peek().is(".") && t.get(p + 1).kind() == Kind.IDENT) {
            next();
            sb.append('.').append(next().text());
        }
        return sb.toString();
    }

    /** statements until end / else / onException */
    private List<Stmt> block() {
        List<Stmt> out = new ArrayList<>();
        while (true) {
            Token k = peek();
            if (k.kind() == Kind.EOF || k.is("end") || k.is("else") || k.is("onException") || k.is("function")) return out;
            if (k.is(";")) { next(); continue; }
            Stmt s = statement();
            if (s != null) out.add(s);
        }
    }

    private Stmt statement() {
        Token k = peek();
        int line = k.line();
        if (k.is("if")) {
            next();
            expect("(");
            Expr c = expr();
            expect(")");
            List<Stmt> then = block();
            List<Stmt> other = List.of();
            if (accept("else")) other = block();
            expect("end");
            return new If(c, then, other);
        }
        if (k.is("while")) {
            next();
            expect("(");
            Expr c = expr();
            expect(")");
            List<Stmt> body = block();
            expect("end");
            return new While(c, body);
        }
        if (k.is("for")) {
            next();
            expect("(");
            String v = next().text();
            if (!peek().is("from")) type();          // for (i int from 1 to 10 by 1)
            expect("from");
            Expr from = expr();
            expect("to");
            Expr to = expr();
            Expr by = null;
            if (accept("by")) by = expr();
            expect(")");
            List<Stmt> body = block();
            expect("end");
            return new For(v, from, to, by, body);
        }
        if (k.is("try")) {
            next();
            List<Stmt> body = block();
            List<Stmt> onEx = List.of();
            if (peek().is("onException")) {
                next();
                if (accept("(")) { while (!peek().is(")")) next(); next(); }
                onEx = block();
            }
            expect("end");
            return new Try(body, onEx);
        }
        if (k.is("call")) {
            next();
            String prog = peek().kind() == Kind.STRING ? next().text() : qualified();
            List<Expr> args = new ArrayList<>();
            if (accept("(")) {
                while (!peek().is(")")) { args.add(expr()); accept(","); }
                expect(")");
            }
            while (!peek().is(";")) next();           // {isNoRefresh = yes} etc.
            expect(";");
            return new CallProgram(prog, args, line);
        }
        if (k.is("set")) {
            next();
            List<Name> targets = new ArrayList<>();
            targets.add(name());
            while (accept(",")) targets.add(name());
            String state = next().text().toLowerCase(Locale.ROOT);
            while (accept(",")) next();
            expect(";");
            return new SetEmpty(targets, state);
        }
        if (k.is("move")) {
            next();
            Expr from = expr();
            expect("to");
            Name to = name();
            String mode = "";
            Expr count = null;
            if (accept("for")) {
                if (accept("all")) mode = "all";
                else { mode = "count"; count = expr(); }
            } else if (peek().kind() == Kind.IDENT && !peek().is(";")) {
                mode = next().text().toLowerCase(Locale.ROOT);
            }
            expect(";");
            return new Move(from, to, mode, count, line);
        }
        if (k.is("return")) {
            next();
            Expr v = null;
            if (!peek().is(";")) v = expr();
            expect(";");
            return new Return(v);
        }
        if (k.is("exit")) {
            next();
            String what = peek().is(";") ? "function" : next().text().toLowerCase(Locale.ROOT);
            expect(";");
            return new Exit(what);
        }
        if (k.is("continue")) {
            next();
            if (!peek().is(";")) next();
            expect(";");
            return new Exit("continue");
        }
        // local declaration: NAME type [= expr];
        if (k.kind() == Kind.IDENT && t.get(p + 1).kind() == Kind.IDENT && !t.get(p + 1).is("in")) {
            String n = next().text();
            TypeRef ty = type();
            Expr init = null;
            if (accept("=")) init = expr();
            if (peek().is("{")) { while (!peek().is("}")) next(); next(); }
            expect(";");
            return new LocalDecl(n, ty, init);
        }
        Expr e = expr();
        if (accept("=")) {
            if (!(e instanceof Name target)) throw error("cannot assign to " + e);
            Expr v = expr();
            expect(";");
            return new Assign(target, v, line);
        }
        expect(";");
        return new ExprStmt(e, line);
    }

    // ---- expressions: || , && , not, comparison / is / in, + -, * / %, unary, primary --------------------

    private Expr expr() { return or(); }

    private Expr or() {
        Expr l = and();
        while (peek().is("||")) { next(); l = new Binary("||", l, and()); }
        return l;
    }

    private Expr and() {
        Expr l = not();
        while (peek().is("&&")) { next(); l = new Binary("&&", l, not()); }
        return l;
    }

    private Expr not() {
        if (peek().is("!") || (peek().is("not") && !t.get(p + 1).is("numeric") && !t.get(p + 1).is("blanks"))) {
            next();
            return new Unary("!", not());
        }
        return comparison();
    }

    private Expr comparison() {
        Expr l = additive();
        while (true) {
            Token k = peek();
            if (k.is("==") || k.is("!=") || k.is("<") || k.is(">") || k.is("<=") || k.is(">=")) {
                next();
                l = new Binary(k.text(), l, additive());
            } else if (k.is("is") || k.is("not")) {
                next();
                boolean neg = k.is("not");
                if (accept("not")) neg = !neg;
                String what = next().text().toLowerCase(Locale.ROOT);    // numeric | blanks | null
                l = new IsTest(l, what, neg);
            } else if (k.is("in")) {
                next();
                Name arr = name();
                Expr from = null;
                if (accept("from")) from = additive();
                l = new InTest(l, arr, from);
            } else {
                return l;
            }
        }
    }

    private Expr additive() {
        Expr l = multiplicative();
        while (peek().is("+") || peek().is("-") || peek().is("::")) {
            String op = next().text();
            l = new Binary(op, l, multiplicative());
        }
        return l;
    }

    private Expr multiplicative() {
        Expr l = unary();
        while (peek().is("*") || peek().is("/") || peek().is("%")) {
            String op = next().text();
            l = new Binary(op, l, unary());
        }
        return l;
    }

    private Expr unary() {
        if (peek().is("-")) { next(); return new Unary("-", unary()); }
        if (peek().is("+")) { next(); return unary(); }
        return primary();
    }

    private Expr primary() {
        Token k = peek();
        if (k.is("(")) {
            next();
            Expr e = expr();
            expect(")");
            return e;
        }
        if (k.kind() == Kind.STRING) { next(); return new Str(k.text()); }
        if (k.kind() == Kind.NUMBER) { next(); return new Num(new BigDecimal(k.text())); }
        if (k.is("true")) { next(); return new Bool(true); }
        if (k.is("false")) { next(); return new Bool(false); }
        if (k.kind() != Kind.IDENT) throw error("unexpected '" + k.text() + "'");
        // call? qualified name followed by "("
        int save = p;
        String q = qualified();
        if (peek().is("(")) {
            next();
            List<Expr> args = new ArrayList<>();
            while (!peek().is(")")) { args.add(expr()); accept(","); }
            expect(")");
            return new Call(q, args, k.line());
        }
        p = save;
        return name();
    }

    private Name name() {
        int line = peek().line();
        List<Segment> segs = new ArrayList<>();
        Expr subFrom = null, subTo = null;
        while (true) {
            Token id = next();
            if (id.kind() != Kind.IDENT) throw error("name expected, got '" + id.text() + "'");
            List<Expr> subs = new ArrayList<>();
            while (peek().is("[")) {
                next();
                Expr a = expr();
                if (accept(":")) {                         // substring: always the last bracket of the name
                    subFrom = a;
                    subTo = expr();
                    expect("]");
                    break;
                }
                expect("]");
                subs.add(a);
            }
            segs.add(new Segment(id.text(), subs));
            if (subFrom != null) break;
            if (peek().is(".") && t.get(p + 1).kind() == Kind.IDENT) { next(); continue; }
            break;
        }
        return new Name(List.copyOf(segs), subFrom, subTo, line);
    }

    // ---- token helpers -------------------------------------------------------------------------------------

    private Token peek() { return t.get(p); }

    private Token next() { return t.get(p < t.size() - 1 ? p++ : p); }

    private boolean accept(String s) {
        if (peek().is(s)) { p++; return true; }
        return false;
    }

    private void expect(String s) {
        if (!accept(s)) throw error("expected '" + s + "' but found '" + peek().text() + "'");
    }

    private EglSyntaxException error(String msg) {
        return new EglSyntaxException(file + ":" + peek().line() + ": " + msg);
    }

    public static final class EglSyntaxException extends RuntimeException {
        public EglSyntaxException(String m) { super(m); }
    }
}
