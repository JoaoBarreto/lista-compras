# Decisões

Registo das decisões de produto e técnicas, para acompanhar as alterações ao longo do tempo. Mais recente em cima. Formato: data, decisão, porquê, consequências.

## 2026-11-02 · De "Lista de Compras" a "Life Manager"

- **Decisão:** a app passa a ser a app pessoal do casal. Já existem Compras e Tarefas; acrescentam-se calendário, notificações e quantidades opcionais (Fase 9 do [ROADMAP](ROADMAP.md)). Nome visível: **Life Manager**.
- **Porquê:** o utilizador quer uma única app para a vida a dois, não só compras.
- **Consequências:** `applicationId` (`com.joaobarreto.listacompras`), pacote `com.example.listacompras`, nome da base Room (`lista_compras.db`) e keystore **não mudam**. Mudar o `applicationId` criava uma app nova e os dois telemóveis perdiam os dados. Só muda `app_name` (e textos). O repositório mantém o nome `lista-compras`.

## 2026-11-02 · Notificações: verificação periódica (poupança de bateria)

- **Decisão:** avisos de "o outro adicionou…" por uma verificação periódica do WorkManager (~15 min, o mínimo do Android), com notificação local. Sem serviço permanente, sem FCM/UnifiedPush.
- **Porquê:** o utilizador quer gastar pouca bateria e não ter problemas no futuro (serviços permanentes são mortos pela Samsung; FCM exigia dependências novas, conta Google e trabalho no servidor). O atraso até ~15 min é aceite.
- **Atraso real:** o WorkManager periódico pode atrasar bastante mais de 15 min com o telemóvel parado (Doze, sobretudo Samsung). A verificação do critério faz-se com o telemóvel em uso normal; não é uma garantia de horário.
- **Alternativas rejeitadas:** serviço em primeiro plano com SSE (bateria, notificação fixa); FCM/UnifiedPush (dependências, servidor, dados via Google).
- **Consequências:** só funciona com o Tailscale ligado no telemóvel. Os **lembretes de eventos** são independentes: são alarmes locais, agendados a partir da base de dados local, por isso chegam à hora certa mesmo sem rede.

## 2026-11-02 · Calendário com tipos de evento extensíveis

- **Decisão:** cada evento tem um **tipo** (collection `event_types`: nome + cor). Tipos iniciais: Família, Veronica, João. O utilizador pode criar mais tipos na app. Eventos coloridos pela cor do tipo, sempre com o nome do tipo em texto também.
- **Porquê:** perceber à primeira vista de quem é cada evento; a lista de tipos não fica fixa no código.
- **Consequências:** duas collections novas sincronizadas (`event_types`, `events`), logo o motor de sync deixa de ser só para `items` (task 9.4) e a Room sobe para a versão 2 com migração. Sem eventos recorrentes por agora (ideia futura). Sem biblioteca de calendário: agenda em Compose + pickers do Material 3.

## 2026-11-02 · Quantidade opcional nas Compras

- **Decisão:** campo opcional ao lado do nome; vazio = nada gravado nem mostrado. O campo `quantity` já existe em todas as camadas, por isso não há alteração de esquema.
- **Fora do âmbito:** editar a quantidade de um item existente; quantidade nas Tarefas.

## 2026-11-03 · Esquema Room v2 completo na 9.4 e subscrição realtime com recuo

- **Decisão:** a 9.4 cria já todo o esquema v2 (coluna `collection`, tabelas `events` e `event_types`, entidades, DAOs, DTOs), em vez de só a coluna. Assim há **uma só migração** (1→2) e o motor de sync já tem onde escrever os registos do calendário. Os repositórios e a UI ficam para a 9.6/9.7.
- **Realtime:** a app subscreve `items/*`, `event_types/*` e `events/*`; se o servidor recusar com 4xx, volta a subscrever só `items/*`. O pull das collections do calendário é isolado do de `items`. Motivo: o servidor do calendário ainda não foi verificado e as Compras/Tarefas nunca podem perder o realtime por causa dele.
- **Para a 9.6:** um registo de `events` que não se descodifica faz o pull dessa collection falhar sempre (é registado e ignorado); decidir se se salta só esse registo. O aplicador de eventos passou a ignorar qualquer exceção (antes, uma falha de base de dados num evento de item forçava reconexão e novo pull).

## 2026-11-03 · Testes automáticos de interface em vez de testes manuais

- **Decisão:** sempre que possível, os testes que o utilizador faria à mão (criar, editar, apagar, avisos, texto lido pelo TalkBack, comportamento offline) passam a ser testes instrumentados do Compose, escritos pelo assistente. Com o telemóvel ligado por USB e depuração ativa, o assistente corre `adb` e `connectedDebugAndroidTest` por si; o utilizador só é chamado para o que exige uma pessoa (ver o ecrã, desbloquear, criar dados na app). O emulador do computador não arranca (*segfault*), por isso não há alternativa sem telemóvel.
- **Fica manual:** o que um teste não vê (aspeto, modo escuro) e o que exige ver o sistema agir (alarme real a disparar, reinício do telemóvel).

## 2026-11-03 · Lembretes de eventos: um só alarme exato (9.8)

- **Decisão:** a app mantém **um único alarme** (`AlarmManager`), o do próximo lembrete, calculado a partir do Room. Ao disparar, mostra o que estiver em atraso e agenda o seguinte. Editar, apagar, pull e realtime só obrigam a recalcular; não há alarmes por evento para seguir ou cancelar.
- **Permissão:** `USE_EXACT_ALARM` (Android 13+, concedida na instalação e irrevogável) e `SCHEDULE_EXACT_ALARM` só até ao Android 12L. A app é de uso pessoal, sem Play Store, por isso a política da Play não se aplica. Se alguma vez não for permitido, usa `setAndAllowWhileIdle`.
- **Marca (watermark):** guarda-se o instante até ao qual os lembretes já foram mostrados (`sync_meta.reminderWatermark`). O alarme é agendado a partir da marca, não de "agora", para um lembrete atrasado pelo Doze nunca se perder. Na primeira execução a marca é "agora" (instalar não repete o passado) e um relógio posto para trás faz a marca recuar.
- **Reagendar quando:** qualquer mudança nos eventos (observador no Room), arranque do telemóvel, atualização da app, mudança de hora e de fuso. O fuso lê-se de novo a cada cálculo.
- **Regras de tempo:** ver [DATA_MODEL](DATA_MODEL.md). Evento de dia inteiro: 09:00 locais. Evento com hora: um lembrete ainda se mostra até 2 horas depois do início.
- **Confirmado no telemóvel (2026-11-03):** o alarme está lá, à mesma hora, depois de `adb install -r` (o recetor volta a agendá-lo) e depois de reiniciar o telemóvel.

## 2026-11-03 · Grelha mensal com a semana a começar à segunda-feira

- **Decisão:** a grelha do calendário e o seu cabeçalho começam sempre na **segunda-feira** (constante `CalendarDates.WEEK_START`), em vez de seguir o idioma do telemóvel. Os dados de semana do idioma pt-PT podem indicar domingo, e o cabeçalho e as células têm de concordar sempre.
- **Dia selecionado:** é a única fonte de verdade do ecrã; o mês mostrado é o mês desse dia. Mudar de mês ajusta o dia (31 jan → 28 fev → 28 mar), como faz o `java.time`.
- **Os eventos de dia inteiro** aparecem no dia da sua data (lida em UTC), nunca recalculada no fuso do telemóvel.
