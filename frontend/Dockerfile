# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN --mount=type=secret,id=node_ca_bundle,target=/tmp/node-ca.pem \
    if [ -f /tmp/node-ca.pem ]; then export NODE_EXTRA_CA_CERTS=/tmp/node-ca.pem; fi; \
    npm ci --no-audit --no-fund
COPY . .
RUN npm run build
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
