# CLAUDE.md — StackStudy

Guia para quem (humano ou agente) trabalha neste repositório. **Mantenha este arquivo atualizado**
ao fim de cada fase: comandos, convenções, decisões.

## Regra de ouro: sem commits

**Nunca** rode `git commit`, `git push`, crie branches, tags ou PRs, e não faça stage (`git add`).
Todas as alterações ficam apenas no working tree; o dono do projeto revisa e commita manualmente.

## Produto

Cockpit para estudantes STEM. Módulos: **Cockpit** (`/`), **The Stack** (`/stack`), **Deep Work**
(`/deep-work`), **Flashcards** (`/flashcards`), **Grade Predictor** (`/grades`). Rota interna
`/_design` mostra o design system.

| Fase | Escopo | Status |
| --- | --- | --- |
| 0 | Fundação: monorepo, Docker, schema, auth, app shell, design system, CI | ✅ |
| 1 | The Stack: CRUD semestres/matérias/tarefas, board, entregas 14 dias | ⏳ |
| 2 | Deep Work: timer no backend, modos, commit message, heatmap, metas, streak | ⏳ |
| 3 | Flashcards: CRUD, Markdown + KaTeX, SM-2 (serviço puro), previsão 7 dias | ⏳ |
| 4 | Grade Predictor: regras, nota necessária, what-if, faltas, CR/GPA, PDF | ⏳ |
| 5 | Cockpit e polimento: dashboard, radar de risco, estados vazios, atalhos | ⏳ |

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

Pacotes por feature: `auth`, `subject` (inclui semestres), `task`, `session`, `flashcard`, `grade`,
`common`. Em cada feature: `*Controller` → `*Service` → `*Repository`, DTOs em `dto/` (records),
entidades JPA nunca saem do service.

- `common.error`: `ApiException` + `ErrorCode` + `GlobalExceptionHandler`. Toda resposta de erro
  segue `{ "code", "message", "details": [{ "field", "message" }] }`. O frontend traduz pelo `code`.
- `common.security`: `JwtService`, `JwtAuthenticationFilter`, `CurrentUser`, `SecurityProperties`.
- `common.persistence.BaseEntity`: `id` UUID, `created_at`, `updated_at`.
- Migrations em `src/main/resources/db/migration` (Flyway). `ddl-auto=validate`.

### Frontend (`frontend/src`)

```
app/            App, router, NAV_ITEMS (rotas e breadcrumbs)
components/ui/  Button, Card, Chip, ProgressBar, PageTitle, StatBlock, Heatmap, Icon, TextField, Wordmark
components/shell/ AppShell, Sidebar, Topbar, StatusBar, CommandPalette
features/<x>/   telas e hooks por módulo (auth já existe)
lib/api/        client.ts (fetch, refresh automático, retry de GET), auth.ts, types.ts,
                config.ts (VITE_API_URL, tempos), slow.ts (aviso de lentidão), wake.ts (wake-up ping)
stores/         Zustand: authStore, uiStore, sessionStore, networkStore
i18n/           react-i18next; locales/pt-BR.json (padrão) e en.json
styles/         tokens.css (design tokens) e index.css (base + componentes)
test/           setup.ts e utils.tsx
```

## Comandos

```bash
# Tudo via Docker (requer .env)
docker compose up --build           # app :5173, API :8080, Swagger :8080/swagger-ui
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

## Auth (como funciona)

- `POST /api/auth/register|login` → `{ accessToken, tokenType, expiresIn, user }` + cookie
  `ss_refresh` (HttpOnly, Path=/api/auth; dev: SameSite=Strict e sem Secure; prod: SameSite=Lax e
  Secure, ambos por env).
- Access token JWT HMAC-SHA (HS256–HS512, o jjwt escolhe pelo tamanho do `JWT_SECRET`), 15 min, só em memória no frontend (Zustand), nunca em localStorage.
- Refresh token: 32 bytes aleatórios, guardado como SHA-256 no banco, **rotação** a cada
  `POST /api/auth/refresh`. Reuso de um token já rotacionado revoga todos os tokens do usuário.
- `POST /api/auth/logout` revoga e limpa o cookie. `GET /api/me` devolve o perfil.
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
   `subjects.grading_rule` é `JSONB` (formato definido na Fase 4); `min_attendance_pct` padrão 75.
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
