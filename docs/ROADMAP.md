# Roadmap

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
- [ ] **4.3** `RealtimeClient` (SSE): conectar, subscrever `items`, aplicar eventos, reconectar com backoff, re-subscrever + pull após reconectar
  - ✔ Duas instâncias (dois telemóveis/emuladores): alteração num aparece no outro em segundos
  - Estado: implementada e verificada num telemóvel contra o servidor real (create/update/delete, reconexão ao reabrir). Falta o teste com duas instâncias, possível só depois da 5.2.
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
- [ ] **6.3** Build release assinada e instalação nos dois telemóveis
  - ✔ Atualização por cima mantém os dados
  - Estado: implementado (`applicationId` `com.joaobarreto.listacompras`, assinatura por `keystore.properties`, APK em `android/app/build/outputs/apk/release/app-release.apk`, assinatura confirmada com `apksigner`). Testado em 2026-11-02 com um telemóvel: instalação limpa e atualização por cima (v1 → v2, `versionCode` 2) mantiveram o login e os itens já sincronizados. Um item adicionado offline na v1 não apareceu na v2; um force-stop offline mantém o item pendente; a perda limitou-se à instalação por cima (aceite pelo utilizador). Falta o segundo telemóvel.

- [ ] **6.4** Impedir a adição enquanto ainda não há lista ativa (primeira execução sem servidor alcançado)
  - ✔ Sem lista: botão desativado, o texto escrito mantém-se no campo e aparece "A obter a lista do servidor…"; com lista, tudo como antes
  - Estado: implementado (`ListRepository.hasActiveList`, `ShoppingUiState.canAdd`); testes passam. Difícil de reproduzir no telemóvel (só ocorre antes de a lista ser obtida na primeira execução); verificado por testes unitários.

## Fase 8 · Tarefas da casa
Decisões do utilizador (2026-11-02): dois separadores fixos (Compras e Tarefas, abre em Compras); a tarefa só tem nome e feita/por fazer; partilhada pelas duas contas. Desenho em [DATA_MODEL](DATA_MODEL.md#collection-lists). Sem alterações de esquema (servidor ou Room).

- [x] **8.1** Criar a lista "Tarefas" no servidor (feito pelo utilizador no dashboard do PocketBase)
  - ✔ `GET /api/collections/lists/records?sort=created` devolve duas listas, "Compras" primeiro e "Tarefas" depois (confirmado pelo utilizador no dashboard em 2026-11-02; a verificação ponta a ponta fica para a 8.3)
- [ ] **8.2** Cliente: conhecer as duas listas, com a lista selecionada
  - Guardar os ids das duas listas em `sync_meta`; `add`, `observeItems` e `clearChecked` usam a lista selecionada; o pull completo e o realtime tratam as duas listas; se "Tarefas" ainda não existir no servidor, tentar de novo ao ligar
  - ✔ Testes unitários: itens de cada lista não se misturam; o pull não apaga os itens da outra lista; eventos de realtime de ambas são aplicados; sem a lista "Tarefas" a app de compras funciona como antes
  - Estado: implementado (`ListKind`, `ActiveListProvider` por tipo, `ensureLists`, pull e realtime sobre todas as listas conhecidas, `ShoppingViewModel` usa `SHOPPING`); 146 testes. Por verificar no telemóvel: nada deve mudar na lista de Compras.
- [ ] **8.3** Interface: dois separadores e seleção da lista
  - ✔ Abre em Compras; trocar de separador mostra a outra lista sem perder o scroll nem o texto do campo de adição; funciona offline
- [ ] **8.4** Texto e aspeto das tarefas
  - ✔ "Por fazer / Feitas / Limpar feitas", sem quantidade, estados vazios próprios, tudo em `strings.xml`

## Fase 7 · Ideias futuras (fora do MVP)

- [ ] Mais listas além de Compras e Tarefas (supermercado, farmácia…) com criação livre (a versão mínima, só Compras + Tarefas, é a Fase 8)
- [ ] Categorias com agrupamento visual
- [ ] Sugestões a partir do histórico (autocomplete)
- [ ] Notificação opcional quando o outro adiciona/conclui itens
- [ ] Widget no ecrã inicial
- [ ] Partilha de texto para a app ("adicionar à lista")
