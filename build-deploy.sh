#!/system/bin/sh
# build-deploy.sh - VideoModeFix one-shot: build APK + KSU zip, deploy live.
# HyperDL-style: ./build-deploy.sh -d  ->  then just reboot.
#
#   ./build-deploy.sh -d | --deploy   build + deploy to /data/adb + pm install APK
#   ./build-deploy.sh -c | --clean    wipe local artifacts
#   ./build-deploy.sh -h | --help     this text
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
DEPLOY=false
CLEAN=false
for a in "$@"; do case "$a" in
  -d|--deploy) DEPLOY=true ;;
  -c|--clean) CLEAN=true ;;
  -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
  *) echo "unknown flag '$a' (try -h)"; exit 1 ;;
esac; done

if [ "$CLEAN" = true ]; then
  rm -rf lsposed-app/out lsposed-app/dexb lsposed-app/dexbex \
         lsposed-app/classes.dex lsposed-app/unsigned.apk \
         lsposed-app/VideoModeFix.apk ksu-module/videomodefix.apk
  echo "clean."
  exit 0
fi

echo "== [1/3] build APK =="
(cd "$ROOT/lsposed-app" && sh ./build-apk.sh VideoModeFix.apk > /dev/null) && echo "apk ok"
cp -f "$ROOT/lsposed-app/VideoModeFix.apk" "$ROOT/ksu-module/videomodefix.apk"

echo "== [2/3] build KSU zip =="
(cd "$ROOT/ksu-module" && sh ./build.sh > /dev/null)
ZIP=$(ls -t "$ROOT/ksu-module/releases"/VideoModeFix-v*.zip | head -n 1)
echo "zip ok: $ZIP"

if [ "$DEPLOY" = false ]; then
  echo "dry-run done (use -d to deploy). No reboot needed."
  exit 0
fi

echo "== [3/3] deploy live =="
su -c "
  set -e
  pm install -r '$ROOT/lsposed-app/VideoModeFix.apk' | grep -qi success
  echo 'apk installed'
  T=/data/adb/modules/videomodefix
  mkdir -p \"\$T/bin\" \"\$T/webroot\"
  cp -f '$ROOT/ksu-module/module.prop' '$ROOT/ksu-module/customize.sh' \
        '$ROOT/ksu-module/service.sh' '$ROOT/ksu-module/post-fs-data.sh' \
        '$ROOT/ksu-module/action.sh' '$ROOT/ksu-module/system.prop' \
        '$ROOT/ksu-module/sepolicy.rule' '$ROOT/ksu-module/videomodefix.apk' \"\$T/\"
  cp -f '$ROOT/ksu-module/bin/'* \"\$T/bin/\"
  cp -f '$ROOT/ksu-module/webroot/'* \"\$T/webroot/\"
  chmod 755 \"\$T/service.sh\" \"\$T/post-fs-data.sh\" \"\$T/action.sh\" \"\$T/bin/\"*
  chmod 644 \"\$T/module.prop\" \"\$T/webroot/\"*
  chcon -R u:object_r:system_file:s0 \"\$T\"
  echo 'module synced'
"
echo ""
echo "DEPLOY DONE. REBOOT NOW, then record with IG to test."
