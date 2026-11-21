# CLAUDE.md — StackStudy

Guia para quem (humano ou agente) trabalha neste repositório. **Mantenha este arquivo atualizado**
ao fim de cada fase: comandos, convenções, decisões.

## Regra de ouro: sem commits

**Nunca** rode `git commit`, `git push`, crie branches, tags ou PRs, e não faça stage (`git add`).
Todas as alterações ficam apenas no working tree; o dono do projeto revisa e commita manualmente.

## Produto

Cockpit para estudantes de qualquer curso (posicionamento neutro, ver Decisão 27). Módulos:
**Cockpit** (`/`), **The Stack** (`/stack`), **Deep Work** (`/deep-work`), **Flashcards**
(`/flashcards`), **Grade Predictor** (`/grades`). Rota interna `/_design` mostra o design system.

| Fase | Escopo | Status |
| --- | --- | --- |
| 0 | Fundação: monorepo, Docker, schema, auth, app shell, design system, CI | ✅ |
| 1 | The Stack: semestres (atual/arquivar), matérias, tarefas por prazo, filtros, atalhos | ✅ |
| 2 | Deep Work: sessões no servidor (pausa/retomada), Pomodoro/Flow, heatmap, streak, meta | ✅ |
| 3 | Grade Predictor: avaliações, nota necessária, exame final, what-if, faltas, CR/GPA | ✅ |
| 4 | Flashcards: decks, cartas Markdown, SM-2 puro, fila diária com limites, revisão, CSV | ✅ |
| 5 | Perfil e preferências: perfil, padrões das fases, e-mail/senha, sessões, exportar/excluir (LGPD) | ✅ |
| 6 | Cockpit e polimento: dashboard, radar de risco, estados vazios, atalhos | ⏳ |

## Estrutura

```
/backend    Spring Boot (Java 21, Maven Wrapper)
/frontend   React + Vite + TS
/.github    workflows de CI (build + testes dos dois lados)
docker-compose.yml   postgres 16 + backend (dev) + frontend (dev); perfil "prod" = imagem de produção
.env.example         variáveis locais; copie para .env (gitignored)
render.yaml          Blueprint do Render (só o backend; banco é o Neon)
frontend/vercel.json rewrite /api -> Render + fallback de SPA
DEPLOY.md            passo a passo do deploy (Neon + Render + Vercel) e backup
```

### Backend (`com.stackstudy`)

Pacotes por feature: `auth` (inclui `AuthSession`), `account` (perfil, preferências, conta,
exportação), `subject` (inclui semestres), `task`, `session`, `flashcard`, `grade`, `common`. Em cada feature: `*Controller` → `*Service` → `*Repository`, DTOs em `dto/` (records),
entidades JPA nunca saem do service.

- `common.error`: `ApiException` + `ErrorCode` + `GlobalExceptionHandler`. Toda resposta de erro
  segue `{ "code", "message", "details": [{ "field", "message" }] }`. O frontend traduz pelo `code`.
- `common.security`: `JwtService`, `JwtAuthenticationFilter`, `CurrentUser`, `SecurityProperties`.
- `common.persistence.BaseEntity`: `id` UUID, `created_at`, `updated_at`.
- `common.web.PageResponse`: página estável `{ items, page, size, totalElements, totalPages,
  hasNext }` (nunca exponha o `Page` do Spring).
- `common.dev.DemoDataSeeder`: dados de demonstração, só perfil `dev` e só com `SEED_DEMO=true`
  (usuário `demo@stackstudy.local` / `demo-password`, fictício). Idempotente.
- Migrations em `src/main/resources/db/migration` (Flyway). `ddl-auto=validate`.

### Frontend (`frontend/src`)

```
app/            App, router, NAV_ITEMS (rotas e breadcrumbs)
components/ui/  Button, Card, Chip, ProgressBar, PageTitle, StatBlock, Heatmap, Icon, TextField,
                SelectField, TextAreaField, Dialog, Loading/Error/EmptyState, Wordmark
components/shell/ AppShell, Sidebar, Topbar, StatusBar, CommandPalette
features/<x>/   telas e hooks por módulo (auth, stack, deepwork, grades, flashcards)
features/flashcards/ FlashcardsPage (decks, busca, estatísticas), DeckPage (cartas, CSV),
                ReviewPage (revisão em foco), FlashcardDialogs (host lido do flashcardStore),
                CardList, Markdown.tsx; lógica pura e testada em markdownParse.ts,
                reviewSession.ts, csv.ts, days.ts
features/grades/ GradesPage, SubjectGradeCard, SimulatorCard ("e se"), SideCards (CR e faltas),
                GradeDialogs (preferências, regra, avaliações, faltas), SubjectGradeLine (linha na
                Stack), GradingRulePicker (seletor de regra, também no diálogo de matéria);
                calculator.ts = espelho do GradeCalculator (BigInt em centésimos),
                simulation.ts, format.ts, summary.ts (mensagem única por matéria)
features/deepwork/ DeepWorkPage, TimerCard, InsightsSections (stats, heatmap, histórico), dialogs;
                DeepWorkEngine (montado no AppShell: fim do pomodoro, pausas, alertas, título da aba);
                lógica pura e testada em clock.ts, pomodoro.ts, heatmapGrid.ts, format.ts
features/stack/ StackPage, SubjectPanel, TaskToolbar (filtros + criação rápida), TaskList,
                Task/Subject/Semester dialogs; lógica pura e testada em due.ts, grouping.ts,
                filters.ts, shortcuts.ts; queries.ts (TanStack Query)
features/profile/ ProfilePage (Perfil, Preferências, Conta, Privacidade), *Section, queries.ts;
                lógica pura e testada em helpers.ts (formulário ↔ PATCH, User-Agent, fusos, download)
lib/api/        client.ts (fetch, refresh automático, retry de GET), auth.ts, stack.ts, deepwork.ts,
                grades.ts, flashcards.ts, account.ts (/api/me/...),
                types.ts,
                config.ts (VITE_API_URL, tempos), slow.ts (aviso de lentidão), wake.ts (wake-up ping)
stores/         Zustand: authStore, uiStore, networkStore, flashcardStore (diálogo aberto, também
                pelo Ctrl K), stackStore (filtros, seleção e diálogo
                do The Stack), deepWorkStore (modo, matéria, ciclo e pausa; persistido no localStorage)
lib/            keyboard.ts (guardas dos atalhos), useNow.ts (re-render do timer), useDebouncedValue.ts,
                useTimeZone.ts (fuso do perfil ou do navegador), syncUserLanguage.ts (idioma da
                conta ao entrar), initials.ts (avatar)
i18n/           react-i18next; locales/pt-BR.json (padrão) e en.json
styles/         tokens.css (design tokens) e index.css (base + componentes)
test/           setup.ts e utils.tsx (`mockApi` roteia o fetch por "MÉTODO /caminho")
```

