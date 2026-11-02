# Arquitetura

## Visão geral

```
┌─────────────── Android ───────────────┐          ┌──────── Mini PC ────────┐
│ UI (Compose) ─► ViewModel ─► Repository│          │  tailscale serve (HTTPS)│
│                               │   ▲    │          │          │              │
│                      Room ◄───┘   │    │  Tailnet │          ▼              │
│                        ▲          │    │◄────────►│   PocketBase :8090      │
│            SyncWorker (outbox)────┤    │          │   (SQLite, REST, SSE)   │
│            RealtimeClient (SSE)───┘    │          └─────────────────────────┘
└────────────────────────────────────────┘
```

Princípio: **a UI só conhece o Room.** Rede e sync atualizam o Room; a UI reage via `Flow`.

## Camadas e regras de dependência

```
ui  ──►  domain  ◄──  data
```

| Camada | Conteúdo | Pode depender de |
|---|---|---|
| `ui` | Composables, ViewModels, UiState, navegação | `domain` |
| `domain` | Modelos (`ShoppingItem`), interface `ItemRepository` | nada (Kotlin puro) |
| `data` | Room, API, SSE, sync, implementação do repositório | `domain` |
| `di` | Módulos Hilt | todas |

`ui` nunca importa classes de `data`. `domain` não importa Android.

## Fluxo de dados

### Leitura
`ItemRepository.observeItems()` devolve `Flow<List<ShoppingItem>>` vindo do Room. O ViewModel transforma em `UiState` (separando por comprar / comprados). `ShoppingViewModel` (`ui/shopping`) combina `observeItems()` com `SyncStatusProvider.status` num único `StateFlow<ShoppingUiState>` (`toBuy`, `bought`, `syncStatus`, `isLoading`, este último `true` até o Room emitir pela primeira vez). Mantém a ordem do repositório (`createdAt, id`); um `add` recusado (sem lista/utilizador) é ignorado por agora.

### Escrita (ex.: marcar como comprado)
1. ViewModel chama `repository.setChecked(id, true)`.
2. Repositório, **numa transação Room**: atualiza `items` e insere uma linha em `pending_ops`.
3. UI atualiza imediatamente (otimista, porque lê do Room).
4. Agenda-se `SyncWorker` (e tenta-se de imediato se houver rede).
5. `SyncWorker` envia cada operação ao servidor por ordem; em sucesso apaga-a de `pending_ops`.

### Receber alterações do outro telemóvel
1. `RealtimeClient` mantém uma ligação SSE à subscrição `items`.
2. Cada evento `create|update|delete` é aplicado ao Room (ignorando itens com operações pendentes locais, ver abaixo).
3. UI atualiza sozinha.

## Sincronização offline

### Tabelas locais

- `items`: espelho dos itens, com campo extra `syncState` (`SYNCED`, `PENDING`).
- `pending_ops` (outbox): `opId` (auto), `itemId`, `type` (`CREATE`, `UPDATE`, `DELETE`), `payloadJson`, `createdAt`, `attempts`.
- `sync_meta`: chave/valor (ex.: `lastFullSyncAt`).

### Regras

1. **IDs no cliente:** itens criados offline recebem um ID de 15 caracteres `[a-z0-9]`, aceite pelo PocketBase no `POST`.
2. **Coalescência** (`OutboxCoalescer`, `data/sync`): várias operações sobre o mesmo item são fundidas antes de enviar. Sobrevive sempre uma linha existente (mantém o `opId`); só o `payloadJson` muda.
   - `CREATE` + `UPDATE`… → um `CREATE` com os patches fundidos (um `checked_by` vazio é omitido)
   - `CREATE` + … + `DELETE` → nada
   - `UPDATE`… → um `UPDATE` com os patches fundidos (o último valor de cada campo ganha)
   - `UPDATE`… + `DELETE` → só o `DELETE`
   - **`CREATE` já tentado (`attempts > 0`) fica congelado:** pode já existir no servidor (a resposta pode ter-se perdido). Não se fundem `UPDATE`s nele (um reenvio seria rejeitado por id duplicado) e um `DELETE` posterior mantém-se (senão ficava um item fantasma no servidor).
   - O congelamento só funciona se `attempts > 0` significar "pode ter chegado ao servidor": o `SyncWorker` tem de gravar `incrementAttempts` **antes** de enviar o pedido (se só incrementasse após a resposta, um worker morto a meio do `POST` deixava `attempts == 0` e o `CREATE`+`DELETE` seguinte desaparecia, deixando um item fantasma). Também só pode correr um worker de cada vez (trabalho único).
   - Corre no início de cada passagem do `OutboxProcessor`, antes de enviar seja o que for (assim nenhuma operação está em voo).
