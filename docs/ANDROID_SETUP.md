# Configuração do Projeto Android

## Criar o projeto

Android Studio → New Project → **Empty Activity** (Compose) em `android/`.

- Linguagem: Kotlin; build config: Kotlin DSL (`build.gradle.kts`)
- `applicationId`: `com.joaobarreto.listacompras` (builds debug: `.debug` no fim, para coexistirem com a release). `namespace` e pacotes de código: `com.example.listacompras`.
- Min SDK: 26 · Target/Compile SDK: última estável

## Dependências permitidas

Usar `gradle/libs.versions.toml` (version catalog). **Confirmar a última versão estável de cada uma** antes de fixar; não copiar versões de memória.

| Área | Biblioteca |
|---|---|
| UI | Compose BOM, Material 3, `material-icons-extended`, `activity-compose`, `navigation-compose` |
| Lifecycle | `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` |
| DI | Hilt (`hilt-android`, `hilt-compiler`, `hilt-navigation-compose`, `hilt-work`; o `androidx.hilt:hilt-compiler` 1.4.0 é necessário para gerar a factory do `@HiltWorker`) |
| Local | Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP) |
| Rede | Retrofit, `converter-kotlinx-serialization`, OkHttp, `okhttp-sse` (5.5.1, mesma versão do OkHttp), `logging-interceptor` (só debug) |
| Serialização | `kotlinx-serialization-json` |
| Coroutines | `kotlinx-coroutines-android` (fixada na mesma versão dos testes; evita que a app resolva um `core` antigo incompatível com `kotlinx-coroutines-test`) |
| Background | `work-runtime-ktx` 2.12.0 (`androidx.hilt:hilt-work`/`hilt-compiler` 1.4.0; versões estáveis confirmadas no Google Maven) |
| Preferências | `datastore-preferences` |
| Datas | `java.time` (core library desugaring se min SDK < 26 vier a ser necessário) |
| Testes | JUnit, `kotlinx-coroutines-test`, Turbine 1.2.1, MockWebServer, `room-testing`, `androidx.compose.ui:ui-test-junit4` |

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

## Build release assinada

- Keystore: `/var/home/behonder/keystores/lista-compras-release.jks` (fora do repositório; chave RSA 4096, validade ~27 anos). Palavras-passe em `android/keystore.properties` (ignorado pelo git; chaves `storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
- **Fazer cópia de segurança da keystore e do `keystore.properties`**, como os backups do servidor. Sem a keystore não é possível atualizar a app por cima: só desinstalando (perde dados locais e login).
- Sem `keystore.properties`, `assembleDebug` funciona e `assembleRelease` falha com uma mensagem clara.
- Cada nova versão: subir `versionCode` (e `versionName`) em `app/build.gradle.kts`.
- Sem minificação (R8) na release: app instalada à mão por duas pessoas, e o R8 pode partir serialização/Retrofit em execução sem que os testes deem por isso.
- Comando e resultado:
  ```bash
  ./gradlew assembleRelease
  adb install -r app/build/outputs/apk/release/app-release.apk
  ```
- Primeira instalação: desinstalar a app debug antiga (`com.example.listacompras`); esperar que o estado seja "Sincronizado" antes, para não perder alterações pendentes.


## JDK e adb nesta máquina

Não há Java nem `adb` no PATH do anfitrião. Estão na home do distrobox `androidstudio`:

- `JAVA_HOME=/home/behonder/distrobox/home/androidstudio/android-studio/jbr`
- `adb`: `/home/behonder/distrobox/home/androidstudio/Android/Sdk/platform-tools/adb`

Basta exportar `JAVA_HOME` e correr `./gradlew` no anfitrião (sem `distrobox enter`).
