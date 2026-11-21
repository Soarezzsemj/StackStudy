-- Regra de nota "soma de pontos" (SUM), ao lado da média ponderada e da aritmética.
-- A coluna da regra já existe desde a V4 (grade_method): só passa a aceitar 'SUM'. Matérias
-- existentes não mudam: subjects.grade_method continua NULL (herda) ou o valor que tinha, e
-- grade_settings.grade_method continua WEIGHTED por padrão. Todas as colunas novas nascem NULL ou
-- com um padrão que não afeta as médias (ver GradeMigrationV6IntegrationTest).

-- ---------------------------------------------------------------------------
-- regra: WEIGHTED | ARITHMETIC | SUM
-- ---------------------------------------------------------------------------
ALTER TABLE subjects DROP CONSTRAINT ck_subjects_grade_method;
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_grade_method
        CHECK (grade_method IS NULL OR grade_method IN ('WEIGHTED', 'ARITHMETIC', 'SUM'));
ALTER TABLE grade_settings DROP CONSTRAINT ck_gs_method;
ALTER TABLE grade_settings
    ADD CONSTRAINT ck_gs_method CHECK (grade_method IN ('WEIGHTED', 'ARITHMETIC', 'SUM'));

-- ---------------------------------------------------------------------------
-- arredondar antes de comparar (só na soma). Padrão: compara o valor exato.
-- ---------------------------------------------------------------------------
ALTER TABLE grade_settings ADD COLUMN round_before_compare BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE grade_settings ADD COLUMN rounding_scale INTEGER NOT NULL DEFAULT 1;
ALTER TABLE grade_settings
    ADD CONSTRAINT ck_gs_rounding_scale CHECK (rounding_scale BETWEEN 0 AND 2);

-- Na matéria, NULL = herda a preferência (como as outras colunas de regra).
ALTER TABLE subjects ADD COLUMN round_before_compare BOOLEAN;
ALTER TABLE subjects ADD COLUMN rounding_scale INTEGER;
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_rounding_scale
        CHECK (rounding_scale IS NULL OR rounding_scale BETWEEN 0 AND 2);
-- Teto opcional do total da matéria (ex.: com bônus, não passa de 10). NULL = sem teto.
ALTER TABLE subjects ADD COLUMN points_cap NUMERIC(6, 2);
ALTER TABLE subjects
    ADD CONSTRAINT ck_subjects_points_cap CHECK (points_cap IS NULL OR points_cap > 0);

-- ---------------------------------------------------------------------------
-- avaliações: quanto vale (soma) e se é bônus. O peso continua (média ponderada) e é preservado
-- ao trocar de regra. "nota ≤ quanto vale" é validado no service, só na regra SUM.
-- ---------------------------------------------------------------------------
ALTER TABLE assessments ADD COLUMN max_points NUMERIC(6, 2);
ALTER TABLE assessments ADD COLUMN bonus BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assessments
    ADD CONSTRAINT ck_assessments_max_points CHECK (max_points IS NULL OR max_points > 0);
