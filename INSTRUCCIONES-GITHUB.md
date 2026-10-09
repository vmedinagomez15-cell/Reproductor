# FlacPrueba 0.5: compilar APK con GitHub Actions

1. En GitHub, crea un repositorio nuevo (recomendado: privado), por ejemplo `FlacPrueba`.
2. Sube **el contenido extraído** de este ZIP a la raíz del repositorio, incluyendo la carpeta oculta `.github/workflows/` (no subas solamente el ZIP).
3. Abre la pestaña **Actions** de ese repositorio.
4. Selecciona **Compilar APK FlacPrueba 0.5** y pulsa **Run workflow** en la rama `main` (o `master`). También se ejecuta al subir cambios a esas ramas.
5. Espera a que termine en verde. En la ejecución, busca **Artifacts** y descarga `FlacPrueba-v0.5-APK`.
6. Descomprime el archivo descargado: dentro encontrarás `app-debug.apk`. Instálalo en tu Android.

**Nota:** El APK está firmado con una clave de prueba. No es una versión de publicación para Play Store. La compilación y el funcionamiento en dispositivos reales todavía no han sido verificados.

**Si no aparece Run workflow:** confirma que el archivo `.github/workflows/build-apk.yml` está en la rama predeterminada.
