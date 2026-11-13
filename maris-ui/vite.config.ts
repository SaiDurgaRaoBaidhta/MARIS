import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": "/src" } },
  server: {
    // dev proxy: /api -> the middleware (maris-middleware, port 8090) (production: nginx.conf.template, MIDDLEWARE_UPSTREAM)
    proxy: { "/api": { target: "http://localhost:8090", changeOrigin: true } },
  },
});
