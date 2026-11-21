# Deploy do StackStudy (Neon + Render + Vercel)

Guia passo a passo, na ordem certa. Tudo usa planos gratuitos.

```
Navegador ──► Vercel (frontend estático)
                │  /api/*  (rewrite: o navegador vê uma origem só)
                ▼
              Render (backend Spring Boot, Docker, plano free)
                │  pooled: aplicação  ·  direta: migrations (Flyway)
                ▼
              Neon (PostgreSQL, plano free)

GitHub Actions ── pg_dump semanal (criptografado) ──► artifact privado
```

**Como cada parte se comporta no plano free**

- **Render free:** hiberna após ~15 min sem tráfego. Tem 512 MB de RAM e CPU bem limitada (~0,1 CPU).
  - Medimos o boot da imagem com esses limites: cerca de **90–105 s** até o health responder. Com o tempo de o Render acordar o container, conte com **1,5–2 min** no primeiro acesso depois de um tempo parado.
  - Com o serviço acordado, as respostas são normais. Veja "Quando migrar para sempre ligado".
- **Neon free:** o banco "dorme" depois de alguns minutos parado e acorda sozinho em menos de 1 s.
- **Frontend:** mostra o aviso "Acordando o servidor…" quando uma requisição passa de 3 s.

---

## Checklist rápido

- [ ] 1. Neon: projeto criado, **URL direta** e **URL pooled** copiadas
- [ ] 2. `JWT_SECRET` gerado e guardado num gerenciador de senhas
- [ ] 3. Render: serviço criado pelo `render.yaml`, variáveis preenchidas, logs com migration aplicada, `/actuator/health/liveness` e `/actuator/health/readiness` = `UP`
- [ ] 4. `frontend/vercel.json` com o host real do Render; projeto na Vercel com root `frontend/`; primeiro deploy ok
- [ ] 5. Teste ponta a ponta: cadastro, login, refresh, logout e cookie `HttpOnly` + `Secure`
- [ ] 6. GitHub: repositório **privado**, secrets do backup criados, backup rodado uma vez à mão
- [ ] 7. Monitor gratuito chamando `/actuator/health/liveness` a cada 5–10 min (opcional, recomendado)
- [ ] 8. Leu "O que fazer se…"

---

## 1. Neon (banco)

1. Crie uma conta em <https://neon.tech> e um **projeto** (um só).
   - **Região:** escolha a mesma área do Render. Recomendado: **AWS US East 1 (N. Virginia)** com o Render em `virginia`, que já é o padrão no `render.yaml`.
     - O que mais pesa é a distância entre backend e banco: cada requisição faz várias consultas.
     - Neon em São Paulo com Render nos EUA deixa tudo mais lento.
   - **Versão do Postgres:** a padrão serve. Anote qual é, porque o backup usa (passo 6).
2. No painel do projeto, clique em **Connect**. Ele mostra a *connection string*.
   - **URL pooled:** com **Connection pooling ligado**, o host tem `-pooler`, por exemplo `ep-xxx-pooler.us-east-1.aws.neon.tech`.
     - Passa pelo PgBouncer do Neon e aguenta muitas conexões.
     - É a da **aplicação** (`DATABASE_URL`).
   - **URL direta:** com **Connection pooling desligado**, o host **não tem** `-pooler`.
     - Conecta direto no Postgres.
     - É a das **migrations** (`DATABASE_DIRECT_URL`) e do **backup**. O pooler não é adequado para DDL, locks e `pg_dump`.
