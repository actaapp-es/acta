# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

## [5.7.0] · 2026-11-23
### Cambiado
- **«Hola Acta» sin pitidos ni micrófono parpadeando.** La escucha continua ya no usa el reconocedor de voz de Android (que pita y se reinicia cada pocos segundos). Ahora mantiene un único flujo de micrófono, estable, y reconoce la frase en el propio móvil comparándola con **tu voz**.
### Añadido
- **Grabar mi voz** (Ajustes → Agente de voz): dices «Hola Acta» 5 veces y el móvil aprende a reconocerla, sin conexión. Hace falta antes de activar la escucha continua.
- Cuando la frase encaja, un fragmento corto se envía a Groq para confirmar que de verdad dice «Hola Acta» antes de hacer nada (límite de 40 confirmaciones por hora). Si no lo confirma, no pasa nada.

## [5.6.0] · 2026-11-23
### Cambiado
- **El micrófono ya no se queda escuchando de fondo por defecto.** La escucha continua de «Hola Acta» pasa a ser opcional y experimental (Ajustes → Agente de voz), porque con el reconocimiento de voz de Android el micrófono se abre y se cierra cada pocos segundos.
### Añadido
- **Hablar con Acta bajo demanda, sin escuchar de fondo:** mosaico «Hablar con Acta» en los ajustes rápidos y acceso directo al mantener pulsado el icono. Abre el micrófono solo un momento para una orden (llamar, guardar contacto o programar llamada) y lo cierra.

## [5.5.2] · 2026-11-23
### Corregido
- **El micrófono ya no se abre y se cierra sin parar.** Los reinicios de la escucha podían encadenarse en ráfagas; ahora hay un único reinicio pendiente y un mínimo de espera entre uno y otro.
### Añadido
- **Escuchar solo con la pantalla encendida** (activado por defecto, en Ajustes → Agente de voz): con la pantalla apagada el micrófono queda libre y se gasta menos batería. Se puede desactivar para llamar con el móvil bloqueado.

## [5.5.1] · 2026-11-23
### Corregido
- **«Hola Acta» ya no se activa solo.** Ahora solo vale la frase completa «Hola Acta» al principio de lo que se dice y con confianza suficiente del reconocedor (antes bastaba un fragmento parcial o una variante parecida). Si no se oye una orden después, no hace ruido ni da ningún aviso.

## [5.5.0] · 2026-11-23
### Añadido
- **Agente de voz «Hola Acta»** (Ajustes → Agente de voz): sin abrir la app, di «Hola Acta» y pide *llama a…*, *guarda a… con el número…* o *avísame para llamar a… dentro de X horas*. La frase de activación se detecta en el propio móvil con el reconocimiento de voz del sistema; solo después de oírla se envía tu orden (audio o texto) a Groq, que entiende la intención y Acta la ejecuta. Responde en voz alta. Incluye *Probar ahora* y un botón *Hablar ahora* en el aviso fijo, para pedir algo sin decir la frase.
- Necesita tu clave de Groq y los permisos de micrófono, contactos (leer y guardar) y teléfono. Mientras estás en una llamada se pausa.
- Tras reiniciar el móvil, Android 14 o superior puede impedir que arranque solo: se reanuda al abrir Acta.

## [5.4.0] · 2026-11-23
### Añadido
- **Control de llamadas perdidas:** al perder una llamada llega un aviso con *Llamar*, *En 15 min* y *Más* (llamar, WhatsApp o historial). En Recientes aparece un aviso fijo con los números que te llamaron y aún no has devuelto, y una pantalla con todos (últimos 14 días) para llamar, escribir por WhatsApp, programar o descartar. Dejan de estar pendientes en cuanto hablas con esa persona. Se puede desactivar en Ajustes → Llamadas y spam.
- **Programar una llamada desde el teclado:** escribe un número, mantén pulsado sobre él y elige *Programar llamada*.

## [5.3.0] · 2026-11-23
### Añadido
- **Velocidad de reproducción:** un botón junto al reproductor de cada llamada cambia entre 1×, 1,5× y 2× sin alterar el tono de la voz. Se recuerda la última elegida.
- **Foto de perfil por contacto:** desde la ficha de la persona (o tocando su avatar) puedes elegir una imagen. Se ve en el historial, los contactos, el detalle de la llamada y la pantalla de llamada.
- **Fondo de la pantalla de llamada por contacto:** cada persona puede tener su propia imagen de fondo cuando llama o la llamas.
- Las imágenes se guardan solo en el almacenamiento privado de Acta y no modifican los contactos del teléfono.

## [5.2.3] · 2026-11-22
### Añadido
- **Menú al pulsar un contacto del widget:** en vez de llamar directamente, sale un desplegable con *Llamar*, *Enviar WhatsApp* y *Ver historial* de ese contacto.
- **Widget de contacto 1×1:** un solo contacto en una celda, con el mismo desplegable al pulsarlo.

## [5.2.2] · 2026-11-22
### Mejorado
- **Límite de uso de Groq:** cuando el plan gratuito alcanza su límite, Acta lee cuánto hay que esperar, lo muestra («Reintento automático en 12 min») y vuelve a intentarlo sola, hasta 15 minutos de espera. Si Groq pide más, indica cuánto falta.
- **Sin repetir la transcripción:** si el resumen falla después de transcribir, el siguiente intento reutiliza la transcripción ya hecha (se guarda unas horas en la memoria privada de la app) y no vuelve a gastar minutos de audio.

## [5.2.1] · 2026-11-21
### Eliminado
- **Aviso de versiones nuevas:** la app ya no consulta GitHub ni ningún otro servidor para buscar actualizaciones. Ahora Acta solo se conecta a internet si activas la IA con tu clave de Groq. Al actualizar se cancela la tarea programada de la versión anterior.

## [5.2.0] · 2026-11-21
### Añadido
- **Añadir a contactos tras la llamada:** si alguien que no está en tus contactos se presenta en la conversación, al terminar el análisis con IA aparece una notificación para añadirlo con su nombre y número ya rellenados (también en «Añadir a contactos» de la ficha de la llamada). Necesita la IA activada.
- **Botón «Contexto» durante la llamada:** resumen en pantalla de quién es esa persona según vuestras llamadas anteriores (de qué habéis hablado y qué queda pendiente). Funciona sin conexión.
- **«Llamando…»** en pantalla mientras suena una llamada saliente (antes mostraba «Elige la tarjeta» por error).
- **Reducir el peso de grabaciones antiguas** (Ajustes › Almacenamiento).
### Mejorado
- **Audios mucho más ligeros:** la grabación con ambas voces se guarda en AAC mono de 24 kbps (unas 10 veces menos que antes) en lugar de WAV sin comprimir; las demás rutas también bajan de bitrate. La IA sigue distinguiendo quién habla y admite llamadas más largas.
### Corregido
- Con Bluetooth conectado, al pasar a altavoz el botón de audio vuelve a ofrecer Bluetooth.

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
