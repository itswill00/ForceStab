# VideoModeFix — Agent Handoff

Read this first in any new session. It holds everything learned so far:
project facts, device truth, user rules, and where things stand.

## User rules (standing, do not relax without asking)

- Project language is **full English**, public-friendly. No Indonesian in
  code, comments, README, or WebUI. Chat with the user in casual Indonesian.
- Module author is **itswill00** everywhere.
- **No version bumps** until a real change exists. **No `git push`, no
  release tags, no release-asset updates without explicit user confirmation.**
- Audit before every git operation: names, English, stale refs, syntax
  (`sh -n`), build artifacts. Commits need signoff + Change-Id when the
  user asks for a squash.
- Code comments: human voice, never numbered lists (`# 1. …`), never AI slop.
- WebUI structure must mirror HyperDL/HyperCore: `webui/` Vue 3 + Vite +
  singlefile → `webroot/index.html`; `src/{App.vue,main.js,assets,components,helpers}`;
  `shell.js` bridge in the `ksu.exec(cmd, '{}', id)` callback style.
- Think flexibly: gate installs with `abort`, consider all situations
  (missing deps, disabled modules, conflicting providers).

## What this project is

Force third-party camera apps into a real video-mode pipeline via an
LSPosed Java hook (`lsposed-app/`, package `com.videomodefix`) plus a
KernelSU companion (`ksu-module/`, id `videomodefix`) that one-flash
installs the bundled APK and offers Verify / hook-log WebUI.
Repo: `https://github.com/itswill00/VideoModeFix` (MIT).

## Proven device truth (Redmi 24117RN76O, Helio G99/mt6789, Android 15)

- `availableVideoStabilizationModes=[0 1]`, `availableOpticalStabilization=[0]`
  (no OIS hardware — nothing can fake it).
- Third-party requests flip `PREVIEW→VIDEO_RECORD`, fps→`[30,30]`,
  `videoStabilizationMode→[ON]`, `eismode=[1]`, `previeweis=[1]` — all
  accepted, verified live via dumpsys + hook logs.
- Still no visible stabilization: stock video runs Xiaomi `CUSTOM (32772)`
  sessions (1080p + encoder); WhatsApp runs `NORMAL (0)` with a 4K
  ImageReader stream. The HAL gates EIS at session/stream level.
- Positive side effect: TikTok live looks visibly cleaner (fixed fps +
  video intent), worth running for picture quality alone.
- Stock camera stutter in the first 1–2s of recording is native (EIS/AE
  warmup), unrelated to this module. Stock camera must NOT be in LSPosed
  scope for daily use (spy only).

## Hard lessons (do not rediscover)

- App processes **cannot** read `/data/adb` (SELinux) — tunables live in
  `/data/local/tmp/videomodefix.conf`, read per process start; `ftune` +
  app kill iterates with zero reboots.
- Never hook abstract classes (`CameraCaptureSession`, `CameraDevice`
  interface) — LSPosed throws; use concrete `CameraDeviceImpl`.
- Only `XposedBridge.log` + `hookAllMethods` are safe on obfuscated LSPosed
  runtimes; `hookMethod(Member,…)` may not exist.
- Never package `classes.zip` as `classes.dex` — it segfaults LSPosed's
  `ObfuscationManager.obfuscateDex` and kills the `lspd` daemon (recover:
  uninstall APK + reboot).
- ReZygisk's custom ELF loader rejects foreign `.so` files — this project
  ships zero native code because of it.
- Session redirect (`sess=1`) technically works (modern SessionConfiguration
  + stock params, 4K drop, use-case override) but stays `NORMAL` mode;
  `usecase=3` blanks some previews; IG black-screens on redirect/drop —
  gate invasive paths per-package with `only=`.
- APK/dex updates need a (soft) reboot — `killall system_server` suffices.
  Parameter changes never need one.
- KSU WebUI content must respect `--window-inset-top` (statusbar overlap).

## Current state

- Device is clean (nothing installed) unless the user says otherwise.
- `main` is one squashed signed-off commit + Change-Id; release `v0.1.0`
  exists with the KSU zip attached; `update.json` points at it.
- Open blind shot (not attempted): hidden `createCustomCaptureSession`
  with undocumented Xiaomi magic ints. Low probability, may break pipelines.

## Commands

- Full pipeline: `./build-deploy.sh -d` (build APK+zip, pm install, sync
  module, then reboot) from repo root.
- Reboot-free tune: `su -c '/data/adb/modules/videomodefix/bin/ftune "sess=1" com.whatsapp'`
- Verify: Action button or `bin/verify.sh`; hook log:
  `grep -h VideoModeFix /data/adb/lspd/log/modules_*.log`
- LSPosed scope/state DB (as root):
  `sqlite3 /data/adb/lspd/config/modules_config.db`
