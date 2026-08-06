FROM node:22-alpine AS build

WORKDIR /workspace/frontEnd
COPY frontEnd/package.json frontEnd/package-lock.json ./
RUN npm ci
COPY frontEnd/ ./
COPY docs/guide/ /workspace/docs/guide/
COPY docs/example/ /workspace/docs/example/
ARG VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=false
ENV VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED=${VITE_CONVERSATIONAL_VALIDATION_WORKSPACE_ENABLED}
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY frontEnd/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontEnd/dist /usr/share/nginx/html

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=12 \
  CMD wget -q -O - http://127.0.0.1:8080/healthz || exit 1
