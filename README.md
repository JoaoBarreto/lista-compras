# Life Manager

App Android pessoal do casal (antes "Lista de Compras"): **Compras**, **Tarefas da casa** e **Calendário** com eventos coloridos por tipo, lembretes e avisos. Ver [DECISIONS](docs/DECISIONS.md). Funciona **offline** e sincroniza com um servidor [PocketBase](https://pocketbase.io) próprio, acessível por [Tailscale](https://tailscale.com).

## Funcionalidades

- Três separadores: **Compras** (abre por omissão), **Tarefas** ("Limpar grelhador", "Arrancar a relva"…) e **Calendário**.
- Adição rápida de itens, toque para marcar, **toque longo para editar** (nome e, nas Compras, quantidade), deslizar para apagar (com confirmação), "Limpar comprados / feitas". Quantidade opcional nas Compras. As edições também funcionam offline e chegam ao outro telemóvel.
- **Calendário:** grelha mensal (segunda-feira primeiro) com pontos nas cores dos tipos de evento, e agenda do mês por baixo. A barra do mês (anterior / seguinte) fica fixa no topo enquanto se faz scroll. Eventos com hora ou de dia inteiro, notas, e tipos criados e editados na app (nome e cor; os iniciais são Família, Veronica e João).
- **Lembretes de eventos:** à hora, 1 hora antes ou de 1 a 5 dias antes, com um alarme exato local que sobrevive a atualizações e a reinícios. Os eventos de dia inteiro avisam às 09:00.
- **Avisos:** notificação quando o outro adiciona itens às Compras ou cria um evento, verificada em segundo plano cerca de cada 15 minutos (sem FCM, sem serviço em primeiro plano). Interruptores nas Definições.
- **Offline-first:** a interface lê sempre da base de dados local. Tudo o que se faz sem rede fica numa fila e é enviado quando há ligação, sem duplicar itens.
- **Tempo real:** o que uma pessoa altera aparece no outro telemóvel em segundos (Server-Sent Events).
- Indicador de sincronização (Sincronizado / A sincronizar / Offline / Com erros), com o aviso para verificar o Tailscale quando não há ligação.
- Modo escuro, tema dinâmico, suporte a leitores de ecrã (TalkBack: cada evento e cada dia da grelha são lidos por extenso; cada item tem as ações "Editar" e "Apagar") e ícone adaptativo/monocromático.
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
- Cada escrita atualiza a tabela local e a fila de operações na mesma transação. A fila é fundida (`CREATE+UPDATE → CREATE`, `UPDATE+UPDATE → um só`, `CREATE+DELETE → nada`; editar um item ainda não enviado altera o próprio `CREATE`) e enviada por ordem.
- O pull completo reconcilia o servidor com o local, sem apagar itens com operações pendentes.
- O realtime é SSE (não WebSocket), com reconexão e *backoff*, e novo pull depois de cada reconexão.

Detalhes em [`docs/`](docs/): [ARCHITECTURE](docs/ARCHITECTURE.md), [DATA_MODEL](docs/DATA_MODEL.md), [API](docs/API.md), [SERVER_SETUP](docs/SERVER_SETUP.md), [ANDROID_SETUP](docs/ANDROID_SETUP.md) e [ROADMAP](docs/ROADMAP.md) e [DECISIONS](docs/DECISIONS.md).

## Tecnologias

Kotlin · Jetpack Compose (Material 3) · Hilt · Room · Retrofit/OkHttp (`okhttp-sse`) · kotlinx.serialization · WorkManager · AlarmManager · DataStore · JUnit, Turbine, MockWebServer, testes de interface do Compose.

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

1. PocketBase com as coleções `users`, `lists`, `items`, `event_types` e `events` e as regras de [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md). Sem registo público: as duas contas são criadas à mão. Os três tipos de evento iniciais (Família, Veronica, João) criam-se à mão.
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
   ./gradlew connectedDebugAndroidTest   # testes no telemóvel (USB, depuração ativa)
   ```
3. Instalar: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Os testes instrumentados correm numa base de dados em memória, instalam só a app `.debug` e não tocam nos dados da release. Cobrem a migração da base de dados, o ecrã do calendário (criar, editar, apagar, tipos, texto lido pelo TalkBack, offline por construção), a edição de itens (toque longo, quantidade, nome vazio), o alarme de lembretes de ponta a ponta e as notificações.

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

Fases 0 a 9 feitas: Compras, Tarefas, calendário com tipos, lembretes, avisos e grelha mensal; edição de itens (9.11) correção da barra do calendário (9.12) e confirmação antes de apagar um item (9.13); versão instalada 1.9.0. Por validar com **dois telemóveis ao mesmo tempo** (tempo real entre os dois, os avisos com um item ou evento real do outro) e com dados móveis ou rede instável; o modo escuro só foi visto no ecrã do calendário. Ver [`docs/ROADMAP.md`](docs/ROADMAP.md).
