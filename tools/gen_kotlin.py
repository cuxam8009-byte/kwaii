import json, sys

def k(s):
    s = s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n").replace("\t", "\\t")
    return '"' + s + '"'

def kl(items):
    return "listOf(" + ", ".join(k(i) for i in items) + ")"

def step(s):
    t = s["t"]
    if t == "L":
        return f'Learn({k(s["say"])}, {k(s["code"])}, {kl(s["out"])})'
    if t == "E":
        return f'Ex({k(s["say"])}, {k(s["code"])}, {kl(s["opts"])}, {s["ans"]}, {kl(s["out"])}, {k(s["why"])}, {"true" if s["pred"] else "false"})'
    if t == "B":
        return f'Bug({k(s["say"])}, {kl(s["lines"])}, {s["bad"]}, {k(s["why"])})'
    return f'Order({k(s["say"])}, {kl(s["lines"])}, {kl(s["out"])}, {k(s["why"])})'

d = json.load(open("course.json", encoding="utf-8"))
levels = d["levels"]
out = ["package com.pigprolem.utit", "", "val UNITS: List<CUnit> = listOf("]
pos = 0
for u in d["units"]:
    out.append(f'    CUnit({k(u["title"])}, {k(u["sub"])}, listOf(')
    for lv in levels[pos:pos + u["n"]]:
        out.append(f'        Level({k(lv["file"])}, {k(lv["title"])}, {k(lv["sub"])}, {k(lv["kind"])}, listOf(')
        out.append(",\n".join("            " + step(s) for s in lv["steps"]))
        out.append("        )),")
    out.append("    )),")
    pos += u["n"]
out.append(")")
open(sys.argv[1], "w", encoding="utf-8").write("\n".join(out) + "\n")
print("written", sys.argv[1], len(levels), "levels")
