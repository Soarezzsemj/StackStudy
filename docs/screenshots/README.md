# Capturas de tela

O README referencia estes arquivos (PNG, 1440 × 900, tema escuro):

| Arquivo | Tela | Estado sugerido |
| --- | --- | --- |
| `cockpit.png` | `/` | Conta de demonstração: próximo passo, resumo, prazos e radar de risco |
| `grades.png` | `/grades` | História (soma de pontos) e Anatomia (exame) visíveis, simulador aberto |
| `deep-work.png` | `/deep-work` | Timer rodando e heatmap com meses de dados |

## Como gerar

1. `SEED_DEMO=true docker compose up --build` e entre com `demo@stackstudy.local` / `demo-password`.
2. No Chrome, DevTools → *Toggle device toolbar* → 1440 × 900 → menu ⋮ → *Capture screenshot*.
3. Salve com os nomes acima nesta pasta.

Os dados da conta de demonstração são fictícios (Decisão 34 do CLAUDE.md); não capture telas de
contas reais.
