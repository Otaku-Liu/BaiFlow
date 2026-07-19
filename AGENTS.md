# BaiFlow Agent Instructions

## Project Identity
BaiFlow（小白流转）是一个个人服务器上的下载与文件协同中心，重点是文件流转、下载任务、设备协同、传输与通知。项目必须小步推进，每个阶段都要可运行、可验收、可回滚。

## Read First
Before planning or coding, read:
1. `SKILL.md`
2. `docs/01-requirements.md`
3. `docs/02-architecture.md`
4. `docs/08-development-roadmap.md`
5. The module-specific `SKILL.md` for the area being changed.

## Fixed Technical Choices
- Backend: JDK 17, Spring Boot 3.x, MyBatis Plus, Lombok, MySQL 8.
- Web: Vue 3, Vite, Vue Router, Pinia, Axios.
- Android: Java, Retrofit, OkHttp, WorkManager, Foreground Service.
- Deployment: Ubuntu 24, Docker Compose, Nginx, aria2 RPC.

## Current Feature Decisions
- Support users and roles: `ADMIN`, `USER`, `GUEST`.
- Support sharing selected files or folders with generated URLs.
- Support optional extraction code for share links.
- Support privacy folders protected by an additional password.
- Store passwords, share tokens, extraction codes, and privacy passwords as hashes only.
- Do not expose server absolute paths to Web, Android, or public share visitors.

## Development Discipline
- Do not implement future-phase features early.
- Update docs when requirements, APIs, database tables, security rules, or deployment behavior change.
- Keep business logic in backend services, not controllers or mappers.
- Keep Web and Android clients behind documented REST APIs.
- Store file contents on disk or mounted NAS paths; store only metadata in MySQL.
- Protect every file operation with Storage Root boundary checks.

## Phase Order
1. Docs and project boundary.
2. Project skeleton.
3. Auth, users, roles, and basic permissions.
4. File center MVP.
5. Privacy folders.
6. Download center.
7. Transfer center and notifications.
8. Android Java MVP.
9. NAS mounted directory access.
10. Share URLs and public access controls.
11. Deployment and security hardening.