3. Copie as duas no formato que o Neon mostra:
   ```
   postgresql://neondb_owner:SENHA@ep-xxx-pooler.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   postgresql://neondb_owner:SENHA@ep-xxx.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```
   - O backend aceita esse formato direto e converte para JDBC sozinho (`PostgresUrl`). Usuário e senha saem da própria URL.
   - Também aceita `jdbc:postgresql://...`. Nesse caso, preencha `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

## 2. Segredo do JWT

```bash
openssl rand -base64 48
```

Guarde o resultado num gerenciador de senhas.

- **Precisa ter pelo menos 32 bytes.** Se faltar ou for curto, o backend **não sobe**, de propósito.
- Trocar o segredo depois desloga todo mundo (os tokens antigos deixam de valer).

## 3. Render (backend)

1. Crie uma conta em <https://render.com> e conecte o GitHub.
2. **New → Blueprint** e escolha este repositório. O Render lê o `render.yaml` da raiz e cria o serviço `stackstudy-api`:
   - Docker, plano free, `healthCheckPath: /actuator/health/liveness`, região `virginia`.
   - O health check usa o **liveness**, que não consulta o banco (veja o passo 7).
   - Ele **não** cria banco, porque usamos o Neon.
   - Quer outra região? Edite `region` no `render.yaml` antes de criar.
   - Sem Blueprint, faça à mão: **New → Web Service → Docker**, Dockerfile `backend/Dockerfile`, contexto `backend`, health check `/actuator/health/liveness`.
3. Preencha as variáveis (o Render pede cada uma; a tabela completa está no fim):

   | Variável | Valor |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DATABASE_URL` | URL **pooled** do Neon |
   | `DATABASE_DIRECT_URL` | URL **direta** do Neon |
   | `DATABASE_USERNAME` / `DATABASE_PASSWORD` | deixe em branco se estão na URL |
   | `JWT_SECRET` | o segredo do passo 2 |
   | `ALLOWED_ORIGINS` | **em branco** (o rewrite da Vercel faz tudo ser mesma origem) |
   | `JAVA_TOOL_OPTIONS` | em branco |

4. Espere o primeiro deploy. O build leva alguns minutos. Nos **Logs**, procure:
   ```
   The following 1 profile is active: "prod"
   Migrating schema "public" to version "1 - init"
   Successfully applied 1 migration to schema "public", now at version v1
   Tomcat started on port 10000
   Started StackStudyApplication in ... seconds
   ```
5. Confira os dois endpoints de health:
   - `https://<seu-servico>.onrender.com/actuator/health/liveness`: `{"status":"UP"}`. Só diz que o processo está vivo; **não consulta o banco**.
   - `https://<seu-servico>.onrender.com/actuator/health/readiness`: `{"status":"UP"}`. Este **consulta o Neon**; use para diagnóstico, não para pings frequentes. (O `/actuator/health` agregado pede login desde a Fase 7: só os dois probes são públicos.)
   - Anote o host (`<seu-servico>.onrender.com`): ele vai no `vercel.json`.
   - Em prod o Swagger fica desligado (404). Para ligar temporariamente: `SWAGGER_ENABLED=true`.

## 4. Vercel (frontend)

1. Em `frontend/vercel.json`, troque **`SEU-SERVICO.onrender.com`** pelo host real do passo 3:
   ```json
   { "source": "/api/(.*)", "destination": "https://stackstudy-api-xxxx.onrender.com/api/$1" }
   ```
   - **Enquanto o placeholder estiver lá, o build na Vercel falha de propósito** (`scripts/check-vercel-config.mjs`).
   - Commite e faça push.
2. Crie uma conta em <https://vercel.com>, **Add New → Project** e importe o repositório.
   - **Root Directory:** `frontend`
   - **Framework:** Vite (detectado). Build e output já vêm do `vercel.json`.
3. **Environment Variables** (Production):

   | Variável | Valor |
   | --- | --- |
   | `VITE_API_URL` | `/api` (é o padrão; pode omitir) |
   | `VITE_WAKE_URL` | `https://<seu-servico>.onrender.com/actuator/health/liveness` |

   - O `VITE_WAKE_URL` acorda o backend assim que alguém abre o app, antes do login. O ping vai direto ao Render, sem passar pelo rewrite.
   - Use o **liveness**: acordar o backend não precisa acordar o banco junto (o login logo em seguida faz isso).
   - Variáveis `VITE_*` entram **no build**: depois de mudar, faça **Redeploy**.