## Comandos

```bash
# Tudo via Docker (requer .env)
docker compose up --build           # app :5173, API :8080, Swagger :8080/swagger-ui
SEED_DEMO=true docker compose up    # idem, com dados de demonstração (só perfil dev)
docker compose down                 # (-v apaga o volume do Postgres)

# Backend
cd backend
./mvnw verify                       # testes (Testcontainers) + Spotless check
./mvnw spotless:apply               # formata (google-java-format)
./mvnw spring-boot:run              # precisa de DB_*, JWT_SECRET no ambiente

# Backend sem JDK local (o que foi usado na Fase 0 no Windows):
MSYS_NO_PATHCONV=1 docker run --rm -v "$PWD:/app" -v stackstudy_maven-repo:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -w /app maven:3.9-eclipse-temurin-21 ./mvnw -B verify

# Frontend
cd frontend
npm run dev | build | test | lint | format | format:check | typecheck
```

## Convenções

- Código, identificadores, API e commits em **inglês**; interface em **pt-BR** com i18n (pt-BR e en).
- **Comentários no código em português**, curtos e simples (Java, TS, CSS, SQL, YAML, Dockerfile).
  Mensagens da API e resumos do Swagger continuam em inglês.
- **Nenhum texto visível hard-coded**: tudo vem de `i18n/locales/*.json` (adicione a chave nos dois).
- API REST sob `/api`, JSON camelCase, datas ISO-8601 em UTC, IDs UUID.
- **Isolamento por usuário (obrigatório):** services obtêm o usuário só via `CurrentUser.id()`;
  repositórios expõem finders com escopo (`findByIdAndUserId`, ou join até `semesters.user_id`).
  Recurso de outro usuário responde **404** (não 403), para não vazar existência. Nunca aceite
  `userId` vindo do request. Toda feature nova precisa de um teste de "outro usuário não acessa".
- Migrations: **nunca altere uma migration já aplicada**; crie `V<n+1>__descricao.sql`.
- Java: google-java-format (Spotless), constructor injection, records para DTOs, `@Transactional`
  no service.
- TS: strict, `noUncheckedIndexedAccess`, alias `@/`, ESLint sem warnings, Prettier.
  Componentes exportam só componentes; helpers vão em arquivos `.ts` separados.
- Estado de servidor → TanStack Query; estado de UI/sessão → Zustand.
- Testes: serviços de cálculo (nota, GPA, SM-2) e ciclo de vida do timer exigem cobertura forte.

## Design system (identidade aprovada; siga à risca)

Tokens em `frontend/src/styles/tokens.css` (fonte única), mapeados no `tailwind.config.ts` via
`color-mix` (assim `bg-action/10` funciona). Não use hex solto em componentes.

| Token | Valor |
| --- | --- |
| `--bg` / `--surface` / `--surface-raised` | `#080808` / `#111111` / `#1a1a1a` |
| `--border` / `--border-control` | `#272727` / `#626262` |
| `--text` / `--text-secondary` / `--text-muted` / `--text-on-space` | `#f5f5f2` / `#a8a8a8` / `#929292` / `#c9c2da` |
| `--action` (única cor de ação) / hover / ink | `#cdfb50` / `#d9ff6e` / `#080808`, glow `0 0 22px rgba(205,251,80,.22)` |
| `--space-deep` / `--space-glow` | `#3c1c7e` / `#bd6cf7` |
| `--danger` / `--warning` | `#ff8a7a` / `#f2b861` |
| Matérias (ordem de atribuição) | azul `#6aa7ff`, âmbar `#f2b861`, roxo `#bd6cf7`, ciano `#5ad1e6`, rosa `#ff7a90`, lima `#cdfb50` |
| Heatmap 0–4 | `#1c1c1c`, `#2f3a14`, `#5a7119`, `#98bd2f`, `#cdfb50` |

