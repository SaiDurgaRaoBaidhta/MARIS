#!/usr/bin/env python3
"""Which backend programs each old screen calls, and which of them write data.

Reads (read-only) the EGL source: the screen logic (MARIS_RUI RUIFunctions.egl, via the middleware's screen bundle) and
the backend programs (MarisProd-1/MARIS_Server/EGLSource/**/<PGM>.egl). A program writes when its source has an SQL
INSERT / UPDATE / DELETE or an EGL add / replace / delete statement, or when it calls a program that writes.

Usage: tools/screen_inventory.py [path to MarisProd-1]   -> writes docs/screen-inventory.json
"""
import json, os, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EGL = Path(sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/Desktop/MARIS/MarisProd-1"))
BUNDLE = ROOT / "maris-middleware/src/main/resources/egl-screens"

WRITE = re.compile(r"\binsert\s+into\b|\bupdate\s+[\w.]+\s+set\b|\bdelete\s+from\b|^\s*(add|replace|delete)\s+\w", re.I | re.M)
CALL = re.compile(r'\bcall\s+"?([\w.#@$-]+)"?\s*\(', re.I)

def strip_comments(s):
    # one pass, leftmost first: "//*** PROGRAM ..." is a line comment, not the start of a block comment
    return re.sub(r'"(?:\\.|[^"\\])*"|//[^\n]*|/\*.*?\*/', lambda m: m.group(0) if m.group(0).startswith('"') else " ", s, flags=re.S)

# backend programs: own writes + programs they call
programs = {}
for f in (EGL / "MARIS_Server/EGLSource").rglob("*.egl"):
    src = strip_comments(f.read_text(encoding="latin-1"))
    m = re.search(r"^\s*Program\s+([\w#@$-]+)", src, re.M | re.I)
    if not m:
        continue
    programs[m.group(1).upper()] = {"writes": bool(WRITE.search(src)),
                                     "calls": {c.split(".")[-1].upper() for c in CALL.findall(src)}}

def writes(p, seen=None):
    seen = seen or set()
    if p in seen or p not in programs:
        return False
    seen.add(p)
    return programs[p]["writes"] or any(writes(c, seen) for c in programs[p]["calls"])

# screen logic: function bodies by (package, name)
fns = {}
for f in (BUNDLE / "functions").glob("*.egl"):
    src = f.read_text(encoding="latin-1")
    pkg = f.name.split(".")[0].upper()
    parts = re.split(r"^\s*Function\s+", src, flags=re.M | re.I)
    for body in parts[1:]:
        name = re.match(r"([\w#@$-]+)", body).group(1).upper()
        fns.setdefault((pkg, name), body)
by_name = {}
for (pkg, name), body in fns.items():
    by_name.setdefault(name, (pkg, name))

def fn(pkg, name):
    name = name.upper()
    if "." in name:
        q, n = name.rsplit(".", 1)
        return (q, n) if (q, n) in fns else by_name.get(n)
    return (pkg, name) if (pkg, name) in fns else (("MRSCOMMONCODE", name) if ("MRSCOMMONCODE", name) in fns else by_name.get(name))

def screen_programs(pkg, start):
    seen, todo, progs = set(), list(start), set()
    while todo:
        k = todo.pop()
        if not k or k in seen:
            continue
        seen.add(k)
        body = fns[k]
        progs |= {c.split(".")[-1].upper() for c in CALL.findall(body)}
        for callee in re.findall(r"\b([A-Za-z][\w#@$-]*(?:\.[A-Za-z][\w#@$-]*)?)\s*\(", body):
            if callee.lower().startswith(("strlib.", "vglib.", "syslib.", "mathlib.")):
                continue
            todo.append(fn(k[0], callee))
    return progs

screens = json.loads((BUNDLE / "screens.json").read_text())
out = {}
for name, s in sorted(screens.items()):
    pkg = s["pkg"].upper()
    start = [fn(pkg, v["props"]["callFunction"]["id"]) for v in s["vars"]
             if v["type"] == "ExecutablePart" and "callFunction" in v.get("props", {})]
    progs = sorted(screen_programs(pkg, start))
    w = [p for p in progs if writes(p)]
    out[name] = {"module": s["pkg"], "programs": progs, "writes": w, "kind": "update" if w else "read-only"}
(ROOT / "docs/screen-inventory.json").write_text(json.dumps(out, indent=1))
print(f"{len(programs)} backend programs, {len(out)} screens, "
      f"{sum(1 for v in out.values() if v['kind'] == 'update')} update, {sum(1 for v in out.values() if v['kind'] == 'read-only')} read-only")
