# API (PocketBase)

Base URL: `https://<maquina>.<tailnet>.ts.net:8443` (vem de `SERVER_URL` em `local.properties`, exposto via `BuildConfig`).

Cabeçalho em pedidos autenticados: `Authorization: <token>` (o token devolvido no login).

Referência oficial: documentação da API do PocketBase (confirmar detalhes para a versão instalada).

## Autenticação

### Login
`POST /api/collections/users/auth-with-password`

```json
{ "identity": "email@exemplo.pt", "password": "..." }
```
Resposta: `{ "token": "...", "record": { "id": "...", "name": "...", ... } }`

Credenciais erradas devolvem **400** (`"Failed to authenticate."`), não 401.

### Renovar token

Com token **expirado** o servidor deve responder 401 (a documentação do PocketBase só descreve token válido; não confirmado), por isso a app renova antes de expirar e, se falhar, volta ao login.
`POST /api/collections/users/auth-refresh` (autenticado) → novo `token` + `record`.

## Listas

### Obter a lista ativa (MVP)
`GET /api/collections/lists/records?sort=created&perPage=1` → `items[0].id` (a lista mais antiga). Chamado uma vez após o login; o id fica em `sync_meta`.

## Itens

### Listar
`GET /api/collections/items/records?filter=list="<listId>"&sort=created&perPage=200&page=1`

- Resposta: `{ page, perPage, totalPages, totalItems, items: [...] }`
- Paginar até `page >= totalPages` (ou página vazia). O máximo de `perPage` é 500. O `Reconciler` usa `perPage=500` e `sort=created,id` (o `id` desempata itens criados no mesmo instante, para a paginação ser estável).

### Criar (com ID do cliente)
`POST /api/collections/items/records`

```json
{
  "id": "a1b2c3d4e5f6g7h",
  "list": "<listId>",
  "name": "Leite",
  "quantity": "2",
  "checked": false,
  "added_by": "<userId>"
}
```

### Atualizar
`PATCH /api/collections/items/records/{id}` com apenas os campos alterados.

```json
{ "checked": true, "checked_by": "<userId>" }
```

### Ler um item
`GET /api/collections/items/records/{id}` → o registo, ou `404`. Só serve para confirmar se um `CREATE` com resposta perdida chegou ao servidor (ver ARCHITECTURE).

### Apagar
`DELETE /api/collections/items/records/{id}` → `204`. Tratar `404` como sucesso.

## Realtime (SSE)

Não é WebSocket. Fluxo:

1. `GET /api/realtime` com `Accept: text/event-stream`. O primeiro evento é `PB_CONNECT`, com `data: {"clientId":"..."}`.
2. `POST /api/realtime` (com `Authorization`) a registar subscrições:
   ```json
   { "clientId": "<clientId>", "subscriptions": ["items"] }
   ```
3. Chegam eventos com o nome da subscrição (`items`) e dados:
   ```json
   { "action": "create" | "update" | "delete", "record": { ... } }
   ```

Notas de implementação (`okhttp-sse`):

- Usar `EventSources.createFactory(client)` com um `OkHttpClient` com `readTimeout(0)` (sem timeout de leitura).
- A ligação pode cair a qualquer momento (rede móvel, Tailscale a reconectar). Reconectar com backoff, **re-subscrever** (novo `clientId`) e fazer pull completo.
- A autorização da subscrição é definida no momento do `POST /api/realtime`; se o token mudar, voltar a subscrever.

## Erros comuns

| Código | Significado | Ação |
|---|---|---|
| 400 | Dados inválidos / regra falhou | Descartar operação, registar |
| 401 | Token inválido/expirado | `auth-refresh`, senão login |
| 403 | Regra de acesso | Verificar API rules |
| 404 | Registo inexistente | `DELETE`: ok; `PATCH`: descartar |
| 5xx / timeout | Servidor/rede | Retry com backoff |

## Retrofit

Implementação em `data/remote/`: `PocketBaseApi` (interface abaixo), `PocketBaseApiFactory` (Retrofit + `Json { ignoreUnknownKeys; explicitNulls = false }`), `AuthInterceptor` (lê o token de um `TokenProvider`) e `PocketBaseDates` (datas `"2026-10-29 12:34:56.789Z"` ↔ `Instant`). Os campos opcionais do `ItemDto` a `null` não são enviados no `POST`. O PocketBase devolve `""` para campos de texto/relação/select vazios; converter para `null` é responsabilidade do repositório.


```kotlin
interface PocketBaseApi {
    @POST("api/collections/users/auth-with-password")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/collections/users/auth-refresh")
    suspend fun refresh(): AuthResponse

    @GET("api/collections/items/records")
    suspend fun listItems(
        @Query("filter") filter: String,
        @Query("sort") sort: String = "created",
        @Query("perPage") perPage: Int = 200,
        @Query("page") page: Int = 1,
    ): ListResponse<ItemDto>

    @POST("api/collections/items/records")
    suspend fun createItem(@Body body: ItemDto): ItemDto

    @PATCH("api/collections/items/records/{id}")
    suspend fun updateItem(@Path("id") id: String, @Body body: JsonObject): ItemDto

    @DELETE("api/collections/items/records/{id}")
    suspend fun deleteItem(@Path("id") id: String): Response<Unit>
}
```
