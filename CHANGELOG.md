# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

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
