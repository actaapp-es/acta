# Publicar Acta en GitHub (paso a paso)

Tiempo aproximado: 30 minutos. No necesitas saber programar.

## 0 · Antes: protege tu identidad (si te importa el anonimato)
- Crea una cuenta de GitHub con un **alias** (GitHub no exige tu nombre real) y un correo dedicado.
- En GitHub › *Settings › Emails*: activa **Keep my email addresses private** y **Block command line pushes that expose my email**.
- Tu correo de commits será `NUMERO+alias@users.noreply.github.com` (lo ves en esa misma página).
- Activa la verificación en dos pasos (*Settings › Password and authentication*). Es imprescindible.
- Todo lo que publiques en un repositorio público es visible para siempre, incluido el historial de commits.

## 1 · Instalar las herramientas
- **Git para Windows**: https://git-scm.com/download/win (acepta las opciones por defecto; incluye *Git Bash*).
- Opcional: GitHub Desktop, si prefieres botones a comandos.

## 2 · Crear el repositorio
1. En https://github.com/new: nombre `acta`, visibilidad **Public**, sin README ni licencia (ya vienen en el paquete).
2. Anota tu `actaapp-es/acta`.

## 3 · Personalizar y subir
En *Git Bash*, dentro de la carpeta `acta` descomprimida:
```bash
scripts/set-repo.sh TU-USUARIO/acta
git init -b main
git config user.name "tu-alias"
git config user.email "NUMERO+tu-alias@users.noreply.github.com"
git add .
git commit -m "Acta 5.1.6"
git remote add origin https://github.com/TU-USUARIO/acta.git
git push -u origin main
```
La primera vez se abrirá el navegador para iniciar sesión. En *Actions* verás cómo se compila sola.

## 4 · La clave de firma (importante)
Android solo deja actualizar una app si la nueva versión está firmada con **la misma clave**. Elige una opción:

**A. Seguir con tu clave actual** (las versiones nuevas actualizarán tu Acta instalada): en el paquete que te entrego hay `claves-github/` con los tres valores listos.

**B. Crear una clave nueva:** `scripts/make-keystore.sh` (te guía y prepara los valores).

Luego, en GitHub › *Settings › Secrets and variables › Actions › New repository secret*, crea:

| Nombre | Valor |
|---|---|
| `ACTA_KEYSTORE_B64` | contenido de `acta-release.p12.b64` |
| `ACTA_KEYSTORE_PASS` | la contraseña |
| `ACTA_KEY_ALIAS` | `acta` |

Guarda el `.p12` y la contraseña en un gestor de contraseñas **fuera del repositorio**. Si los pierdes no podrás actualizar la app a quien ya la tenga instalada.

## 5 · Publicar una versión
```bash
git tag v5.1.6
git push origin v5.1.6
```
La Action compila el APK y Acta Conexión, y crea la *Release* con `Acta.apk`, `ActaConexion-Windows.zip` y `SHA256SUMS.txt`. (La etiqueta debe coincidir con `versionName` del manifiesto: `v5.1.6` ↔ `5.1.6`.)
Para la siguiente versión: sube `versionCode` y `versionName` en `app/AndroidManifest.xml`, actualiza `CHANGELOG.md`, haz commit y crea la etiqueta nueva.

## 6 · Ajustes recomendados del repositorio
- *Settings › General*: descripción («App de teléfono que graba las dos voces de las llamadas»), web y *topics* (`android`, `call-recorder`, `adb`, `privacy`).
- *Settings › Code security*: activa **Private vulnerability reporting** y Dependabot.
- *Settings › Branches*: protege `main` (exigir PR) si vas a aceptar colaboradores.
- Sube una imagen social (`docs/img/social-preview.png`) en *Settings › Social preview*.

## 7 · Web
La web va aparte, en su propio paquete: sus botones de descarga apuntan a las *Releases* de este repositorio (`.../releases/latest/download/Acta.apk`), así que cada versión nueva queda disponible sin tocar la web.

## 8 · Evitar el aviso de Windows (opcional)
El `.exe` no va firmado y Windows puede mostrar SmartScreen. Para proyectos de código abierto, **SignPath Foundation** ofrece certificados gratuitos: exige licencia aprobada por la OSI, compilación automática en GitHub, verificación en dos pasos y al menos una versión publicada; el certificado se emite a nombre de la fundación (el editor que verá Windows es «SignPath Foundation», no tú). Solicitud en https://signpath.org. La aprobación depende de ellos.

## Mantenimiento
- Revisa los *issues* y las *pull requests* con calma; no estás obligado a aceptar nada.
- Si algún día quieres dejar el proyecto, indica en el README que está archivado (*Settings › Archive this repository*).
