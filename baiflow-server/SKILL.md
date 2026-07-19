---
name: baiflow-server
description: Use when working on the BaiFlow Spring Boot backend, including API design, authentication, MyBatis Plus persistence, MySQL schema usage, file operations, download tasks, transfer tasks, notifications, and aria2 integration.
---

# BaiFlow Server Skill

## Scope
Work only inside `baiflow-server` unless a change requires docs, deployment, Web API consumers, or Android API consumers to be updated.

## Stack
- JDK 17.
- Spring Boot 3.x.
- MyBatis Plus.
- Lombok（@Data / @Getter 自动生成 getter/setter）。
- MySQL 8.
- aria2 RPC for download execution.

## Architecture Rules
- Controller handles HTTP mapping and request/response conversion only.
- Service owns business logic, transactions, validation, and file operation ordering.
- Mapper owns SQL access only.
- DTO, VO, Entity, and request classes must be separate.
- Return the unified API shape: `{ code, message, data, traceId }`.
- Use `/api` as the public API prefix.

## Security Rules
- Require `Authorization: Bearer <token>` for protected APIs.
- Enforce ADMIN, USER, and GUEST role behavior.
- Hash user passwords, share tokens, extraction codes, and privacy folder passwords.
- Never return server absolute paths to Web or Android.
- Resolve file IDs to server paths only inside the backend.
- Normalize paths and verify every operation remains inside a configured Storage Root.
- Store file contents on disk; store only metadata in MySQL.
- Keep MySQL and aria2 internal; do not expose them through public routes.

## Persistence Rules
- Follow `docs/03-database-design.md` for table names, statuses, and indexes.
- Use MyBatis Plus BaseMapper for simple CRUD.
- Use XML Mapper for complex searches, statistics, and joins.
- Keep business decisions out of Mapper XML.
- Database DDL scripts under `db/migration/` are for reference only; Flyway auto-migration is disabled (`spring.flyway.enabled: false`).

## Commenting Rules
- Every Service interface method must carry a Javadoc describing the purpose, parameters, return value, and side effects. 注释使用中文。
- Service implementation methods that contain multi-step logic (transaction boundaries, path safety checks, file operation ordering) must include inline comments explaining each step's intent. 注释和提示信息使用中文。
- Controller methods must carry a brief Javadoc or comment describing the endpoint's purpose, required authentication, and response shape. 注释使用中文。
- Complex algorithms (e.g. path traversal prevention, file hashing, directory deletion) must be documented with inline comments. 注释使用中文。
- All user-facing messages (error messages, response messages) should be in Chinese.
- Source files must use UTF-8 encoding. Ensure `application.yml` sets `spring.jackson.time-zone: Asia/Shanghai`.
- Future-phase TODO: introduce i18n message resource bundles for multi-language support.

## Entity & DDL Documentation Rules
- Every entity class must carry a class-level Javadoc explaining its business concept.
- Every entity field must carry a field-level Javadoc (`/** ... */`) describing the business meaning, allowed values, and null semantics. 注释使用中文。
- Enum values must each have a comment documenting their meaning.
- Database DDL scripts are kept under `db/migration/` as reference documentation only. Flyway auto-migration is disabled.
- DDL scripts must include `COMMENT` on every field and table, matching the entity Javadoc. 注释使用中文。

## Phase Discipline
- Phase 1 only creates startup, MySQL connection, MyBatis Plus config, unified response, global exception handling, and `/api/health`.
- Phase 2 includes users, roles, JWT, and resource permission foundations.
- Sharing and privacy folders must follow docs before implementation.
- Do not implement auth, file operations, downloads, or Android-specific behavior until their phase starts.


