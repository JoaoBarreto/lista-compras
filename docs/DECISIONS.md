# Decisões

Registo das decisões de produto e técnicas, para acompanhar as alterações ao longo do tempo. Mais recente em cima. Formato: data, decisão, porquê, consequências.

## 2026-11-02 · De "Lista de Compras" a "Life Manager"

- **Decisão:** a app passa a ser a app pessoal do casal. Já existem Compras e Tarefas; acrescentam-se calendário, notificações e quantidades opcionais (Fase 9 do [ROADMAP](ROADMAP.md)). Nome visível: **Life Manager**.
- **Porquê:** o utilizador quer uma única app para a vida a dois, não só compras.
- **Consequências:** `applicationId` (`com.joaobarreto.listacompras`), pacote `com.example.listacompras`, nome da base Room (`lista_compras.db`) e keystore **não mudam**. Mudar o `applicationId` criava uma app nova e os dois telemóveis perdiam os dados. Só muda `app_name` (e textos). O repositório mantém o nome `lista-compras`.

## 2026-11-02 · Notificações: verificação periódica (poupança de bateria)

- **Decisão:** avisos de "o outro adicionou…" por uma verificação periódica do WorkManager (~15 min, o mínimo do Android), com notificação local. Sem serviço permanente, sem FCM/UnifiedPush.
- **Porquê:** o utilizador quer gastar pouca bateria e não ter problemas no futuro (serviços permanentes são mortos pela Samsung; FCM exigia dependências novas, conta Google e trabalho no servidor). O atraso até ~15 min é aceite.
- **Alternativas rejeitadas:** serviço em primeiro plano com SSE (bateria, notificação fixa); FCM/UnifiedPush (dependências, servidor, dados via Google).
- **Consequências:** só funciona com o Tailscale ligado no telemóvel. Os **lembretes de eventos** são independentes: são alarmes locais, agendados a partir da base de dados local, por isso chegam à hora certa mesmo sem rede.

## 2026-11-02 · Calendário com tipos de evento extensíveis

- **Decisão:** cada evento tem um **tipo** (collection `event_types`: nome + cor). Tipos iniciais: Família, Veronica, João. O utilizador pode criar mais tipos na app. Eventos coloridos pela cor do tipo, sempre com o nome do tipo em texto também.
- **Porquê:** perceber à primeira vista de quem é cada evento; a lista de tipos não fica fixa no código.
- **Consequências:** duas collections novas sincronizadas (`event_types`, `events`), logo o motor de sync deixa de ser só para `items` (task 9.4) e a Room sobe para a versão 2 com migração. Sem eventos recorrentes por agora (ideia futura). Sem biblioteca de calendário: agenda em Compose + pickers do Material 3.

## 2026-11-02 · Quantidade opcional nas Compras

- **Decisão:** campo opcional ao lado do nome; vazio = nada gravado nem mostrado. O campo `quantity` já existe em todas as camadas, por isso não há alteração de esquema.
- **Fora do âmbito:** editar a quantidade de um item existente; quantidade nas Tarefas.
