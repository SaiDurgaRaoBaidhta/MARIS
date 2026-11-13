# MARIS modernization

Two independent projects. No shared build files, no shared code, separate Dockerfiles and pipelines.

| Folder | What | Deploys as |
|---|---|---|
| `maris-ui/` | React + Tailwind front end | static files in an nginx container |
| `maris-middleware/` | Spring Boot translator: JSON <-> EGL EBCDIC records | one jar / container |
| `deploy/` | docker-compose for local end-to-end runs only | – |
| `docs/` | architecture notes | – |

The contract between them is the middleware's OpenAPI spec (`/v3/api-docs`).
The EGL backend (`MARIS_RUI_Services_Web`) is not modified.
