#!/system/bin/sh
# post-fs-data.sh - keep minimal and safe
MODDIR=${0%/*}
mkdir -p /data/adb/videomodefix/run
echo "$(date +%F_%T) post-fs-data" >> /data/adb/videomodefix/run/boot.log
