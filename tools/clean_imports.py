#!/usr/bin/env python3
"""Remove unused imports from the Kotlin sources.

Operator imports used by property delegation (`getValue`, `setValue`,
`provideDelegate`) are kept even when the name does not appear literally.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java/dev/gitdroid/app"

OPERATOR_IMPORTS = {
    "getValue", "setValue", "provideDelegate",
    "invoke", "iterator", "compareTo", "contains", "get", "set", "plus", "minus",
    "times", "div", "rem", "rangeTo", "unaryMinus", "unaryPlus", "not", "inc", "dec",
    "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign",
    "getAndSet", "component1", "component2", "component3", "component4", "component5",
}

removed = 0
for dirpath, _, files in os.walk(ROOT):
    for name in sorted(files):
        if not name.endswith(".kt"):
            continue
        path = os.path.join(dirpath, name)
        source = open(path).read()
        lines = source.split("\n")
        body = "\n".join(l for l in lines if not l.startswith("import "))

        keep = []
        dropped = []
        for line in lines:
            if not line.startswith("import "):
                keep.append(line)
                continue
            symbol = line.split(".")[-1].strip()
            if " as " in symbol:
                symbol = symbol.split(" as ")[-1].strip()
            if symbol in OPERATOR_IMPORTS:
                keep.append(line)
                continue
            if re.search(r"\b" + re.escape(symbol) + r"\b", body):
                keep.append(line)
            else:
                dropped.append(line)

        if dropped:
            open(path, "w").write("\n".join(keep))
            removed += len(dropped)
            print(f"{path}: removed {len(dropped)}")
            for d in dropped:
                print("   ", d)

print(f"total removed: {removed}")
