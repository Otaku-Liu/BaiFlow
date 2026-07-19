---
name: baiflow-android
description: Use when working on the BaiFlow Android Java app, including login, server configuration, file browsing, upload/download flows, transfer tasks, foreground notifications, Retrofit/OkHttp networking, and Android client security.
---

# BaiFlow Android Skill

## Scope
Work only inside `baiflow-android` unless backend API contracts, docs, or deployment assumptions must be updated.

## Stack
- Android Java.
- Retrofit and OkHttp.
- WorkManager.
- Foreground Service for long-running transfers.
- SharedPreferences for MVP login state.
- Room only when local caching becomes necessary.

## Client Rules
- Store server address and token locally with the simplest safe MVP approach.
- Use one OkHttp interceptor to attach Bearer token.
- Treat 401 as login expiration.
- Show explicit errors for network failure, auth failure, file permission failure, privacy password failure, share access failure, and transfer failure.
- Long upload/download operations must show foreground notifications.

## UX Scope
MVP screens:
- Login.
- Server configuration.
- File list.
- Upload picker.
- Download confirmation.
- Transfer tasks.
- Settings.

## API Rules
- Use backend REST APIs only.
- Do not rely on server absolute paths.
- Keep request and response models aligned with `docs/04-api-design.md`.

## Phase Discipline
- Do not build Android business features before Phase 6.
- Before Phase 6, this module may contain only skeleton project files and documentation.