- Fonte **Manrope** variável (self-hosted via `@fontsource-variable/manrope`), 400–800.
  `.tabular` (tabular-nums) em timers, notas e contadores.
- `.page-title`: 56px, 650, caixa alta, tracking -0.045em, line-height .98.
  `.card-title`: 20px, 650, -0.02em. `.label`: 12–13px, 650, caixa alta, .06em.
- Botões/chips em pílula (999px); `.card` raio 22px; `.card-inner` 16px; `.card-hero` com o
  brilho roxo (sessão ativa, timer, simulador, cartão de estudo). Fundo da página com glow roxo.
- Acessibilidade: contraste ≥ 4.5:1, foco visível verde-limão, alvos ≥ 44px, clicáveis sempre
  `<button>`/`<a>`, `aria-label` em ícones sem texto, ícones SVG de traço 1.8 (`<Icon>`), sem emoji.
- Wordmark `stack.study` minúsculo, 700, ponto verde-limão (`<Wordmark>`).
- Layout: sidebar 248px (drawer no mobile), topbar 52px com breadcrumb `~/stackstudy / <slug>` e
  busca `Ctrl K`, status bar 30px com a sessão ativa em **qualquer** tela.

## The Stack (Fase 1)

API (todas filtradas pelo usuário logado; recurso de outro usuário = 404):

- `GET/POST /api/semesters`, `GET/PUT /api/semesters/{id}`, `POST /api/semesters/{id}/archive`,
  `/unarchive`, `/current`. O primeiro semestre vira atual; arquivar o atual promove o mais
  recente não arquivado; arquivado não pode ser atual (`409 SEMESTER_ARCHIVED`).
- `GET /api/subjects[?semesterId=]`, `POST`, `GET/PUT/DELETE /api/subjects/{id}`. Cor só da
  paleta (sem cor = próxima da ordem); `pendingTasks` vem de uma consulta agrupada (sem N+1).
  Apagar a matéria apaga as tarefas dela (cascade).
- `GET /api/tasks` com `subjectId`, `withoutSubject`, `type` e `status` (repetidos ou separados por
  vírgula), `q` (título/descrição, sem diferenciar maiúsculas, curingas escapados), `dueFrom`,
  `dueTo`, `page`, `size` (1–200, padrão 50). Ordem: prazo crescente, sem prazo no fim.
  `POST`, `GET/PUT/DELETE /api/tasks/{id}`, `PATCH /api/tasks/{id}/status`.

Frontend: grupos Atrasadas / Hoje / Esta semana (até domingo) / Depois / Sem prazo (+ "Concluídas
anteriores" se mostrar feitas), calculados no fuso local. Atalhos: `Ctrl K` (Nova tarefa, Nova
matéria, Novo semestre, navegação), `N` nova, `E` editar selecionada, `Espaço` feita, setas/`j k`
para navegar. Nunca disparam dentro de campos.

## Deep Work (Fase 2)

API (`/api/deepwork`, tudo do usuário logado; de outro usuário = 404):

- `GET /sessions/active` (204 se não houver), `POST /sessions` (`SESSION_ALREADY_ACTIVE` 409),
  `POST /sessions/{id}/pause|resume` (idempotentes), `/complete` (`note` = commit message,
  `focusRating` 1–5), `/cancel` (descarta). Encerrada: `SESSION_NOT_ACTIVE` 409.
- `POST /sessions/manual`, `PUT /sessions/{id}` (só encerrada), `DELETE /sessions/{id}`,
  `GET /sessions` (histórico paginado). `GET/PUT /settings` (25/5/15, longa a cada 4, meta 10 h).
- `GET /heatmap?from=&to=&tz=` (padrão: últimos 12 meses; máx. 400 dias) e `GET /stats?tz=`.
  `tz` é nome IANA (offset solto é recusado); o frontend manda o fuso do navegador.

Tempo efetivo = (fim ou agora) − início − pausas (`paused_seconds` + pausa em aberto). Pomodoro
nunca passa do planejado; qualquer sessão no máximo 12 h. O frontend calcula o timer pelos
timestamps (+ diferença de relógio via `serverTime`); o `setInterval` só redesenha. Atalhos: Espaço
inicia/pausa na tela; Ctrl K "Iniciar foco em <matéria>".

## Grade Predictor (Fase 3)

API (tudo do usuário logado; matéria/falta de outro usuário = 404):

- `GET /api/grades/overview[?semesterId=]`: matérias do semestre (padrão: atual) com regra
  efetiva, avaliações, resultado e faltas, mais o CR por semestre e acumulado. 5 consultas fixas.
- `GET/PUT /api/grades/settings`: preferências (padrão das matérias + escala/peso do CR).
- `GET /api/subjects/{id}/grades`, `PUT /api/subjects/{id}/grading` (regra própria; campo null
  herda), `PUT /api/subjects/{id}/assessments` (substitui a lista: com `id` atualiza, sem cria,
  ausentes apaga; máx. 30).
- `GET/POST /api/subjects/{id}/absences`, `PUT/DELETE /api/absences/{id}`. Uma falta por matéria e
  dia (`ABSENCE_ALREADY_REGISTERED` 409); `count` = aulas perdidas (1–24).

Toda a conta fica em `grade.GradeCalculator` (puro, BigDecimal). O frontend tem o espelho em
`features/grades/calculator.ts` só para o simulador "e se" (nada é salvo). Os casos de
`backend/src/test/resources/grade-calculator-cases.json` rodam **nos dois lados** (JUnit e
Vitest): mudou a regra, mude o JSON e as duas implementações.