3. **Pull completo** (`Reconciler`, `data/sync`): `GET` da lista completa (`perPage=500`, `sort=created,id`; são centenas de itens, no máximo) e reconciliação: o remoto substitui o local, **exceto** itens com operações em `pending_ops`; o que não existe no servidor e não tem operações pendentes é apagado localmente.
   - Isto também resolve apagamentos feitos noutro dispositivo enquanto este estava offline (um filtro por `updated` não os apanharia). Um `DELETE` pendente não ressuscita o item: está pendente, logo é ignorado.
   - **Nunca se aplica um snapshot parcial:** todas as páginas são lidas antes de tocar no Room e tudo é aplicado numa só transação. Se uma página falhar, nada muda (aplicar só parte apagaria os itens ainda não lidos). Só se mexe nos itens da lista ativa.
   - Os ids pendentes são lidos **dentro** da transação de aplicação, não antes do pedido, para proteger escritas feitas durante o fetch.
   - `OutboxProcessor` e `Reconciler` partilham um `SyncLock` (Mutex singleton, não reentrante). Sem ele, o pull podia ler um snapshot sem o item X (o `CREATE` ainda pendente), o processor enviar e apagar a operação, e o pull aplicar: X não está pendente nem no snapshot e seria apagado localmente.
   - O `SyncWorker` corre `process()` e, só se der `Done`, `pull()` (um a seguir ao outro, nunca aninhados). Cobre "ao abrir a app" (o `SessionViewModel` pede sincronização com sessão) e "após esvaziar o outbox". "Ao reconectar" é a 4.3.
   - Erros: mesma classificação do envio (`classifyHttp`): rede/5xx/408/429 e 401 com token → `Retry`; 401 sem token → `AuthLost`. Um 4xx no pull não se descarta (não há nada a descartar): fica `Retry`. Sem lista ativa, tenta `ensureActiveList()` e, se falhar, `Retry`.
   - Em sucesso grava `sync_meta.lastFullSyncAt` (epoch millis), que a 4.4 usa.
4. **Conflitos:** *last-write-wins* ao nível do campo enviado. É aceitável para dois utilizadores e operações idempotentes (marcar/desmarcar).
5. **Envio** (`OutboxProcessor` em `data/sync`, chamado pelo `SyncWorker`):
   - Percorre o outbox por `opId`, uma operação de cada vez. Antes de cada pedido grava `incrementAttempts` (ver regra do `CREATE` congelado). Em sucesso, apaga a operação e, **na mesma transação**, marca o item `SYNCED` se não restarem operações para ele. Repete até o outbox estar vazio. Uma `Mutex` garante uma execução de cada vez. A resposta do servidor não é escrita no Room (é o `Reconciler`, 4.2).
   - Erros:
     - Sem rede / `IOException` / 5xx / 408 / 429: parar a passagem, manter o outbox, `Result.retry()` (backoff exponencial do WorkManager).
     - 401: o `TokenAuthenticator` já tentou renovar. Se ainda há token, o refresh falhou por rede: retry. Se não há token, a sessão terminou: `Result.failure()`; o próximo login volta a pedir sincronização.
     - 400, 403, 404 (num `CREATE`/`UPDATE`) e payload ilegível: descartar a operação, registar só o tipo e o código HTTP (nunca nomes nem token) e incrementar `sync_meta.discardedOps` (a 4.4 usa-o para o estado "Erro"). O item local fica `SYNCED` e o pull seguinte corrige-o. O 403 não consta do desenho original; descarta-se para não bloquear a fila.
     - `DELETE` com 404 conta como sucesso.
     - **`CREATE` reenviado após resposta perdida:** o servidor responde 400 (id duplicado). Se a operação já tinha sido tentada, faz-se `GET items/records/{id}`: se existe, conta como enviado; se dá 404, descarta-se.
   - Agendamento: `SyncScheduler.requestSync()` é chamado pelo repositório depois de cada escrita efetiva (fora da transação) e pelo `SessionViewModel` quando há sessão. Usa trabalho único `outbox-sync` com `ExistingWorkPolicy.APPEND_OR_REPLACE` (com `KEEP`, uma operação escrita mesmo quando uma execução termina ficava por enviar), restrição de rede `CONNECTED` e backoff exponencial.
   - Hilt + WorkManager: `ListaComprasApp` implementa `Configuration.Provider` com `HiltWorkerFactory` e o manifesto remove o `WorkManagerInitializer` por omissão; sem isto a app falha em runtime ao instanciar o `SyncWorker`.

