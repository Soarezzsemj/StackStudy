#!/usr/bin/env bash
# Ensaio de backup e restauração, 100% local (Docker), com os MESMOS comandos do workflow
# .github/workflows/db-backup.yml e do DEPLOY.md ("Como restaurar"):
#
#   1. sobe um Postgres "origem", aplica todas as migrations e põe dados de exemplo em todas as
#      tabelas;
#   2. pg_dump (custom, --no-owner --no-privileges) -> confere com pg_restore --list -> GPG AES256
#      -> sha256;
#   3. confere o sha256, descriptografa e faz pg_restore --clean --if-exists num Postgres "destino"
#      vazio;
#   4. compara a contagem de linhas de cada tabela e uma amostra dos dados.
#
# Não usa credencial real nem nuvem. Requer: docker, gpg e sha256sum (o Git Bash do Windows tem os
# dois). Uso, na raiz do repositório:  bash scripts/db-restore-drill.sh
set -euo pipefail

IMAGE="${PG_IMAGE:-postgres:16-alpine}"
SRC=stackstudy-drill-src
DST=stackstudy-drill-dst
MIGRATIONS=backend/src/main/resources/db/migration
WORK="$(mktemp -d)"
# Frase só deste ensaio (como a BACKUP_PASSPHRASE do GitHub, mas descartável).
PASSPHRASE="drill-$(date +%s)-$RANDOM$RANDOM"
export MSYS_NO_PATHCONV=1

cleanup() {
  docker rm -f "$SRC" "$DST" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

psql_src() { docker exec -i "$SRC" psql -q -v ON_ERROR_STOP=1 -U postgres -d stackstudy "$@"; }

start() {
  docker rm -f "$1" >/dev/null 2>&1 || true
  docker run -d --name "$1" -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=stackstudy "$IMAGE" >/dev/null
  until docker exec "$1" pg_isready -U postgres -d stackstudy -q; do sleep 1; done
  # pg_isready responde antes do script de init terminar; espera o banco aceitar consultas.
  until docker exec "$1" psql -U postgres -d stackstudy -tAc "select 1" >/dev/null 2>&1; do sleep 1; done
}

echo "==> Subindo Postgres de origem e destino ($IMAGE)"
start "$SRC"
start "$DST"

echo "==> Aplicando as migrations na origem"
for f in $(ls "$MIGRATIONS"/V*__*.sql | sort -V); do
  echo "    $(basename "$f")"
  psql_src < "$f"
done

echo "==> Dados de exemplo (todas as tabelas)"
psql_src <<'SQL'
INSERT INTO users (id, name, email, password_hash, course) VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'Ana Drill', 'ana@drill.local', '$2a$10$hash', 'Direito');
INSERT INTO auth_sessions (id, user_id, user_agent, last_used_at, expires_at) VALUES
  ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000a1', 'drill', now(), now() + interval '14 days');
INSERT INTO refresh_tokens (user_id, session_id, token_hash, expires_at) VALUES
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000f1', repeat('a', 64), now() + interval '14 days');
INSERT INTO semesters (id, user_id, name, start_date, end_date, is_current) VALUES
  ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', '2026.2', '2026-08-01', '2026-12-15', true);
INSERT INTO subjects (id, semester_id, name, color, credits, grade_method, passing_grade) VALUES
  ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 'História', '#6aa7ff', 4, 'SUM', 7);
INSERT INTO assessments (subject_id, name, weight, score, max_points, position) VALUES
  ('00000000-0000-0000-0000-0000000000c1', 'P1', 1, 2.5, 3, 0),
  ('00000000-0000-0000-0000-0000000000c1', 'P2', 1, NULL, 4, 1);
INSERT INTO absences (subject_id, date, count) VALUES ('00000000-0000-0000-0000-0000000000c1', '2026-09-01', 2);
INSERT INTO tasks (user_id, subject_id, title, type, due_date) VALUES
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000c1', 'Resenha: ação ç ã', 'READING', now() + interval '2 days');
INSERT INTO study_sessions (user_id, subject_id, started_at, ended_at, mode, effective_seconds, source) VALUES
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000c1', now() - interval '1 hour', now() - interval '35 minutes', 'POMODORO', 1500, 'TIMER');
INSERT INTO decks (id, user_id, name) VALUES ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000a1', 'Datas');
INSERT INTO cards (id, user_id, deck_id, front, back, tags, due_on, last_reviewed_at) VALUES
  ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000d1', '1822?', 'Independência', '{historia,brasil}', CURRENT_DATE + 1, now());
