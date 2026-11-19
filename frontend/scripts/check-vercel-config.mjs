// Trava do deploy na Vercel: falha o build se o host do Render ainda for o placeholder.
// Roda só no buildCommand do vercel.json; o `npm run build` local e o CI não passam por aqui.
import { readFileSync } from 'node:fs';

const PLACEHOLDER = 'SEU-SERVICO.onrender.com';
const config = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'));
const apiRewrite = (config.rewrites ?? []).find((r) => r.source === '/api/(.*)');

if (!apiRewrite) {
  console.error('vercel.json: falta o rewrite de /api/(.*) para o backend.');
  process.exit(1);
}
if (apiRewrite.destination.includes(PLACEHOLDER)) {
  console.error(
    `vercel.json: troque "${PLACEHOLDER}" pelo host real do serviço no Render (veja DEPLOY.md).`,
  );
  process.exit(1);
}
if (!apiRewrite.destination.startsWith('https://')) {
  console.error('vercel.json: o destino do rewrite de /api precisa usar https://.');
  process.exit(1);
}
console.log(`vercel.json ok: /api -> ${apiRewrite.destination}`);
