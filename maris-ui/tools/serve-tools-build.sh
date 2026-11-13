#!/usr/bin/env bash
# A production build with the tools hook on (window.__maris) and the demo data (no middleware needed), served on :5180, for compare-screens.mjs / check-screens.mjs
set -euo pipefail
cd "$(dirname "$0")/.."
VITE_TOOLS=1 VITE_MARIS_API=mock npx vite build --outDir dist-tools >/dev/null
exec npx vite preview --outDir dist-tools --port "${1:-5180}" --strictPort
