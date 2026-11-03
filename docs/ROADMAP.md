# Roadmap

> ## Ponto de situação (fim de 2026-11-02) · retomar aqui
> - **Release instalada/mais recente:** v8 (`versionCode` 8, 1.3.0), com 9.1, 9.2, 9.2b e 9.3. Instalar com `adb install -r android/app/build/outputs/apk/release/app-release.apk` (receita de build na memória do assistente: Gradle dentro do distrobox `androidstudio`).
> - **Feitas e confirmadas pelo utilizador:** 9.1, 9.2, 9.2b (login com teclado e texto branco; a cor foi aplicada na v7).
> - **9.3 implementada, por confirmar:** o utilizador instalou a v8 ("alterações efetuadas") mas ainda não reportou o teste das notificações. Perguntar o resultado primeiro: aviso com a app fechada (cerca de 15 min), itens vistos não avisam, interruptor nas Definições, permissão pedida uma vez. Só então marcar `[x]`.
> - **Servidor:** o utilizador diz ter criado/ajustado `event_types` e `events` segundo [DATA_MODEL](DATA_MODEL.md) (incluindo `reminder` como *select* `at_time`/`1h`/`1d`, `all_day` sem *Nonempty*, pattern da `color`, regras de API e os 3 tipos iniciais Família `#4CAF50`, Veronica `#E91E63`, João `#2196F3`). **Não verificado**: confirmar na 9.6 com a primeira chamada à API.
> - **9.4 implementada em 2026-11-03, por verificar** a migração Room num dispositivo (ver a tarefa). Depois: 9.5 (confirmar o servidor) e 9.6.
> - **Lembretes de método:** uma tarefa de cada vez; commit por tarefa; não mexer em `applicationId`/pacote/nome da Room; textos em `strings.xml` PT-PT; `server/` não está neste repositório.
> - Decisões e porquê: [DECISIONS](DECISIONS.md).

Regras: uma tarefa de cada vez, por ordem. Marcar `[x]` só quando **todos** os critérios de aceitação passarem. Para o contexto de cada fase, ver os documentos indicados.

---

## Fase 0 · Servidor
Docs: [SERVER_SETUP](SERVER_SETUP.md), [DATA_MODEL](DATA_MODEL.md)

- [x] **0.1** Criar `server/` com `Dockerfile`, `docker-compose.yml`, `.env.example`, `.gitignore`
  - ✔ `docker compose up -d --build` arranca; `curl http://127.0.0.1:8090/api/health` devolve 200
- [x] **0.2** Criar superuser e collections (`users` ajustada, `lists`, `items`) com regras
  - ✔ Existem migrações em `server/pb_migrations/` e estão em git
  - ✔ Pedido sem token a `items` não devolve dados
- [x] **0.3** Tailscale no host + `tailscale serve --bg --https=8443 8090`
  - ✔ `https://<maquina>.<tailnet>.ts.net:8443/api/health` responde a partir de um telemóvel com Tailscale
- [x] **0.4** Criar as duas contas de utilizador e a lista "Compras"
  - ✔ Login por `curl` devolve token para ambas
- [x] **0.5** Backups
  - ✔ Backup diário configurado, cópia para outro disco, restauro testado uma vez

## Fase 1 · Esqueleto Android
Docs: [ANDROID_SETUP](ANDROID_SETUP.md), [ARCHITECTURE](ARCHITECTURE.md)

- [x] **1.1** Criar projeto Compose em `android/` com version catalog e plugins
  - ✔ `./gradlew assembleDebug` compila
- [x] **1.2** Configurar Hilt, estrutura de pacotes, tema Material 3, `SERVER_URL` via `BuildConfig`
  - ✔ App abre num ecrã vazio com tema; `BuildConfig.SERVER_URL` correto
- [x] **1.3** Navegação com dois destinos (login, lista) e strings em PT-PT
  - ✔ Navega entre placeholders; sem strings hardcoded

## Fase 2 · Rede e autenticação
Docs: [API](API.md)

- [x] **2.1** DTOs, `PocketBaseApi`, `AuthInterceptor`, parser de datas do PocketBase
  - ✔ Testes com MockWebServer para login, listar, criar, atualizar, apagar
- [x] **2.2** `TokenStore` + `AuthRepository` + ecrã de login
  - ✔ Login real contra o servidor; token persiste após fechar a app; erros mostram mensagem clara
