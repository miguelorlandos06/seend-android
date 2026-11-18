# Seend — Cliente Android (Kotlin + Jetpack Compose)

Cliente nativo de la mensajería Seend, compatible 100% con el servidor
Go que entregamos en `seend-go/`. Reemplaza los prototipos HTML
(`auth.html`, `chat.html`) por una app Android moderna con Material 3.

## ✨ Mejoras respecto a los HTML

| Aspecto | HTML original | App Android |
| --- | --- | --- |
| Arquitectura | scripts sueltos en 2 HTML | MVVM + Repository + Room + WS |
| Estado de UI | variables globales | `StateFlow` reactivo |
| Persistencia | `localStorage` | Room (mensajes, usuarios, sesión) + DataStore |
| Reconexión WS | ninguna | backoff exponencial (hasta 30s) |
| Lista de chats | dependía del tipo `chats` del server | **se construye localmente** desde los mensajes guardados en Room (el server Go no envía `chats`) |
| Avatares | `<img>` con S3 | Coil + `AsyncImage` con cache |
| Subir avatar | `fetch + xhr` | OkHttp `PUT` con compresión JPEG bajo 300KB |
| Escribiendo | `setInterval` client-side | throttle de 1s vía `repo.avisarEscribiendo` |
| Presencia | polling cada 30s | reactiva vía `presencia` + `usuarios` |
| Reply / cita | barra inline | `ReplyBar` reutilizable |
| Tema | modo claro fijo | Material 3 con **dark mode** automático |
| Splash | none | Core SplashScreen (API 12+) con fallback |
| Navegación | links HTML | Compose Navigation con rutas tipadas |
| Estado de conexión | invisible | banner visible bajo la top bar |

## 📁 Estructura

```
seend-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/seend/app/
│       │   ├── SeendApp.kt              # Application + ServiceLocator
│       │   ├── MainActivity.kt          # host de Compose + splash
│       │   ├── data/
│       │   │   ├── model/                # Protocol.kt + Dominio.kt
│       │   │   ├── local/                # Room (entities, DAOs, db)
│       │   │   ├── remote/              # WebSocketClient + ApiClient
│       │   │   └── repo/                # SeendRepository + SessionStore
│       │   ├── ui/
│       │   │   ├── theme/                # Color, Theme, Type, Shapes
│       │   │   ├── components/           # Avatar, MessageBubble, etc.
│       │   │   ├── auth/                # AuthScreen + ViewModel
│       │   │   ├── chatlist/            # ChatListScreen + ViewModel
│       │   │   ├── users/                # UsersScreen + ViewModel
│       │   │   ├── conversation/        # ConversationScreen + ViewModel
│       │   │   └── profile/             # MyProfile + OtherProfile
│       │   ├── nav/                      # Rutas, ConnectionViewModel, NavHost
│       │   └── util/                     # TimeFormat
│       └── res/                          # strings, themes, iconos
├── gradle/
│   └── libs.versions.toml              # version catalog
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## 🔌 Compatibilidad con el servidor Go

El protocolo JSON sobre WebSocket es **idéntico** al del servidor Go
(`internal/ws/handler.go`). Mismos `tipo` de mensajes, mismos
campos, mismos endpoints HTTP. No hay que tocar el servidor para que
esta app funcione.

Detalles importantes que resolvimos en el cliente:

- El servidor Go **no envía el tipo `chats`** ni soporta `marcar_leido`
  (los HTML lo usaban pero el Node original tampoco los implementaba al
  final). La lista de conversaciones se deriva de los mensajes
  guardados en Room con la query `ultimosMensajes` de `MensajeDao`.
- El "leído" es **local**: cuando abres una conversación, los
  mensajes entrantes se marcan `leido=1` en Room y se hace `ack` al
  servidor para que los borre de su cola de pendientes.
- Al recibir `pendientes` o `conversacion`, persistimos en Room y
  mandamos `ack` con los ids, igual que el Node original.
- Los estados (`enviado/entregado/visto`) son informativos: el server
  sólo envía `enviado` o `entregado`. El `visto` no se implementa en
  el server Go, así que la app no lo reclama.

## 🛠️ Compilar

Requisitos:
- Android Studio Koala (2024.1) o superior.
- JDK 17.
- SDK 34 (compileSdk y targetSdk).
- MinSdk 24 (Android 7.0).

### Desde Android Studio

1. `File → Open` y selecciona la carpeta `seend-android/`.
2. Gradle sincroniza las dependencias automáticamente.
3. `Run → Run app` (elige un emulador o un dispositivo físico).

### Desde línea de comandos

```bash
cd seend-android
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:installDebug      # instalar en dispositivo conectado
./gradlew :app:assembleRelease   # release APK (con R8 minify)
```

El APK de debug se genera en
`app/build/outputs/apk/debug/app-debug.apk`.

### Apuntar a otro servidor

Edita `app/build.gradle.kts` → `buildConfigField "SERVER_URL"` o
sobrescríbelo desde `local.properties` con:

```
seend.server_url=http://10.0.2.2:8000
```

El `10.0.2.2` es el alias estándar del host desde el emulador Android.
Para un dispositivo físico, usa la IP LAN de tu PC.

## 🚀 Combinar con el servidor Go

1. Despliega el servidor Go en Render (véase el `seend-go/README.md`).
2. Edita `app/build.gradle.kts` y pone la URL pública de Render en
   `SERVER_URL`.
3. Recompila y firma el APK.

El servidor sigue siendo la fuente de verdad; la app hace caché local
en Room para funcionar sin conexión estable, pero la autenticación y el
envío de mensajes siempre requieren el servidor.

## 🔐 Seguridad / permisos

- `INTERNET` y `ACCESS_NETWORK_STATE` son los únicos permisos.
- Token de sesión persistido en DataStore (no en SharedPreferences
  plano). Nunca se imprime en logs.
- El avatar se comprime a JPEG bajo 300KB antes de subirlo.
- El `network_security_config` permite cleartext sólo para `10.0.2.2`
  y `localhost` (desarrollo local). Render usa HTTPS por defecto.
