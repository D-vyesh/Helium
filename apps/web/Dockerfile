FROM node:22-alpine AS deps
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /workspace
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /workspace/node_modules ./node_modules
COPY package.json package-lock.json ./
COPY apps/web ./apps/web
RUN npm --workspace apps/web run build

FROM node:22-alpine
WORKDIR /workspace
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=build /workspace/package.json /workspace/package-lock.json ./
COPY --from=build /workspace/apps/web ./apps/web
COPY --from=build /workspace/node_modules ./node_modules
EXPOSE 3000
CMD ["npm", "--workspace", "apps/web", "run", "start"]
