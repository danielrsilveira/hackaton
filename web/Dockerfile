# syntax=docker/dockerfile:1
# Build do Angular + nginx servindo os estáticos e encaminhando /api para a API.
# Mesmo papel que S3 + CloudFront terão na AWS.

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
# Gera config.json (autenticação) a cada start a partir das variáveis de ambiente. O chmod e o sed
# tornam o script robusto a checkouts no Windows (sem bit de execução / com CRLF).
COPY docker-entrypoint.d/ /docker-entrypoint.d/
RUN sed -i 's/\r$//' /docker-entrypoint.d/*.sh && chmod +x /docker-entrypoint.d/*.sh
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
