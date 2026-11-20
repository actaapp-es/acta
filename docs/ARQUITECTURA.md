# Arquitectura

## Cómo se graban las dos voces
Android reserva la captura del audio de la línea (`VOICE_CALL`) a apps del sistema o a procesos con identidad **shell**. Acta aprovecha la depuración por red local del propio móvil:

1. **Activación (una vez y tras cada reinicio).** Con el móvil por USB, `adb tcpip 5555` abre el puerto de depuración en el móvil. Lo hace **Acta Conexión** (o tú a mano). Después se puede quitar el cable.
2. **Conexión.** `NativeAdbManager` usa un cliente ADB propio (libadb-android) para conectarse a `localhost:5555`. La primera vez Android pide aceptar la huella de la app; las claves se guardan en el móvil. Se mantiene viva una conexión (`startKeepAlive`).
3. **Grabación.** Al contestar, el cliente lanza `AdbRecorderMain` con `app_process` como usuario `shell`, en proceso hijo y sesión propia (`setsid` + `nohup`), de modo que perder el canal ADB no corta la grabación. Usa `AudioRecord` con `VOICE_CALL` (ambas voces) y recorre una «escalera» de fuentes si alguna no está disponible. Escribe un WAV y termina cuando aparece un archivo de parada.
4. **Recuperación.** Al colgar se pide la parada, se comprueba la cabecera WAV, se descarga el archivo y se **cifra** (AES-256-GCM, clave en Android Keystore) en el almacenamiento privado de la app.
5. **Reserva.** Si no hay conexión ADB, Acta graba con el micrófono (se capta la otra voz si hay altavoz).

Alternativa: **Shizuku** (`ShizukuManager`, `RecorderUserService`) hace lo mismo con el servicio de Shizuku.

## Módulos principales (`app/src/com/acta/recorder`)
| Archivo | Función |
|---|---|
| `ActaInCallService`, `InCallActivity`, `CallManager` | App de teléfono: gestión de llamadas e interfaz |
| `Recorder`, `NativeAdbManager`, `AdbRecorderMain` | Grabación (ADB propio, Shizuku, micrófono) |
| `AdbWatch`, `BootReceiver` | Avisos y reconexión |
| `TutorialActivity`, `SetupActivity`, `NativePairActivity` | Asistentes de configuración |
| `Crypto`, `AppLock`, `RecordingProvider` | Cifrado, bloqueo biométrico, compartir archivos |
| `Summarizer`, `AutoAnalyzer`, `AnalysisJob`, `CallContext` | IA opcional (Groq) |
| `Db`, `Tasks`, `ScheduleCall`, `Cleanup` | Datos, tareas, llamadas programadas, borrado automático |

## Acta Conexión (`pc/`)
Programa de Go (solo biblioteca estándar). Arranca un servidor HTTP local (solo `127.0.0.1`, con token) que sirve una interfaz web mostrada en una ventana de Edge/Chrome (`--app`). Vigila con `adb devices -l`, activa el modo con `adb tcpip 5555` y se reactiva al reconectar el cable. Descarga `platform-tools` de dl.google.com la primera vez.

## Privacidad
Ver [PRIVACY.md](../PRIVACY.md). El único tráfico de red de la app es a Groq (opcional, con consentimiento) y los enlaces que pulsa el usuario.
