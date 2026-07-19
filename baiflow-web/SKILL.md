---
name: baiflow-web
description: Use when working on the BaiFlow Vue 3 web management frontend, including dashboard layout, login flow, file center, download center, transfer center, device center, notification center, settings, API integration, and UI conventions.
---

# BaiFlow Web Skill

## Scope
Work only inside `baiflow-web` unless backend API contracts, docs, or deployment assets must be updated.

## Stack
- Vue 3.
- Vite.
- Vue Router.
- Pinia.
- Axios.
- Element Plus or Naive UI when a UI library is introduced.

## UI Principles
- Build a practical management console, not a marketing landing page.
- Prioritize dense, scannable tables for files, tasks, and notifications.
- Use clear empty states that tell the user what action is available.
- Require confirmation for destructive actions.
- Always show status, progress, or errors for long-running tasks.

## API Rules
- Communicate only through backend REST APIs and event endpoints.
- Inject Bearer token from a single Axios interceptor.
- Redirect to login on 401.
- Do not display server absolute paths.
- Show permission denied, share expired, extraction code required, and privacy password required states explicitly.
- Use the unified response shape from `docs/04-api-design.md`.

## State Rules
- `authStore` owns token and user state.
- `fileStore` owns current Storage Root, directory, and file list.
- `transferStore` owns transfer/download progress.
- `notificationStore` owns unread count and notification list.

## Phase Discipline
- Phase 1 only creates Vue startup, login placeholder, shell layout, home page, and API request wrapper.
- Do not implement real auth, file management, download management, or Android-related behavior before the matching phase.


