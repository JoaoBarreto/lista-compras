# Configuração do Projeto Android

## Criar o projeto

Android Studio → New Project → **Empty Activity** (Compose) em `android/`.

- Linguagem: Kotlin; build config: Kotlin DSL (`build.gradle.kts`)
- Package: `com.example.listacompras` (confirmar/alterar)
- Min SDK: 26 · Target/Compile SDK: última estável

## Dependências permitidas

Usar `gradle/libs.versions.toml` (version catalog). **Confirmar a última versão estável de cada uma** antes de fixar; não copiar versões de memória.

| Área | Biblioteca |
|---|---|
| UI | Compose BOM, Material 3, `material-icons-extended`, `activity-compose`, `navigation-compose` |
| Lifecycle | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` |
| DI | Hilt (`hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `hilt-work`) |
| Local | Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP) |
| Rede | Retrofit, `converter-kotlinx-serialization`, OkHttp, `okhttp-sse`, `logging-interceptor` (só debug) |
| Serialização | `kotlinx-serialization-json` |
| Background | `work-runtime-ktx` |
| Preferências | `datastore-preferences` |
| Datas | `java.time` (core library desugaring se min SDK < 26 vier a ser necessário) |
| Testes | JUnit, `kotlinx-coroutines-test`, Turbine, MockWebServer, `room-testing`, `androidx.compose.ui:ui-test-junit4` |

Plugins: `com.android.application`, `O AGP 9 inclui o Kotlin embutido; não aplicar o plugin kotlin.android.`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`.

## Estrutura de pacotes

```
com.example.listacompras
├── ListaComprasApp.kt              # @HiltAndroidApp
├── MainActivity.kt
├── di/                             # Módulos Hilt (Network, Database, Repository, Dispatchers)
├── domain/
│   ├── model/                      # ShoppingItem, Category, SyncState, SyncStatus
│   └── repository/                 # ItemRepository, AuthRepository (interfaces)
├── data/
│   ├── local/                      # AppDatabase, ItemDao, PendingOpDao, entities
│   ├── remote/
│   │   ├── PocketBaseApi.kt
│   │   ├── dto/
│   │   ├── AuthInterceptor.kt
│   │   └── realtime/RealtimeClient.kt
│   ├── sync/                       # SyncWorker, OutboxCoalescer, Reconciler
│   ├── auth/                       # TokenStore (DataStore + Keystore)
│   └── repository/                 # ItemRepositoryImpl, AuthRepositoryImpl
└── ui/
    ├── theme/
    ├── navigation/                 # NavHost, rotas
    ├── login/                      # LoginScreen, LoginViewModel
    └── shopping/                   # ShoppingScreen, ShoppingViewModel, componentes
```

## Configuração por ambiente

`android/local.properties` (no `.gitignore`):

```
SERVER_URL=https://<maquina>.<tailnet>.ts.net:8443/
```

`app/build.gradle.kts`: lê a propriedade e expõe-na via `buildConfigField("String", "SERVER_URL", "\"...\"")`, com `buildFeatures { buildConfig = true }`. A URL termina em `/` (requisito do Retrofit).

O build falha com uma mensagem clara se `SERVER_URL` faltar em `local.properties` ou não terminar em `/`.

## Manifest

- `<uses-permission android:name="android.permission.INTERNET"/>`
- `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>`
- Não definir `usesCleartextTraffic` (o servidor é HTTPS).
- `android:name=".ListaComprasApp"` na `<application>`.

## Rede

- Um único `OkHttpClient` para REST (timeouts normais) e um derivado com `readTimeout(0)` para SSE.
- `AuthInterceptor` adiciona `Authorization`.
- Logging só em builds debug e **nunca** com o corpo do login em claro.

## Build e instalação

### Debug (desenvolvimento e uso pessoal)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Em alternativa, enviar o `.apk` ao outro telemóvel e permitir "instalar de fontes desconhecidas" para a app que o abre.

### Release assinada

1. Gerar keystore (guardar fora do repositório e fazer cópia de segurança):
   ```bash
   keytool -genkey -v -keystore lista-compras.jks -keyalg RSA -keysize 2048 -validity 10000 -alias lista
   ```
2. `android/keystore.properties` (no `.gitignore`): `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
3. Configurar `signingConfigs.release` a ler esse ficheiro; `./gradlew assembleRelease`.
4. Manter **sempre a mesma keystore**: atualizações só instalam por cima se a assinatura for igual.

### Autonomia e bateria (Samsung)

Se o sync em segundo plano falhar, verificar em Definições → Bateria que a app não está em "dormir"/otimização agressiva.
