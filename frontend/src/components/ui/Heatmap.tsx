import { useTranslation } from 'react-i18next';
import { HEAT_LEVELS, heatLevel } from './scales';

export interface HeatmapProps {
  /** Matriz de valores: values[linha][coluna]. Estilo GitHub: 7 linhas (dias) × N semanas. */
  values: number[][];
  /** Descrição acessível do gráfico todo. */
  label: string;
  /** Valor do nível máximo; o padrão é o maior valor da matriz. */
  max?: number;
  cellSize?: number;
  gap?: number;
  /** Texto do tooltip de cada célula, ex.: "2h em seg 05/12". */
  cellTitle?: (row: number, column: number, value: number) => string;
  showLegend?: boolean;
}

export function Heatmap({
  values,
  label,
  max,
  cellSize = 12,
  gap = 3,
  cellTitle,
  showLegend = true,
}: HeatmapProps) {
  const { t } = useTranslation();
  const rows = values.length;
  const columns = values.reduce((m, r) => Math.max(m, r.length), 0);
  const top = max ?? values.reduce((m, r) => Math.max(m, ...r, 0), 0);
  const step = cellSize + gap;
  const width = Math.max(0, columns * step - gap);
  const height = Math.max(0, rows * step - gap);

  return (
    <figure className="m-0 inline-flex max-w-full flex-col gap-3">
      <div className="max-w-full overflow-x-auto">
        <svg
          role="img"
          aria-label={label}
          width={width}
          height={height}
          viewBox={`0 0 ${width} ${height}`}
        >
          {values.map((row, r) =>
            row.map((value, c) => {
              const level = heatLevel(value, top);
              return (
                <rect
                  key={`${r}-${c}`}
                  data-level={level}
                  x={c * step}
                  y={r * step}
                  width={cellSize}
                  height={cellSize}
                  rx={3}
                  fill={`var(--heat-${level})`}
                >
                  {cellTitle && <title>{cellTitle(r, c, value)}</title>}
                </rect>
              );
            }),
          )}
        </svg>
      </div>
      {showLegend && (
        <figcaption className="flex items-center gap-2 text-xs text-text-muted" aria-hidden="true">
          <span>{t('heatmap.less')}</span>
          {Array.from({ length: HEAT_LEVELS }, (_, level) => (
            <span
              key={level}
              className="inline-block rounded-[3px]"
              style={{ width: cellSize, height: cellSize, background: `var(--heat-${level})` }}
            />
          ))}
          <span>{t('heatmap.more')}</span>
        </figcaption>
      )}
    </figure>
  );
}
