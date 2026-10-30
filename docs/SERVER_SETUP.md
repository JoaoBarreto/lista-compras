# Configuração do Servidor

Executar no mini PC (Linux com Docker e Docker Compose instalados).

## 1. Ficheiros em `server/`

### `Dockerfile`

```dockerfile
FROM alpine:3.20
ARG PB_VERSION
RUN apk add --no-cache ca-certificates unzip wget
ADD https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_amd64.zip /tmp/pb.zip
RUN unzip /tmp/pb.zip -d /pb && rm /tmp/pb.zip && chmod +x /pb/pocketbase
EXPOSE 8090
CMD ["/pb/pocketbase", "serve", "--http=0.0.0.0:8090"]
```

Se o mini PC for ARM, trocar `linux_amd64` por `linux_arm64`.

### `.env` (não vai para o git; criar a partir de `.env.example`)

```
# Ver a última versão em https://github.com/pocketbase/pocketbase/releases (usar >= 0.23)
PB_VERSION=
```

### `docker-compose.yml`

```yaml
services:
  pocketbase:
    build:
      context: .
      args:
        PB_VERSION: ${PB_VERSION}
    container_name: pocketbase
    restart: unless-stopped
    ports:
      - "127.0.0.1:8090:8090"   # só local; o acesso externo é via tailscale serve
    volumes:
      - ./pb_data:/pb/pb_data
      - ./pb_migrations:/pb/pb_migrations
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8090/api/health"]
      interval: 30s
      timeout: 5s
      retries: 3
```

### `.gitignore` (raiz do repositório)

```
server/.env
server/pb_data/
android/local.properties
android/keystore.properties
*.jks
*.keystore
```

## 2. Arrancar

```bash
cd server
cp .env.example .env      # preencher PB_VERSION
docker compose up -d --build
curl -s http://127.0.0.1:8090/api/health
```

## 3. Criar o superuser

```bash
docker compose exec pocketbase /pb/pocketbase superuser create EMAIL PASSWORD
```

Dashboard (temporariamente local): `http://127.0.0.1:8090/_/`. Depois do passo 4, em `https://<maquina>.<tailnet>.ts.net:8443/_/`.

## 4. Tailscale + HTTPS

1. Instalar o Tailscale no **host** (não no container) e fazer login: `sudo tailscale up`.
2. No painel de administração do Tailscale: ativar **MagicDNS** e **HTTPS Certificates**.
3. Expor o PocketBase **apenas dentro da tailnet**, na porta **8443** (a 443 pode estar ocupada por outros serviços do host, como o Pi-hole):
   ```bash
   sudo tailscale serve --bg --https=8443 8090
   tailscale serve status
   ```
   Resultado: `https://<maquina>.<tailnet>.ts.net:8443` → `http://127.0.0.1:8090`, com certificado válido.
   Se a 443 estiver livre, podes usar `sudo tailscale serve --bg 8090` e dispensar a porta no URL.
4. Instalar a app Tailscale nos dois telemóveis, com a mesma conta/tailnet. Ativar "VPN sempre ligada" no Android para evitar ter de a ligar à mão.
5. **Não usar `tailscale funnel`**: expõe à internet pública.

Verificação a partir de um telemóvel com Tailscale ligado: abrir `https://<maquina>.<tailnet>.ts.net:8443/api/health` → `{"code":200,...}`.

> **Não testar a partir do próprio mini PC:** um pedido ao endereço `.ts.net` feito na própria máquina não passa pelo `tailscale serve` e pode ser respondido por outro serviço local (ex.: certificado self-signed do Pi-hole).

## 5. Configurar o PocketBase

No dashboard:

1. Collection `users`: aplicar as regras de [DATA_MODEL](DATA_MODEL.md), adicionar campo `name`, criar as duas contas.
2. Criar as collections `lists` e `items` conforme [DATA_MODEL](DATA_MODEL.md), com as regras indicadas.
3. Criar um registo em `lists` (ex.: "Compras").
4. Confirmar que existem ficheiros em `server/pb_migrations/` e fazer commit.

Teste rápido de regras (sem token deve falhar):

```bash
curl -i https://<maquina>.<tailnet>.ts.net:8443/api/collections/items/records
# esperado: 200 com lista vazia ou 403, nunca dados sem autenticação
```

> Nota: com `listRule` exigindo autenticação, um pedido sem token devolve 200 com `items: []`. O importante é não devolver dados.

## 6. Backups

O PocketBase tem backups integrados (Dashboard → Settings → Backups):

1. Ativar backup automático diário (cron `0 3 * * *`) e manter os últimos 7.
2. Copiar `server/pb_data/backups/` para **outro disco ou máquina** (um backup no mesmo disco não protege contra falha do disco). Exemplo com cron do host:
   ```
   30 3 * * * rsync -a /caminho/server/pb_data/backups/ /mnt/disco-externo/lista-compras-backups/
   ```
3. Testar o restauro pelo menos uma vez (Dashboard → Backups → Restore, ou extrair o zip para `pb_data/`).

## 7. Atualizar o PocketBase

1. Fazer backup.
2. Alterar `PB_VERSION` em `.env`.
3. `docker compose up -d --build`.
4. Ler as notas de versão antes de saltar versões maiores (podem ter alterações incompatíveis).

## Resolução de problemas

| Sintoma | Causa provável |
|---|---|
| App não liga, `curl` local funciona | Tailscale desligado no telemóvel, ou `tailscale serve` não ativo |
| Resposta JSON com `error` / `key` / `hint` / `took` | É o Pi-hole (ou outro serviço) na porta 443; servir o PocketBase noutra porta (8443) |
| Erro de certificado | HTTPS Certificates não ativado na tailnet |
| Dados perdidos após `docker compose down -v` | Volume apagado; `pb_data` deve ser bind mount (como acima) |
| Realtime cai com frequência | Normal em rede móvel; a app reconecta e ressincroniza |
