# Lista de Compras

App Android de listas partilhadas por duas pessoas: **Compras** e **Tarefas da casa**. Funciona **offline** e sincroniza com um servidor [PocketBase](https://pocketbase.io) próprio, acessível por [Tailscale](https://tailscale.com).

## Funcionalidades

- Duas listas fixas, em dois separadores: **Compras** (abre por omissão) e **Tarefas** ("Limpar grelhador", "Arrancar a relva"…).
- Adição rápida de itens, toque para marcar, deslizar para apagar, "Limpar comprados / feitas".
- **Offline-first:** a interface lê sempre da base de dados local. Tudo o que se faz sem rede fica numa fila e é enviado quando há ligação, sem duplicar itens.
- **Tempo real:** o que uma pessoa altera aparece no outro telemóvel em segundos (Server-Sent Events).
- Indicador de sincronização (Sincronizado / A sincronizar / Offline / Com erros), com o aviso para verificar o Tailscale quando não há ligação.
- Modo escuro, tema dinâmico, suporte a leitores de ecrã (TalkBack) e ícone adaptativo/monocromático.
- Interface em português europeu.

## Como funciona

```
UI (Compose) ─► ViewModel ─► Repositório ─► Room (fonte de verdade local)
                                   │
                          Outbox (operações pendentes)
                                   │
              SyncWorker / Realtime ─► Retrofit + OkHttp ─► PocketBase (HTTPS via Tailscale)
```

- Os IDs dos itens são gerados no cliente (15 caracteres `[a-z0-9]`), para criar itens offline.
- Cada escrita atualiza a tabela local e a fila de operações na mesma transação. A fila é fundida (`CREATE+UPDATE → CREATE`, `CREATE+DELETE → nada`) e enviada por ordem.
- O pull completo reconcilia o servidor com o local, sem apagar itens com operações pendentes.
- O realtime é SSE (não WebSocket), com reconexão e *backoff*, e novo pull depois de cada reconexão.

Detalhes em [`docs/`](docs/): [ARCHITECTURE](docs/ARCHITECTURE.md), [DATA_MODEL](docs/DATA_MODEL.md), [API](docs/API.md), [SERVER_SETUP](docs/SERVER_SETUP.md), [ANDROID_SETUP](docs/ANDROID_SETUP.md) e [ROADMAP](docs/ROADMAP.md).

## Tecnologias

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · Retrofit/OkHttp (`okhttp-sse`) · kotlinx.serialization · WorkManager · DataStore · JUnit, Turbine, MockWebServer.

Sem o SDK do PocketBase para Kotlin. `minSdk` 26.

## Estrutura

```
.
├── android/    # Projeto Android (módulo app)
├── docs/       # Arquitetura, modelo de dados, API, setup, roadmap
└── CLAUDE.md   # Instruções para a IA que trabalha no repositório
```

O servidor PocketBase corre à parte (Docker); a sua configuração está descrita em [`docs/SERVER_SETUP.md`](docs/SERVER_SETUP.md). O modelo de dados e as regras de acesso estão em [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).

## Servidor

1. PocketBase com as coleções `users`, `lists` e `items` e as regras de [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md). Sem registo público: as duas contas são criadas à mão.
2. Duas listas na coleção `lists`, criadas nesta ordem: **Compras** e depois **Tarefas**. A app distingue-as pela ordem de criação.
3. Exposto só na rede privada, com HTTPS: `tailscale serve --bg --https=8443 8090`. Nada de Tailscale Funnel nem de portas abertas à internet.

## Compilar a app

Requisitos: JDK 17+ (ou o JBR do Android Studio) e o Android SDK.

1. Criar `android/local.properties` (ignorado pelo git) com o endereço do servidor, **terminado em `/`**:
   ```properties
   sdk.dir=/caminho/para/Android/Sdk
   SERVER_URL=https://<maquina>.<tailnet>.ts.net:8443/
   ```
2. A partir de `android/`:
   ```bash
   ./gradlew assembleDebug          # APK de debug (applicationId com sufixo .debug)
   ./gradlew testDebugUnitTest      # testes unitários
   ./gradlew lintDebug              # lint
   ```
3. Instalar: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Release assinada

1. Criar uma keystore **fora do repositório** e um `android/keystore.properties` (ignorado pelo git):
   ```properties
   storeFile=/caminho/para/keystores/lista-compras-release.jks
   storePassword=...
   keyAlias=lista-compras
   keyPassword=...
   ```
2. Subir `versionCode` (e `versionName`) em `android/app/build.gradle.kts`.
3. `./gradlew assembleRelease` e instalar `app/build/outputs/apk/release/app-release.apk` com `adb install -r`.

**Guardar uma cópia da keystore e do `keystore.properties`.** Sem eles não é possível atualizar a app por cima: só desinstalando, o que apaga os dados locais e o login. Sem `keystore.properties`, o `assembleDebug` funciona e o `assembleRelease` falha com uma mensagem clara.

## Segredos

Nada disto entra no repositório: URL do servidor, palavras-passe, keystore e `keystore.properties` (todos no `.gitignore`). Os logs não registam tokens, palavras-passe, nomes de itens nem o endereço do servidor; os de diagnóstico só existem em builds de debug.

## Estado

O MVP está feito e instalado. Falta apenas validar com dois telemóveis ao mesmo tempo, com dados móveis e com rede instável. Ver [`docs/ROADMAP.md`](docs/ROADMAP.md).
