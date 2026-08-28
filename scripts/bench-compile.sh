#!/usr/bin/env bash
# Times a clean compile of the generated bench model across shapes.
# Usage: scripts/bench-compile.sh <scalaSwitch|-> ; e.g. "++2.13.16" or "-"
set -u
SWITCH="${1:--}"
SHAPES=("8 1 0" "8 2 1" "8 4 1" "8 2 3" "8 3 3" "8 4 3")
printf '%-12s %-10s %-9s %s\n' SHAPE CLASSES MEMBERS "COMPILE(s)"
for shape in "${SHAPES[@]}"; do
  info=$(python3 scripts/gen-bench-model.py $shape bench/src/main/scala/io/moka/bench/Model.scala)
  classes=$(sed -E 's/.*classes=([0-9]+).*/\1/' <<<"$info")
  members=$(sed -E 's/.*members~([0-9]+).*/\1/' <<<"$info")
  if [ "$SWITCH" = "-" ]; then
    out=$(timeout 300 sbt -batch "bench/clean" "bench/compile" 2>&1)
  else
    out=$(timeout 300 sbt -batch "$SWITCH" "bench/clean" "bench/compile" 2>&1)
  fi
  if [ $? -ne 0 ]; then t="FAIL/TIMEOUT"; else
    t=$(grep -oE 'Total time: [0-9]+ s' <<<"$out" | tail -1 | grep -oE '[0-9]+')
  fi
  printf '%-12s %-10s %-9s %s\n' "${shape// /,}" "$classes" "$members" "$t"
done