Regras de cálculo (conferir com a faculdade de cada um):

- Média ponderada = Σ(peso × nota) ÷ Σ(peso); pesos relativos (não precisam somar 100).
  Aritmética = todos os pesos 1. Padrões: ponderada, escala 0–10, mínima 6,0, faltas 25%.
- Média arredondada **uma vez**, 2 casas, meio para cima; a aprovação compara a média arredondada.
- Nota necessária = menor nota (2 casas, arredondada para cima) **igual em todas as avaliações sem
  nota** que leva a média a ≥ mínima. Acima da escala = não dá para passar direto.
- Exame final (opcional): tem direito quem fica abaixo da mínima e ≥ "média mínima do exame".
  Resultado = (p × média + (100 − p) × exame) ÷ 100, com p = peso da média (padrão 50, < 100);
  passa com resultado ≥ "mínima após o exame" (padrão 5,0). Com avaliações abertas, a nota de
  exame mostrada é a do melhor caso (nota máxima no que falta).
- Faltas: base = total de aulas da regra ou, sem ele, a carga horária. Limite =
  piso(base × limite% ÷ 100). Aviso a partir de 75% do limite (e no limite); erro acima dele.
  Faltas não alteram a situação das notas (o cartão mostra os dois).
- CR = Σ(nota final convertida × peso) ÷ Σ(peso), peso = créditos (ou carga horária). Conversão
  linear (nota × máx. do CR ÷ máx. da matéria), escala 0–10 ou 0–4.0. Entram matérias não
  trancadas com nota (parcial conta e é marcada como "parcial"); sem créditos/horas ficam de fora.
  O acumulado pondera todas as matérias juntas (não é a média dos semestres). Nota acima da
  escala (soma com bônus sem teto) entra como o máximo.

### Regra SUM (soma de pontos)

Regras de nota = enum `grade.GradingRule` (`WEIGHTED`, `ARITHMETIC`, `SUM`), gravado em
`subjects.grade_method` / `grade_settings.grade_method` (a coluna é da V4; a V6 só ampliou o
`CHECK`). Padrão das preferências continua `WEIGHTED` / 6,0 (nada existente muda); escolher SUM
nas preferências sugere mínima 7. O diálogo de matéria (The Stack) também escolhe a regra
(`gradingRule` em `POST/PUT /api/subjects`, null = herda).

- Avaliação na soma: `maxPoints` (vale quantos pontos, > 0, ≤ escala, obrigatório) e `score` de 0
  até `maxPoints`. Sem peso (o peso antigo fica guardado; trocar de regra não apaga nada).
- Total = Σ notas lançadas (exato). Aprova com total ≥ mínima comparando o valor **exato**
  (6,99 reprova com 7). `roundBeforeCompare` (padrão false) + `roundingScale` (0–2, padrão 1):
  arredonda o total meio para cima antes de comparar (6,95 → 7,0 aprova). Exibição: 2 casas.
- Faltam = limiar − total, para cima (2 casas). Pode ganhar = Σ `maxPoints` das sem nota.
  Já passou (`SECURED`) com pendentes; possível (`NOT_STARTED`/`ON_TRACK`/`AT_RISK`, em risco
  quando precisa de > mínima ÷ escala do que falta); impossível quando faltam > pode ganhar.
  Necessário por avaliação = faltam × vale ÷ pode ganhar (para cima), em `sum.requiredPerItem`.
- Σ `maxPoints` (sem bônus) ≠ escala: só aviso (`scaleMismatch`), nunca bloqueia.
- Bônus (`assessments.bonus`): soma ao total, fora do previsto. Teto opcional por matéria
  (`subjects.points_cap`, ≥ mínima). Padrão: sem bônus e sem teto.
- Exame: mesma regra das médias, com o total no lugar da média. Enquanto houver avaliação sem
  nota, `finalGrade` é null (pontos parciais não entram no CR).
- Nota acima de `maxPoints` gravada antes (matéria que era média) vira "sem vale quantos pontos"
  (`missingMaxPoints`), em vez de quebrar.

**Adicionar uma regra nova (um lugar só para a conta):** valor em `GradingRule`; ramo em
`GradeCalculator.evaluate` e no espelho `calculator.ts`; casos no JSON compartilhado; migration
ampliando os dois `CHECK`; textos `grades.method.*`, `grades.methodHint.*` e, se precisar, ramo em
`summary.ts` (`gradeHeadline`). Telas (cartão, Stack, simulador, Cockpit/radar futuros) **só leem**
o resultado e usam `gradeHeadline` para a mensagem; nenhuma refaz a conta.

## Flashcards (Fase 4)

API (`/api/flashcards`, tudo do usuário logado; de outro usuário = 404):

- `GET /decks?tz=` (contagens total/novas/vencidas e quantas entram hoje), `POST /decks`,
  `PUT/DELETE /decks/{id}` (apaga as cartas), `GET /decks/{id}/export` (`{ fileName, content }`),
  `POST /decks/{id}/import` (`{ csv }` → `{ imported, errors: [{ line, code }] }`).
- `GET /cards?deckId=&q=&page=&size=` (busca em frente, verso e tags), `POST /cards`,
  `GET/PUT/DELETE /cards/{id}` (`deckId` diferente move a carta, com o progresso).
