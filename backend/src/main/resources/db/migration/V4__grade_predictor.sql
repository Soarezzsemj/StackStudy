-- Fase Grade Predictor: avaliações, faltas, regras de nota por matéria e preferências do usuário.
-- As tabelas grades/absences e as colunas grading_rule/min_attendance_pct vieram da V1, mas nunca
-- foram usadas pela aplicação (sem API). Por isso dá para reorganizá-las sem migrar dados reais.

-- ---------------------------------------------------------------------------
-- grades -> assessments (avaliações da matéria)
-- ---------------------------------------------------------------------------
ALTER TABLE grades RENAME TO assessments;
ALTER TABLE assessments RENAME COLUMN label TO name;
ALTER TABLE assessments RENAME COLUMN value TO score;
-- A nota usa a escala da matéria; o teto é validado no service (a escala pode mudar).
ALTER TABLE assessments DROP CONSTRAINT ck_grades_value;
ALTER TABLE assessments DROP CONSTRAINT ck_grades_max_value;
ALTER TABLE assessments DROP COLUMN max_value;
-- Ligação com tarefa fica para depois; a coluna sai para não sugerir algo que não existe.
DROP INDEX idx_grades_task_id;
ALTER TABLE assessments DROP COLUMN task_id;
ALTER TABLE assessments ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

-- Peso zero não faz sentido em média ponderada (e dividiria por zero).
UPDATE assessments SET weight = 1 WHERE weight <= 0;
ALTER TABLE assessments DROP CONSTRAINT ck_grades_weight;
ALTER TABLE assessments ADD CONSTRAINT ck_assessments_weight CHECK (weight > 0);
ALTER TABLE assessments ADD CONSTRAINT ck_assessments_score CHECK (score IS NULL OR score >= 0);
ALTER TABLE assessments ADD CONSTRAINT ck_assessments_position CHECK (position >= 0);

ALTER INDEX idx_grades_subject_id RENAME TO idx_assessments_subject_id;
ALTER TABLE assessments RENAME CONSTRAINT grades_pkey TO assessments_pkey;
ALTER TABLE assessments RENAME CONSTRAINT grades_subject_id_fkey TO assessments_subject_id_fkey;

-- ---------------------------------------------------------------------------
-- absences: no máximo um registro por matéria e dia (count = aulas perdidas no dia)
-- ---------------------------------------------------------------------------
DELETE FROM absences a
USING absences b
WHERE a.subject_id = b.subject_id AND a.date = b.date AND a.id > b.id;
ALTER TABLE absences ADD CONSTRAINT ck_absences_count_max CHECK (count <= 24);
-- O índice único também atende a busca por matéria.
DROP INDEX idx_absences_subject_id;
CREATE UNIQUE INDEX uq_absences_subject_date ON absences (subject_id, date);

-- ---------------------------------------------------------------------------
-- subjects: regra de nota própria. NULL = usa a preferência do usuário.
-- ---------------------------------------------------------------------------
ALTER TABLE subjects DROP COLUMN grading_rule;
ALTER TABLE subjects DROP COLUMN min_attendance_pct;

ALTER TABLE subjects ADD COLUMN grade_method VARCHAR(10);
ALTER TABLE subjects ADD COLUMN grade_scale_max NUMERIC(6, 2);
ALTER TABLE subjects ADD COLUMN passing_grade NUMERIC(6, 2);
ALTER TABLE subjects ADD COLUMN max_absence_pct NUMERIC(5, 2);
ALTER TABLE subjects ADD COLUMN final_exam_enabled BOOLEAN;
ALTER TABLE subjects ADD COLUMN final_exam_min_average NUMERIC(6, 2);
ALTER TABLE subjects ADD COLUMN final_exam_average_weight NUMERIC(5, 2);
ALTER TABLE subjects ADD COLUMN final_exam_passing_grade NUMERIC(6, 2);
-- Nota tirada no exame final (quando já aconteceu).
ALTER TABLE subjects ADD COLUMN final_exam_score NUMERIC(6, 2);

ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_grade_method CHECK (grade_method IS NULL OR grade_method IN ('WEIGHTED', 'ARITHMETIC'));
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_grade_scale CHECK (grade_scale_max IS NULL OR grade_scale_max BETWEEN 1 AND 100);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_passing_grade CHECK (passing_grade IS NULL OR passing_grade >= 0);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_max_absence CHECK (max_absence_pct IS NULL OR max_absence_pct BETWEEN 0 AND 100);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_exam_min CHECK (final_exam_min_average IS NULL OR final_exam_min_average >= 0);
-- Peso da média no resultado final; 100 deixaria o exame sem efeito (e dividiria por zero).
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_exam_weight
        CHECK (final_exam_average_weight IS NULL OR (final_exam_average_weight >= 0 AND final_exam_average_weight < 100));
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_exam_passing CHECK (final_exam_passing_grade IS NULL OR final_exam_passing_grade >= 0);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_exam_score CHECK (final_exam_score IS NULL OR final_exam_score >= 0);

-- ---------------------------------------------------------------------------
-- grade_settings: preferências do usuário (criada sob demanda; sem linha = padrões)
-- ---------------------------------------------------------------------------
CREATE TABLE grade_settings (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    grade_method              VARCHAR(10)   NOT NULL DEFAULT 'WEIGHTED',
    grade_scale_max           NUMERIC(6, 2) NOT NULL DEFAULT 10.00,
    passing_grade             NUMERIC(6, 2) NOT NULL DEFAULT 6.00,
    max_absence_pct           NUMERIC(5, 2) NOT NULL DEFAULT 25.00,
    final_exam_enabled        BOOLEAN       NOT NULL DEFAULT FALSE,
    final_exam_min_average    NUMERIC(6, 2) NOT NULL DEFAULT 4.00,
    final_exam_average_weight NUMERIC(5, 2) NOT NULL DEFAULT 50.00,
    final_exam_passing_grade  NUMERIC(6, 2) NOT NULL DEFAULT 5.00,
    gpa_scale                 VARCHAR(4)    NOT NULL DEFAULT 'TEN',
    gpa_weight                VARCHAR(10)   NOT NULL DEFAULT 'CREDITS',
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_grade_settings_user UNIQUE (user_id),
    CONSTRAINT ck_gs_method CHECK (grade_method IN ('WEIGHTED', 'ARITHMETIC')),
    CONSTRAINT ck_gs_scale CHECK (grade_scale_max BETWEEN 1 AND 100),
    CONSTRAINT ck_gs_passing CHECK (passing_grade >= 0 AND passing_grade <= grade_scale_max),
    CONSTRAINT ck_gs_max_absence CHECK (max_absence_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_gs_exam_min CHECK (final_exam_min_average >= 0 AND final_exam_min_average <= passing_grade),
    CONSTRAINT ck_gs_exam_weight CHECK (final_exam_average_weight >= 0 AND final_exam_average_weight < 100),
    CONSTRAINT ck_gs_exam_passing CHECK (final_exam_passing_grade >= 0 AND final_exam_passing_grade <= grade_scale_max),
    CONSTRAINT ck_gs_gpa_scale CHECK (gpa_scale IN ('TEN', 'FOUR')),
    CONSTRAINT ck_gs_gpa_weight CHECK (gpa_weight IN ('CREDITS', 'WORKLOAD'))
);
