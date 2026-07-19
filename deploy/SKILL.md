---
name: baiflow-deploy
description: Use when working on BaiFlow deployment assets, including Docker Compose, Nginx reverse proxy, MySQL 8, aria2, Ubuntu 24 deployment, environment variables, file permissions, HTTPS, and production security hardening.
---

# BaiFlow Deploy Skill

## Scope
Work only inside `deploy` unless backend, frontend, Android, or docs changes are required to keep deployment accurate.

## Target Runtime
- Ubuntu 24 is the primary deployment target.
- CentOS 7 is not the preferred target because of old system libraries and higher maintenance cost.
- Use Docker Compose for the main runtime.

## Services
Expected services:
- `baiflow-server` for Spring Boot.
- `mysql` for MySQL 8.
- `aria2` for download execution.
- `nginx` for static frontend, API reverse proxy, and HTTPS.
- `redis` only when a later phase needs it.

## Security Rules
- Do not expose MySQL publicly.
- Do not expose aria2 RPC publicly.
- Put secrets in environment variables or `.env`; do not commit real secrets.
- Nginx must proxy `/api/` to backend, proxy public share routes, and serve Web static files.
- Configure upload size and timeout limits intentionally.
- File storage directories must be explicit and outside application code directories.

## Phase Discipline
- Phase 1 may create Docker Compose and Nginx drafts only.
- Production hardening, HTTPS, sharing links, and audit logging belong to later phases.

