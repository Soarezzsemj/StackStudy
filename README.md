# stack.study

Cockpit para estudantes de qualquer curso: matérias e entregas, foco (Pomodoro/Flow),
flashcards com repetição espaçada e simulador de notas em uma única plataforma minimalista.

| Módulo | O que faz | Fase |
| --- | --- | --- |
| **Cockpit** | Sessão ativa, radar de risco, próximas entregas, metas semanais, heatmap | 5 |
| **The Stack** | Matérias em cards, tarefas TODO/DOING/DONE com tipo, prazo e peso | 1 |
| **Deep Work** | Pomodoro/Flow por matéria, "commit message" da sessão, heatmap | 2 |
| **Flashcards** | Decks por matéria, SM-2, previsão de revisões de 7 dias | 3 |
| **Grade Predictor** | Nota necessária, cenários, faltas, CR (0–10) e GPA (4.0) | 4 |

**Status:** Fase 0 (fundação) concluída: auth completa, app shell, design system e schema inicial.

## Stack

- **Backend:** Java 21 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA · Flyway · PostgreSQL 16 · Springdoc
- **Frontend:** React 18 · Vite · TypeScript · Tailwind CSS · TanStack Query · Zustand · React Router · cmdk · react-i18next
- **Testes:** JUnit 5 + Testcontainers · Vitest + Testing Library

## Rodando com Docker (recomendado)

```bash
cp .env.example .env
# preencha JWT_SECRET (ex.: openssl rand -base64 48) e troque as senhas do Postgres
docker compose up --build
```

| Serviço | URL |
| --- | --- |
| App | http://localhost:5173 |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui |
| Health | http://localhost:8080/actuator/health |
| Design system | http://localhost:5173/_design |

## Rodando localmente

Pré-requisitos: JDK 21, Node 22+, Docker (para o Postgres e para os testes de integração).

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
cd backend && ./mvnw verify        # testes + Testcontainers + Spotless check
cd backend && ./mvnw spotless:apply # formata o Java

cd frontend && npm test            # Vitest
cd frontend && npm run lint        # ESLint
cd frontend && npm run format      # Prettier
cd frontend && npm run typecheck
```

## Deploy

Produção em planos gratuitos: **Neon** (Postgres) + **Render** (backend, Docker) + **Vercel**
(frontend, com rewrite de `/api` para o Render). Passo a passo, checklist, variáveis de ambiente,
backup e solução de problemas em **[`DEPLOY.md`](./DEPLOY.md)**.

Para testar a imagem de produção do backend localmente (perfil `prod`, limite de 512 MB):

```bash
docker compose --profile prod up --build backend-prod   # http://localhost:8081/actuator/health
```

Mais detalhes de arquitetura, convenções e decisões estão em [`CLAUDE.md`](./CLAUDE.md).
