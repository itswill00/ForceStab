// VideoModeFix LSPosed hook - pushes third-party camera apps into video mode.
// Lessons baked in:
//  - untrusted_app cannot read /data/adb (SELinux) -> tunables live in
//    /data/local/tmp/videomodefix.conf (reboot-free iteration).
//  - LSPosed scope decides WHICH packages; hook never guesses.
//  - never hook abstract classes (CameraCaptureSession/CameraDevice).
//  - only XposedBridge.log + hookAllMethods are safe on obfuscated runtimes.
// Lesson: untrusted_app processes CANNOT read /data/adb/videomodefix/config
// (SELinux denial, silent fallback). So behavior is built-in:
//   - LSPosed scope decides WHICH packages (framework only calls us for those)
//   - hook always sets AOSP flag + eismode + previeweis (rejections are logged)
package com.videomodefix;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.util.Range;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;

public class Hook implements IXposedHookLoadPackage {
  // Tunables WITHOUT reboot: read each process start from a world-readable
  // file (app contexts cannot read /data/adb, but can read /data/local/tmp).
  // Iterate with: echo "fps=0" > /data/local/tmp/videomodefix.conf (as root),
  // then force-stop the target app and reopen. Reboot only for dex changes.
  static final String CONF = "/data/local/tmp/videomodefix.conf";
  static boolean cfgFps = true, cfgEis = true, cfgPeis = true;
  static int cfgIntent = -1; // -1 = keep app intent, else force this intent id
  // only= : comma package list for the INVASIVE paths (session redirect,
  // drop4k). Empty = apply everywhere (not recommended: IG black-screens).
  static final java.util.Set<String> sOnly = new java.util.HashSet<String>();
  static boolean pkgAllowed(String pkg) { return sOnly.isEmpty() || sOnly.contains(pkg); }

