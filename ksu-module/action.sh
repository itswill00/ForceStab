#!/system/bin/sh
# action.sh - KernelSU Action button: run verify
MODDIR=${0%/*}
sh "$MODDIR/bin/verify.sh"
echo ""
echo "log: /data/adb/videomodefix/run/verify.log"
