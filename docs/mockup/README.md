# Mockups do StackStudy

> **As imagens são REFERÊNCIA VISUAL** (layout, espaçamento, hierarquia, estilo de componentes).
> Nomes, matérias, números, datas e preços são **fictícios** e nunca viram texto fixo no app.
> Quando o mockup divergir de um prompt de fase em texto, **vale o prompt**.

Os PNGs foram otimizados sem perda (pixels idênticos, largura original de 2880 px) na Fase 13.1.

| Tela | Arquivo | Rota real | Fase | O que o mockup mostra e o app ainda não tem |
| --- | --- | --- | --- | --- |
| Login | `login.png` | `/login` | já existe (visual: 13.1) | Botão "Entrar com Google" no login (o OAuth existe, desligado, e hoje só o cadastro o oferece); "Esqueci a senha"; "Manter conectado neste dispositivo". |
| Cadastro | `cadastro.png` | `/signup` | já existe (Fase 11; visual: 13.1) | "Continuar com Google" só aparece com `AUTH_GOOGLE_ENABLED`; linha "Free · conta gratuita" na prévia do perfil (depende de planos). |
| Cockpit | `cockpit.png` | `/` | já existe (Fase 6) | Cartão principal com o cronômetro da sessão ativa e botões Finalizar/Pausar (o app mostra o "próximo passo"); "Metas da semana" por matéria; peso de cada entrega; heatmap de 20 semanas com hoje/sequência/semestre ao lado; título "COCKPIT" (o app usa a saudação). |
| The Stack | `the-stack.png` | `/stack` | já existe (Fase 1); quadro: Fase 15 | Grade de cartões de matéria (código, professor, tarefas feitas/total, próxima entrega, horas da semana); quadro kanban TODO/DOING/DONE por matéria (Fase 15). O app mantém a lista por prazo. |
| Deep Work | `deep-work.png` | `/deep-work` | já existe (Fase 2) | Abas 25/5, 50/10, Custom e Flow (o app tem Pomodoro/Flow e o ritmo nas preferências); "mensagem de commit" no cartão (o app pede ao concluir); "Log de hoje" ao lado do cronômetro; filtro do heatmap por matéria; "Distribuição · semana" junto do heatmap. |
| Flashcards | `flashcards.png` | `/flashcards` | já existe (Fase 4) | Botão "Criar com IA"; revisão embutida na página (o app revisa em `/flashcards/review`); retenção como número do topo; lista lateral de decks com "N hoje". |
| Grade Predictor | `grade-predictor.png` | `/grades` | Fase 3; layout: Fase 14 | Barras do CR por semestre e o GPA 4,0 ao lado do CR 0–10 (o app mostra CR do semestre e acumulado na escala escolhida). Abas, regra, destaque, "precisa X", cenários e "Todas as matérias" já seguem o mockup. |
| Salas de estudo | `salas-de-estudo.png` | `/rooms`, `/rooms/:id` | já existe (Fase 10) | Título curto "SALAS"; código com botão Copiar no cartão da sala; "Bloquear usuário" no cartão de regras. |
| Horários | `horarios.png` | — | futura | Tela inteira (grade semanal de aulas, "hoje", adicionar aula, conflitos) e o item "Horários" no menu. |
| Cadernos | `cadernos.png` | — | futura | Tela inteira (notas por matéria, editor, IA na nota, exportar) e o item "Cadernos" no menu. |
| Estudo com IA | `estudo-com-ia.png` | — | futura | Tela inteira (enviar material, gerar flashcards e questões, revisar e salvar). |
| Planos | `planos.png` | — | futura | Tela inteira (planos, cobrança, perguntas) e o cartão "Plano grátis · IA 7 de 10" na barra lateral. |
| Gerenciar assinatura | `gerenciar-assinatura.png` | — | futura | Tela inteira (plano atual, uso, histórico de cobranças). |

Elementos que aparecem em todas as telas do mockup e dependem de fase futura: cartão de plano/uso
de IA na barra lateral, itens "Horários" e "Cadernos" no menu e "GPA" preenchido na barra de status.
As matérias da barra lateral viraram, na Fase 14, uma grade de quadradinhos com a nota (o mockup
mostra uma lista com horas por matéria; a Fase 14 pediu a grade).
