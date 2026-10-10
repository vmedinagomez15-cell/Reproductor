# FlacPrueba 5.4

- Biblioteca con pestañas Canciones y Álbumes; búsqueda y ordenamiento solo en Canciones.
- Carrusel 3D y lista expandible de pistas únicamente en Álbumes.
- Disco de vinilo centrado sobre la portada con animación de entrada.
- Nuevo tema Agua Cristal, fondo difuminado animado y respuesta táctil tenue; respeta reducción de movimiento.
- Solicitud de permiso de notificaciones Android 13+ para controles multimedia.
- Notificación multimedia con anterior, pausa/reanudar y siguiente. Servicio de reproducción en primer plano.
- Fecha de incorporación preservada durante nuevos escaneos de la misma URI.

## Nota sobre Android
No existe un permiso general de “reproducción en segundo plano”. El permiso de notificaciones permite mostrar controles; el servicio de tipo mediaPlayback mantiene el audio mientras la app pasa a segundo plano. Las restricciones de batería pueden depender del fabricante.

## Instalación
Sube el contenido del ZIP a una rama nueva y ejecuta GitHub Actions, seleccionando la rama de la versión 5.4. El APK generado conserva el mismo applicationId y la firma de depuración del proyecto.

## Validación pendiente
Se verificó sintaxis JavaScript; no se pudo compilar APK en este entorno por falta de SDK de Android. Verifica la compilación en GitHub Actions y prueba la notificación, vinilo y carrusel en un dispositivo real.
