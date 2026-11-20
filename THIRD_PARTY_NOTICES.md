# Componentes de terceros

Acta incluye el código fuente de los siguientes componentes en `third_party/`. Cada uno mantiene su licencia.

| Componente | Ubicación | Licencia | Origen |
|---|---|---|---|
| **libadb-android** (cliente ADB) | `third_party/vendor_adb/io/github/muntashirakon` | GPL-3.0-or-later **o** Apache-2.0 (según cada archivo; BSD-3-Clause / MIT en algunos) | https://github.com/MuntashirAkon/libadb-android |
| Clases `android.sun.*` (criptografía de apoyo para ADB) | `third_party/vendor_adb/android/sun` | GPL-2.0 con la **excepción Classpath** (derivadas de OpenJDK; Oracle) | OpenJDK |
| AndroidX Annotation | `third_party/vendor_adb/androidx`, `third_party/vendor/androidx` | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| **Shizuku API** | `third_party/vendor/rikka`, `third_party/vendor/moe`, `third_party/gen_aidl` | MIT / Apache-2.0 | https://github.com/RikkaApps/Shizuku-API |

Acta se distribuye bajo **GPL-3.0-or-later**. Se usa libadb-android bajo su opción GPL-3.0-or-later y los componentes MIT/Apache-2.0 son compatibles con ella. Los archivos derivados de OpenJDK conservan su cabecera de licencia GPL-2.0 con la excepción Classpath, que permite combinarlos con otro software.

> Este resumen es informativo y no es asesoramiento jurídico. Antes de redistribuir, revisa las cabeceras de cada archivo y los repositorios de origen. Si detectas un error en las atribuciones, abre un issue.

Las fuentes tipográficas y los iconos de la app son propios o de uso libre incluidos en el repositorio; la web usa solo tipografías del sistema.
