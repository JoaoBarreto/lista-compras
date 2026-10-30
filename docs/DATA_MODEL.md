# Modelo de Dados

Fonte de verdade do esquema. Qualquer alteração aqui implica uma migração em `server/pb_migrations/` (o PocketBase gera-as automaticamente ao editar collections no dashboard; fazer commit delas).

> Nota: a partir do PocketBase 0.23 os campos chamam-se *fields* (antes *schema*) e existem *superusers* em vez de *admins*. Usar uma versão ≥ 0.23.

## Collection `users` (auth, built-in)

Duas contas, criadas manualmente. Ajustes:

| Definição | Valor | Porquê |
|---|---|---|
| `createRule` | *(vazio / null: só superuser)* | Sem registo público |
| `listRule` / `viewRule` | `@request.auth.id != ""` | Cada um precisa de ver o nome do outro (`added_by`) |
| `updateRule` | `id = @request.auth.id` | Só edita o próprio perfil |
| `deleteRule` | *(null)* | |
| Campo extra `name` | text | Nome a mostrar |

## Collection `lists`

| Campo | Tipo | Obrigatório | Notas |
|---|---|---|---|
| `id` | text (auto, 15 chars) | sim | |
| `name` | text | sim | Ex.: "Supermercado" |
| `created` / `updated` | autodate | sim | |

**Regras (list/view/create/update/delete):** `@request.auth.id != ""`

MVP: existe uma única lista, criada manualmente no dashboard (ou por migração seed). A app usa a primeira lista. Várias listas ficam para a Fase 7.

## Collection `items`

| Campo | Tipo | Obrigatório | Notas |
|---|---|---|---|
| `id` | text (auto, 15 chars `[a-z0-9]`) | sim | **Pode ser enviado pelo cliente no create** |
| `list` | relation → `lists` (single) | sim | Cascade delete |
| `name` | text (1–100) | sim | |
| `quantity` | text (≤ 30) | não | Livre: "2", "500 g", "1 cx" |
| `checked` | bool | sim (default `false`) | |
| `category` | select (single) | não | `oils_sauces`, `drinks`, `meat`, `deli`, `frozen`, `fruit`, `personal_care`, `dairy`, `cleaning`, `sweet_pantry`, `savory_pantry`, `bakery`, `fish_seafood`, `pet`, `baby`, `snacks`, `household`, `vegetables`, `other`. Editado à mão no dashboard; os nomes em português ficam no `strings.xml` |
| `added_by` | relation → `users` (single) | sim | |
| `checked_by` | relation → `users` (single) | não | Preenchido ao marcar |
| `created` / `updated` | autodate | sim | |

**Índices:** `(list, checked)`.

**Regras:**

| Operação | Regra |
|---|---|
| list / view | `@request.auth.id != ""` |
| create | `@request.auth.id != "" && @request.body.added_by = @request.auth.id` |
| update | `@request.auth.id != ""` |
| delete | `@request.auth.id != ""` |

Com apenas duas contas criadas pelo superuser, "autenticado" equivale a "autorizado". Se alguma vez houver mais agregados, será preciso modelar membros por lista e restringir as regras.

## Modelos na app

### Domain

```kotlin
data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: String?,
    val checked: Boolean,
    val category: Category?,
    val addedBy: String,
    val checkedBy: String?,
    val updatedAt: Instant,
    val syncState: SyncState,
)

// `slug` is the server value (e.g. DAIRY("dairy")); Category.fromSlug() returns null for unknown/blank.
enum class Category(val slug: String) {
    OILS_SAUCES, DRINKS, MEAT, DELI, FROZEN, FRUIT, PERSONAL_CARE, DAIRY, CLEANING,
    SWEET_PANTRY, SAVORY_PANTRY, BAKERY, FISH_SEAFOOD, PET, BABY, SNACKS, HOUSEHOLD,
    VEGETABLES, OTHER,
}
enum class SyncState { SYNCED, PENDING }
```

