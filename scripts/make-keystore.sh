#!/usr/bin/env bash
# Crea una clave de firma NUEVA para publicar Acta y prepara los 3 secretos de GitHub.
# Uso: scripts/make-keystore.sh
# Guarda el archivo acta-release.p12 y la contraseña en un gestor de contraseñas: si los pierdes,
# nadie podrá actualizar la app instalada con una versión firmada con otra clave.
set -euo pipefail
read -r -s -p "Elige una contraseña para la clave (mín. 8 caracteres): " PASS; echo
[ ${#PASS} -ge 8 ] || { echo "Demasiado corta"; exit 1; }
keytool -genkeypair -keystore acta-release.p12 -storetype PKCS12 -storepass "$PASS" -alias acta \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Acta"
base64 -w0 acta-release.p12 > acta-release.p12.b64 2>/dev/null || base64 acta-release.p12 | tr -d '\n' > acta-release.p12.b64
cat <<M

Creados: acta-release.p12 (la clave) y acta-release.p12.b64 (para GitHub).
En GitHub: Settings > Secrets and variables > Actions > New repository secret:
  ACTA_KEYSTORE_B64  = contenido del archivo acta-release.p12.b64
  ACTA_KEYSTORE_PASS = la contraseña que acabas de elegir
  ACTA_KEY_ALIAS     = acta
Después BORRA acta-release.p12.b64 y guarda acta-release.p12 en un lugar seguro. Nunca los subas al repositorio.
M