  static void readConfig() {
    cfgFps = true; cfgEis = true; cfgPeis = true; cfgIntent = -1; cfgSess = false;
    cfgDrop4k = -1; cfgUsecase = -1; sSessKeys.clear();
    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(CONF))) {
      String l;
      while ((l = br.readLine()) != null) {
        l = l.trim();
        if (l.startsWith("fps=")) cfgFps = l.endsWith("1");
        else if (l.startsWith("eis=")) cfgEis = l.endsWith("1");
        else if (l.startsWith("peis=")) cfgPeis = l.endsWith("1");
        else if (l.startsWith("sess=")) cfgSess = l.endsWith("1");
        else if (l.startsWith("usecase=")) {
          try { cfgUsecase = Long.parseLong(l.substring(8).trim()); } catch (NumberFormatException ignored) {}
        } else if (l.startsWith("only=")) {
          sOnly.clear();
          for (String p : l.substring(5).split(",")) {
            p = p.trim();
            if (!p.isEmpty()) sOnly.add(p);
          }
        } else if (l.startsWith("skey_")) {
          int eq = l.indexOf('=');
          if (eq > 5) sSessKeys.put(l.substring(5, eq).trim(), l.substring(eq + 1).trim());
        }
        else if (l.startsWith("drop4k=")) {
          try { cfgDrop4k = Integer.parseInt(l.substring(7).trim()); } catch (NumberFormatException ignored) {}
        }
        else if (l.startsWith("intent=")) {
          try { cfgIntent = Integer.parseInt(l.substring(7).trim()); } catch (NumberFormatException ignored) {}
        }
      }
    } catch (Throwable ignored) { /* defaults stand */ }
  }
  static final ThreadLocal<Boolean> sForcing = new ThreadLocal<Boolean>() {
    @Override protected Boolean initialValue() { return false; }
  };

  static void forceVendorTags(Object builderObj) {
    if (!(builderObj instanceof CaptureRequest.Builder)) return;
    CaptureRequest.Builder b = (CaptureRequest.Builder) builderObj;
    if (cfgEis) {
      try {
        b.set(new CaptureRequest.Key<Integer>("com.mediatek.eisfeature.eismode", Integer.class), 1);
      } catch (Throwable t) { XposedBridge.log("VideoModeFix: eismode rejected: " + t.getMessage()); }
    }
    if (cfgPeis) {
      try {
        b.set(new CaptureRequest.Key<Integer>("com.mediatek.eisfeature.previeweis", Integer.class), 1);
      } catch (Throwable t) { XposedBridge.log("VideoModeFix: previeweis rejected: " + t.getMessage()); }
    }
    if (cfgIntent >= 0) {
      try {
        b.set(CaptureRequest.CONTROL_CAPTURE_INTENT, cfgIntent);
      } catch (Throwable t) { XposedBridge.log("VideoModeFix: intent rejected: " + t.getMessage()); }
    }
  }

  // NOTE (v6): do NOT hook CameraCaptureSession methods here - the class is
  // abstract and LSPosed refuses ("Cannot hook abstract methods"), which
  // aborts handleLoadPackage AFTER hooks 1-2 installed. Verification is done
  // out-of-band via dumpsys (see bin/verify.sh), not from inside the hook.

  // Full recipe dump (v9): log EVERY key of the built request once per
  // process, so stock (stable) can be diffed against 3rd-party (shaky).
  static void logBuiltRequest(Object reqObj) {
    try {
      if (!(reqObj instanceof CaptureRequest)) return;
      CaptureRequest req = (CaptureRequest) reqObj;
      StringBuilder sb = new StringBuilder();
      for (CaptureRequest.Key<?> k : req.getKeys()) {
        Object v;
        try {
          v = req.get(k);
          if (v instanceof int[]) v = java.util.Arrays.toString((int[]) v);
        } catch (Throwable t) { v = "ERR"; }
        sb.append(k.getName()).append("=").append(v).append(";");
      }
      XposedBridge.log("VideoModeFix: built[" + sb.length() + "] " + sb.toString());
    } catch (Throwable t) { XposedBridge.log(t); }
  }

  // Template spy: log which capture template the app requests.
  // Finding: stock video AND 3rd-party apps both use TEMPLATE_PREVIEW (1),
  // so the template is NOT the differentiator. Template IDs: 1=PREVIEW,
  // 2=STILL, 3=RECORD, 4=SNAPSHOT, 5=ZSL, 6=MANUAL.

  // Session spy (v12): log HOW the session is created (which overload, mode?).
  // Stock gets CUSTOM operating mode, WA gets NORMAL. Read-only, no rewrite.
  // Session redirect (experimental): WA uses the deprecated
  // createCaptureSession(List, cb, handler) which carries NO session parameters,
  // so the MTK EIS pipeline (initrequest/proprietaryRequest) never boots.
  // When conf has sess=1, rebuild the call as modern
  // createCaptureSession(SessionConfiguration) with EIS session params and
  // skip the original. Any failure -> fall through to the original call.
  static java.util.concurrent.Executor sSessExec =
    java.util.concurrent.Executors.newSingleThreadExecutor();
  static boolean cfgSess = false;
  static int cfgDrop4k = -1; // drop surface at this index in redirect (WA 4K killer?); -1 = keep all
  static long cfgUsecase = -1; // OutputConfiguration stream use case override; -1 = keep
  static final java.util.Map<String, String> sSessKeys = new java.util.HashMap<String, String>();

  static String sCurPkg = "";

  static void maybeRedirectSession(XC_MethodHook.MethodHookParam p, Object deviceObj) {
    if (!cfgSess || !pkgAllowed(sCurPkg) || p.args == null || p.args.length < 2) return;
    if (!(p.args[0] instanceof java.util.List)) return;
    try {
      java.util.List<?> surfaces = (java.util.List<?>) p.args[0];
      Object cb = p.args[1];
      if (!(cb instanceof android.hardware.camera2.CameraCaptureSession.StateCallback)) return;
      android.hardware.camera2.CameraCaptureSession.StateCallback callback =
        (android.hardware.camera2.CameraCaptureSession.StateCallback) cb;
      if (!(deviceObj instanceof android.hardware.camera2.CameraDevice)) return;
      android.hardware.camera2.CameraDevice dev = (android.hardware.camera2.CameraDevice) deviceObj;

      java.util.List<android.hardware.camera2.params.OutputConfiguration> outs =
        new java.util.ArrayList<android.hardware.camera2.params.OutputConfiguration>();
      int idx = 0;
      for (Object s : surfaces) {
        if (s instanceof android.view.Surface) {
          XposedBridge.log("VideoModeFix: surface[" + idx + "]=" + s);
          if (idx != cfgDrop4k) {
            android.hardware.camera2.params.OutputConfiguration oc =
              new android.hardware.camera2.params.OutputConfiguration((android.view.Surface) s);
            if (cfgUsecase >= 0) {
              try {
                oc.getClass().getMethod("setStreamUseCase", long.class).invoke(oc, cfgUsecase);
              } catch (Throwable t) { XposedBridge.log("VideoModeFix: usecase rejected: " + t.getMessage()); }
            }
            outs.add(oc);
          }
        }
        idx++;
      }
      if (outs.isEmpty()) return;

      // session params: stock boot blob + any skey_ overrides from conf
      // (values: int scalars, or int arrays as "a,b,c")
      CaptureRequest.Builder pb = dev.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_PREVIEW);
      pb.set(new CaptureRequest.Key<Integer>("com.mediatek.configure.setting.initrequest", Integer.class), 1);
      pb.set(new CaptureRequest.Key<Integer>("com.mediatek.configure.setting.proprietaryRequest", Integer.class), 1);
      pb.set(new CaptureRequest.Key<Integer>("com.mediatek.eisfeature.eismode", Integer.class), 1);
      pb.set(new CaptureRequest.Key<Integer>("com.mediatek.eisfeature.previeweis", Integer.class), 1);
      for (java.util.Map.Entry<String, String> e : sSessKeys.entrySet()) {
        try {
          String v = e.getValue();
          if (v.contains(",")) {
            String[] parts = v.split(",");
            int[] arr = new int[parts.length];
            for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
            pb.set(new CaptureRequest.Key<int[]>("com.mediatek." + e.getKey(), int[].class), arr);
          } else {
            pb.set(new CaptureRequest.Key<Integer>("com.mediatek." + e.getKey(), Integer.class),
              Integer.parseInt(v.trim()));
          }
        } catch (Throwable t) { XposedBridge.log("VideoModeFix: skey rejected " + e.getKey() + ": " + t.getMessage()); }
      }

      android.hardware.camera2.params.SessionConfiguration sc =
        new android.hardware.camera2.params.SessionConfiguration(
          android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR,
          outs, sSessExec, callback);
      sc.setSessionParameters(pb.build());
      dev.createCaptureSession(sc);
      p.setResult(null); // skip the deprecated original
      XposedBridge.log("VideoModeFix: session redirected with EIS params");
    } catch (Throwable t) {
      XposedBridge.log("VideoModeFix: redirect failed, original kept: " + t.getMessage());
    }
  }

  static void spySessionCreation(Class<?> devCls, Method hookAll) throws Exception {
    XC_MethodHook spy = new XC_MethodHook() {
      @Override protected void beforeHookedMethod(MethodHookParam p) {
        try {
          StringBuilder sb = new StringBuilder();
          if (p.args != null) {
            for (Object a : p.args) {
              if (a == null) sb.append("null,");
              else if (a instanceof Integer) sb.append("int=").append(a).append(",");
              else sb.append(a.getClass().getSimpleName()).append(",");
            }
          }
          XposedBridge.log("VideoModeFix: sessionCreate via " + p.method.getName()
            + " args[" + sb + "]");
          maybeRedirectSession(p, p.thisObject);
          // SessionConfiguration path: dump session parameters (Xiaomi keys live here)
          if (p.args != null) {
            for (Object a : p.args) {
              if (a != null && a.getClass().getName().equals("android.hardware.camera2.params.SessionConfiguration")) {
                try {
                  Object params = a.getClass().getMethod("getSessionParameters").invoke(a);
                  if (params instanceof CaptureRequest) {
                    CaptureRequest req = (CaptureRequest) params;
                    StringBuilder kb = new StringBuilder();
                    for (CaptureRequest.Key<?> k : req.getKeys()) {
                      Object v;
                      try {
                        v = req.get(k);
                        if (v instanceof int[]) v = java.util.Arrays.toString((int[]) v);
                      } catch (Throwable t) { v = "ERR"; }
                      kb.append(k.getName()).append("=").append(v).append(";");
                    }
                    XposedBridge.log("VideoModeFix: sessionParams[" + kb.length() + "] " + kb.toString());
                  } else {
                    XposedBridge.log("VideoModeFix: sessionParams=null");
                  }
                } catch (Throwable t) { XposedBridge.log("VideoModeFix: sessionParams read failed: " + t.getMessage()); }
              }
            }
          }
        } catch (Throwable t) { XposedBridge.log(t); }
      }
    };
    for (String m : new String[]{"createCaptureSession", "createCustomCaptureSession",
        "createConstrainedHighSpeedCaptureSession", "createReprocessableCaptureSession"}) {
      try {
        hookAll.invoke(null, devCls, m, spy);
      } catch (Throwable t) { XposedBridge.log("VideoModeFix: session spy skipped " + m); }
    }
  }

  @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
    readConfig();
    sCurPkg = lp.packageName;
    XposedBridge.log("VideoModeFix: hooking " + lp.packageName
      + " cfg{fps=" + (cfgFps ? 1 : 0) + " eis=" + (cfgEis ? 1 : 0)
      + " peis=" + (cfgPeis ? 1 : 0) + " intent=" + cfgIntent
      + " sess=" + (cfgSess ? 1 : 0) + " drop4k=" + cfgDrop4k
      + " usecase=" + cfgUsecase + " skeys=" + sSessKeys.size() + "}");
    try {
      Method hookAll = XposedBridge.class.getMethod("hookAllMethods", Class.class, String.class, XC_MethodHook.class);

      Class<?> builder = Class.forName("android.hardware.camera2.CaptureRequest$Builder");
      hookAll.invoke(null, builder, "set", new XC_MethodHook() {
        @Override protected void beforeHookedMethod(MethodHookParam p) {
          try {
            String ks = String.valueOf(p.args[0]);
            if (ks.contains("videoStabilizationMode")) p.args[1] = 1;
            // EIS needs a FIXED frame rate: stock uses [30,30], apps ask [14,30].
            if (cfgFps && ks.contains("aeTargetFpsRange")) p.args[1] = new Range<Integer>(30, 30);
            if (sForcing.get()) return;
            sForcing.set(true);
            try {
              forceVendorTags(p.thisObject);
            } finally {
              sForcing.set(false);
            }
          } catch (Throwable t) { XposedBridge.log(t); }
        }
      });

      hookAll.invoke(null, Class.forName("android.hardware.camera2.CameraCharacteristics"), "get", new XC_MethodHook() {        @Override protected void afterHookedMethod(MethodHookParam p) {
          try {
            if (p.args[0] == CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) {
              p.setResult(new int[]{0, 1});
            }
          } catch (Throwable t) { XposedBridge.log(t); }
        }
      });
      hookAll.invoke(null, builder, "build", new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          try {
            logBuiltRequest(p.getResult());
          } catch (Throwable t) { XposedBridge.log(t); }
        }
      });
      // Note: android.hardware.camera2.CameraDevice itself is abstract and
      // unhookable - use the concrete impl. Fall back silently if absent.
      String[] deviceImpls = {
        "android.hardware.camera2.impl.CameraDeviceImpl",
        "android.hardware.camera2.CameraDevice"
      };
      for (String cn : deviceImpls) {
        try {
          final Class<?> devCls = Class.forName(cn);
          hookAll.invoke(null, devCls, "createCaptureRequest", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
              try {
                if (p.args != null && p.args.length > 0) {
                  XposedBridge.log("VideoModeFix: template=" + p.args[0] + " in " + lp.packageName);
                }
              } catch (Throwable t) { XposedBridge.log(t); }
            }
          });
          break;
        } catch (Throwable t) { XposedBridge.log("VideoModeFix: template hook skipped (" + cn + "): " + t.getMessage()); }
      }
      XposedBridge.log("VideoModeFix: hooks installed for " + lp.packageName);
      try {
        spySessionCreation(Class.forName("android.hardware.camera2.impl.CameraDeviceImpl"), hookAll);
      } catch (Throwable t) { XposedBridge.log("VideoModeFix: session spy unavailable: " + t.getMessage()); }
    } catch (Throwable t) {
      XposedBridge.log(t);
    }
  }
}