- `GET /queue?deckId=&tz=`, `POST /cards/{id}/review` (`{ rating, tz }` → `{ reviewId, card }`),
  `DELETE /reviews/{id}` (desfaz; só a última da carta, senão `REVIEW_NOT_LATEST` 409).
- `GET /stats?deckId=&tz=`, `GET/PUT /settings` (padrão 20 novas e 100 revisões por dia).

Regras (`flashcard.Sm2`, puro e testado):

- Botões → qualidade: Errei 2, Difícil 3, Bom 4, Fácil 5 (`q < 3` = falha).
- Acerto: 1 dia, 6 dias, depois intervalo × EF (o EF **de antes** da resposta), meio para cima.
  Falha: repetições 0, intervalo 1, lapso se já tinha sido aprendida. EF muda em toda resposta
  (EF + 0,1 − (5 − q)(0,08 + (5 − q)·0,02)), mínimo 1,3. "Errei" volta amanhã (sem repetir na
  mesma sessão).
- Próxima revisão = **data local** de hoje (fuso do navegador) + intervalo (`cards.due_on`).
  Nova = nunca revisada (`due_on` nulo). Madura = intervalo ≥ 21 dias.
- Fila: vencidas (mais antigas primeiro) e depois novas (ordem de criação), cortadas pelos
  limites menos o que já foi respondido hoje (dia local). Limite próprio do deck nunca passa do
  global. Até 2.000 candidatas por tipo (`DailyLimits` puro).
- CSV (`CardCsv`, RFC 4180): `frente,verso[,tags]`, cabeçalho opcional, até 1 MB e 2.000 linhas;
  linha inválida vira erro com código (o frontend traduz) e não derruba as outras.

## Auth (como funciona)

- `POST /api/auth/register|login` → `{ accessToken, tokenType, expiresIn, user }` + cookie
  `ss_refresh` (HttpOnly, Path=/api/auth; dev: SameSite=Strict e sem Secure; prod: SameSite=Lax e
  Secure, ambos por env).
- Access token JWT HMAC-SHA (HS256–HS512, o jjwt escolhe pelo tamanho do `JWT_SECRET`), 15 min, só em memória no frontend (Zustand), nunca em localStorage.
- Refresh token: 32 bytes aleatórios, guardado como SHA-256 no banco, **rotação** a cada
  `POST /api/auth/refresh`. Reuso de um token já rotacionado (numa sessão ainda ativa) encerra
  todas as sessões do usuário. Token de sessão já encerrada só dá 401 (não é reuso).
- **Sessões (V7)**: cada login cria uma `auth_sessions` (User-Agent cortado em 255, sem IP); os
  refresh tokens giram dentro dela (`refresh_tokens.session_id`). O access token leva o claim
  `sid`; o `JwtAuthenticationFilter` aceita só se a sessão não foi encerrada
  (`SessionStatusChecker` → `ActiveSessionCache`, 30 s em memória e evict ao encerrar). Token sem
  `sid` (antes da V7) é recusado e o frontend renova pelo refresh. Sessões encerradas/vencidas há
  30 dias são apagadas no login (sem job de fundo).
- `POST /api/auth/logout` revoga, encerra a sessão e limpa o cookie. `GET /api/me` devolve o perfil.

## Perfil e conta (Fase 5)

API (`/api/me`, sempre o usuário do token; sessão de outro usuário = 404):

- `PUT /profile` (nome de exibição, curso, instituição, período, bio ≤ 280, `avatarColor` da paleta
  das matérias; vazio = sem valor). Avatar = iniciais + cor (sem upload).
- `GET/PATCH /preferences`: idioma e fuso (no usuário; `timeZone: ""` = navegador) e os padrões das
  outras fases **gravados nos settings delas** (`grade_settings`, `deep_work_settings`,
  `flashcard_settings`), numa transação. Campo ausente = mantém. Não há cópia: as telas das fases
  continuam lendo os próprios settings.
- `PUT /email` e `PUT /password` exigem `currentPassword` (`INVALID_CURRENT_PASSWORD` **400**, não
  401, que o frontend usa para renovar a sessão). Senha nova encerra as outras sessões, gira o
  refresh desta e devolve tokens + cookie novos.
- `GET /sessions`, `DELETE /sessions/{id}`, `DELETE /sessions/others`, `DELETE /sessions` (todas,
  inclusive esta; limpa o cookie).
- `GET /export`: JSON com todas as tabelas do usuário (`DataExportService.QUERIES`), sem hash de
  senha nem de token. Tabela nova com `user_id` precisa entrar lá (o teste confere).
- `DELETE /` com `{ email, currentPassword }`: apaga o usuário; o banco apaga o resto em cascata.

Frontend: idioma da conta aplicado ao entrar (`syncUserLanguage`) e trocado também pela barra
lateral (grava na conta). Fuso por `useTimeZone()` nas consultas de heatmap, estatísticas e fila.
- Frontend: `apiRequest` intercepta 401, faz **um** refresh compartilhado (dedupe) e repete a
  requisição; se o refresh falhar, limpa a sessão e `RequireAuth` manda para `/login`.
  No carregamento, `useSessionBootstrap` tenta restaurar a sessão pelo cookie.

## Variáveis de ambiente