- [x] **2.3** Renovação de token e tratamento de 401
  - ✔ Teste unitário: 401 → refresh → repete pedido; refresh falhado → volta ao login

## Fase 3 · Dados locais e repositório (offline-first)
Docs: [ARCHITECTURE](ARCHITECTURE.md), [DATA_MODEL](DATA_MODEL.md)

- [x] **3.1** Room: `ItemEntity`, `PendingOpEntity`, `SyncMetaEntity`, DAOs
  - ✔ Testes instrumentados dos DAOs
- [x] **3.2** `ItemRepository`: `observeItems()`, `add`, `setChecked`, `delete`, `clearChecked`, tudo com escrita local + outbox na mesma transação
  - ✔ Testes unitários: cada operação altera `items` e cria a `pending_op` correta
- [x] **3.3** `OutboxCoalescer`
  - ✔ Testes: `CREATE+UPDATE`→`CREATE`, `CREATE+DELETE`→nada, `UPDATE+UPDATE`→um `UPDATE`

## Fase 4 · Sincronização e realtime
- [x] **4.1** `SyncWorker`: envia o outbox por ordem, com tratamento de erros da tabela de [API](API.md#erros-comuns)
  - ✔ Teste: com API falsa a falhar e depois a recuperar, o outbox esvazia sem duplicar itens
- [x] **4.2** `Reconciler` (pull completo)
  - ✔ Teste: remoto substitui local, exceto itens com operações pendentes; apagamentos remotos são aplicados
- [x] **4.3** `RealtimeClient` (SSE): conectar, subscrever `items`, aplicar eventos, reconectar com backoff, re-subscrever + pull após reconectar
  - ✔ Duas instâncias (dois telemóveis/emuladores): alteração num aparece no outro em segundos
  - Estado: implementada e verificada num telemóvel contra o servidor real (create/update/delete, reconexão ao reabrir). Fechada em 2026-11-02: o utilizador confirmou com dois telemóveis que a release v3 funciona como esperado.
- [x] **4.4** `SyncStatus` derivado (Sincronizado / A sincronizar / Offline / Erro)
  - ✔ Reflete corretamente modo avião ligado/desligado
  - Verificado no telemóvel em 2026-10-30: modo avião ligado → Offline em ~2 ms; desligado → A sincronizar até ao `pull done` e só então Sincronizado.

## Fase 5 · Interface
- [x] **5.1** `ShoppingViewModel` + `UiState` (por comprar / comprados / estado de sync)
  - ✔ Testes com Turbine
- [x] **5.2** Ecrã da lista: duas secções, campo fixo de adição rápida, toque para marcar, deslizar para apagar, "Limpar comprados"
  - ✔ Funciona com avião ligado; teclado não tapa o campo de adição
  - Verificado no telemóvel em 2026-11-02: adição offline, teclado, sincronização depois de reconectar. Já é possível o teste de duas instâncias da 4.3.
- [x] **5.3** Indicador de sincronização e mensagem "Verifica se o Tailscale está ligado" quando offline
  - ✔ Visível e compreensível nos quatro estados
  - Substituir os restos de diagnóstico da 4.3/4.4: o coletor `SyncStatus` temporário no `MainActivity` e o log dos valores brutos no `RealtimeModule` (`BuildConfig.DEBUG`) pelo indicador visível; chamar `SyncStatusProvider.acknowledgeErrors()` ao tocar no erro.
  - Verificado no telemóvel em 2026-11-02.
- [x] **5.4** Estados vazios, modo escuro, acessibilidade básica (content descriptions, alvos de toque ≥ 48dp)
  - ✔ Lista vazia e "tudo comprado" mostram uma mensagem
  - ✔ Modo escuro legível (login, lista, faixas, fundo do deslizar) e sem flash branco ao arrancar
  - ✔ Com TalkBack, cada item lê nome, quantidade e estado, marca-se com duplo toque e tem a ação "Apagar"
  - ✔ Com o tamanho de letra máximo nada fica cortado; alvos de toque ≥ 48dp
  - Verificado no telemóvel em 2026-11-02.

## Fase 6 · Polimento e entrega
- [x] **6.1** Revisão de erros e logs (sem dados sensíveis), lint limpo
  - ✔ Nenhum log contém tokens, palavras-passe, nomes/quantidades de itens, nomes de utilizadores, emails nem o URL do servidor
  - ✔ Logs de diagnóstico (`d`) só em builds de debug
  - ✔ Sem avisos do compilador; `lintDebug` só com avisos de atualização de versões (tratados à parte, ver 6.1b)
  - ✔ Sem textos visíveis hardcoded
  - Verificado no computador em 2026-11-02: lint passou de 16 para 8 avisos (todos de versões); sem avisos do compilador. O deslizar para apagar passou para a API `onDismiss`; por reconfirmar no telemóvel.
- [x] **6.1b** Atualizar dependências desatualizadas (decisão do utilizador, 2026-11-02)
  - Confirmar a última versão estável de cada uma (core-ktx, activity-compose, Compose BOM, navigation-compose, Kotlin, kotlinx-serialization-json, Gradle) antes de fixar
  - ✔ `testDebugUnitTest`, `assembleDebug` e `lintDebug` passam; app testada no telemóvel
  - Verificado no telemóvel em 2026-11-02 (um telemóvel). Atualizado em 3 commits (AndroidX; Gradle 9.8.2; Kotlin 2.4.21 + kotlinx-serialization 1.12.0).
- [ ] **6.2** Testes em dispositivo real fora de casa (dados móveis) e com rede instável
  - ✔ Lista de verificação: criar offline → ligar rede → aparece no outro; apagar offline; dois a editar o mesmo item
- [x] **6.3** Build release assinada e instalação nos dois telemóveis
  - ✔ Atualização por cima mantém os dados
  - Estado: implementado (`applicationId` `com.joaobarreto.listacompras`, assinatura por `keystore.properties`, APK em `android/app/build/outputs/apk/release/app-release.apk`, assinatura confirmada com `apksigner`). Testado em 2026-11-02 com um telemóvel: instalação limpa e atualização por cima (v1 → v2, `versionCode` 2) mantiveram o login e os itens já sincronizados. Um item adicionado offline na v1 não apareceu na v2; um force-stop offline mantém o item pendente; a perda limitou-se à instalação por cima (aceite pelo utilizador). Fechada em 2026-11-02: instalada e a funcionar no segundo telemóvel (confirmado pelo utilizador).

- [ ] **6.4** Impedir a adição enquanto ainda não há lista ativa (primeira execução sem servidor alcançado)
  - ✔ Sem lista: botão desativado, o texto escrito mantém-se no campo e aparece "A obter a lista do servidor…"; com lista, tudo como antes
  - Estado: implementado (`ListRepository.hasActiveList`, `ShoppingUiState.canAdd`); testes passam. Difícil de reproduzir no telemóvel (só ocorre antes de a lista ser obtida na primeira execução); verificado por testes unitários.

## Fase 8 · Tarefas da casa
Decisões do utilizador (2026-11-02): dois separadores fixos (Compras e Tarefas, abre em Compras); a tarefa só tem nome e feita/por fazer; partilhada pelas duas contas. Desenho em [DATA_MODEL](DATA_MODEL.md#collection-lists). Sem alterações de esquema (servidor ou Room).

- [x] **8.1** Criar a lista "Tarefas" no servidor (feito pelo utilizador no dashboard do PocketBase)
  - ✔ `GET /api/collections/lists/records?sort=created` devolve duas listas, "Compras" primeiro e "Tarefas" depois (confirmado pelo utilizador no dashboard em 2026-11-02; a verificação ponta a ponta fica para a 8.3)
- [x] **8.2** Cliente: conhecer as duas listas, com a lista selecionada
  - Guardar os ids das duas listas em `sync_meta`; `add`, `observeItems` e `clearChecked` usam a lista selecionada; o pull completo e o realtime tratam as duas listas; se "Tarefas" ainda não existir no servidor, tentar de novo ao ligar
  - ✔ Testes unitários: itens de cada lista não se misturam; o pull não apaga os itens da outra lista; eventos de realtime de ambas são aplicados; sem a lista "Tarefas" a app de compras funciona como antes
  - Verificado por testes unitários em 2026-11-02 (146 testes) e no telemóvel com a release v3 (as Compras não mudaram).
- [x] **8.3** Interface: dois separadores e seleção da lista
  - ✔ Abre em Compras; trocar de separador mostra a outra lista sem perder o scroll nem o texto do campo de adição; funciona offline
  - Verificado no telemóvel em 2026-11-02 (release v3 instalada por cima da v2, um telemóvel).
- [x] **8.4** Texto e aspeto das tarefas
  - ✔ "Por fazer / Feitas / Limpar feitas", sem quantidade, estados vazios próprios, tudo em `strings.xml`
  - Verificado no telemóvel em 2026-11-02 (release v3 instalada por cima da v2, um telemóvel).

## Fase 9 · Life Manager (calendário, notificações, quantidades)
Decisões do utilizador (2026-11-02), ver [DECISIONS](DECISIONS.md): a app passa de "lista de compras" a **app pessoal do casal**, com o nome **Life Manager**; calendário partilhado com tipos de evento coloridos e extensíveis; notificações pelo método de poupança de bateria (verificação periódica, até ~15 min de atraso); quantidade opcional nos itens de Compras. Docs: [DATA_MODEL](DATA_MODEL.md#collection-event_types), [ARCHITECTURE](ARCHITECTURE.md#notificações-e-lembretes).

- [x] **9.1** Quantidade opcional nas Compras
  - Segundo campo ao lado do nome, só em Compras; vazio = não se grava nem mostra quantidade (a linha mostra só o nome). Máx. 30 caracteres. Editar a quantidade de um item existente fica fora do âmbito.
  - ✔ Testes: `onAdd` com quantidade vazia/só espaços → `quantity = null`; com valor → guardada com trim; a lista mostra a quantidade só quando existe; Tarefas não mostram o campo; funciona offline
  - Estado: implementado em 2026-11-02 (`AddItemBar` com campo "Quantidade" só nas Compras; `ShoppingViewModel.onAdd(name, quantity)`); 148 testes, `assembleDebug` e `lintDebug` passam. O foco volta ao nome depois de adicionar. Verificado no telemóvel em 2026-11-02 (v4); só a legenda do campo ficou para a 9.2b.
- [x] **9.2** Nome "Life Manager" e documentação
  - Só o nome visível (`app_name`) e README/docs. **Não mudar** `applicationId`, pacote, nome da base Room nem keystore (senão seria outra app e os dois telemóveis perdiam os dados).
  - ✔ O ícone da app mostra "Life Manager"; docs coerentes com a nova visão; `DECISIONS.md` criado
  - Estado: docs feitos e `app_name` = "Life Manager" em 2026-11-02 (release v4, `versionCode` 4 / 1.2.0, com a 9.1). Verificado no telemóvel em 2026-11-02 (v4).
- [x] **9.2b** Ecrã de login acolhedor e campo de quantidade mais compacto (pedido do utilizador após testar a v4)
  - Login: fundo em gradiente (laranja quente do ícone no topo, quase preto em baixo no modo escuro; creme no claro), ícone circular, nome "Life Manager" e frase "A vida a dois, organizada."; formulário legível nos dois modos
  - Quantidade: legenda curta "Qtd." (descrição para TalkBack "Quantidade (opcional)") e campo com a altura do campo do nome
  - ✔ Verificado no telemóvel (claro, escuro, letra grande, teclado aberto no login)
  - Estado: implementado em 2026-11-02 (release v6); por verificar. O teclado cortava o formulário na v5: agora o ecrã faz scroll e esconde o logo com o teclado aberto. Resultado da v4: 9.1 e 9.2 confirmadas no telemóvel, exceto a legenda do campo de quantidade (a causa desta tarefa).
- [ ] **9.3** Base das notificações e aviso de itens novos nas Compras
  - Permissão `POST_NOTIFICATIONS` (Android 13+, pedida em contexto), canais de notificação, `NotificationChecker` periódico (WorkManager, ~15 min), marcador `lastNotified*` em `sync_meta`, interruptor nas definições
  - ✔ Testes unitários: só itens de outro utilizador; vários itens → uma notificação agrupada; nunca duas vezes o mesmo item; sem permissão não falha
  - ✔ No telemóvel: com a app fechada, um item adicionado pelo outro gera aviso (cerca de 15 min, mas pode demorar mais com o telemóvel parado; ver DECISIONS)
  - Estado: implementado em 2026-11-02 (release v8, 1.3.0; 157 testes, lint limpo). "Definições" no topo (interruptor + Terminar sessão). Por verificar no telemóvel, com o outro telemóvel a adicionar.
- [ ] **9.4** Motor de sincronização para várias collections + migração Room v1→v2
  - `pending_ops` ganha a coluna `collection`; `OutboxProcessor`, `Reconciler`, `RealtimeEventApplier` e `OutboxCoalescer` passam a tratar `items`, `events` e `event_types`; subscrição realtime `events/*` e `event_types/*`
  - ✔ Migração Room testada (dados da v1 intactos, `schemas/2.json` em git); todos os testes de sync existentes continuam a passar; as Compras e Tarefas não mudam
  - Estado: implementado em 2026-11-03 (esquema Room v2 completo: coluna `collection` + tabelas `events`/`event_types`, entidades, DAOs, DTOs, mappers; `MIGRATION_1_2` com o SQL de `schemas/2.json`; pull isolado, realtime por tópico com recuo para `items/*`). 168 testes unitários, `assembleDebug`, `compileDebugAndroidTestKotlin` e `lintDebug` passam. **Não fazer nenhuma release com a base v2 enquanto o `MigrationTest` não passar** (os telemóveis têm dados reais em v1; se a migração falhar a app falha ao arrancar). **Por verificar:** `MigrationTest` (instrumentado) não correu: sem dispositivo e o emulador do computador falha com *segfault*; correr `./gradlew connectedDebugAndroidTest` com o telemóvel ligado por USB (instala a app de teste `.debug` e não mexe nos dados da release).
- [ ] **9.5** Collections `event_types` e `events` no servidor
  - Criadas no dashboard do PocketBase (migração gerada e guardada em git), com os três tipos iniciais: Família, Veronica, João
  - ✔ Pedido sem token não devolve dados; ambos os utilizadores leem e escrevem; ver [DATA_MODEL](DATA_MODEL.md)
  - Estado: collections criadas pelo utilizador no dashboard em 2026-11-02; campos e regras por confirmar contra o DATA_MODEL na 9.6. O utilizador aceita reinstalar a app nos dois telemóveis, mas a migração Room (9.4) mantém-se por boa prática.
- [ ] **9.6** Camada de dados do calendário (offline-first)
  - `EventRepository`, `EventTypeRepository` (as entidades Room, DAOs, DTOs e mappers já existem desde a 9.4)
  - ✔ Testes: criar/editar/apagar evento e tipo offline gera a `pending_op` certa; pull e realtime aplicam eventos dos dois utilizadores
- [ ] **9.7** Ecrã do calendário (3.º separador)
  - Agenda por dia/mês com `DatePicker`/`TimePicker` do Material 3 (sem biblioteca de calendário), criar/editar/apagar evento, escolher o tipo, gerir tipos (nome + cor), cada evento com a cor do seu tipo
  - ✔ Eventos coloridos por tipo, com texto do tipo também (a cor nunca é a única pista); modo escuro legível; TalkBack lê título, data e tipo; funciona offline
- [ ] **9.8** Lembretes de eventos
  - `AlarmManager` local a partir do Room, reagendado após sync, edição, apagar e reboot (`RECEIVE_BOOT_COMPLETED`); antecedência escolhida no evento (campo `reminder`: nenhuma / à hora / 1 h / 1 dia). Confirmar antes as regras atuais de alarmes exatos (Android 13/14).
  - ✔ Evento criado pelo outro telemóvel é agendado aqui; apagar cancela o alarme; sobrevive a reinício do telemóvel
- [ ] **9.9** Aviso de evento novo criado pelo outro
  - Reutiliza o `NotificationChecker` da 9.3
  - ✔ Mesmas regras da 9.3 (só do outro, agrupado, sem repetição)

## Fase 7 · Ideias futuras (fora do MVP)

- [ ] Mais listas além de Compras e Tarefas (supermercado, farmácia…) com criação livre (a versão mínima, só Compras + Tarefas, é a Fase 8)
- [ ] Categorias com agrupamento visual
- [ ] Sugestões a partir do histórico (autocomplete)
- [ ] Notificação quando o outro conclui itens (a de "adicionou" está na Fase 9)
- [ ] Eventos recorrentes (anuais, semanais) no calendário
- [ ] Editar a quantidade de um item já existente
- [ ] Widget no ecrã inicial
- [ ] Partilha de texto para a app ("adicionar à lista")