### Realtime (SSE)

`RealtimeClient` (`data/sync`), exposto ao resto da app pela interface `RealtimeSync` (domínio):

- **Ordem por ligação:** ligar → esperar `PB_CONNECT` (traz o `clientId`) → subscrever `items/*` → enviar o outbox (`OutboxProcessor.process()`, porque o WorkManager pode estar parado num backoff acumulado offline) → pull completo → aplicar eventos. Sem sessão (token ausente), `process()` e `pull()` devolvem `AuthLost` sem tocar em nada: um pedido sem token seria rejeitado, as operações descartadas e o pull poderia apagar a lista local. Subscrever *antes* do pull garante que nada entre o snapshot e a subscrição se perde. Depois de subscrever com sucesso pede-se também `requestSync()`.
- **Eventos** (`RealtimeEventApplier`): com as mesmas regras do `Reconciler` para um só registo. `create`/`update` fazem upsert como `SYNCED`, `delete` apaga; em qualquer caso ignora-se o item se tiver operações em `pending_ops` (lidas dentro da transação), o que cobre também o eco da própria alteração. Registos de outra lista, ações desconhecidas e JSON inválido são ignorados sem fechar a ligação (regista-se só a ação). Cada evento é aplicado sob o `SyncLock`, por isso um evento que chega durante um pull é aplicado depois do snapshot e nunca é sobrescrito por ele.
- O *listener* OkHttp só coloca mensagens num buffer ilimitado (`callbackFlow`); nunca bloqueia a thread do OkHttp. O pull nunca é chamado com o lock tomado.
- **Reconexão:** quando a ligação fecha ou falha (incluindo subscrição rejeitada ou pull com `Retry`), espera-se com backoff exponencial (1 s, dobrando até 30 s, com ±20 % de jitter) e volta-se a ligar com novo `clientId`. O backoff só volta ao início depois de subscrever **e** fazer o pull com sucesso (se voltasse ao início logo após subscrever, um pull que falha sempre reconectava de segundo a segundo, sem fim). Qualquer exceção na ligação (rede, resposta que não é `text/event-stream`, data inválida...) é só mais uma reconexão e nunca chega ao ciclo de vida da activity. `AuthLost` no pull termina a tentativa até haver nova sessão. O fecho por inatividade do servidor (5 min) é só mais uma reconexão.
- **Token:** a subscrição fica autorizada com o token usado no `POST`. `TokenStore.tokenFlow` emite também nas renovações do mesmo utilizador (o `AuthState` não emite nesse caso); o cliente usa `collectLatest` sobre ele, por isso uma renovação reinicia a ligação (nova subscrição) e o logout cancela-a.
- **Ciclo de vida:** `MainActivity` corre `RealtimeSync.run()` dentro de `repeatOnLifecycle(STARTED)`: a ligação só existe com a app visível. Em segundo plano trata do resto o `SyncWorker`; ao voltar, a nova ligação faz pull e apanha o que se perdeu.

## Autenticação

- Duas contas criadas manualmente pelo superuser no PocketBase; registo público desativado.
- Login com email/password → token guardado de forma segura (DataStore + cifra com Android Keystore).
  - `TokenStore` (`data/auth`) guarda o token cifrado (AES-256/GCM, chave não exportável no Keystore, via `TokenCipher`) e o `userId`/nome (necessários para `added_by`/`checked_by`). Mantém o valor em memória para o `AuthInterceptor` o ler de forma síncrona.
  - Token ilegível (chave perdida, reinstalação) conta como sessão terminada, sem crash. O ficheiro `datastore/auth.preferences_pb` está excluído dos backups, porque a chave do Keystore não é copiada.
  - A navegação é conduzida por `AuthRepository.authState` (`Loading`/`LoggedOut`/`LoggedIn`): login, logout e, futuramente, falha de refresh limitam-se a mudar este estado.
  - **401 → refresh:** `TokenAuthenticator` (OkHttp `Authenticator`) renova o token uma única vez para todos os pedidos concorrentes (chamada síncrona a `auth-refresh`, fora do dispatcher) e repete o pedido. Se o servidor rejeitar o refresh (400/401/403/404), a sessão é limpa e a navegação volta ao login (os dados locais ficam). Erros de rede/5xx mantêm a sessão: estar offline nunca termina a sessão.
  - **Refresh preventivo:** ao arrancar, `AuthRepository.refreshSessionIfNeeded()` renova o token se faltarem menos de 7 dias (lê `exp` do JWT). Um token já expirado pode ser recusado pelo `auth-refresh` (não confirmado na documentação), por isso renovar antes de expirar é o que mantém os dois telemóveis autenticados.
  - Credenciais erradas: o PocketBase responde **400** (não 401) em `auth-with-password`.