Ver `.env.example`. Principais: `JWT_SECRET` (obrigatório, ≥ 32 chars), `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_ACCESS_TTL` (PT15M), `JWT_REFRESH_TTL` (P14D), `CORS_ALLOWED_ORIGINS`,
`REFRESH_COOKIE_SECURE`, `REFRESH_COOKIE_SAME_SITE`, `VITE_API_PROXY_TARGET`, `VITE_API_URL`
(padrão `/api`), `VITE_WAKE_URL`.
Perfis Spring: `dev` (padrão, logs DEBUG), `prod` (ver abaixo), `test`.

Produção (`application-prod.yml`, lista completa com descrição em `DEPLOY.md`): `DATABASE_URL`
(pooled), `DATABASE_DIRECT_URL` (direta, Flyway), `DATABASE_USERNAME`/`DATABASE_PASSWORD`
(opcionais), `JWT_SECRET`, `ALLOWED_ORIGINS`, `SWAGGER_ENABLED`, `DB_POOL_MAX`, `JVM_MAX_HEAP`,
`JAVA_OPTS`, `JAVA_TOOL_OPTIONS`, `PORT` (injetada pelo Render).

## Deploy (Neon + Render + Vercel)

Passo a passo em `DEPLOY.md`. Resumo da arquitetura:

- **Neon** (Postgres free): URL *pooled* para a aplicação, URL *direta* para o Flyway e o backup.
  `PostgresUrl` + `DatabaseUrlEnvironmentPostProcessor` (só no perfil `prod`) aceitam a URL do
  Neon como ela vem (`postgresql://user:senha@host/db`) e convertem para JDBC.
- **Render** (Docker, free, 512 MB, ~0,1 CPU): `backend/Dockerfile`, estágio final `prod` =
  `eclipse-temurin:21-jre-alpine`, usuário não-root, jar extraído + **CDS** (`app.jsa`, gerado por
  uma execução de treino no build). JVM: `-Xmx${JVM_MAX_HEAP:-300m}`, SerialGC, C1 só.
  Hikari máx. 5, Tomcat máx. 20 threads, health sem detalhes, Swagger desligado.
- **Health**: `/actuator/health/liveness` (só `livenessState`, **não toca no banco**) é o que se
  pinga: `healthCheckPath` do Render, monitor externo (UptimeRobot/cron-job.org, 5–10 min) e
  `VITE_WAKE_URL`. `/actuator/health` e `/actuator/health/readiness` (com `db`) são só para
  diagnóstico. Componentes (`/actuator/health/db`) não são públicos.
- **Vercel**: `frontend/vercel.json` faz rewrite de `/api/(.*)` para o Render (mesma origem, cookie
  sem CORS) e fallback de SPA. O build falha se o host ainda for o placeholder.
- **Cold start no frontend**: aviso "Acordando o servidor…" após 3 s (`ServerWakeNotice`),
  retry com backoff 1 s/2 s/4 s **só em GET** (rede, 502, 503, 504) e wake-up ping
  (`VITE_WAKE_URL`, direto no Render) antes do refresh inicial da sessão.
- **Backup**: `.github/workflows/db-backup.yml` (semanal + manual), `pg_dump -Fc` criptografado
  com GPG, como artifact. Repositório precisa ser privado.
- Teste local da imagem de produção: `docker compose --profile prod up --build backend-prod`
  (porta 8081, 512 MB). Para simular a CPU do Render: `docker run --cpus 0.1 --memory 512m ...`.

## Decisões

1. **Semestre mínimo na Fase 0**: `POST/GET /api/semesters` e `GET /api/semesters/{id}` existem só
   para provar (e testar) o isolamento entre usuários. Update, arquivar e delete ficam para a Fase 1.
2. **404 para recurso de outro usuário** (em vez de 403), para não revelar se o ID existe.
3. **Ownership por join**: seguindo o modelo aprovado, só `semesters` e `study_sessions` têm
   `user_id`; matérias, tarefas, decks e afins resolvem o dono via `subject → semester → user`.
4. **CSRF desabilitado**: a API é stateless (Bearer). O único cookie é o refresh, HttpOnly,
   SameSite=Strict e restrito a `/api/auth`.
5. **E-mail normalizado** (trim + minúsculas) antes de salvar e de buscar.
6. **Senha**: 8–72 caracteres (72 é o limite do BCrypt).
7. `study_sessions` tem índice único parcial: **no máximo uma sessão ativa por usuário**
   (`ended_at IS NULL`). Também há `CHECK`s de domínio (enums, faixas, cor `#rrggbb`).
8. `tasks.due_date` é `TIMESTAMPTZ` (permite horário de entrega); `cards.tags` é `TEXT[]`;
   `subjects.grading_rule` (JSONB) e `min_attendance_pct` saíram na V4 sem nunca terem sido
   usados (ver Decisão 41).
9. **Fonte self-hosted** (`@fontsource-variable`) em vez de Google Fonts: sem dependência externa.
10. **Tailwind 3** com tokens via `color-mix(... <alpha-value>)` para manter modificadores de opacidade.
11. `/_design` é pública (não mostra dados), para conferência visual sem login.
12. **Testcontainers 1.21.4** (override no pom): versões anteriores falham com Docker Engine 29+
    (versão mínima de API).
13. Recharts entra como dependência para gráficos complexos das próximas fases; heatmap e barras
    simples são SVG/CSS próprios (`<Heatmap>`).
