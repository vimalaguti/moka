#!/usr/bin/env python3
"""Generate a synthetic moka model of a given shape, for compile-time measurement.

  width  W: scalar fields per case class
  depth  D: levels of nesting below the root
  branch B: nested case-class fields per level (B=3 approximates what an array
            hop costs once `matched`/`all` triple each element subtree)

Types are emitted at package level, which the Scala 2 macro requires.
"""
import sys, os, pathlib

W, D, B = (int(x) for x in sys.argv[1:4])
out = pathlib.Path(sys.argv[4])

lines = ["package io.moka.bench", "", "import io.moka._", ""]

def scalars(n):
    return [f"s{i}: Int" for i in range(n)]

# Level D is the leaf level (scalars only); level 0 is the root.
for level in range(D, -1, -1):
    for idx in range(B ** level if level > 0 else 1):
        name = f"L{level}_{idx}"
        params = scalars(W)
        if level < D:
            for b in range(B):
                child = f"L{level + 1}_{idx * B + b}"
                params.append(f"n{b}: {child}")
        if level == 0:
            lines.append("@moka")
        lines.append(f"final case class {name}({', '.join(params)})")
        if level == 0:
            lines.append(f"object {name} {{")
            lines.append(f"  val Fields = generateFields[{name}]")
            lines.append("}")
        lines.append("")

# One use site, so the refinement is actually selected through.
path = "L0_0.Fields"
if B > 0:
    for level in range(D):
        path += ".n0"
lines += ["object Use {", f"  val deepest: String = {path}.s0", "}", ""]

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text("\n".join(lines))

members = sum((B ** l) * (W + (B if l < D else 0)) for l in range(D + 1))
print(f"W={W} D={D} B={B} classes={sum(B**l for l in range(D+1))} members~{members}")
