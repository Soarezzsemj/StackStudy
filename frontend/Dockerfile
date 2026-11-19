# syntax=docker/dockerfile:1

# ---- dev: servidor do Vite com o código montado ----
FROM node:22-alpine AS dev
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
EXPOSE 5173
# Atualiza o node_modules (volume) a cada início e sobe o Vite.
CMD ["sh", "-c", "npm install --no-audit --no-fund && npm run dev"]

# ---- build: gera os arquivos estáticos ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- prod: arquivos estáticos no nginx ----
FROM nginx:1.27-alpine AS prod
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