14. Idioma da interface: preferência salva em `localStorage` (`stackstudy.lang`), padrão pt-BR; o
    `locale` do usuário é enviado no cadastro.
15. Corrida entre abas: duas abas fazendo refresh ao mesmo tempo podem disparar a detecção de reuso
    e deslogar ambas. É aceitável por ora; se incomodar, sincronizar o refresh com BroadcastChannel.
16. **Deploy em Neon + Render + Vercel** (todos free). O Postgres do Render não é usado (o free
    expira em 30 dias).
17. **Mesma origem via rewrite da Vercel**: o navegador só fala com `<app>.vercel.app/api`, então o
    cookie de refresh funciona sem CORS. Em prod o cookie é `SameSite=Lax` + `Secure`.
18. **CORS vazio = nenhuma configuração registrada** (não "rejeitar tudo"): com o rewrite, o
    backend pode ver um `Origin` diferente do próprio host; registrar CORS vazio daria 403.
19. **Flyway com conexão própria** (`DATABASE_DIRECT_URL`); a aplicação usa a URL pooled, com
    `prepareThreshold=0` (seguro atrás do PgBouncer).
20. **URL do Neon aceita como vem** (`postgresql://user:senha@host/db`): o mesmo valor serve no
    Render e no backup (`pg_dump` só entende esse formato).
21. **`JWT_SECRET` < 32 bytes impede o boot** (validação em `SecurityProperties`, todos os perfis).
22. **Retry só em GET** (rede, 502/503/504; backoff 1/2/4 s). POST nunca repete, e o refresh menos
    ainda: um refresh repetido depois de um timeout dispararia a detecção de reuso e deslogaria.
    Por isso, com `VITE_WAKE_URL`, o refresh inicial **espera o wake-up ping** (até 150 s).
23. **CDS na imagem de produção**: com ~0,1 CPU o boot medido caiu de ~106 s para ~92 s e a
    memória de ~231 MB para ~182 MB. O resto do cold start é limite de CPU do plano free.
24. `README.md` estava em UTF-16 (herdado do primeiro commit); foi convertido para UTF-8.
25. **Pings frequentes usam só o liveness.** Um health com banco a cada poucos minutos impede o
    Neon de dormir (scale to zero) e gasta CU-horas 24 h por dia (~180 CU-h/mês a 0,25 CU),
    acima da cota free. Coberto por `HealthProbesIntegrationTest` (spy no `DataSource`).
26. **CI do backend usa o Maven do runner** (`mvn verify`), não o `./mvnw`: o wrapper
    `only-script` baixa a distribuição do Maven sem retry, e uma falha de rede pontual derrubou o
    job. O `setup-java` faz cache do `~/.m2` e o `MVN_FLAGS` liga retry nos downloads. O wrapper
    (Maven 3.9.11, LF, testado limpo) continua para uso local.
27. **Posicionamento neutro**: o produto é para estudantes de qualquer curso, não só Engenharia/STEM.
    Textos (tagline, `index.html`, README, Swagger) não citam área; dados de exemplo misturam
    matérias de áreas diferentes. Um campo "curso" no perfil, quando existir (Fase de Perfil), será
    texto livre, sem lista fixa de cursos.
28. **Tarefa com matéria opcional → `tasks.user_id`** (V2). Exceção à Decisão 3: sem matéria não há
    join até o dono. A V2 preenche `user_id` das tarefas existentes pelo join antes do `NOT NULL`.
29. **Prazo = instante + `due_all_day`.** Sem hora, o frontend manda o fim do dia local
    (23:59:59) e marca dia inteiro: a tarefa só atrasa quando o dia acaba. Agrupamento por dia é
    feito no navegador (fuso do usuário). Mudar de fuso pode deslocar a data de tarefas sem hora.
30. **Tipos de tarefa**: `EXAM`, `ASSIGNMENT`, `READING`, `PROJECT`, `EXERCISE_LIST`, `OTHER`.
    `LAB` (V1) virou `OTHER` na V2. Prioridade `LOW/MEDIUM/HIGH`; status `TODO/DOING/DONE`, com
    `completed_at` preenchido ao concluir (base para streaks na Fase 2).
31. **Um semestre atual por usuário** via índice único parcial (`WHERE is_current`) e `CHECK` que
    proíbe atual + arquivado. Corridas que violem restrições respondem `409 CONFLICT`.
32. **Matérias numa consulta só** no frontend (`GET /api/subjects`), filtradas pelo semestre atual
    no cliente: tarefas de semestres antigos ainda mostram nome e cor da matéria.
33. **Lista de tarefas paginada** (100 por página no front, "Carregar mais"); a busca só vai à
    API após 250 ms sem digitar. Índice `(user_id, status, due_date)` cobre o filtro padrão.
34. **Seed de demonstração** só em `@Profile("dev & !prod")` + `SEED_DEMO=true`; um teste garante
    que o bean não existe fora do dev.
35. **Sessão de foco vive no servidor; pausas do pomodoro (curta/longa) só no navegador.** A
    pausa é um timestamp no `deepWorkStore` (localStorage): sobrevive a recarregar, mas não
    sincroniza entre abas/aparelhos. Pausa não vira registro no banco.
36. **Dia da sessão = dia local em que ela começou** (`started_at AT TIME ZONE tz`). Uma sessão
    que cruza a meia-noite conta inteira no dia do início. Streak, "hoje", semana (seg–dom), mês
    e média (últimos 30 dias ÷ 30) usam o mesmo fuso. Sequência atual vale até o fim do dia
    seguinte ao último estudo.