4. **Deploy.** O app fica em `https://<projeto>.vercel.app`.

## 5. Verificação ponta a ponta

Abra o app numa janela anônima, com o DevTools aberto (F12).

1. **Primeiro acesso:** se o backend estava dormindo, o aviso "Acordando o servidor…" aparece. Espere.
2. **Cadastro:** crie uma conta. Você deve cair no Cockpit.
3. **Mesma origem:** na aba *Network*, as chamadas vão para `https://<projeto>.vercel.app/api/...`. Nunca devem ir direto para `onrender.com`; só o ping de wake vai.
4. **Cookie:** em *Application → Cookies → <projeto>.vercel.app*, confira `ss_refresh`:
   - **HttpOnly** ✓ e **Secure** ✓
   - **SameSite** `Lax`
   - **Path** `/api/auth`
5. **Recarregar** a página: você continua logado (o app renova a sessão pelo cookie).
6. **Refresh do token:** o access token dura 15 min. Espere 16 min com a aba aberta e clique em outra página: deve continuar funcionando, com um `POST /api/auth/refresh` 200 no Network.
   - Para testar mais rápido, ponha `JWT_ACCESS_TTL=PT1M` no Render, teste e **remova depois**.
7. **Logout:** clique em Sair. O cookie some. Recarregue: você vai para o login.
8. **Login** de novo com a mesma conta.
9. **Segurança (Fase 7):**
   - `curl -sI https://<projeto>.vercel.app | grep -i -E "content-security|strict-transport|x-frame"`: os três cabeçalhos aparecem.
   - `curl -s -o /dev/null -w "%{http_code}\n" https://<seu-servico>.onrender.com/actuator/health`: **401** (só liveness e readiness são públicos).
   - Errar a senha 11 vezes seguidas na mesma conta: a 11ª tentativa responde "Muitas tentativas" (429) e a conta fica bloqueada por 15 min, mesmo com a senha certa.

## 6. Backup do banco (GitHub Actions)

O workflow `.github/workflows/db-backup.yml` faz o backup assim:

1. Roda todo domingo às 06:00 UTC, ou quando você dispara à mão.
2. Faz `pg_dump` (formato custom, `--no-owner --no-privileges`).
3. **Criptografa** com GPG (AES-256) e guarda como artifact.

> ⚠️ **O repositório precisa ser privado.** Mesmo criptografado, o backup não deve ficar público.

1. **Settings → Secrets and variables → Actions → New repository secret:**
   - `DATABASE_DIRECT_URL`: URL **direta** do Neon no formato `postgresql://...`. Não use a JDBC nem a pooled.
   - `BACKUP_PASSPHRASE`: frase longa e aleatória (ex.: `openssl rand -base64 32`). **Guarde num gerenciador de senhas: sem ela o backup não abre.**
2. *(Opcional)* Na aba **Variables**: `BACKUP_RETENTION_DAYS` (padrão 30; máximo 90 em repositório privado no plano free).
3. Se o seu Neon usa uma versão do Postgres **maior que 17**, ajuste `PG_MAJOR` no workflow. Se esquecer, o próprio workflow avisa e falha.
4. **Actions → DB backup → Run workflow.** Confira que terminou verde e que há um artifact `db-backup-<data>`.

### Como restaurar

```bash
# 1. Baixar o artifact (ou pela página do run no GitHub)
gh run list --workflow db-backup.yml
gh run download <RUN_ID> --dir restore
cd restore/db-backup-*/

# 2. Conferir integridade e descriptografar (pede a BACKUP_PASSPHRASE)
sha256sum -c stackstudy-*.dump.gpg.sha256
gpg --output backup.dump --decrypt stackstudy-*.dump.gpg

# 3. Restaurar (cliente pg_restore da mesma versão ou mais nova que a do pg_dump)
pg_restore --no-owner --no-privileges --clean --if-exists \
  --dbname "postgresql://...URL DIRETA DO DESTINO..." backup.dump
```

