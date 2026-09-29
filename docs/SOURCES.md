# Hook references (read before building any native code)
- topjohnwu/zygisk-module-sample `module/jni/example.cpp` — canonical Zygisk
  ModuleBase template: preAppSpecialize reading nice_name via JNI, companion,
  DLCLOSE.
- j-hc/zygisk-detach `zygisk/jni/module.cpp` — production pattern: filter the
  process in pre-specialize, `pltHook` libbinder in post-specialize, companion
  serving config.
- frknkrc44/HMA-OSS `zygote/` — proof that Zygisk can replace LSPosed for Java
  hooks via its own ART layer (LSPlant). Too heavy for this project, so
  VideoModeFix uses LSPosed for the Java hook.
- Chromium `VideoCaptureCamera2.java` — the correct pattern: check
  CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES, then set
  CONTROL_VIDEO_STABILIZATION_MODE_ON.
- LSPosed/LSPosed `ObfuscationManager` ("for module dexes") — identified from
  source while debugging a daemon crash (a ZIP had been packaged as
  `classes.dex`).
- PerformanC/ReZygisk + ThePedroo/CSOLoader — loader forensics for a
  `Failed to load module` case (custom ELF loader; native gate removed).

Architecture conclusion: native Zygisk was only a gate (removed in v0.2.0);
the Java hook via LSPosed does the forcing. No public module forces EIS for
IG/TikTok — this is new.
