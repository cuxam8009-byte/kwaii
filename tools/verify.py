import subprocess, sys, re, itertools, unicodedata, json
from concurrent.futures import ThreadPoolExecutor
import course_src as C

def nfc(x):
    if isinstance(x, str): return unicodedata.normalize("NFC", x)
    if isinstance(x, list): return [nfc(i) for i in x]
    if isinstance(x, dict): return {k: nfc(v) for k, v in x.items()}
    return x

UNITS = nfc(C.UNITS)
cache = {}
def run(code):
    if code in cache: return cache[code]
    try:
        p = subprocess.run([sys.executable, "-I", "-c", code], capture_output=True, text=True, timeout=3,
                           input="", env={"PYTHONIOENCODING": "utf-8", "PYTHONUTF8": "1"})
        r = (p.returncode, p.stdout.rstrip("\n").split("\n") if p.stdout.strip("\n") != "" else [], p.stderr)
    except subprocess.TimeoutExpired:
        r = (-9, [], "TIMEOUT")
    cache[code] = r
    return r

def errline(stderr):
    m = re.findall(r'File "<string>", line (\d+)', stderr)
    return int(m[-1]) if m else None

def errtype(stderr):
    last = [l for l in stderr.strip().split("\n") if l.strip()]
    return last[-1].split(":")[0] if last else ""

problems = []
def bad(msg): problems.append(msg)

jobs = []
for u in UNITS:
    for lv in u["levels"]:
        for i, s in enumerate(lv["steps"]):
            jobs.append((lv, i, s))

def snippets(lv, i, s):
    t = s["t"]
    if t == "L": return [s["code"]]
    if t == "E":
        if s["pred"]: return [s["code"]]
        return [s["code"].replace("___", o) for o in s["opts"]]
    if t == "B": return ["\n".join(s["lines"])]
    if t == "O":
        n = len(s["lines"])
        out = ["\n".join(s["lines"])]
        if n <= 5:
            out += ["\n".join(p) for p in itertools.permutations(s["lines"])]
        return out
    return []

all_snips = set()
for lv, i, s in jobs:
    for c in snippets(lv, i, s): all_snips.add(c)
with ThreadPoolExecutor(4) as ex:
    list(ex.map(run, list(all_snips)))

result = []
for u in UNITS:
    for lv in u["levels"]:
        steps = []
        for i, s in enumerate(lv["steps"]):
            tag = f'{lv["file"]}#{i+1}'
            t = s["t"]
            if t == "L":
                rc, out, err = run(s["code"])
                if s["err"]:
                    if rc == 0: bad(f"{tag} Learn expected error but ran fine")
                    steps.append({"t": "L", "say": s["say"], "code": s["code"], "out": [s["err"]]})
                else:
                    if rc != 0 or not out: bad(f"{tag} Learn failed: {err[-120:]}")
                    steps.append({"t": "L", "say": s["say"], "code": s["code"], "out": out})
            elif t == "E":
                opts, ans = s["opts"], s["ans"]
                if len(set(opts)) != len(opts): bad(f"{tag} duplicate options")
                if not (0 <= ans < len(opts)): bad(f"{tag} ans out of range")
                if s["pred"]:
                    rc, out, err = run(s["code"])
                    if rc != 0: bad(f"{tag} pred code fails")
                    if opts[ans].split("\n") != out: bad(f"{tag} pred answer {opts[ans]!r} != actual {out!r}")
                    for k, o in enumerate(opts):
                        if k != ans and o.split("\n") == out: bad(f"{tag} pred wrong option equals actual")
                else:
                    if "___" not in s["code"]: bad(f"{tag} no blank")
                    rc, out, err = run(s["code"].replace("___", opts[ans]))
                    if rc != 0 or not out: bad(f"{tag} correct option fails: {err[-100:]}")
                    for k, o in enumerate(opts):
                        if k == ans: continue
                        rc2, out2, _ = run(s["code"].replace("___", o))
                        if rc2 == 0 and out2 == out: bad(f"{tag} wrong option {o!r} also gives correct output")
                steps.append({"t": "E", "say": s["say"], "code": s["code"], "opts": opts, "ans": ans, "out": out,
                              "why": s["why"], "pred": s["pred"]})
            elif t == "B":
                rc, out, err = run("\n".join(s["lines"]))
                if rc == 0: bad(f"{tag} bug code runs fine")
                ln = errline(err)
                if ln != s["bad"] + 1: bad(f"{tag} bug line mismatch: expected {s['bad']+1} got {ln} ({errtype(err)})")
                if not (0 <= s["bad"] < len(s["lines"])): bad(f"{tag} bad index out of range")
                steps.append({"t": "B", "say": s["say"], "lines": s["lines"], "bad": s["bad"], "why": s["why"]})
            elif t == "O":
                lines = s["lines"]
                if len(set(lines)) != len(lines): bad(f"{tag} order duplicate lines")
                rc, out, err = run("\n".join(lines))
                if rc != 0 or not out: bad(f"{tag} order solution fails")
                if len(lines) <= 5:
                    good = [p for p in itertools.permutations(lines) if run("\n".join(p))[0] == 0 and run("\n".join(p))[1] == out]
                    if len(good) != 1: bad(f"{tag} order has {len(good)} valid permutations")
                steps.append({"t": "O", "say": s["say"], "lines": lines, "out": out, "why": s["why"]})
        result.append({"file": lv["file"], "title": lv["title"], "sub": lv["sub"], "kind": lv["kind"], "steps": steps})

print("PROBLEMS:", len(problems))
for p in problems: print(" -", p)
json.dump({"units": [{"title": u["title"], "sub": u["sub"], "n": len(u["levels"])} for u in UNITS], "levels": result},
          open("course.json", "w"), ensure_ascii=False)
print("snippets run:", len(all_snips))