### Ensaio de restauração (testado)

Backup que nunca foi restaurado não é backup. O script `scripts/db-restore-drill.sh` repete, **só no seu computador** (Docker, sem nuvem e sem credencial real), os mesmos comandos do workflow e da restauração acima:

1. Sobe dois Postgres vazios, aplica todas as migrations no primeiro e põe dados em todas as tabelas.
2. `pg_dump --format=custom --no-owner --no-privileges` → `pg_restore --list` → GPG AES-256 → `sha256sum`.
3. `sha256sum -c` → `gpg --decrypt` → `pg_restore --clean --if-exists` no segundo banco, duas vezes (a restauração é repetível).
4. Compara a contagem de linhas de cada tabela e uma amostra com acentos e arrays.

```bash
bash scripts/db-restore-drill.sh      # na raiz do repositório; precisa de docker, gpg e sha256sum
```

Resultado do ensaio feito na Fase 7 (Postgres 16, migrations V1–V8): **OK**. As 15 tabelas (`users`, `auth_sessions`, `refresh_tokens`, `semesters`, `subjects`, `assessments`, `absences`, `tasks`, `study_sessions`, `decks`, `cards`, `card_reviews` e os três `*_settings`) voltaram com as mesmas contagens e os mesmos dados. Rode de novo depois de qualquer migration nova; para testar com a versão do seu Neon, use `PG_IMAGE=postgres:17-alpine bash scripts/db-restore-drill.sh`.

Para ensaiar com o **backup real** (artifact do GitHub), siga "Como restaurar" apontando o `pg_restore` para um **branch novo do Neon**, nunca para o banco em uso.

**Dica:** restaure primeiro num **branch novo do Neon** (Branches → New branch), confira os dados e só então troque as URLs do Render para esse branch. Assim o banco atual não é tocado.

## 7. Manter o backend acordado (monitor gratuito)

O Render free hiberna depois de ~15 min sem tráfego, e acordar leva 1,5–2 min. Um monitor externo que chama o backend a cada 5–10 min evita isso: o serviço nunca chega a dormir.

**Use sempre o liveness:** `https://<seu-servico>.onrender.com/actuator/health/liveness`

### Por que não usar o readiness

| Endpoint | O que confere | Toca no banco? | Uso |
| --- | --- | --- | --- |
| `/actuator/health/liveness` | O processo Java está vivo | **Não** | Monitor, health check do Render, `VITE_WAKE_URL` |
| `/actuator/health/readiness` | O app **e o banco** respondem | **Sim** (abre conexão com o Neon) | Diagnóstico manual |
| `/actuator/health` (agregado) e componentes | Detalhes | Sim | **Não é público** (401) |

