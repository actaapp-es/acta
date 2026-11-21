# Política de privacidad de Acta

Última actualización: 20 de noviembre de 2026

Acta es software libre. Esta política explica qué datos trata la aplicación, dónde se guardan y cuándo salen de tu teléfono.

## En resumen
Todo se guarda en tu móvil. El proyecto no tiene servidores ni cuentas de usuario, y no recibe tus grabaciones, contactos ni transcripciones.

## Datos que Acta guarda en tu teléfono
- Grabaciones de llamadas, **cifradas** con AES-256-GCM con una clave protegida por Android Keystore.
- Datos de cada llamada: número, nombre del contacto, fecha, duración, SIM y notas.
- Transcripciones, resúmenes, tareas y contexto generados.
- Ajustes de la app (incluida, si la introduces, tu clave de Groq, en el almacenamiento privado de la app).

Estos datos permanecen en tu teléfono hasta que los borres. Al desinstalar la app, Android los elimina.

## IA (opcional)
Las funciones de transcripción, resumen, tareas, contexto y «Pregunta a Acta» solo funcionan si introduces tu propia clave de Groq y aceptas el aviso que muestra la app. Al analizar una llamada, la app envía a Groq, Inc., por conexión cifrada (HTTPS), el audio de esa llamada, su transcripción y, para el contexto, fragmentos de llamadas anteriores con la misma persona. **Nunca se envía el número de teléfono.** El tratamiento por parte de Groq se rige por su propia política. Sin clave, nada sale del móvil.

## Conexiones de red
- A Groq, solo si usas la IA.
- Acta Conexión (programa de Windows) descarga una vez las herramientas oficiales de Android (adb) desde dl.google.com. No envía datos a ningún sitio.
- La app puede abrir enlaces (GitHub, Telegram) solo cuando pulsas en ellos.

## Permisos
Teléfono, registro de llamadas, contactos, micrófono, notificaciones y, para las dos voces, la depuración inalámbrica local que tú activas. Se usan únicamente para el funcionamiento descrito.

## Tus derechos
Tus datos están en tu dispositivo: puedes verlos, exportarlos y borrarlos desde la app, o desinstalarla.

## Contacto
Abre un issue en el repositorio del proyecto o escribe al canal de Telegram https://t.me/actaapp.
