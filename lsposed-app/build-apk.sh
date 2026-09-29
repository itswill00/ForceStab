#!/system/bin/sh
# build-apk.sh - manual APK build with audits (javac + d8 + aapt2 + apksigner).
# Usage: ./build-apk.sh [output.apk]   (default: VideoModeFix.apk)
#
# Requirements (Termux): javac, d8, aapt2, apksigner  (pkg install d8 aapt2 apksigner)
# Plus: libs/android.jar -- download once, e.g.
#   curl -L -o libs/android.jar \
#     https://github.com/Sable/android-platforms/raw/master/android-34/android.jar
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-VideoModeFix.apk}"
KEYSTORE="$SCRIPT_DIR/debug.keystore"
ANDROIDSRC_JAR="$SCRIPT_DIR/libs/android.jar"
cd "$SCRIPT_DIR"
[ -f "$ANDROIDSRC_JAR" ] || { echo "missing libs/android.jar (see header)"; exit 1; }
[ -f "$KEYSTORE" ] || {
  echo "generating debug keystore..."
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias debug -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=VideoModeFix" > /dev/null 2>&1
}
rm -rf out dexb dexbex classes.dex unsigned.apk
mkdir -p out dexb
echo "[1] javac (app + compile-only Xposed stubs)"
javac -source 8 -target 8 -nowarn -cp "$ANDROIDSRC_JAR" -d out $(find stub src -name "*.java")
echo "[2] d8 (app classes only -- stubs are compile-only, never packaged)"
d8 --min-api 29 --lib "$ANDROIDSRC_JAR" --output dexb/classes.zip $(find out/com -name "*.class")
echo "[3] extract real classes.dex (NEVER package the zip itself as dex)"
python3 -c "import zipfile; zipfile.ZipFile('dexb/classes.zip').extractall('dexbex')"
cp dexbex/classes.dex ./classes.dex
python3 -c "
d=open('classes.dex','rb').read(8)
assert d.startswith(b'dex\n'), 'NOT A DEX: '+repr(d)
print('dex magic OK:', d)"
echo "[4] aapt2 link + embed dex (audited)"
aapt2 link -o unsigned.apk --manifest AndroidManifest.xml -A assets --min-sdk-version 29 --target-sdk-version 34 -I "$ANDROIDSRC_JAR"
python3 -c "
import zipfile
z=zipfile.ZipFile('unsigned.apk','a'); z.write('classes.dex','classes.dex'); z.close()
z=zipfile.ZipFile('unsigned.apk'); d=z.read('classes.dex')
assert d.startswith(b'dex\n'), 'APK dex BAD: '+repr(d[:8])
print('apk contents:', z.namelist())"
echo "[5] sign + verify"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$OUT" unsigned.apk
apksigner verify "$OUT" && echo "SIGNED+VERIFIED: $OUT"
ls -lh "$OUT"
