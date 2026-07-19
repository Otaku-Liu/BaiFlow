# BaiFlow Phase 1 Scaffold Design

## Goal
Create a runnable Phase 1 scaffold for BaiFlow with a Spring Boot backend, a Vue 3 web app, local development configuration conventions, and a deployment draft that can be expanded in later phases.

## Scope
- Create `baiflow-server` as a Maven Spring Boot 3.4.x application under `com.baiflow`.
- Create `baiflow-web` as a Vue 3 + pnpm + JavaScript project with Element Plus, Vue Router, Pinia, and Axios.
- Add repository support files such as `.gitignore` and `README.md`.
- Add `deploy/docker-compose.yml` without Nginx.
- Keep real development secrets in local-only `application-dev.yml`.

## Architecture
The backend will expose `/api/health` and provide the shared application foundations that later phases will extend: unified API responses, global exception handling, and base configuration wiring for MySQL, Redis, Flyway, and JWT-related properties. The web app will remain intentionally thin in Phase 1: one root layout, one home page, and one API client wrapper.

## Security
- Real MySQL and Redis credentials live only in `baiflow-server/src/main/resources/application-dev.yml`.
- `application-dev.yml` is ignored by Git.
- The repository keeps only non-sensitive defaults and examples.
- JWT secret and initial admin credentials are configuration-driven placeholders for later phases.

## Validation
- Backend must compile and its tests must pass.
- Web dependencies must install and the production build must succeed.
- Docker Compose file must parse with `docker compose config`.
