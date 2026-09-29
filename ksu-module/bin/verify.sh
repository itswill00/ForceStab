#!/system/bin/sh
# verify.sh - read-only evidence: HAL caps plus the most recent camera
# requests from third-party apps.
LOG=/data/adb/videomodefix/run/verify.log
mkdir -p /data/adb/videomodefix/run
{
echo "=== $(date) VideoModeFix verify ==="
echo "-- HAL caps --"
/system/bin/dumpsys media.camera 2>&1 | grep -A1 -E "android.control.availableVideoStabilizationModes \(1001a\)|android.lens.info.availableOpticalStabilization \(90003\)" | head -n 20
echo "-- last request (expect OFF before hook, ON after hook) --"
/system/bin/dumpsys media.camera 2>&1 | grep -A1 -E "android.control.videoStabilizationMode \(10011\)|android.lens.opticalStabilizationMode \(80004\)|android.control.captureIntent" | head -n 20
echo "-- recent camera clients (Instagram) --"
/system/bin/dumpsys media.camera 2>&1 | grep -iE "connect.*instagram|disconnect.*instagram" | head -n 10
} 2>&1 | tee "$LOG"
