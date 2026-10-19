# Vinilo Android

Primera base nativa de Vinilo, creada con Kotlin, Jetpack Compose y AndroidX Media3.

## Qué incluye esta primera versión
- Interfaz oscura con acento coral e identidad visual de disco de vinilo.
- Solicitud de permiso y lectura de canciones locales mediante MediaStore.
- Búsqueda por título, artista o álbum.
- Reproducción, pausa, anterior y siguiente.
- Servicio Media3 para reproducción en segundo plano y controles multimedia del sistema.

## Compilar en GitHub
1. Sube todos los archivos de este proyecto al repositorio `infinity-bys/vinilo-android`, manteniendo las carpetas.
2. Abre la pestaña **Actions** del repositorio.
3. Selecciona **Build Vinilo APK** y pulsa **Run workflow**, o espera a que termine la compilación automática tras subir los archivos.
4. En la ejecución completada, descarga el artefacto `vinilo-debug-apk`. Dentro está `app-debug.apk`.

Este APK de depuración sirve para instalar y probar; no es todavía un APK de publicación firmado para Play Store.

## Nota
Esta es una primera base de trabajo. Debe compilarse y probarse en un dispositivo real antes de considerarla lista. Las funciones de favoritos persistentes, listas de reproducción, temporizador, ecualizador y gestión/compartición de archivos aún no están implementadas.
