# VideoModeFix

[![version](https://img.shields.io/badge/version-v0.1.0-blue)](../../releases)
[![license](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![root](https://img.shields.io/badge/root-KernelSU%20%2B%20LSPosed-orange)](#requirements)

Push any third-party camera app into a real **video mode** pipeline
(Instagram, TikTok, WhatsApp, Telegram, …) — on phones where social apps
record from a photo-mode pipeline while EIS and video tuning stay locked to
the stock camera app. Just check the app in the LSPosed scope; no rebuild
needed.

Concretely the hook forces: `VIDEO_RECORD` intent, fixed 30fps, the
stabilization flag, MediaTek EIS vendor tags, and — optionally — a rebuilt
capture session carrying the stock camera's session parameters.

> **Honest scope:** video-mode forcing cannot conjure OIS. If your phone
> has no OIS hardware (like the test device below), no module can fake it —
> and on some HALs (like that same test device) even a perfect video-mode
> request still doesn't engage EIS. See
> [Status](#status-what-was-proven-redmi-24117rn76o-mtk).

---

## Table of contents

- [Problem](#problem)
- [Proof (from a real device)](#proof-from-a-real-device)
- [How it works](#how-it-works)
- [Repository layout](#repository-layout)
- [Requirements](#requirements)
- [Install](#install)
- [Verify](#verify)
- [Tuning without reboot](#tuning-without-reboot)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)
- [Build from source](#build-from-source)
- [Credits & sources](#credits--sources)
- [Disclaimer](#disclaimer)
- [License](#license)

---

## Problem

Many phones (flagship to mid-range) ship with EIS/OIS that works fine in the
stock camera app, but footage from TikTok / Instagram is shaky. Reasons:

1. **Apps don't ask for video mode.** On Camera2, requests default to a
   photo-style pipeline (`PREVIEW` intent, variable fps, no stabilization).
   Most social apps record video from exactly that pipeline and never switch
   it to video mode.
2. **Vendors gate EIS.** On MediaTek/Xiaomi HALs, the real stabilization runs
   through vendor tags (`com.mediatek.eisfeature.eismode` / `previeweis`,
   Xiaomi preview extensions) that third-party apps never touch.
3. **The AOSP flag alone may not engage the HAL.** Setting only the standard
   flag can leave the request at `[ON]` while the picture still shakes.

## Proof (from a real device)

Test device: **Redmi `24117RN76O`** (Helio G99 / `mt6789`), Android 15,
main sensor `s5khm6`, rooted via KernelSU. All read-only (`dumpsys`):

| Check | Result |
|---|---|
| `availableVideoStabilizationModes` | `[0 1]` — EIS exposed to 3rd-party apps |
| `availableOpticalStabilization` | `[0]` on **all** cameras — no OIS hardware |
| `xiaomi.previewSupported` | `[1]` — preview EIS supported by HAL |
| IG `Last request sent` (before fix) | `captureIntent=[PREVIEW]`, `videoStabilizationMode=[OFF]`, no `eismode`/`previeweis` |
| IG live request (after fix) | `videoStabilizationMode=[ON]` |

Check yours (root shell):

```sh
su -c 'dumpsys media.camera' | grep -A1 -E \
  "availableVideoStabilizationModes \(1001a\)|availableOpticalStabilization \(90003\)"
```

If the first key is `[0 1]`, this module applies to you. If the second key is
`[0]`, you have no OIS — same as the test device.

## How it works

Two layers, each doing one job:

```text
Any Camera2 app     --Camera2-->  [ LSPosed hook (VideoModeFix APK) ]  --video mode-->  HAL
                                          ^ scoped by
                                   LSPosed Manager (per-app toggle)
                                   [ KernelSU module: one-flash installer ]
                                   [ + WebUI Verify / hook-log viewer      ]
```

- **`lsposed-app/` (the actual fix).** An LSPosed Java hook that, for
  scoped packages only (scope is set in the LSPosed Manager), converts the
  app's photo-mode pipeline into a video-mode one:
  - rewrites `captureIntent` to `VIDEO_RECORD` (optional, on by default in
    current tuning) and locks `aeTargetFpsRange` to `[30,30]` — EIS needs
    fixed fps;
  - forces `CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE = ON` on every
    `CaptureRequest.Builder.set(...)` call (recursion-guarded);
  - injects MediaTek vendor tags `eismode=1` / `previeweis=1`
    (rejections are logged, never fatal);
  - spoofs `CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES` as `[OFF, ON]`;
  - optionally rebuilds deprecated-API sessions as modern
    `SessionConfiguration` with the stock camera's session parameters
    (`initrequest`, `proprietaryRequest`, …), can drop a 4K stream, and can
    set a stream use case — all gated per-package (some apps black-screen);
  - exposes read-only spies (capture template, session creation path,
    full request/session dumps) used during development.
- **`ksu-module/` (companion).** No native code, no HAL overwrite. It provides:
  - one-flash install (the hook APK is bundled and `pm install`ed);
  - KernelSU WebUI with Verify + hook-log viewer;
  - Action button + `bin/verify.sh` to snapshot HAL caps and recent camera
    requests;
  - `bin/ftune` for reboot-free parameter tuning (see below);
  - `service.sh` boot log of camera capabilities.

> A very early version shipped a Zygisk native `.so`. It was **removed**:
> ReZygisk's custom ELF loader (`csoloader`) refused to load it
> (`Failed to load module [...] arm64-v8a.so`), and it contributed nothing —
> the Java hook does all the work. See
> [Troubleshooting](#troubleshooting).

## Repository layout

```text
VideoModeFix/
├── README.md
├── LICENSE
├── update.json              # OTA info for KernelSU Manager
├── build-deploy.sh          # one-shot: build APK+zip, deploy live, then reboot
├── ksu-module/              # KernelSU companion (installer + verify + WebUI)
│   ├── module.prop customize.sh service.sh post-fs-data.sh action.sh
│   ├── system.prop sepolicy.rule
│   ├── bin/{videomodefix,verify.sh,ftune}
│   ├── webroot/index.html    # generated: vite build output (git-ignored)
│   ├── webui/                # Vue 3 + Vite source (HyperDL convention)
│   │   ├── package.json vite.config.js index.html
│   │   └── src/{App.vue,main.js,assets,components,helpers}
│   ├── releases/             # built zips (git-ignored)
│   └── build.sh              # packs zip; rebuilds webui when stale
├── lsposed-app/             # the hook (built to VideoModeFix.apk)
│   ├── AndroidManifest.xml  assets/xposed_init
│   ├── src/com/videomodefix/Hook.java
│   ├── stub/                # compile-only Xposed API stubs (never packaged)
│   ├── libs/android.jar     # local only, git-ignored (see build docs)
│   └── build-apk.sh
└── docs/
    └── SOURCES.md           # upstream references consulted
```

## Requirements

- Root via **KernelSU** (or fork) with a working **Zygisk** provider
  (ReZygisk / ZygiskNext) — tested on ReZygisk.
- **LSPosed framework** installed and working (`org.lsposed.manager`).
- Any app that uses the camera (it must use the Camera2 API — see below).
- Android 10+ (minSdk 29).
- A MediaTek/Xiaomi-style HAL helps (`eismode`/`previeweis`); the AOSP flag
  part works on any device exposing `[0 1]`.

## Install (one flash)

1. **Flash `VideoModeFix-v0.1.0.zip` in the KernelSU Manager.** The bundled
   `VideoModeFix.apk` hook is `pm install`ed automatically during flash
   (see `ksu-module/customize.sh`).
2. **LSPosed Manager → Modules → VideoModeFix → enable**, scope: check **only**
   the camera apps (e.g. `com.instagram.android`). Do **not** check System
   Framework.
3. **Reboot once** (a soft reboot — killing `system_server` — is enough;
   LSPosed/ART keeps the old dex cached otherwise), then record and verify.
4. Done. Defaults are safe: flag + fixed fps + vendor tags for every scoped
   app; the invasive paths (`intent` rewrite, `sess`, `drop4k`, `usecase`)
   are off unless you enable them via [Tuning](#tuning-without-reboot).
   The WebUI shows Verify + hook log.

## Verify

1. Open the target app, record a few seconds with its camera.
2. Run the module's Action button, or:
   ```sh
   su -c 'sh /data/adb/modules/videomodefix/bin/verify.sh'
   ```
3. While the camera is **open**, dump the live request:
   ```sh
   su -c 'dumpsys media.camera' | grep -A1 "videoStabilizationMode (10011)"
   ```
   Expected: `[ON]` (was `[OFF]` before the fix).
4. Confirm in LSPosed's modules log:
   `VideoModeFix: hooks installed for com.instagram.android`, plus a
   `cfg{...}` line showing the active tuning. No `rejected` / `failed` lines.
5. Judge the footage with your own eyes — that is the only metric that
   matters.

## Tuning without reboot

The hook reads `/data/local/tmp/videomodefix.conf` on every target-process
start (app processes can read it, unlike `/data/adb`). Change parameters,
kill the app, reopen — no reboot, no rebuild:

```sh
su -c '/data/adb/modules/videomodefix/bin/ftune "sess=1" com.whatsapp'
```

Keys (`1` = on, `0` = off; `intent=-1` keeps the app intent):

| Key | Default | Effect |
|---|---|---|
| `fps` | `1` | lock `aeTargetFpsRange` to `[30,30]` |
| `eis` / `peis` | `1` | set MTK `eismode` / `previeweis` |
| `intent` | `-1` | force `captureIntent` (use `3` = `VIDEO_RECORD`) |
| `sess` | `0` | rebuild deprecated sessions as modern `SessionConfiguration` with EIS session params |
| `drop4k` | `-1` | drop surface at this index during rebuild (e.g. a 4K stream that blocks EIS) |
| `usecase` | `-1` | `OutputConfiguration` stream use case override ( risky: wrong values blank the preview) |
| `skey_<suffix>` | — | extra MTK session key, e.g. `skey_nrfeature.3dnrmode=1` or arrays as `skey_multicamfeature.multiCamConfigScalerCropRegion=0,0,4000,3000` |
| `only` | empty (= all scoped apps) | restrict the invasive paths (`sess`, `drop4k`) to these packages, e.g. `only=com.whatsapp` |

One call replaces the whole file — pass all lines at once. A dex change
still needs a (soft) reboot; parameter changes never do.

Legacy note: `/data/adb/videomodefix/config` from older versions is
informational only — app processes could never read `/data/adb` (SELinux).

## Status: what was proven (Redmi 24117RN76O, MTK)

- **Proven:** video-mode forcing works: third-party requests flip from
  `PREVIEW` intent, variable fps and `videoStabilizationMode=[OFF]` to
  `intent=VIDEO_RECORD`, fixed fps `[30,30]`, `[ON]`, with `eismode=[1]` +
  `previeweis=[1]` accepted (no rejections) — all verified live via `dumpsys`
  and hook logs.
- **Not working (this device):** stock video sessions run Xiaomi `CUSTOM`
  `CUSTOM` operating mode (`32772`) with 1080p + encoder streams, while
  WhatsApp opens `NORMAL (0)` sessions with a 4K ImageReader stream. The HAL
  gates real EIS at session/stream level, which request flags cannot change.
- Conclusion: on HALs like this one the module is best-effort and may show
  no visible difference. On HALs that honor request flags it should work.
  Session rebuild (modern `SessionConfiguration` + stock session params,
  4K-stream drop, stream use-case override) was attempted: sessions rebuild
  cleanly but the operating mode stays `NORMAL` and footage stays shaky.
  Forcing a Xiaomi `CUSTOM` mode would need the hidden
  `createCustomCaptureSession` with undocumented magic ints — not attempted
  (blind, may break the camera pipeline).

## Limitations

- **No OIS.** `availableOpticalStabilization=[0]` means no OIS hardware;
  nothing software-side can stabilize optics.
- **EIS crops** ~10–20% of the frame; low light may still smear.
- **4K60 and some modes**: the HAL can disable EIS regardless of the request;
  the hook cannot override that.
- **MediaTek-leaning.** Vendor tags are MTK-specific; on Snapdragon/QCOM the
  AOSP flag still applies but the vendor part is skipped (rejected keys are
  logged, not fatal).
- **Legacy Camera API.** Apps still on the deprecated `android.hardware.Camera`
  API (`Camera.Parameters`) are not hooked — only Camera2 (which includes
  CameraX, used by most modern apps).
- **Invasive paths are app-sensitive.** Session rebuild / 4K-drop / use-case
  override can blank the preview in some apps (seen on Instagram) while
  working in others. Gate them with `only=` and test per app.
- **App updates** can change the camera pipeline; re-run
  [Verify](#verify) after big updates.
- **arm64.** Native bits are gone, but testing was arm64-only.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `videoStabilizationMode` still `[OFF]` | Hook not loaded: check LSPosed scope, reboot after every APK update, confirm `hooks installed` in log. |
| `[ON]` but still shaky | The HAL gates real EIS at session/stream level — see [Status](#status-what-was-proven-redmi-24117rn76o-mtk). Try the `sess` / `drop4k` / `skey_` tunables; check the hook log for `rejected` lines. |
| Black preview in an app | An invasive path (`sess`, `drop4k`, `usecase`) broke that app's pipeline. Clear those keys via `ftune`, kill the app, reopen. Use `only=` to scope experiments to one app. |
| `NoSuchMethodError: hookMethod` | Stale hook build. Current code uses reflection + `hookAllMethods` only — rebuild the APK from this repo. (Seen on obfuscated LSPosed runtimes where `hookMethod(Member,…)` is absent.) |
| `lspd` crash in `ObfuscationManager.obfuscateDex` after enabling | Bad APK packaging (a ZIP was embedded as `classes.dex` instead of the extracted dex). Rebuild with `build-apk.sh`, which asserts `dex\n` magic twice. Never package `classes.zip` as `classes.dex`. |
| `Failed to load module [.../arm64-v8a.so]` (ReZygisk) | Leftover from an early native-gate experiment (since removed — there is no `zygisk/` dir anymore). Update to a current zip. |
| LSPosed Manager says “Not installed” | Its daemon died (see crash above). Uninstall the offending APK, reboot; the daemon restarts at boot. |
| OIS-related keys | Out of scope — see [Limitations](#limitations). |

## Build from source

In Termux:

```sh
pkg install d8 aapt2 apksigner openjdk-21 unzip nodejs   # javac, python3, npm
cd lsposed-app
curl -L -o libs/android.jar \
  https://github.com/Sable/android-platforms/raw/master/android-34/android.jar
./build-apk.sh VideoModeFix.apk     # asserts dex magic, signs, verifies

cd ../ksu-module
./build.sh                       # rebuilds webui/ if stale, then packs
                                 # ksu-module/releases/VideoModeFix-v0.1.0.zip
```

`stub/` contains minimal compile-only Xposed API signatures (matching
`de.robv.android.xposed`). They are used for `javac`/`d8` classpaths only and
are **never packaged** — the real classes come from the LSPosed framework at
runtime.

## Credits & sources

Hook patterns and loader knowledge taken from upstream (see
[`docs/SOURCES.md`](docs/SOURCES.md)):

- [`topjohnwu/zygisk-module-sample`](https://github.com/topjohnwu/zygisk-module-sample)
  — canonical Zygisk `ModuleBase` + companion pattern.
- [`j-hc/zygisk-detach`](https://github.com/j-hc/zygisk-detach) — production
  Zygisk pattern (process filter in pre-specialize, `pltHook` in post,
  companion serving config).
- [`frknkrc44/HMA-OSS`](https://github.com/frknkrc44/HMA-OSS) — reference for
  Zygisk-side ART hooking without LSPosed (not used; documented why).
- [`LSPosed/LSPosed`](https://github.com/LSPosed/LSPosed) — framework;
  `ObfuscationManager.obfuscateDex` ("for module dexes") identified from its
  source during crash analysis.
- [`PerformanC/ReZygisk`](https://github.com/PerformanC/ReZygisk) and
  [`ThePedroo/CSOLoader`](https://github.com/ThePedroo/CSOLoader) — loader
  forensics for the `Failed to load module` case.
- Chromium `VideoCaptureCamera2.java` — the correct
  check-then-set stabilization pattern.
- [`Sable/android-platforms`](https://github.com/Sable/android-platforms) —
  `android.jar` used for builds.

## Disclaimer

Root modifications can bootloop your device. The companion module is
read-only by design (no HAL/prop overwrites), but the hook alters camera
requests in third-party apps. Test with [Verify](#verify), keep a way to
reach recovery/safe mode, and disable the LSPosed scope or the KernelSU
module if anything misbehaves. No warranty — see [LICENSE](LICENSE).

## License

[MIT](LICENSE).
