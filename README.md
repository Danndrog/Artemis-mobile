# Artemis Mobile — prototipo 0.2

Aplicación Android nativa para Android 11+ (API 30), inspirada en un flujo de agente de automatización. No es una aplicación oficial de Google ni un port directo del repositorio ARTEMIS.

## Funcionalidad implementada en el código
- UI nativa para introducir una instrucción, API key y nombre del modelo.
- Solicitud HTTPS al endpoint OpenAI Chat Completions.
- Captura local de texto accesible de la pantalla mediante AccessibilityService.
- El texto de pantalla solo se envía a la IA si el usuario marca expresamente la casilla de consentimiento.
- La IA puede proponer una acción por turno: `tap_text`, `type_text`, `back`, `home`, `scroll_forward` o `none`.
- Confirmación explícita antes de ejecutar la acción propuesta.
- La API key se mantiene en memoria y no se guarda en preferencias; se pierde al cerrar la app.

## Requisitos de compilación
- Android Studio, JDK 17, Android SDK Platform 35.
- Android Gradle Plugin 8.7.3 y Kotlin 2.0.21.
- Abrir la carpeta del proyecto en Android Studio, sincronizar Gradle y ejecutar en un dispositivo/emulador. Crear APK desde Build > Build APK(s).

## Configuración
1. Proporciona una API key válida para `https://api.openai.com/v1/chat/completions`.
2. Usa un modelo disponible para tu cuenta y compatible con Chat Completions.
3. Activa manualmente el servicio de accesibilidad solo si aceptas que la app pueda leer texto de la pantalla para automatizar tareas.
4. Para enviar el contexto de pantalla a la IA, marca la casilla de consentimiento en la app.
5. Revisa cada JSON propuesto y pulsa Confirmar y ejecutar. La app ejecuta solo una acción por turno.

## Limitaciones y seguridad
- No se ha compilado ni probado en hardware en esta entrega. El comportamiento de accesibilidad varía según cada app y MIUI.
- Este prototipo llama directamente a OpenAI desde el teléfono. Aunque la key no se persiste en la app, una petición en el dispositivo sigue teniendo riesgos; para producción es preferible un backend HTTPS propio con autenticación, cuotas y límites.
- No automatiza pagos, compras, contraseñas, mensajes, cambios de seguridad ni acciones destructivas; revisa siempre las propuestas.
- El texto visible puede contener información sensible. La transmisión requiere consentimiento en cada sesión; evita usarlo en apps bancarias, gestores de contraseñas o pantallas privadas.
- No incluye ADB embebido, MCP completo ni todas las capacidades del ARTEMIS original.


## Compilación en GitHub Actions
El workflow `.github/workflows/build-apk.yml` permite compilar el APK de depuración automáticamente después de subir el proyecto a un repositorio GitHub y ejecutar Actions. El APK se publica como artefacto de la ejecución. Revisa y prueba el artefacto antes de instalarlo.