37. **Fim do pomodoro com o app fechado**: ao reabrir, o `DeepWorkEngine` conclui sozinho e o
    servidor limita o efetivo ao planejado. Duas abas concluindo juntas: a segunda recebe 409 e
    só ressincroniza.
38. **Heatmap com faixas fixas** (0, ≤30 min, ≤1 h, ≤2 h, >2 h) em vez de relativas ao máximo:
    o mesmo dia não muda de cor quando surge um dia recorde.
39. `deep_work_settings` é criada sob demanda (sem linha = padrões). `sessionStore` saiu: a
    sessão ativa vem do TanStack Query (`useActiveSession`), inclusive na status bar.
40. Seed de demo (dev) inclui ~5 meses de sessões manuais determinísticas para o heatmap.
41. **Regra de nota em colunas tipadas (V4)** em vez do JSONB `grading_rule`: dá para ter `CHECK`
    e validação por campo. `grades` virou `assessments` (nota na escala da matéria, sem
    `max_value`, peso > 0) e `absences` ganhou índice único `(subject_id, date)`. Nada disso tinha
    API, então não havia dado real para migrar.
42. **Regra por matéria = só o que difere** (colunas null herdam de `grade_settings`). Mudar a
    preferência muda todas as matérias sem regra própria. `grade_settings` é criada sob demanda.
43. **Arredondamento único e consistente**: a média exibida é a que decide a aprovação, e a nota
    necessária é a menor que, com esse arredondamento, aprova (testado: necessária aprova,
    necessária − 0,01 reprova). Simulador e backend usam os mesmos casos de teste.
44. **Lista de avaliações salva de uma vez** (`PUT .../assessments`): o diálogo edita a tabela e
    manda tudo. Id de avaliação de outra matéria/usuário = 404.
45. **Escala não desce abaixo da maior nota lançada** (validação em `PUT .../grading`). Se a
    preferência mudar e deixar uma matéria herdada incoerente (ex.: mínima > escala), a
    calculadora não quebra: mostra "impossível".
46. **Faltas na Stack** vêm do mesmo `GET /api/grades/overview` (cache do TanStack Query); salvar
    matéria ou semestre invalida as notas.
47. **Cartas e revisões com `user_id`** (V5), como as tarefas: a fila usa o índice
    `(user_id, due_on)` sem join até o semestre. Deck também tem dono direto (matéria opcional);
    apagar a matéria deixa o deck solto (`ON DELETE SET NULL`) em vez de perder as cartas.
48. **Próxima revisão como `DATE` local**, não instante: "vence hoje" é comparado com a data do
    fuso enviado. Viajar de fuso pode adiantar/atrasar um dia, como no Deep Work.
49. **Desfazer guarda o estado anterior na própria revisão** (`prev_*` em `card_reviews`);
    desfazer apaga a revisão, então o limite do dia volta a contar certo.
50. **Markdown simples próprio** (`markdownParse.ts`), sem HTML cru nem dependência; KaTeX ficou
    de fora. Import/export de CSV trafegam como JSON (sem multipart nem mudar o cliente HTTP).
    Export não neutraliza fórmulas de planilha (`=...`): o arquivo é do próprio usuário.
51. A fila é lida uma vez por sessão de revisão (sem refetch); ao sair, decks e estatísticas são
    invalidados.
52. **Soma de pontos reaproveita `grade_method`** (V6) em vez de criar `grading_rule`: a coluna já
    era a regra por matéria (null = herda). Criar outra duplicaria a fonte da verdade.
53. **Soma compara o valor exato por padrão**; arredondar antes é opção (`roundBeforeCompare`).
    Médias continuam como antes (2 casas, comparação com a média arredondada).
54. **Soma parcial não entra no CR** (`finalGrade` null até tudo lançado): 3 de 10 pontos no começo
    do semestre não é nota 3. Médias seguem entrando como parciais.
55. **Trocar de regra preserva dados**: peso, `maxPoints` e bônus ausentes no PUT de avaliações
    mantêm o valor gravado; a conta usa só os campos da regra efetiva.
56. **Sessão como tabela própria** (`auth_sessions`) em vez de agrupar refresh tokens: os tokens
    giram a cada 15 min, a sessão é o que o usuário reconhece ("Chrome no Windows").
57. **Encerrar sessão corta o access token na hora** (claim `sid` + checagem no filtro com cache de
    30 s). Custa uma consulta por sessão a cada 30 s; com várias instâncias o corte pode levar 30 s.
58. **Preferências sem duplicar dados**: o Perfil é uma porta única (`PATCH /api/me/preferences`)
    para os settings que já existiam em cada fase. Os padrões continuam os mesmos (nada muda para
    quem nunca abriu o Perfil).
59. **Excluir conta pede e-mail digitado e senha** (o pedido era só o e-mail; a senha evita
    exclusão por quem pegou o computador desbloqueado). `DELETE` direto em `users` + cascade.
60. **Exportação genérica por SQL** (colunas viram camelCase): uma coluna nova aparece sozinha;
    hashes de senha/token são filtrados também por nome de coluna.
61. Sem limite de tentativas na senha atual (como no login). Se virar problema, rate limit por
    usuário nos endpoints de `/api/me` que pedem senha.
