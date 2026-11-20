#!/usr/bin/env bash
# Compila Acta con las herramientas estándar del SDK de Android. Uso: scripts/build-android.sh
# Salida: build/Acta-<versión>.apk (firmado; con ACTA_KEYSTORE o con una clave de depuración).
# Variables opcionales:
#   ANDROID_HOME        SDK de Android (en GitHub Actions ya está configurado)
#   ACTA_REPO           "usuario/repositorio": enlaces de descarga que usa la app (p. ej. github.repository)
#   ACTA_KEYSTORE / ACTA_KEYSTORE_PASS / ACTA_KEY_ALIAS   clave de firma de publicación
#   ANDROID_JAR, AAPT2, D8, ZIPALIGN, APKSIGNER            para sustituir herramientas concretas
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
latest() { ls -d "$1"/* 2>/dev/null | sort -V | tail -1; }
if [ -z "${ANDROID_JAR:-}" ]; then ANDROID_JAR="$(latest "$SDK/platforms")/android.jar"; fi
if [ -z "${AAPT2:-}${D8:-}${ZIPALIGN:-}${APKSIGNER:-}" ] || [ -n "$SDK" ]; then BT="$(latest "$SDK/build-tools" || true)"; fi
AAPT2="${AAPT2:-${BT:-}/aapt2}"; D8="${D8:-${BT:-}/d8}"; ZIPALIGN="${ZIPALIGN:-${BT:-}/zipalign}"; APKSIGNER="${APKSIGNER:-${BT:-}/apksigner}"
VC="$(grep -o 'android:versionCode="[0-9]*"' app/AndroidManifest.xml | grep -o '[0-9]*')"
VN="$(grep -o 'android:versionName="[^"]*"' app/AndroidManifest.xml | sed 's/.*="\(.*\)"/\1/')"
OUT=build; rm -rf "$OUT"; mkdir -p "$OUT"/{gen,classes,dex,src}
echo "Acta $VN ($VC) · android.jar: $ANDROID_JAR"

cp -r app/src/. "$OUT/src/"
if [ -n "${ACTA_REPO:-}" ]; then
  sed -i "s#REPO = \"actaapp-es/acta\"#REPO = \"$ACTA_REPO\"#" "$OUT/src/com/acta/recorder/Project.java"
fi

"$AAPT2" compile --dir app/res -o "$OUT/res.zip"
"$AAPT2" link -I "$ANDROID_JAR" --manifest app/AndroidManifest.xml --min-sdk-version 29 --target-sdk-version 36 \
  --version-code "$VC" --version-name "$VN" --java "$OUT/gen" -o "$OUT/base.apk" "$OUT/res.zip"

find "$OUT/src" third_party "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
$D8 --min-api 29 --lib "$ANDROID_JAR" --output "$OUT/dex" "$OUT/classes.jar"

cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex )

if [ "${SKIP_SIGN:-0}" = "1" ]; then echo "Sin firmar: $OUT/unsigned.apk"; exit 0; fi
"$ZIPALIGN" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ -z "${ACTA_KEYSTORE:-}" ]; then
  echo "AVISO: sin ACTA_KEYSTORE; se firma con una clave de depuración temporal (no sirve para actualizar apps ya instaladas)."
  ACTA_KEYSTORE="$OUT/debug.jks"; ACTA_KEYSTORE_PASS=android; ACTA_KEY_ALIAS=acta-debug
  keytool -genkeypair -keystore "$ACTA_KEYSTORE" -storepass "$ACTA_KEYSTORE_PASS" -alias "$ACTA_KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Acta debug" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$ACTA_KEYSTORE" --ks-pass "pass:$ACTA_KEYSTORE_PASS" --ks-key-alias "$ACTA_KEY_ALIAS" \
  --out "$OUT/Acta-$VN.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify "$OUT/Acta-$VN.apk"
echo "OK -> $OUT/Acta-$VN.apk"
