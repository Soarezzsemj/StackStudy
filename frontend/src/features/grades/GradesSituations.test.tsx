import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, within } from '@testing-library/react';
import { routes } from '@/app/router';
import type { GradeMethod, GradesOverview } from '@/lib/api/grades';
import type { Semester } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { SITUATIONS, subjectWith } from '@/test/gradeFixtures';
import { emptyOverview, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

// "Todas as matérias": cada situação (Possível, Em risco, Aprovado, Reprovado) em cada regra.

const semester: Semester = {
  id: 's1',
  name: '2026.2',
  startDate: '2026-08-01',
  endDate: '2026-12-15',
  archived: false,
  current: true,
};

const LABEL = {
  POSSIBLE: 'Possível',
  AT_RISK: 'Em risco',
  PASSED: 'Aprovado',
  FAILED: 'Reprovado',
} as const;

const METHOD = {
  SUM: 'Soma de pontos',
  WEIGHTED: 'Média ponderada',
  ARITHMETIC: 'Média aritmética',
} as const;

const methods: GradeMethod[] = ['SUM', 'WEIGHTED', 'ARITHMETIC'];
// Nome de cada matéria = regra + faixa (ex.: "SUM POSSIBLE").
const subjects = methods.flatMap((m) =>
  (Object.keys(LABEL) as (keyof typeof LABEL)[]).map((bucket) => ({
    ...subjectWith(m, SITUATIONS[m][bucket].scores, `${m}-${bucket}`),
    name: `${m} ${bucket}`,
  })),
);

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
});

describe('Todas as matérias: situação por regra', () => {
  it.each(
    methods.flatMap((m) =>
      (Object.keys(LABEL) as (keyof typeof LABEL)[]).map((b) => [m, b] as const),
    ),
  )('%s: %s', async (method, bucket) => {
    useAuthStore.getState().setSession('token', testUser);
    mockApi({
      'GET /api/semesters': () => jsonResponse(200, [semester]),
      'GET /api/grades/overview': () =>
        jsonResponse(200, {
          ...emptyOverview,
          semesterId: 's1',
          subjects,
        } satisfies GradesOverview),
    });
    renderWithProviders(routes, { initialEntries: ['/grades'] });

    const all = await screen.findByRole('region', { name: 'Todas as matérias' });
    const button = within(all).getByRole('button', {
      name: `Abrir ${method} ${bucket} no simulador (${LABEL[bucket]})`,
    });
    // Nome, regra e situação visíveis (texto, não só cor).
    expect(button).toHaveTextContent(`${method} ${bucket}`);
    expect(button).toHaveTextContent(METHOD[method]);
    expect(button).toHaveTextContent(LABEL[bucket]);
    expect(button.querySelector('svg')).not.toBeNull();
  });
});
