# stack.study

Cockpit para estudantes de qualquer curso: matérias e prazos, foco (Pomodoro/Flow), flashcards com
repetição espaçada e simulador de notas, numa plataforma só, em pt-BR e en.

| Módulo | O que faz |
| --- | --- |
| **Cockpit** (`/`) | Próximo passo sugerido, foco do dia e da semana, sequência, prazos, radar de risco, próxima prova, mini heatmap e primeiros passos |
| **The Stack** (`/stack`) | Semestres, matérias e tarefas por prazo (atrasadas, hoje, semana), filtros e atalhos de teclado |
| **Deep Work** (`/deep-work`) | Sessões Pomodoro/Flow no servidor (pausa e retomada), heatmap, sequência e meta semanal |
| **Flashcards** (`/flashcards`) | Decks, cartas em Markdown, SM-2, fila diária com limites, importação/exportação CSV |
| **Grade Predictor** (`/grades`) | Média ponderada, aritmética ou **soma de pontos**, nota necessária, simulador "e se", exame final, faltas e CR |
| **Perfil** (`/profile`) | Perfil, preferências (padrões de todas as telas), e-mail, senha, dispositivos, exportar dados e excluir conta |

Atalho global: **Ctrl K** (ações, foco e navegação).

## Capturas de tela

As imagens ficam em [`docs/screenshots/`](./docs/screenshots/) (lista do que capturar e como em
[`docs/screenshots/README.md`](./docs/screenshots/README.md)).

| Cockpit | Grade Predictor | Deep Work |
| --- | --- | --- |
| ![Cockpit](./docs/screenshots/cockpit.png) | ![Grade Predictor](./docs/screenshots/grades.png) | ![Deep Work](./docs/screenshots/deep-work.png) |

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Security (JWT + refresh em cookie HttpOnly), Spring Data JPA, Flyway, PostgreSQL 16, Springdoc
- **Frontend:** React 18, Vite 6, TypeScript (strict), Tailwind CSS 3, TanStack Query 5, Zustand, React Router 6, cmdk, react-i18next
- **Testes:** JUnit 5 + Testcontainers (Postgres real), Vitest + Testing Library
- **Produção (planos gratuitos):** Neon (Postgres) + Render (backend em Docker, 512 MB) + Vercel (frontend)

## Arquitetura

```
Navegador ──► Vercel (SPA estática, code splitting por tela)
                │  /api/*  rewrite: mesma origem, cookie sem CORS
                ▼
              Render (Spring Boot, uma instância)
                │  JWT (15 min, com id da sessão) · refresh rotativo em cookie HttpOnly
                │  rate limit em memória · cabeçalhos de segurança
                ▼
              Neon (PostgreSQL; pooled para a app, direta para o Flyway)

GitHub Actions: CI (testes dos dois lados, audit) + backup semanal criptografado (pg_dump + GPG)
```

- **Backend** em pacotes por feature (`auth`, `account`, `cockpit`, `subject`, `task`, `session`,
  `grade`, `flashcard`, `common`): controller → service → repository, DTOs em records, entidade
  nunca sai do service. Todo dado é filtrado pelo usuário do token; recurso de outro usuário
  responde 404.
- **Contas puras e testadas** fora do Spring: `GradeCalculator` (BigDecimal, com espelho em
  TypeScript para o simulador e os mesmos casos de teste nos dois lados), `Sm2`, `StreakCalculator`.
- **Frontend** em `features/<módulo>`; estado de servidor no TanStack Query, de tela no Zustand;
  todo texto no i18n.
- **Plano grátis:** o Cockpit carrega numa chamada só (16 consultas fixas, medidas em teste); o
  aviso "Acordando o servidor…" e os esqueletos cobrem o cold start do Render.

Convenções, API de cada módulo e as decisões (com o porquê) estão em [`CLAUDE.md`](./CLAUDE.md).
Desempenho (EXPLAIN das consultas e tamanho do bundle) em [`docs/performance.md`](./docs/performance.md).

### Decisões que mais pesam

- Access token só em memória; refresh em cookie HttpOnly com rotação e detecção de reuso.
- Sessões por dispositivo: encerrar uma derruba o acesso na hora (claim `sid` checado no filtro).
- Só login, cadastro, refresh, logout e os probes de health são públicos (um teste varre todas as
  rotas para garantir).
- Preferências do Perfil gravam nos settings de cada módulo (não há cópia dos dados).
- Exportação (LGPD) genérica por SQL e exclusão em cascata pelo banco.

## Rodando com Docker (recomendado)

```bash
cp .env.example .env
# preencha JWT_SECRET (ex.: openssl rand -base64 48) e troque as senhas do Postgres
docker compose up --build
# com dados de demonstração (usuário demo@stackstudy.local / demo-password):
SEED_DEMO=true docker compose up
```

| Serviço | URL |
| --- | --- |
| App | http://localhost:5173 |
| API | http://localhost:8080/api |
| Swagger UI (só em dev) | http://localhost:8080/swagger-ui |
| Health (liveness) | http://localhost:8080/actuator/health/liveness |
| Design system | http://localhost:5173/_design |

## Rodando localmente

Pré-requisitos: JDK 21, Node 22+, Docker (Postgres e testes de integração).

```bash
docker compose up -d postgres

# backend (as variáveis do .env precisam estar no ambiente; DB_URL apontando para localhost)
cd backend
./mvnw spring-boot:run

# frontend (o Vite faz proxy de /api para http://localhost:8080)
cd frontend
npm install
VITE_API_PROXY_TARGET=http://localhost:8080 npm run dev
```

## Testes e qualidade

```bash
cd backend && ./mvnw verify          # unitários + integração (Testcontainers) + Spotless
cd backend && ./mvnw spotless:apply  # formata o Java

cd frontend && npm test              # Vitest
cd frontend && npm run lint          # ESLint (sem warnings)
cd frontend && npm run typecheck
cd frontend && npm run format:check
cd frontend && npm run build

bash scripts/db-restore-drill.sh     # ensaio local de backup + restauração (Docker)
```

Os fluxos críticos têm testes dedicados: autenticação (rotação, reuso, sessões, rate limit),
rotas públicas versus protegidas, isolamento entre usuários em todos os módulos, as calculadoras
(notas, SM-2, sequências), exportação/exclusão de conta, Cockpit (número de consultas), planos de
consulta (EXPLAIN) e, no frontend, sessão expirada, offline, páginas de erro e contraste dos tokens.

## Deploy

Passo a passo (Neon + Render + Vercel), checklist, variáveis, backup, **restauração testada** e
solução de problemas em **[`DEPLOY.md`](./DEPLOY.md)**.

Imagem de produção do backend localmente (perfil `prod`, limite de 512 MB):

```bash
docker compose --profile prod up --build backend-prod   # http://localhost:8081/actuator/health/liveness
```

## Termos e privacidade

Os Termos de uso e a Política de Privacidade (LGPD) ficam em
`/terms` e `/privacy` no app, em pt-BR e en, com link no cadastro e no rodapé. **Antes de divulgar,
troque o contato de privacidade** (`legal.privacy` nos arquivos de idioma).
