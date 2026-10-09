# FLAC · Prueba 0.4

## Instalación

Abre `FlacPrueba-0.4.apk` desde Archivos en Android y acepta actualizar la prueba anterior. Conserva el mismo identificador y certificado de desarrollo de 0.1. No requiere desinstalarla.

1. Abre **Configuración → Escaneo de música → Elegir carpeta**.
2. Elige una carpeta local donde estén tus FLAC y confirma el acceso. Se incluyen sus subcarpetas. Android puede impedir elegir la raíz de almacenamiento o Descargas: elige una subcarpeta, por ejemplo Música/FLAC.
3. Espera a que termine el escaneo. La Biblioteca mostrará los archivos encontrados. **Volver a escanear** actualiza el catálogo cuando agregues o muevas música. No se recorre todo el teléfono ni se reescanea automáticamente al iniciar.
4. Toca una canción para reproducirla. Toca el vinilo del minirreproductor para abrir la pantalla completa.
5. En esa pantalla, toca **⋮ → Añadir archivo de letras** y elige el LRC de esa misma grabación. No se escanean ni se asocian letras automáticamente.
6. Toca **Letras** para mostrarlas. Si hace falta, ajusta su tiempo en pasos de medio segundo. La importación conserva una copia privada por canción.

## Cambios de la versión 0.4

- El reproductor distribuye portada, información, progreso, controles y Letras a lo largo del área segura disponible. La portada conserva su proporción cuadrada. La fila inferior queda cerca del borde seguro; en horizontal se utilizan dos columnas.
- Al minimizar, los componentes se recogen hacia el centro del vinilo. Al abrirlo, la transición se invierte. Se conserva la pantalla elegida de portada o letras.
- Al tocar Letras, la portada se retira suavemente y las líneas aparecen de forma gradual. Al cerrar, regresa la portada. El nombre de la canción, el progreso y los controles mantienen su posición.
- Atrás cierra primero las letras si están abiertas y después minimiza el reproductor. Los tres puntos mantienen una sola opción: Añadir archivo de letras.
- Las transiciones respetan la preferencia de movimiento reducido y se estabilizan cuando cambia el tamaño de pantalla. Las animaciones no invocan pausa, salto ni cambio de pista.
- Se conservan las correcciones de 0.3: áreas seguras nativas y carga local de portadas incrustadas en biblioteca y reproductor.

## Incluido

- Biblioteca, Playlists y Configuración en cápsula inferior.
- Minirreproductor con vinilo que gira al reproducir; apertura de reproducción independiente.
- Estilos Sólido y Liquid Glass, claro/oscuro y cinco colores.
- Cristal oscuro transparente sin reflejo blanco transversal. Halo solo en la sección activa y transiciones al navegar.
- Portadas incrustadas solicitadas al mostrar la biblioteca y compartidas con el minirreproductor y la pantalla completa. Lectura directa del bloque PICTURE del FLAC, etiquetas de imagen compatibles y alternativa con el lector de Android. Si no hay imagen compatible, fondo abstracto.
- Barra sin círculo; tiempo transcurrido y tiempo restante, avance mediante toque/arrastre, anterior/siguiente.
- Escaneo acotado a la carpeta autorizada y sus subcarpetas; acceso persistente, catálogo local y reescaneo manual. Solo nombres que terminan en `.flac` sin distinguir mayúsculas.
- LRC estándar local con asociación manual, persistencia, ajuste temporal y salto al tocar una línea.
- Playlist Favoritas mediante el corazón de cada canción. Listas personalizadas todavía pendientes.

## Límites de esta prueba

La reproducción se pausa al salir de la aplicación, bloquearla o abrir el selector de archivos. El servicio de reproducción en segundo plano sigue pendiente.

Objetivo Android 8.1/API 27 o posterior, SDK 35. El efecto visual necesita una versión suficientemente reciente de Android System WebView. La reproducción FLAC usa el decodificador del sistema; no se garantiza cualquier variante ni salida bit-perfect. No se ha ejecutado esta APK en un dispositivo físico o emulador Android.

