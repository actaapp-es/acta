# Compilar Acta

## App Android
Requisitos: **JDK 17** y el **SDK de Android** con una plataforma (34 o superior) y las build-tools (`aapt2`, `d8`, `zipalign`, `apksigner`). No hace falta Gradle.

```bash
export ANDROID_HOME=$HOME/Android/Sdk      # o la ruta de tu SDK
scripts/build-android.sh
# → build/Acta-<versión>.apk, firmado con una clave de depuración temporal
```

Para firmar con tu clave:
```bash
ACTA_KEYSTORE=acta-release.p12 ACTA_KEYSTORE_PASS=... ACTA_KEY_ALIAS=acta scripts/build-android.sh
```
Para que los enlaces de descarga de la app apunten a tu repositorio: `ACTA_REPO=actaapp-es/acta scripts/build-android.sh` (las Actions lo hacen solas).

El script hace: `aapt2 compile/link` → `javac` (Java 8, sin lambdas) → `d8` → `zipalign` → `apksigner`.

## Acta Conexión (Windows)
Requisitos: Go 1.24 o superior. Se puede compilar desde cualquier sistema:
```bash
cd pc
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-H windowsgui -s -w" -o ActaConexion.exe .
```
El icono y la información de versión están en `resource_windows_amd64.syso` (se regenera con `tools/mkres.py web/logo.svg resource_windows_amd64.syso <versión>`; necesita Python, Pillow y `rsvg-convert`).
