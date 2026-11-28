import type { Config } from 'tailwindcss';

/** Cor de token que aceita opacidade do Tailwind (ex.: `bg-action/10`). */
const c = (token: string) =>
  `color-mix(in srgb, var(${token}) calc(<alpha-value> * 100%), transparent)`;

/**
 * Os design tokens ficam como variáveis CSS em src/styles/tokens.css.
 * Aqui o Tailwind só liga os nomes a essas variáveis.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: c('--bg'),
        surface: { DEFAULT: c('--surface'), raised: c('--surface-raised') },
        border: { DEFAULT: c('--border'), control: c('--border-control') },
        text: {
          DEFAULT: c('--text'),
          secondary: c('--text-secondary'),
          muted: c('--text-muted'),
          'on-space': c('--text-on-space'),
        },
        action: {
          DEFAULT: c('--action'),
          hover: c('--action-hover'),
          ink: c('--action-ink'),
        },
        space: { deep: c('--space-deep'), glow: c('--space-glow') },
        success: c('--success'),
        danger: c('--danger'),
        warning: c('--warning'),
        info: c('--info'),
        subject: {
          blue: c('--subject-blue'),
          amber: c('--subject-amber'),
          purple: c('--subject-purple'),
          cyan: c('--subject-cyan'),
          pink: c('--subject-pink'),
          lime: c('--subject-lime'),
        },
        heat: {
          0: c('--heat-0'),
          1: c('--heat-1'),
          2: c('--heat-2'),
          3: c('--heat-3'),
          4: c('--heat-4'),
        },
      },
      fontFamily: {
        sans: 'var(--font-sans)',
      },
      borderRadius: {
        card: 'var(--radius-card)',
        inner: 'var(--radius-inner)',
        small: 'var(--radius-small)',
        pill: 'var(--radius-control)',
      },
      boxShadow: {
        action: 'var(--action-glow)',
        dialog: 'var(--shadow-dialog)',
      },
      spacing: {
        sidebar: 'var(--sidebar-width)',
        topbar: 'var(--topbar-height)',
        statusbar: 'var(--statusbar-height)',
        control: 'var(--control-height)',
        'control-lg': 'var(--control-height-lg)',
      },
      letterSpacing: {
        title: '-0.045em',
        card: '-0.02em',
        label: '0.06em',
      },
    },
  },
  plugins: [],
} satisfies Config;
