# Guía de instalación

Necesitas: un móvil Android 10 o superior, y para la grabación de las dos voces, un ordenador (Windows recomendado) y el cable USB.

## 1 · Instalar la app
1. En el móvil, abre la [última versión](https://github.com/actaapp-es/acta/releases/latest) y descarga **Acta.apk**.
2. Ábrelo. Android te pedirá permitir «instalar apps de esta fuente»: acéptalo para tu navegador.
   - Si tu Android muestra un aviso de «desarrollador no verificado», pulsa **Instalar de todos modos**.
3. Abre Acta, ponla como **app de teléfono** y concede los permisos. Ya puedes usarla; graba con el micrófono.

## 2 · Activar la grabación de las dos voces
Pulsa **Empezar** en el aviso de la pantalla principal. El tutorial detecta lo que ya has hecho y te lleva a cada ajuste:
1. Opciones de desarrollador (tocar 7 veces el número de versión).
2. Depuración USB.
3. **Acta Conexión** en el ordenador: descarga `ActaConexion-Windows.zip` de la misma página de versiones, extrae y abre `ActaConexion.exe`. Conecta el cable, acepta el aviso del móvil y espera a que todo se ponga en verde.
4. En el móvil, pulsa **Conectar** y haz la prueba de grabación.

Tras **reiniciar el móvil** hay que repetir el paso 3 (1 minuto). Con «Iniciar con Windows» y «Seguir vigilando» activos en Acta Conexión, se activa solo al conectar el cable.

### ¿Sin Windows?
En Mac o Linux, el tutorial te da los dos comandos para la consola (`adb devices` y `adb tcpip 5555`).

## Avisos de Windows
Acta Conexión no está firmado por un editor reconocido, así que SmartScreen puede mostrar «Windows protegió su PC»: elige *Más información › Ejecutar de todas formas*. Comprueba la huella SHA-256 con `SHA256SUMS.txt`:
```powershell
Get-FileHash .\ActaConexion-Windows.zip -Algorithm SHA256
```

## Actualizar
Descarga el nuevo `Acta.apk` e instálalo encima; se conservan tus datos si está firmado con la misma clave.
