#!/usr/bin/env bash
# Rebuilds public/screens, public/data and src/features/*/ JSON from the reference UI (../maris-2-v4.html).
# Needs Google Chrome and playwright-core (not a project dependency):  npm i --no-save playwright-core@1.48.2
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(mktemp -d)
HTML=../maris-2-v4.html
# the EGL source data of every screen (layout, wiring, server programs) is one line in the reference page
grep -m1 '^window.MARIS_SCREENS = ' "$HTML" | sed 's/^window.MARIS_SCREENS = //; s/;$//' > "$OUT/screens-src.json"
node tools/capture-screens.mjs "$OUT"            # menus + every screen's final layout
node tools/capture-web-pages.mjs "$OUT"          # web report pages, with their look inlined
mkdir -p "$OUT/data" && node tools/capture-data.mjs "$OUT/data"
cp "$OUT/data/fleet.json" "$OUT/data/maps.json" public/data/ && cp "$OUT/data/bart-logo.png" public/ && cp "$OUT/data/userGroups.json" src/features/auth/
node tools/build-screens.mjs "$OUT" "$OUT/screens-src.json" .
echo "done: $OUT"
