-- Fase 1 (The Stack): semestre atual, status e carga horária da matéria, tarefas com matéria
-- opcional (dono direto em tasks.user_id), descrição, prioridade e prazo com hora opcional.
-- Escrita para rodar também em um banco que já tenha dados (V1 aplicada em produção).

-- ---------------------------------------------------------------------------
-- semesters: no máximo um semestre atual por usuário, e nunca arquivado
-- ---------------------------------------------------------------------------
ALTER TABLE semesters ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE;

-- Quem já tem semestres ganha como atual o mais recente não arquivado.
UPDATE semesters
SET is_current = TRUE
WHERE id IN (
    SELECT DISTINCT ON (user_id) id
    FROM semesters
    WHERE NOT archived
    ORDER BY user_id, start_date DESC, created_at DESC
);

ALTER TABLE semesters
    ADD CONSTRAINT ck_semesters_current_not_archived CHECK (NOT (is_current AND archived));
CREATE UNIQUE INDEX uq_semesters_one_current ON semesters (user_id) WHERE is_current;

-- ---------------------------------------------------------------------------
-- subjects: carga horária e status
-- ---------------------------------------------------------------------------
ALTER TABLE subjects ADD COLUMN workload_hours INTEGER;
ALTER TABLE subjects ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'IN_PROGRESS';
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_workload_hours CHECK (workload_hours IS NULL OR workload_hours >= 0);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_status CHECK (status IN ('IN_PROGRESS', 'PASSED', 'FAILED', 'DROPPED'));

-- ---------------------------------------------------------------------------
-- tasks: matéria opcional, então o dono fica na própria tarefa
-- ---------------------------------------------------------------------------
ALTER TABLE tasks ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE CASCADE;
UPDATE tasks t
SET user_id = se.user_id
FROM subjects su
JOIN semesters se ON se.id = su.semester_id
WHERE su.id = t.subject_id;
ALTER TABLE tasks ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE tasks ALTER COLUMN subject_id DROP NOT NULL;

ALTER TABLE tasks ADD COLUMN description TEXT;
ALTER TABLE tasks ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM';
ALTER TABLE tasks ADD COLUMN due_all_day BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_description CHECK (description IS NULL OR char_length(description) <= 5000);
ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'));

-- Tipos novos: trabalho e leitura. LAB deixa de existir e vira OTHER.
UPDATE tasks SET type = 'OTHER' WHERE type = 'LAB';
ALTER TABLE tasks DROP CONSTRAINT ck_tasks_type;
ALTER TABLE tasks
    ADD CONSTRAINT ck_tasks_type
        CHECK (type IN ('EXAM', 'ASSIGNMENT', 'READING', 'PROJECT', 'EXERCISE_LIST', 'OTHER'));

-- A lista da tela filtra sempre por usuário e quase sempre por status, ordenando por prazo.
DROP INDEX idx_tasks_due_date;
CREATE INDEX idx_tasks_user_status_due ON tasks (user_id, status, due_date);
