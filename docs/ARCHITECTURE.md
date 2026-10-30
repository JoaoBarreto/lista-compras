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
`ItemRepository.observeItems()` devolve `Flow<List<ShoppingItem>>` vindo do Room. O ViewModel transforma em `UiState` (separando por comprar / comprados).

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
2. **Coalescência:** várias operações sobre o mesmo item podem ser fundidas antes de enviar (ex.: `CREATE` + `UPDATE` → um `CREATE`; `CREATE` + `DELETE` → nada).
3. **Pull completo:** ao abrir a app, ao reconectar e após esvaziar o outbox, fazer `GET` da lista completa (são centenas de itens, no máximo) e reconciliar: o remoto substitui o local, **exceto** itens com operações em `pending_ops`.
   - Isto também resolve apagamentos feitos noutro dispositivo enquanto este estava offline (um filtro por `updated` não os apanharia).
4. **Conflitos:** *last-write-wins* ao nível do campo enviado. É aceitável para dois utilizadores e operações idempotentes (marcar/desmarcar).
5. **Erros:**
   - Sem rede / 5xx / timeout: manter no outbox, retry com backoff (WorkManager).
   - 401: tentar `auth-refresh`; se falhar, voltar ao ecrã de login mantendo os dados locais.
   - 400/404 numa operação: descartar a operação e registar (não bloquear a fila). `DELETE` com 404 conta como sucesso.

### Realtime (SSE)

- Reconectar automaticamente com backoff exponencial limitado.
- Após cada reconexão: **nova subscrição** (o `clientId` muda) e **pull completo**, porque se podem ter perdido eventos.
- A ligação só corre com a app em primeiro plano. Em segundo plano, o `SyncWorker` periódico/por constraint trata do resto.

## Autenticação

- Duas contas criadas manualmente pelo superuser no PocketBase; registo público desativado.
- Login com email/password → token guardado de forma segura (DataStore + cifra com Android Keystore).
- Interceptor OkHttp adiciona o cabeçalho `Authorization`.
- Token renovado com `auth-refresh` quando perto de expirar ou ao receber 401.

## Estado de sincronização (UI)

Derivado, não obtido do Tailscale (a app não sabe se o Tailscale está ativo; só sabe se alcança o servidor):

| Estado | Condição |
|---|---|
| Sincronizado | outbox vazio e último pull/SSE OK |
| A sincronizar | outbox com operações e rede disponível |
| Offline | sem rede ou servidor inalcançável (mostrar dica: "Verifica se o Tailscale está ligado") |
| Erro | falha persistente de autenticação ou operações descartadas |

## Testes

| Tipo | Alvo | Ferramentas |
|---|---|---|
| Unitários | ViewModels, coalescência do outbox, reconciliação | JUnit, Turbine, fakes |
| API | Retrofit + DTOs | MockWebServer |
| Instrumentados | DAOs Room | AndroidX Test |
| UI | ecrã da lista | Compose UI Test |
