---
name: baiflow-project
description: Use when working on the BaiFlow personal file flow center project, including backend, web frontend, Android Java client, deployment, documentation, or cross-module architecture decisions.
---

# BaiFlow Project Skill

## Project Positioning
BaiFlow is a personal server based download and file collaboration center. It prioritizes file flow, task execution, device collaboration, transfer tracking, notifications, and later NAS-mounted storage access.

## Fixed Stack
- Backend: JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8.
- Web: Vue 3, Vite, Vue Router, Pinia, Axios.
- Android: Java, Retrofit, OkHttp, WorkManager, Foreground Service.
- Deployment: Ubuntu 24, Docker Compose, Nginx, aria2 RPC.

## Development Rules
- Keep every phase small, runnable, and reversible.
- Do not implement future-phase features early.
- Keep clients behind REST APIs; do not let Web or Android access database or server paths directly.
- Store files on disk or mounted NAS paths; store only metadata in MySQL.
- Protect all file operations with Storage Root boundary checks.
- Enforce roles ADMIN, USER, and GUEST for all protected resources.
- Support share URLs and privacy folders through hashed tokens/passwords only.
- Update docs when architecture, API, database, deployment, or scope changes.

## Reference Docs
Read these files before planning or implementing related work:
- `docs/01-requirements.md` for scope and non-goals.
- `docs/02-architecture.md` for module boundaries.
- `docs/03-database-design.md` for tables and statuses.
- `docs/04-api-design.md` for API conventions.
- `docs/07-deployment-security.md` for deployment and security constraints.
- `docs/09-coding-standards.md` for coding rules.
- `docs/10-continuation-guide.md` when resuming in a new conversation.


