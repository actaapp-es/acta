# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [5.1.7] · 2026-11-21
### Añadido
- Aviso de versiones nuevas (versión completa): una vez al día Acta consulta la API pública de GitHub y, si hay una versión más reciente, te lo notifica con un enlace a la página oficial. No envía datos tuyos (GitHub solo ve la IP). Se desactiva en Ajustes › «Avisar de versiones nuevas».

## [5.1.6] · 2026-11-20
### Seguridad
- La clave de Groq se guarda ahora cifrada con el Android Keystore (antes, en claro dentro de los datos privados de la app). Las claves guardadas con versiones anteriores se migran solas.
- Se quitó de un comentario del código un número de teléfono de ejemplo con formato real.
### Añadido
- En Ajustes: enlaces a Instagram y TikTok de Acta y un apartado de donaciones voluntarias con Bitcoin (solo en la versión completa).

## [5.1.5] · 2026-11-20
### Añadido
- Al instalar, el tutorial se abre solo. Si sales de él puedes usar la app, pero un aviso amarillo «Termina el tutorial de instalación» permanece hasta completarlo.

## [5.1.4] · 2026-11-20
### Añadido
- El tutorial pide primero «Permitir ajustes restringidos» (Android 13+), necesario para poder fijar Acta como app de teléfono cuando se instala fuera de la tienda.

## [5.1.3] · 2026-11-20
### Corregido
- El botón «Establecer» de app de teléfono predeterminada del tutorial y del aviso inicial: si Android no muestra el diálogo (p. ej. tras rechazarlo), abre los ajustes de apps predeterminadas.
- El enlace de Acta Conexión del tutorial ahora se abre al tocarlo y lleva a la página de la última versión.

## [5.1.2] · 2026-11-20
### Corregido
- La app se cerraba al abrirla si no se había concedido el permiso de contactos. Ahora se abre siempre y ofrece conceder los permisos desde el aviso superior.

### Añadido
- Pantalla de error: si la app falla, muestra el motivo exacto y permite copiarlo para el aviso de errores. El texto no sale del teléfono.

## [5.1] · 2026-11-20
### Añadido
- Proyecto de código abierto (GPL-3.0-or-later), publicado en GitHub con compilación y versiones automáticas.
- El tutorial se integra con **Acta Conexión**, el programa de Windows que activa la grabación y la reactiva tras reiniciar el móvil. Se mantiene la vía manual (adb).
- Enlace al código fuente en Ajustes.

### Cambiado
- Las descargas del tutorial apuntan al repositorio del proyecto.

## [5.0] y anteriores
- Borrado programado de audios, borrado en lote y favoritos.
- Tutorial inteligente que detecta el progreso; acceso sin bloqueos al instalar desde cero.
- Grabación de ambas voces con cliente ADB propio, IA opcional (Groq), buscador, ficha de persona, llamadas programadas, doble SIM, widgets.
