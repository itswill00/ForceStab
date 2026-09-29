#!/system/bin/sh
# customize.sh - install-time gates, works on KernelSU and Magisk.
# Rather than installing half-working, stop early with a clear reason.
SKIPMOUNT=false
PROPFILE=false
POSTFSDATA=true
LATESTARTSERVICE=true

VER=$(grep '^version=' "$MODPATH/module.prop" 2>/dev/null | cut -d= -f2)
ui_print "- VideoModeFix ${VER:-unknown}"
ui_print "- target: force video-mode pipeline for 3rd-party camera clients"

# A module counts as usable only if its folder exists and the manager
# hasn't flagged it disabled or pending removal.
mod_enabled() {
  [ -d "/data/adb/modules/$1" ] || return 1
  [ -e "/data/adb/modules/$1/disable" ] && return 1
  [ -e "/data/adb/modules/$1/remove" ] && return 1
  return 0
}

# The hook targets modern Camera2 behavior, so anything below Android 10
# is out from the start.
API=$(getprop ro.build.version.sdk 2>/dev/null || echo 0)
if [ "$API" -lt 29 ]; then
  abort "! Android 10+ required (this device: sdk $API)."
fi

# LSPosed itself runs on top of a Zygisk provider, so one of those has to
# be around and switched on first.
if ! mod_enabled rezygisk && ! mod_enabled zygisk_next && ! mod_enabled zygisknext; then
  abort "! No enabled Zygisk provider found (need ReZygisk or ZygiskNext)."
fi
n=0
mod_enabled rezygisk && n=$((n + 1))
mod_enabled zygisk_next && n=$((n + 1))
mod_enabled zygisknext && n=$((n + 1))
[ "$n" -gt 1 ] && ui_print "! warning: $n Zygisk providers enabled; keep only one to avoid conflicts."

# Without the LSPosed host there is nothing to run the hook in.
if ! mod_enabled zygisk_lsposed; then
  abort "! LSPosed framework not found/enabled. Install its Zygisk module, reboot, then flash this."
fi

# Sanity check on our own package: flashing without the hook APK inside
# would leave a dead module behind.
if [ ! -f "$MODPATH/videomodefix.apk" ]; then
  abort "! videomodefix.apk missing from this zip - broken package, re-download."
fi

# One-flash promise: the hook APK goes in together with the module, so a
# failed install aborts the whole flash instead of stranding the user.
ui_print "- installing hook APK..."
if pm install -r "$MODPATH/videomodefix.apk" 2>&1 | grep -qi "success"; then
  ui_print "- hook APK installed."
else
  abort "! pm install failed. Install $MODPATH/videomodefix.apk manually, then re-flash."
fi

# Leave behind a small seed file documenting the defaults. The hook itself
# doesn't read it (app sandboxes can't reach /data/adb); it only feeds the
# status command and reminds what the built-in behavior is.
CONF=/data/adb/videomodefix/config
mkdir -p /data/adb/videomodefix
[ -f "$CONF" ] || cat > "$CONF" <<'EOF'
# VideoModeFix seed (informational; live tuning lives in the LSPosed scope
# plus /data/local/tmp/videomodefix.conf - see README "Tuning without reboot")
packages=com.instagram.android,com.zhiliaoapp.musically,com.ss.android.ugc.trill
mode=video
EOF
ui_print "- config: $CONF"

set_perm_recursive $MODPATH/bin 0 0 0755 0755
set_perm_recursive $MODPATH/webroot 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/action.sh 0 0 0755
set_perm $MODPATH/bin/videomodefix 0 0 0755
set_perm $MODPATH/bin/verify.sh 0 0 0755
set_perm $MODPATH/bin/ftune 0 0 0755

ui_print "- NEXT (one reboot total):"
ui_print "  1. LSPosed Manager > Modules > VideoModeFix > enable."
ui_print "  2. Scope: check ONLY camera apps (never System Framework)."
ui_print "  3. Reboot once, then record and Verify."
