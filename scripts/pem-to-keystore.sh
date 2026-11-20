#!/usr/bin/env bash
# Convierte una clave existente (key.pem + cert.pem) en un keystore PKCS12 para firmar con la
# MISMA clave que ya usas y que las nuevas versiones actualicen la app instalada.
# Uso: scripts/pem-to-keystore.sh key.pem cert.pem
set -euo pipefail
KEY="${1:?key.pem}"; CERT="${2:?cert.pem}"
read -r -s -p "Contraseña para el nuevo keystore (mín. 8 caracteres): " PASS; echo
openssl pkcs12 -export -inkey "$KEY" -in "$CERT" -name acta -out acta-release.p12 -passout "pass:$PASS"
base64 -w0 acta-release.p12 > acta-release.p12.b64 2>/dev/null || base64 acta-release.p12 | tr -d '\n' > acta-release.p12.b64
echo "Creados acta-release.p12 y acta-release.p12.b64. Alias: acta. Sigue los pasos de make-keystore.sh para los secretos."
