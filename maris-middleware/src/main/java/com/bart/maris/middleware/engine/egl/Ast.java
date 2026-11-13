package com.bart.maris.middleware.engine.egl;

import java.math.BigDecimal;
import java.util.List;

/** The part of the EGL language the old screens' statement groups use. */
public final class Ast {
    private Ast() {}

    // ---- expressions ---------------------------------------------------------------------------------------

    public sealed interface Expr permits Str, Num, Bool, Name, Call, Unary, Binary, IsTest, InTest {}

    public record Str(String value) implements Expr {}

    public record Num(BigDecimal value) implements Expr {}

    public record Bool(boolean value) implements Expr {}

    /** a.b[i].c[j][1:3]: segments, each with its subscripts; an optional substring at the end */
    public record Name(List<Segment> segments, Expr subFrom, Expr subTo, int line) implements Expr {
        public String first() { return segments.get(0).name(); }
        public String dotted() {
            StringBuilder sb = new StringBuilder();
            for (Segment s : segments) { if (!sb.isEmpty()) sb.append('.'); sb.append(s.name()); }
            return sb.toString();
        }
        public boolean hasSubscripts() { return segments.stream().anyMatch(s -> !s.subscripts().isEmpty()); }
    }

    public record Segment(String name, List<Expr> subscripts) {}

    /** f(args) or pkg.f(args) or lib.f(args) */
    public record Call(String name, List<Expr> args, int line) implements Expr {}

    public record Unary(String op, Expr operand) implements Expr {}

    public record Binary(String op, Expr left, Expr right) implements Expr {}

    /** x is [not] numeric | blanks */
    public record IsTest(Expr operand, String what, boolean negated) implements Expr {}

    /** x in array [from n] */
    public record InTest(Expr value, Name array, Expr from) implements Expr {}

    // ---- statements ----------------------------------------------------------------------------------------

    public sealed interface Stmt permits Assign, If, While, For, CallProgram, Try, SetEmpty, Move, Return, ExprStmt, LocalDecl, Exit {}

    public record Assign(Name target, Expr value, int line) implements Stmt {}

    public record If(Expr cond, List<Stmt> then, List<Stmt> otherwise) implements Stmt {}

    public record While(Expr cond, List<Stmt> body) implements Stmt {}

    public record For(String var, Expr from, Expr to, Expr by, List<Stmt> body) implements Stmt {}

    public record CallProgram(String program, List<Expr> args, int line) implements Stmt {}

    public record Try(List<Stmt> body, List<Stmt> onException) implements Stmt {}

    public record SetEmpty(List<Name> targets, String state) implements Stmt {}

    /** move a to b [for all | for n | byName | byPosition | withV60Compat] */
    public record Move(Expr from, Name to, String mode, Expr count, int line) implements Stmt {}

    public record Return(Expr value) implements Stmt {}

    public record ExprStmt(Expr expr, int line) implements Stmt {}

    public record LocalDecl(String name, TypeRef type, Expr init) implements Stmt {}

    /** exit while / exit for / continue */
    public record Exit(String what) implements Stmt {}

    // ---- declarations --------------------------------------------------------------------------------------

    /** char(6), num(3,1), int, string, boolean, smallint, or a record type name */
    public record TypeRef(String name, int length, int decimals) {
        public boolean isPrimitive() {
            return switch (name.toUpperCase(java.util.Locale.ROOT)) {
                case "CHAR", "MBCHAR", "NUM", "NUMC", "DECIMAL", "PACF", "SMALLINT", "INT", "BIGINT", "BIN", "STRING", "BOOLEAN", "HEX" -> true;
                default -> false;
            };
        }
    }

    public record Param(String name, TypeRef type) {}

    public record Function(String pkg, String name, List<Param> params, TypeRef returns, List<Stmt> body, String file) {}
}
