# build -> nginx.  docker build -t maris-ui . && docker run -p 8080:80 -e MIDDLEWARE_UPSTREAM=http://middleware:8090 maris-ui
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# http (default) = call the middleware behind /api; mock = answer from the bundled demo data
ARG VITE_MARIS_API=http
ENV VITE_MARIS_API=$VITE_MARIS_API
RUN npm run build

FROM nginx:1.27-alpine
# the nginx image renders /etc/nginx/templates/*.template with the environment at start-up
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV MIDDLEWARE_UPSTREAM=http://middleware:8090
EXPOSE 80
