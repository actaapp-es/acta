# Cómo contribuir a Acta

¡Gracias por querer ayudar! Estas son las reglas básicas.

## Antes de empezar
- **Dudas de uso:** canal de Telegram https://t.me/actaapp (los issues son para errores y mejoras).
- **Fallos de seguridad:** no los publiques; sigue [SECURITY.md](SECURITY.md).
- **Cambios grandes:** abre primero un issue para comentarlo y no perder tu tiempo.

## Entorno
Necesitas JDK 17 y el SDK de Android (plataforma 34+ y build-tools). Sin Gradle:

```bash
export ANDROID_HOME=/ruta/al/sdk
scripts/build-android.sh            # APK firmado con una clave de depuración
```
La app está escrita en **Java 8** (sin lambdas ni librerías externas, a propósito: el APK es pequeño y auditable). Acta Conexión (carpeta `pc/`) es Go **sin dependencias**; compila con `cd pc && GOOS=windows go build`.

## Pull requests
1. Haz un fork y crea una rama desde `main`.
2. Cambios pequeños y enfocados; un tema por PR.
3. Mantén el estilo del código existente (4 espacios, `.editorconfig`).
4. Prueba en un móvil real y di cuál en la PR.
5. **No añadas permisos, dependencias ni conexiones de red** sin justificarlo claramente.
6. La interfaz está en **español**; mensajes cortos, tono sobrio.

## Principios del proyecto
- Privacidad ante todo: nada sale del móvil sin que el usuario lo decida; sin analítica ni seguimiento.
- Sin cuentas ni servidores propios.
- Simplicidad: menos fricción para quien instala.

Al contribuir aceptas que tu aportación se publique bajo GPL-3.0-or-later.

## Conducta
Lee el [Código de conducta](CODE_OF_CONDUCT.md).
