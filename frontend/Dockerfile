FROM node:25-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_WS_URL=ws://localhost/ws
ARG VITE_APP_NAME="FinTrack Pro"
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_WS_URL=$VITE_WS_URL
ENV VITE_APP_NAME=$VITE_APP_NAME
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:25-alpine
WORKDIR /app
RUN npm install -g serve
COPY --from=build /app/dist .
EXPOSE 3000
CMD ["serve", "-s", ".", "-l", "3000"]
