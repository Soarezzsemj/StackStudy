import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {
  Button,
  Card,
  Chip,
  clampPercent,
  Heatmap,
  heatLevel,
  PageTitle,
  ProgressBar,
  StatBlock,
} from '@/components/ui';

describe('Button', () => {
  it('renders a native button of type button, primary by default', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Iniciar</Button>);
    const btn = screen.getByRole('button', { name: 'Iniciar' });
    expect(btn).toHaveAttribute('type', 'button');
    expect(btn).toHaveClass('btn', 'btn-primary');
    await userEvent.click(btn);
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('supports the secondary variant and disabled state', async () => {
    const onClick = vi.fn();
    render(
      <Button variant="secondary" disabled onClick={onClick}>
        Cancelar
      </Button>,
    );
    const btn = screen.getByRole('button', { name: 'Cancelar' });
    expect(btn).toHaveClass('btn-secondary');
    expect(btn).toBeDisabled();
    await userEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('icon-only buttons are named by aria-label', () => {
    render(
      <Button iconOnly aria-label="Buscar">
        <svg />
      </Button>,
    );
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeInTheDocument();
  });
});

describe('Card', () => {
  it('renders title, eyebrow and body', () => {
    render(
      <Card title="Cálculo II" eyebrow="MAT-201">
        corpo
      </Card>,
    );
    expect(screen.getByRole('heading', { name: 'Cálculo II' })).toBeInTheDocument();
    expect(screen.getByText('MAT-201')).toBeInTheDocument();
    expect(screen.getByText('corpo')).toBeInTheDocument();
  });

  it('hero variant adds the space glow class', () => {
    const { container } = render(<Card variant="hero">x</Card>);
    expect(container.firstChild).toHaveClass('card', 'card-hero');
  });
});

describe('Chip', () => {
  it('renders text with an optional color dot', () => {
    render(<Chip dotColor="#6aa7ff">Física</Chip>);
    expect(screen.getByText('Física')).toBeInTheDocument();
    expect(screen.getByTestId('chip-dot')).toHaveStyle({ backgroundColor: '#6aa7ff' });
  });
});

describe('ProgressBar', () => {
  it('exposes progressbar semantics', () => {
    render(<ProgressBar value={3} max={4} label="Progresso" showValue />);
    const bar = screen.getByRole('progressbar', { name: 'Progresso' });
    expect(bar).toHaveAttribute('aria-valuenow', '75');
    expect(screen.getByText('75%')).toBeInTheDocument();
  });

  it('clamps out-of-range values', () => {
    expect(clampPercent(150, 100)).toBe(100);
    expect(clampPercent(-5, 100)).toBe(0);
    expect(clampPercent(5, 0)).toBe(0);
    expect(clampPercent(Number.NaN, 100)).toBe(0);
  });
});

describe('PageTitle', () => {
  it('renders an h1 with description', () => {
    render(<PageTitle title="Deep Work" description="Foco" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Deep Work' })).toHaveClass('page-title');
    expect(screen.getByText('Foco')).toBeInTheDocument();
  });
});

describe('StatBlock', () => {
  it('renders label, value and unit', () => {
    render(
      <dl>
        <StatBlock label="GPA" value="3.42" unit="/ 4.0" />
      </dl>,
    );
    expect(screen.getByText('GPA')).toBeInTheDocument();
    expect(screen.getByText('3.42')).toHaveClass('tabular');
    expect(screen.getByText('/ 4.0')).toBeInTheDocument();
  });
});

describe('Heatmap', () => {
  it('maps values to 5 levels', () => {
    expect(heatLevel(0, 100)).toBe(0);
    expect(heatLevel(1, 100)).toBe(1);
    expect(heatLevel(25, 100)).toBe(1);
    expect(heatLevel(26, 100)).toBe(2);
    expect(heatLevel(75, 100)).toBe(3);
    expect(heatLevel(100, 100)).toBe(4);
    expect(heatLevel(500, 100)).toBe(4);
    expect(heatLevel(10, 0)).toBe(0);
  });

  it('renders one cell per value with an accessible label', () => {
    const values = [
      [0, 10, 20],
      [30, 40, 0],
    ];
    const { container } = render(
      <Heatmap values={values} label="Estudo" cellTitle={(r, c, v) => `${r}-${c}:${v}`} />,
    );
    expect(screen.getByRole('img', { name: 'Estudo' })).toBeInTheDocument();
    const cells = container.querySelectorAll('rect');
    expect(cells).toHaveLength(6);
    expect(cells[0]).toHaveAttribute('data-level', '0');
    expect(cells[4]).toHaveAttribute('data-level', '4');
    expect(screen.getByText('1-1:40')).toBeInTheDocument();
    expect(screen.getByText('Menos')).toBeInTheDocument();
  });
});