Se admiten hasta 20000 FLAC por escaneo y letras de hasta 2 MB/5000 marcas. Los metadatos se leen durante el escaneo, lo que puede tardar con carpetas grandes. Un fallo de acceso impide completar el escaneo y se informa en Configuración. Mover un archivo puede cambiar su identificador y exigir volver a asociar el LRC. La firma conserva preferencias de 0.1, pero el acceso nuevo por carpeta puede dar identificadores distintos de los archivos antes seleccionados individualmente.

## Privacidad y funcionamiento

Sin permiso de Internet, servidores, anuncios o analítica. Audio y escaneo usan APIs nativas de Android. La interfaz HTML/CSS/JS está empaquetada dentro del APK y se muestra en WebView; no es una web remota. Navegaciones y cargas de red se bloquean. El puente nativo solo se expone al documento empaquetado. Los metadatos se presentan como texto, no como HTML.

La app únicamente toma permiso de lectura sobre la carpeta seleccionada y sobre el LRC elegido. No modifica música ni letras originales. Guarda el catálogo, las preferencias, favoritos y copias privadas de letras en el dispositivo. Al desinstalar se borran esos datos privados.

## Verificación realizada

- Compilación Java/DEX contra SDK 35, empaquetado y firma APK v2/v3 verificados. versionCode 4 y versionName 0.4.0.
- El certificado coincide con las pruebas anteriores, por lo que se instala como actualización y conserva los datos de la app.
- Pruebas de la interfaz empaquetada en Chromium con puente nativo simulado: apertura y minimización hacia el vinilo; animación de letras; retroceso; pulsaciones rápidas; letra activa y salto por línea; ajuste temporal; menú de importación; carpeta y reescaneo; callbacks de reproducción; movimiento reducido.
- Comprobación de la distribución en 320×640, 390×844, 430×900 y 736×390, además del tamaño inicial 390×816. Portada cuadrada, controles dentro del área visible y sin desplazamiento al mostrar letras. Inspección visual en modo claro y oscuro.
- Los lectores nativos de FLAC y LRC son los mismos que en 0.3. Sus pruebas se incluyen en tests/.
- No se ha ejecutado este APK en un teléfono o emulador Android. Queda comprobar la fluidez, instalación y funcionamiento completo en el dispositivo real.

Para repetir las pruebas de interfaz: instala Playwright y ejecuta `node tests/ui-smoke.cjs`. Puedes indicar un Chromium existente con `FLAC_BROWSER=/ruta/al/navegador`. `FLAC_SCREENSHOTS` permite guardar capturas en una carpeta existente.

## Compilar

Android Studio: abre la carpeta, usa JDK 17 y SDK 35. `gradlew.bat assembleDebug` (Windows) o `./gradlew assembleDebug` (Linux/macOS). Gradle 8.9, AGP 8.7.3. Esta ruta está preparada; el APK entregado se produjo con `build-local.sh`.

`build-local.sh` requiere Java 17, ECJ 3.38.0 y build-tools/platform 35. Define FLAC_ANDROID_JAR, FLAC_BUILD_TOOLS y FLAC_ECJ_JAR con rutas absolutas y ejecútalo. No es necesario instalar esos componentes en el teléfono.

Se incluye la clave de desarrollo `signing/debug.keystore` (contraseña convencional `android`) para actualizar las pruebas. Es solo una clave de pruebas; una publicación debe utilizar una clave de producción propia.


## Reproducción en segundo plano (actualización)

Se añadió `PlaybackService` como servicio en primer plano de tipo `mediaPlayback`. La actividad ya no pausa automáticamente al entrar en `onStop`. Mientras reproduce, se muestra una notificación con control de pausa y detener. El reproductor sobrevive a la destrucción de la actividad mientras la música continúa. Al volver a abrir, se recupera el reproductor activo. Requiere probar en un dispositivo Android real; no se compiló un APK en esta entrega.