### Room

Base de dados `lista_compras.db` (versão 1, schema exportado em `android/app/schemas/`; sem `fallbackToDestructiveMigration`: alterar o esquema exige migração). Excluída dos backups do Android, porque o servidor é a fonte de verdade e um outbox restaurado repetiria operações antigas.

- `items` (`ItemEntity`): `id` (PK), `listId`, `name`, `quantity?`, `checked`, `category?` (string crua do servidor; o mapeamento para `Category` é feito no repositório), `addedBy`, `checkedBy?`, `createdAt`, `updatedAt` (epoch millis), `syncState`. Índice `(listId, checked)`.
- `pending_ops` (`PendingOpEntity`): `opId` (auto), `itemId`, `type` (`CREATE|UPDATE|DELETE`), `payloadJson`, `createdAt`, `attempts`. Índice em `itemId`. **Sem chave estrangeira para `items`**: um `DELETE` pendente tem de sobreviver à linha apagada.
- `sync_meta` (`SyncMetaEntity`): `key` (PK), `value`. Chaves em uso: `activeListId`, `discardedOps` (operações descartadas), `lastFullSyncAt` (último pull com sucesso, epoch millis).

DAOs: `ItemDao`, `PendingOpDao` (com `updatePayload`, usado pela coalescência), `SyncMetaDao` (com `observe(key)`, usado para a chave `activeListId`). Os testes usam base de dados em memória (`androidTest`).

### Repositório e outbox

`ItemRepositoryImpl` (`data/repository`) escreve em `items` e `pending_ops` na mesma transação (`TransactionRunner`, que no Room é `withTransaction`). `payloadJson` é exatamente o corpo HTTP que o `SyncWorker` (4.1) envia, serializado com o `Json` de `PocketBaseApiFactory`. Esta camada não funde operações (isso é do `OutboxCoalescer`, ver ARCHITECTURE) nem envia nada.

| Operação | Efeito em `items` | `pending_op` |
|---|---|---|
| `add` | insere com `syncState = PENDING` | `CREATE`, `ItemDto` completo (sem campos nulos) |
| `setChecked(true)` | `checked`, `checkedBy = userId`, `PENDING` | `UPDATE`, `{"checked":true,"checked_by":"<uid>"}` |
| `setChecked(false)` | `checked = false`, `checkedBy = null`, `PENDING` | `UPDATE`, `{"checked":false,"checked_by":""}` (string vazia: um `null` seria omitido e o servidor manteria o valor antigo) |
| `delete` | apaga a linha | `DELETE`, `{}` (o id vai em `itemId`) |
| `clearChecked` | apaga os marcados da lista ativa | um `DELETE` por item |

Operações sobre um id inexistente não fazem nada. `add` devolve `false` (sem alterar nada) se ainda não houver lista ativa ou utilizador.

**Lista ativa:** guardada em `sync_meta` na chave `activeListId` (`ActiveListProvider`). `ListRepository.ensureActiveList()` vai buscar a primeira lista ao servidor (`GET lists?sort=created&perPage=1`) só se a chave ainda não existir. `SessionViewModel` chama-o quando há sessão (logo após o login, que já exige rede, e em arranques seguintes se a tentativa falhou). Depois disso o id é local e `add` funciona sempre offline. Até haver lista, `observeItems()` emite lista vazia e `add` devolve `false`.

### DTO (rede)

`ItemDto` com `@Serializable`, campos em `snake_case` mapeados com `@SerialName`. Usar `ignoreUnknownKeys = true`. As datas do PocketBase vêm no formato `"2026-10-29 12:34:56.789Z"` (espaço em vez de `T`); tratar no parser.

## Geração de IDs

```kotlin
fun newRecordId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return buildString { repeat(15) { append(chars.random()) } }
}
```

(Usar `SecureRandom` ou `Random.Default`; a colisão com 36^15 é desprezável.)
