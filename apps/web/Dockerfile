FROM node:22-alpine AS deps
WORKDIR /workspace
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/package.json
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /workspace
ENV NEXT_TELEMETRY_DISABLED=1
# NEXT_PUBLIC_* values are inlined into the client bundle at build time.
ARG NEXT_PUBLIC_APP_NAME=HELIUM
ARG NEXT_PUBLIC_HELIUM_API_BASE_URL
ARG NEXT_PUBLIC_HELIUM_WS_BASE_URL
ENV NEXT_PUBLIC_APP_NAME=$NEXT_PUBLIC_APP_NAME \
    NEXT_PUBLIC_HELIUM_API_BASE_URL=$NEXT_PUBLIC_HELIUM_API_BASE_URL \
    NEXT_PUBLIC_HELIUM_WS_BASE_URL=$NEXT_PUBLIC_HELIUM_WS_BASE_URL
COPY --from=deps /workspace/node_modules ./node_modules
COPY package.json package-lock.json ./
COPY apps/web ./apps/web
RUN npm --workspace apps/web run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup -S helium && adduser -S helium -G helium
# Next's standalone output contains the runtime server and only the production
# dependencies it needs. It avoids relying on `next start` and a complete
# monorepo node_modules tree in the final container.
COPY --from=build --chown=helium:helium /workspace/apps/web/.next/standalone ./
COPY --from=build --chown=helium:helium /workspace/apps/web/.next/static ./apps/web/.next/static
USER helium
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
