#!/system/bin/sh
# build.sh - pack the KernelSU module zip. Run from anywhere.
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
cd "$SRC"
OUT="$SRC/releases/VideoModeFix-$(grep '^version=' module.prop | cut -d= -f2).zip"
mkdir -p "$SRC/releases"
rm -f "$OUT"
chmod 0755 customize.sh post-fs-data.sh service.sh action.sh bin/videomodefix bin/verify.sh bin/ftune
# WebUI (same convention as HyperDL): Vue+Vite source in webui/, shipped
# single-file build in webroot/index.html. Rebuild when sources are newer.
if [ ! -f "webroot/index.html" ] || [ -n "$(find webui/src webui/index.html webui/package.json -newer webroot/index.html 2>/dev/null)" ]; then
  echo "-> building webui..."
  (cd webui && [ -d node_modules ] || npm install --no-audit --no-fund)
  (cd webui && node ./node_modules/vite/bin/vite.js build)
  mkdir -p webroot
  cp -f webui/dist/index.html webroot/index.html
fi
# required KSU files at zip root (no zygisk/: native gate removed, see README)
zip -r "$OUT" module.prop customize.sh post-fs-data.sh service.sh action.sh system.prop sepolicy.rule bin webroot videomodefix.apk > /dev/null
echo "built: $OUT"
unzip -l "$OUT" | head -n 30