INSERT INTO card_reviews (user_id, card_id, rating, quality, reviewed_at, prev_interval_days, new_interval_days) VALUES
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-0000000000e1', 'GOOD', 4, now(), 0, 1);
INSERT INTO deep_work_settings (user_id, focus_minutes) VALUES ('00000000-0000-0000-0000-0000000000a1', 50);
INSERT INTO grade_settings (user_id, grade_method, passing_grade) VALUES ('00000000-0000-0000-0000-0000000000a1', 'SUM', 7);
INSERT INTO flashcard_settings (user_id, new_per_day, reviews_per_day) VALUES ('00000000-0000-0000-0000-0000000000a1', 10, 50);
SQL

COUNTS_SQL="SELECT string_agg(table_name || '=' || (xpath('/row/c/text()', query_to_xml('select count(*) as c from public.' || quote_ident(table_name), false, true, '')))[1]::text, ' ' ORDER BY table_name) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
SAMPLE_SQL="SELECT u.email || '|' || s.name || '|' || t.title || '|' || array_to_string(c.tags, ',') || '|' || a.max_points FROM users u JOIN semesters se ON se.user_id = u.id JOIN subjects s ON s.semester_id = se.id JOIN tasks t ON t.subject_id = s.id JOIN cards c ON c.user_id = u.id JOIN assessments a ON a.subject_id = s.id AND a.name = 'P1'"

echo "==> 1/3 Backup (igual ao workflow)"
docker exec "$SRC" pg_dump -U postgres -d stackstudy --format=custom --no-owner --no-privileges > "$WORK/stackstudy.dump"
docker exec -i "$SRC" pg_restore --list < "$WORK/stackstudy.dump" > /dev/null
printf '%s' "$PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
  --symmetric --cipher-algo AES256 --output "$WORK/stackstudy.dump.gpg" "$WORK/stackstudy.dump"
rm -f "$WORK/stackstudy.dump"
(cd "$WORK" && sha256sum stackstudy.dump.gpg > stackstudy.dump.gpg.sha256)
echo "    $(du -h "$WORK/stackstudy.dump.gpg" | cut -f1) criptografado"

echo "==> 2/3 Restauração (igual ao DEPLOY.md)"
(cd "$WORK" && sha256sum -c stackstudy.dump.gpg.sha256)
printf '%s' "$PASSPHRASE" | gpg --batch --yes --pinentry-mode loopback --passphrase-fd 0 \
  --output "$WORK/backup.dump" --decrypt "$WORK/stackstudy.dump.gpg"
docker exec -i "$DST" pg_restore -U postgres --no-owner --no-privileges --clean --if-exists \
  -d stackstudy < "$WORK/backup.dump"

echo "==> 3/3 Conferência"
before=$(docker exec "$SRC" psql -U postgres -d stackstudy -tAc "$COUNTS_SQL")
after=$(docker exec "$DST" psql -U postgres -d stackstudy -tAc "$COUNTS_SQL")
sample_before=$(docker exec "$SRC" psql -U postgres -d stackstudy -tAc "$SAMPLE_SQL")
sample_after=$(docker exec "$DST" psql -U postgres -d stackstudy -tAc "$SAMPLE_SQL")
echo "    origem : $before"
echo "    destino: $after"
echo "    amostra: $sample_after"
if [ "$before" != "$after" ] || [ "$sample_before" != "$sample_after" ] || [ -z "$after" ]; then
  echo "FALHOU: o banco restaurado não bate com a origem." >&2
  exit 1
fi
# A restauração precisa ser repetível (--clean --if-exists por cima de um banco já restaurado).
docker exec -i "$DST" pg_restore -U postgres --no-owner --no-privileges --clean --if-exists \
  -d stackstudy < "$WORK/backup.dump"
again=$(docker exec "$DST" psql -U postgres -d stackstudy -tAc "$COUNTS_SQL")
[ "$again" = "$before" ] || { echo "FALHOU na segunda restauração." >&2; exit 1; }
echo "OK: backup criptografado, restaurado duas vezes e idêntico à origem."