- O Neon cobra o tempo de banco ligado em **CU-horas**. Quando ninguém usa, a compute "dorme" (*scale to zero*) depois de ~5 min e para de consumir.
- Um ping que toca no banco a cada 5–10 min **nunca deixa o Neon dormir**: o banco fica ligado 24 h por dia mesmo sem nenhum usuário.
  - Na menor compute (0,25 CU), isso dá **~180 CU-horas por mês** (0,25 × ~730 h).
  - É mais do que a cota mensal do plano free (confira o valor atual em <https://neon.tech/pricing>). Esgotada a cota, o banco fica indisponível até o mês virar.
- O liveness mantém o **Render** acordado sem acordar o **Neon**. O banco só liga quando alguém realmente usa o app.

> O health check do próprio Render (`healthCheckPath` no `render.yaml`) também usa o liveness, pelo mesmo motivo.

### Opção A: UptimeRobot

1. Crie uma conta em <https://uptimerobot.com> (plano free).
2. **Add New Monitor**:
   - **Monitor Type:** HTTP(s)
   - **URL:** `https://<seu-servico>.onrender.com/actuator/health/liveness`
   - **Monitoring Interval:** 5 minutos (o mínimo do plano free)
3. Em **Alert Contacts**, deixe o seu e-mail: você recebe um aviso se o backend cair.

### Opção B: cron-job.org

1. Crie uma conta em <https://cron-job.org>.
2. **Create cronjob**:
   - **URL:** `https://<seu-servico>.onrender.com/actuator/health/liveness`
   - **Execution schedule:** a cada 5 ou 10 minutos
3. Em **Notifications**, ligue o aviso de falha, se quiser.

### Conferir que funcionou

- No painel do Render, o serviço deixa de aparecer como hibernado, e o primeiro acesso ao app fica rápido.
- No painel do Neon (**Monitoring**), a compute continua indo para **Idle** depois de alguns minutos sem uso do app, mesmo com o monitor ligado. Se ela ficar sempre **Active**, algum ping está usando o health com banco.
- **Horas do Render:** um serviço ligado o mês inteiro usa ~730 h das horas gratuitas mensais de instância do Render (confira o limite atual). Se você tiver outro serviço free no mesmo workspace, eles dividem essas horas.

---

## O que fazer se…

**O primeiro acesso demora muito (cold start).**
- É esperado no plano free: 1,5–2 min depois de um tempo parado.
- Confira se `VITE_WAKE_URL` está definida na Vercel: ela acorda o backend logo que a página abre.
- Ligue o monitor do passo 7: com ele o backend nem chega a dormir.
- Se incomodar no dia a dia, veja "Quando migrar para sempre ligado".

**Aparece erro 502 ou 504.**
1. Veja os **Logs** do Render. Pode ser deploy em andamento (espere), cold start ou o app ter caído.
2. Os GETs são repetidos sozinhos até 3 vezes. Login e cadastro não repetem: tente de novo.
3. O rewrite da Vercel tem limite de tempo para destinos externos. Num cold start longo, a primeira chamada pode estourar; com o backend já acordado, a próxima funciona.
4. **Falta de memória** (log com `OutOfMemoryError`, ou o Render dizendo que passou do limite): ponha `JVM_MAX_HEAP=256m` no Render.

**A migration falhou (o app não sobe).**
1. O log do Flyway diz qual migration e qual erro.
2. Confira se `DATABASE_DIRECT_URL` é a URL **direta** (sem `-pooler`).
3. No Postgres cada migration roda numa transação, então uma falha não deixa nada pela metade.
4. Corrija criando uma **nova** migration (`V<n+1>__...sql`). **Nunca edite uma migration já aplicada**: o Flyway recusa o checksum diferente.
5. Em último caso, restaure o banco a partir de um branch do Neon ou de um backup.

**O banco está inacessível.**
- Compare os dois health:
  - `/actuator/health/liveness` **UP** e `/actuator/health/readiness` **DOWN** quer dizer que o app está vivo e o problema é o banco.
  - Os dois fora do ar quer dizer que é o backend.
- Veja o status no painel do Neon (o banco dorme e acorda sozinho).
- Confira as URLs e a senha. Se você trocou a senha no Neon, atualize as duas URLs no Render e o secret do GitHub.
- Confira se o limite de armazenamento do plano free não estourou.
- Erro de SSL: a URL precisa ter `sslmode=require`.

**Aparece 403 "Invalid CORS request".**
- Só acontece se `ALLOWED_ORIGINS` estiver preenchida e o domínio não estiver na lista.
- Com o rewrite da Vercel, deixe essa variável **vazia**.

**Login funciona, mas ao recarregar a página volta para o login.**
1. O cookie não foi salvo. As chamadas precisam ir para `<projeto>.vercel.app/api/...` (rewrite), não direto para o Render.
2. Confira `REFRESH_COOKIE_SECURE=true` (padrão em prod) e se o site abre em `https`.

**Como rodar o backup ou o restore.** Veja o passo 6 acima.

**Como atualizar o app.**
1. Faça `git push` na branch principal.
2. O **Render** refaz o deploy sozinho quando algo em `backend/` muda. As migrations novas rodam no boot.
3. A **Vercel** refaz o frontend a cada push.
4. Durante o deploy do backend pode haver alguns segundos de 502: os GETs se recuperam sozinhos.

---

## Quando migrar para sempre ligado

Quando o cold start incomodar, troque o plano do Render: serviço → **Settings → Instance Type** → um plano pago (ex.: Starter). **Não precisa mudar código:**

- O serviço deixa de hibernar e ganha mais CPU, então o boot fica bem mais rápido.
- Se a nova instância tiver mais RAM, suba `JVM_MAX_HEAP` (ex.: `512m` numa instância de 1 GB).
- `VITE_WAKE_URL` pode continuar (é inofensivo) ou ser removida na Vercel.
- O monitor do passo 7 deixa de ser necessário para evitar hibernação. Mantenha-o só se quiser os avisos de queda.
- O Neon free continua servindo. Se o banco crescer, troque o plano no painel do Neon, também sem mudar código.

---

## Variáveis de ambiente por serviço

### Render (backend)

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | sim | `prod` (a imagem já usa por padrão). |
| `DATABASE_URL` | sim | URL **pooled** do Neon; conexão da aplicação. |
| `DATABASE_DIRECT_URL` | sim | URL **direta** do Neon; usada só pelas migrations (Flyway). |
| `DATABASE_USERNAME` | não | Usuário do banco, se não estiver na URL. |
| `DATABASE_PASSWORD` | não | Senha do banco, se não estiver na URL. |
| `JWT_SECRET` | sim | Segredo do JWT, com 32 bytes ou mais (`openssl rand -base64 48`). |
| `ALLOWED_ORIGINS` | não | Origens liberadas no CORS, separadas por vírgula; vazio com o rewrite da Vercel. `*` é recusado (não sobe). |
| `JAVA_TOOL_OPTIONS` | não | Flags extras da JVM, lidas pela própria JVM. |
| `JAVA_OPTS` | não | Flags extras da JVM, aplicadas por último (sobrescrevem as padrão). |
| `JVM_MAX_HEAP` | não | Heap máximo da JVM (padrão `300m`). |
| `DB_POOL_MAX` | não | Máximo de conexões no pool (padrão 5). |
| `SWAGGER_ENABLED` | não | `true` liga o Swagger em prod (padrão `false`). |
| `REFRESH_COOKIE_SECURE` | não | Cookie só em HTTPS (padrão `true`). Em prod, `false` **impede o boot**. |
| `REFRESH_COOKIE_SAME_SITE` | não | SameSite do cookie (padrão `Lax`). Só `Strict`, `Lax` ou `None` (este exige Secure). |
| `RATE_LIMIT_ENABLED` | não | Limite de tentativas em login/cadastro/refresh (padrão `true`). Em prod, `false` **impede o boot**. |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | não | Validade dos tokens (padrão `PT15M` / `P14D`). |
| `PORT` | automática | Porta HTTP, definida pelo próprio Render. |

### Vercel (frontend)

| Variável | Obrigatória | Descrição |
| --- | --- | --- |
| `VITE_API_URL` | não | Base da API no navegador (padrão `/api`, que passa pelo rewrite). |
| `VITE_WAKE_URL` | recomendada | `https://<servico>.onrender.com/actuator/health/liveness`; acorda o backend ao abrir o app sem tocar no banco. |

### GitHub (Actions)

| Nome | Tipo | Descrição |
| --- | --- | --- |
| `DATABASE_DIRECT_URL` | secret | URL **direta** do Neon (`postgresql://...`) para o `pg_dump`. |
| `BACKUP_PASSPHRASE` | secret | Frase que criptografa o backup; sem ela não dá para restaurar. |
| `BACKUP_RETENTION_DAYS` | variable | Dias que cada backup fica guardado (padrão 30). |