- Interceptor OkHttp adiciona o cabeçalho `Authorization`.
- Token renovado com `auth-refresh` quando perto de expirar ou ao receber 401.

## Estado de sincronização (UI)

Derivado, não obtido do Tailscale (a app não sabe se o Tailscale está ativo; só sabe se alcança o servidor). `SyncStatusProvider` (domínio) expõe `status: Flow<SyncStatus>` e `acknowledgeErrors()`; a implementação é `SyncStatusMonitor` (`data/sync`), que combina `pending_ops` (contagem), `NetworkMonitor`, `ServerReachability` e as chaves `discardedOps`/`discardedAck`. A função pura `deriveSyncStatus` aplica esta precedência, da mais alta para a mais baixa:

| Estado | Condição |
|---|---|
| Erro | há operações descartadas pelo servidor que o utilizador ainda não reconheceu (`discardedOps > discardedAck`) |
| Offline | sem rede, ou servidor inalcançável (mostrar dica: "Verifica se o Tailscale está ligado") |
| A sincronizar | outbox com operações, ou alcançabilidade ainda desconhecida (acabou de ligar/voltar a rede) |
| Sincronizado | rede, servidor alcançável e outbox vazio |

- **"A sincronizar" com alcançabilidade desconhecida** vai além da tabela original: evita mostrar "Sincronizado" antes de se saber se o servidor responde.
- **Erro só para descartes.** Perder a sessão já leva o utilizador ao ecrã de login, e um 401 com token ainda presente é um problema de rede (aparece como Offline). Por isso a falha de autenticação não é um estado à parte. Um descarte novo volta a mostrar o erro depois de reconhecido (`acknowledgeErrors()` guarda o valor atual em `discardedAck`; a 5.3 chama-o).
- **Rede:** `AndroidNetworkMonitor` ignora redes VPN (`NET_CAPABILITY_NOT_VPN`): o Tailscale corre como VPN, e a rede *por omissão* pode continuar "disponível" em modo avião. Conta-se uma rede real com internet.
- **Alcançabilidade** (`ServerReachability`: Desconhecida / Alcançável / Inalcançável) tem **uma só fonte**, o `RealtimeClient`: Alcançável depois de subscrever e fazer o pull; Inalcançável numa falha de ligação (não num fecho normal, para a reconexão de 5 min por inatividade não mostrar "Offline"); Desconhecida quando a ligação (re)começa **e logo que a rede cai** (um coletor próprio, independente do ciclo de ligação, para que um reinício atrasado nunca deixe um "Alcançável" antigo a mostrar "Sincronizado" sem ligação). O `ServerReachability` conhece o estado da rede e ignora `markReachable()` enquanto a rede está em baixo, com o mesmo lock da mudança de rede, para um handshake que termina logo a seguir à queda não repor "Alcançável". O estado só é visível com a app em primeiro plano, onde o `RealtimeClient` corre sempre.
- **O `RealtimeClient` só liga com token *e* rede.** Cada passagem offline→online (ou novo token) reinicia o ciclo com backoff a zero. Isto também mata ligações "zumbi": uma ligação SSE parada através da VPN pode sobreviver ao modo avião sem nunca dar erro.

## Testes

| Tipo | Alvo | Ferramentas |
|---|---|---|
| Unitários | ViewModels, coalescência do outbox, reconciliação | JUnit, Turbine, fakes |
| API | Retrofit + DTOs | MockWebServer |
| Instrumentados | DAOs Room | AndroidX Test |
| UI | ecrã da lista | Compose UI Test |
