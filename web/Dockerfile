# 빌드 → 정적 서빙. 최종 이미지에 node/소스를 남기지 않는다.
FROM node:22-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts index.html ./
COPY src src
# `npm run build` = tsc --noEmit + vite build. 타입 오류가 있으면 이미지가 안 만들어진다.
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
