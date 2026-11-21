# Desempenho (Fase 7)

## Consultas: EXPLAIN com volume realista

`backend/src/test/java/com/stackstudy/common/QueryPlanIntegrationTest.java` cria 300 usuários com
~40 linhas por tabela (≈ 12 mil tarefas, sessões e cartas), roda `ANALYZE` e confere o plano das
consultas mais chamadas. O teste falha se alguma voltar a varrer a tabela inteira.

| Consulta | Onde roda | Índice | Plano |
| --- | --- | --- | --- |
| Tarefas em aberto até 7 dias | Cockpit | `idx_tasks_user_status_due` (V2) | Bitmap Index Scan |
| Minutos por dia (12 semanas) | Cockpit, Deep Work | `idx_study_sessions_user_started` (V3) | Bitmap Index Scan |
| Contagens por deck | Cockpit, Flashcards | **`idx_cards_user_deck` (V8, novo)** | Bitmap Index Scan |
| Fila: vencidas até hoje | Revisão | `idx_cards_user_due` (V5, parcial) | Bitmap Index Scan |
| Revisões de hoje | Cockpit, Flashcards | `idx_card_reviews_user_reviewed` (V5) | Bitmap Index Scan |
| Sessões ativas | Filtro do JWT, Perfil | `idx_auth_sessions_user_id` (V7) | Bitmap Index Scan |

**Achado:** as contagens por deck (`CardRepository.countsByDeck`) filtram só por `user_id`, e os
dois índices de `cards` eram parciais (`due_on` nulo / não nulo). Antes da V8 o plano era:

```
GroupAggregate
  ->  Sort
        ->  Seq Scan on cards c
              Filter: (user_id = '…'::uuid)
```

Depois da V8 (`CREATE INDEX idx_cards_user_deck ON cards (user_id, deck_id)`):

```
HashAggregate
  ->  Bitmap Heap Scan on cards c
        ->  Bitmap Index Scan on idx_cards_user_deck
              Index Cond: (user_id = '…'::uuid)
```

O Cockpit continua com **16 consultas fixas** por carregamento (`CockpitIntegrationTest`).

## Frontend: tamanho do bundle

Medido com `npm run build` (Vite 6), tamanhos em kB, gzip entre parênteses.

| | Antes (Fase 6) | Depois (Fase 7) |
| --- | --- | --- |
| JS da primeira tela | 1 arquivo: 627 (188) | `index` 125 (42) + `react` 201 (66) + `i18n` 65 (21) + `query` 41 (13) + `palette` 53 (18) ≈ 485 (160) |
| Cada tela | junto no arquivo único | sob demanda: Cockpit 16 (4,7), Stack 27 (7,8), Notas 42 (10,7), Perfil 21 (5,9)… |

- **Code splitting por rota** (`app/lazyPages.ts`): login e cadastro ficam no pacote inicial; as
  telas baixam ao abrir, com esqueleto no lugar.
- **Bibliotecas em arquivos próprios** (`manualChunks`): mudam pouco, então o navegador as mantém
  em cache entre deploys (`/assets/*` com `immutable` na Vercel).
- `recharts` saiu das dependências (nunca foi usado).
- O `index` ainda leva os dois idiomas (com os textos de Termos e Privacidade). Separar `en` em
  arquivo próprio economizaria ~20 kB gzip; fica como próximo passo se o bundle crescer.

## Cache do TanStack Query

Padrões em `lib/queryClient.ts`: `staleTime` 30 s (navegar entre telas não refaz tudo),
`refetchOnWindowFocus` desligado (o backend pode estar dormindo), sem retry em 4xx e no máximo 2
em erros de rede/5xx (só GET; ver Decisão 22). Exceções por tela: fila de revisão com
`staleTime: Infinity` (a ordem não muda no meio da sessão). O Cockpit usa o padrão: voltar a ele em
menos de 30 s mostra o que já tinha; depois disso, mostra o anterior e atualiza por trás.
