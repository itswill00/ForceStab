#!/system/bin/sh
# service.sh - late_start hook. Only reads camera capabilities into a boot
# log; it never writes props or touches the HAL.
MODDIR=${0%/*}
LOG=/data/adb/videomodefix/run/service.log
mkdir -p /data/adb/videomodefix/run

# give the system a moment to finish booting before dumping
for i in $(seq 1 60); do
  [ "$(getprop sys.boot_completed)" = "1" ] && break
  sleep 2
done

{
echo "=== $(date) videomodefix service ==="
echo "model=$(getprop ro.product.model) sdk=$(getprop ro.build.version.sdk)"
# read-only HAL caps snapshot (needs root, we are root here)
/system/bin/dumpsys media.camera 2>&1 | grep -A1 -E "android.control.availableVideoStabilizationModes|android.lens.info.availableOpticalStabilization" | head -n 20
} >> "$LOG" 2>&1
