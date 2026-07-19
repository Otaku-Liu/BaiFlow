# Phase 1 Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the initial BaiFlow Phase 1 scaffold for backend, web, deploy, and repository support files.

**Architecture:** Create a minimal Spring Boot service and Vue 3 app that are runnable in isolation and deliberately limited to Phase 1 concerns. Keep development secrets in an ignored local profile and reserve business behavior for later phases.

**Tech Stack:** Java 17, Maven, Spring Boot 3.4.x, Flyway, MySQL, Redis, Vue 3, pnpm, Element Plus, Vue Router, Pinia, Axios, Docker Compose

---

### Task 1: Repository support files

**Files:**
- Create: `.gitignore`
- Create: `README.md`

- [ ] Add ignore rules for Java, Node, IDE files, local env files, and `baiflow-server/src/main/resources/application-dev.yml`.
- [ ] Add a short project README with module descriptions and Phase 1 run commands.

### Task 2: Backend scaffold

**Files:**
- Create: `baiflow-server/pom.xml`
- Create: `baiflow-server/src/main/java/com/baiflow/BaiflowServerApplication.java`
- Create: `baiflow-server/src/main/java/com/baiflow/config/BaiflowProperties.java`
- Create: `baiflow-server/src/main/java/com/baiflow/controller/HealthController.java`
- Create: `baiflow-server/src/main/java/com/baiflow/entity/ApiResponse.java`
- Create: `baiflow-server/src/main/java/com/baiflow/service/HealthService.java`
- Create: `baiflow-server/src/main/java/com/baiflow/config/GlobalExceptionHandler.java`
- Create: `baiflow-server/src/main/resources/application.yml`
- Create: `baiflow-server/src/main/resources/application-dev.example.yml`
- Create: `baiflow-server/src/main/resources/db/migration/V1__phase1_bootstrap.sql`
- Create: `baiflow-server/src/test/java/com/baiflow/controller/HealthControllerTest.java`

- [ ] Write the failing backend health endpoint test first.
- [ ] Run the backend test to confirm it fails before implementation.
- [ ] Implement the minimal Spring Boot application, `/api/health`, response wrapper, config properties, and exception handler.
- [ ] Add the minimal Flyway migration for Phase 1 bootstrap metadata.
- [ ] Run backend tests again and confirm they pass.

### Task 3: Web scaffold

**Files:**
- Create: `baiflow-web/package.json`
- Create: `baiflow-web/pnpm-lock.yaml`
- Create: `baiflow-web/vite.config.js`
- Create: `baiflow-web/index.html`
- Create: `baiflow-web/src/main.js`
- Create: `baiflow-web/src/App.vue`
- Create: `baiflow-web/src/router/index.js`
- Create: `baiflow-web/src/stores/index.js`
- Create: `baiflow-web/src/stores/auth.js`
- Create: `baiflow-web/src/api/http.js`
- Create: `baiflow-web/src/views/HomeView.vue`
- Create: `baiflow-web/src/styles.css`

- [ ] Create the minimal Vue app with router, Pinia, Axios wrapper, and Element Plus shell.
- [ ] Install dependencies with local temp directory overrides if the default pnpm environment is blocked.
- [ ] Run a production build and confirm it succeeds.

### Task 4: Deploy scaffold

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/.env.example`

- [ ] Add a Compose draft for `mysql` and `redis` only, with comments noting that server and web are local Phase 1 scaffolds for now.
- [ ] Keep secrets out of committed files.

### Task 5: Local development config

**Files:**
- Create locally only: `baiflow-server/src/main/resources/application-dev.yml`

- [ ] Add the provided MySQL, Redis, JWT, and init-admin values to the ignored local development profile.
- [ ] Ensure committed files reference `dev` profile usage without embedding secrets.

### Task 6: Verification

**Files:**
- Verify created project files above

- [ ] Run `mvn test` in `baiflow-server`.
- [ ] Run `pnpm install` and `pnpm build` in `baiflow-web` with local temp overrides if needed.
- [ ] Run `docker compose config` in `deploy`.
- [ ] Report exact verification outcomes and any residual issues.
