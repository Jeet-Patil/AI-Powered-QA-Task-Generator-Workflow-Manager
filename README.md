# Task Manager

A task-tracking application with an AI-assisted QA feature. Users manage tasks through a web UI, then ask a large language model to draft test cases for any task. Generated cases move through a review workflow — `DRAFT` → `APPROVED` — before they can be executed and marked `PASS` or `FAIL`.

The project is a three-tier application: an Angular 22 single-page app with SSR, a Spring Boot 4 REST API, and PostgreSQL.

---

## Table of contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Database setup](#database-setup)
- [Running the application](#running-the-application)
- [AI test-case providers](#ai-test-case-providers)
- [REST API](#rest-api)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Project layout](#project-layout)

---

## Overview

### Purpose

Task Manager exists to shorten the gap between "a feature is written" and "a QA suite exists for it." Writing test cases by hand is slow and inconsistent; a language model can draft a first pass in seconds from a task's title and description.

The application deliberately keeps a human in the loop. Model output is never trusted directly:

- Generated cases land in `DRAFT` and cannot be executed.
- A reviewer must explicitly `APPROVE` a case.
- Only `APPROVED` cases can be executed, and execution records `PASS`, `FAIL`, or `NOT_RUN` plus a timestamp and notes.

### Core capabilities

| Capability | Detail |
|---|---|
| Task CRUD | Create, list, view, edit, delete, with search |
| Status workflow | `TODO` → `IN_PROGRESS` → `COMPLETED`, with business-rule enforcement |
| AI test generation | One case per type: `POSITIVE`, `NEGATIVE`, `VALIDATION`, `BOUNDARY`, `BUSINESS_RULE` |
| Provider choice | Explicit provider selection or automatic fallback |
| Review workflow | Approve/reject each generated case |
| Execution | Record `PASS`/`FAIL` with notes on approved cases |
| Dashboard | Task counts and charts via Chart.js |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Browser                                                     │
│  Angular 22 SPA (SSR enabled)  ── localhost:4201            │
│  Routes: /dashboard /tasks /tasks/new /tasks/:id /tasks/:id/edit
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP (JSON), CORS enabled
                            │ No dev proxy — apiUrl is absolute
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot 4.1.1  ── localhost:8081                       │
│  TaskController      /api/tasks                             │
│  TestCaseController  /api/testcases                         │
│  TaskServiceImpl / TestCaseServiceImpl                      │
│  RestTemplate (connect 5s, read 300s)                       │
└───────┬─────────────────────────────────────┬───────────────┘
        │ JPA / Hibernate (ddl-auto=update)   │ HTTPS to providers
        ▼                                     ▼
┌────────────────────┐        ┌────────────────────────────────┐
│ PostgreSQL 5433    │        │ LLM providers                  │
│ taskmanagement_db  │        │ Ollama (local, OpenAI-compat)  │
│ user: postgres     │        │ Groq   (OpenAI-compat)         │
└────────────────────┘        │ Gemini  (native REST)          │
                               └────────────────────────────────┘
```

### Request path for AI generation

```
TaskDetailsComponent
  └─> TaskService.generateTestCases(taskId, customPrompt, provider)
        └─> POST http://localhost:8081/api/testcases/generate
              └─> TestCaseServiceImpl.generateTestCases()
                    ├─ validate provider ∈ {auto, ollama, groq, gemini}
                    ├─ auto → try Ollama, then Groq, then Gemini
                    ├─ explicit → use only that provider
                    ├─ buildPrompt(title, description, priority)
                    ├─ call provider (OpenAI-compatible or Gemini REST)
                    ├─ parseCases() → tolerant JSON extraction
                    └─ saveAll() with status DRAFT, executionResult NOT_RUN
```

Ollama and Groq share one client because both speak the OpenAI `/v1/chat/completions` protocol; they differ only by base URL and bearer token. Gemini uses its own `generateContent` payload shape.

### Domain model

| Enum | Values |
|---|---|
| `TaskStatus` | `TODO`, `IN_PROGRESS`, `COMPLETED` |
| `Priority` | `LOW`, `MEDIUM`, `HIGH` |
| `TestType` | `POSITIVE`, `NEGATIVE`, `VALIDATION`, `BOUNDARY`, `BUSINESS_RULE` |
| `TestCaseStatus` | `DRAFT`, `APPROVED` |
| `ExecutionResult` | `NOT_RUN`, `PASS`, `FAIL` |

Entities: `Task`, `TestCase`. The `Task` ↔ `TestCase` relationship is one-to-many, with test cases cascade-deleted when a task is deleted.

---

## Technology stack

### Backend

| Component | Version | Source |
|---|---|---|
| Spring Boot | 4.1.1 | `pom.xml` parent |
| Java | 21 | `pom.xml` `java.version` |
| Spring WebMVC | managed | `spring-boot-starter-webmvc` |
| Spring Data JPA | managed | `spring-boot-starter-data-jpa` |
| Spring Validation | managed | `spring-boot-starter-validation` |
| PostgreSQL driver | managed | `org.postgresql` artifact |
| Jackson | managed | `jackson-databind` |
| Lombok | managed | compile-time only |
| H2 | managed | test scope only |

### Frontend

| Component | Version | Source |
|---|---|---|
| Angular | ^22.2.0 | `package.json` dependencies |
| Angular SSR | ^22.2.0 | `@angular/ssr`, `@angular/platform-server` |
| Express | ^5.1.0 | SSR server runtime |
| PrimeNG | ^22.1.1 | UI component library |
| Chart.js | ^4.5.1 | dashboard charts |
| RxJS | ~7.8.0 | async streams |
| TypeScript | ~6.0.2 | |
| Zone.js | ~0.16.3 | |

### Test tooling

| Tool | Version | Purpose |
|---|---|---|
| JUnit 5 / Spring Boot Test | managed | backend unit + integration |
| Mockito | managed | backend unit mocking |
| H2 | managed | in-memory DB for tests |
| Karma + Jasmine | ^6.4.4 / ^5.1.0 | frontend unit tests |
| Vitest | ^5.0.0 | additional frontend tests |
| Playwright | ^1.63.0 | end-to-end tests |
| Chrome channel | system install | Playwright browser target |

### Infrastructure

| Component | Port | Notes |
|---|---|---|
| PostgreSQL | 5433 | container, `taskmanagement_db` |
| Spring Boot API | 8081 | `server.port` |
| Angular dev server | 4201 | `ng serve` |
| Ollama | 11434 | local model server |

---

## Prerequisites

| Requirement | Version | Check command |
|---|---|---|
| JDK | 21+ | `java -version` |
| Maven wrapper | included | `./mvnw.cmd --version` (Windows) or `./mvnw --version` (macOS/Linux) |
| Node.js | 20+ | `node --version` |
| npm | 10+ | `npm --version` |
| PostgreSQL | 14+ | `psql --version` |
| Ollama | 0.35+ (optional) | `ollama --version` |
| Google Chrome | current (for e2e) | required by `playwright.config.ts` `channel: 'chrome'` |

You do not need a separate Maven or global Angular install — the wrappers handle both.

---

## Configuration

### Environment variables

All configuration lives in `src/main/resources/application.properties`. Values are read from environment variables, and the root `.env` file is imported automatically on line 1:

```properties
spring.config.import=optional:file:.env[.properties]
```

Because of this import, you can put values in a `.env` file at the repository root instead of exporting them in every shell. `.env` is already listed in `.gitignore` — never commit it.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_PASSWORD` | **Yes** | none | PostgreSQL password. No default; startup fails without it. |
| `GEMINI_API_KEY` | No | empty | Enables Gemini. Empty disables it. |
| `GEMINI_MODEL` | No | `gemini-3.8-flash` | Gemini model id. |
| `GROQ_API_KEY` | No | empty | Enables Groq. Empty disables it. |
| `GROQ_BASE_URL` | No | `https://api.groq.com/openai` | Groq OpenAI-compatible endpoint. |
| `GROQ_MODEL` | No | `qwen/qwen3.8-27b` | Groq model id. |
| `OLLAMA_ENABLED` | No | `true` | Set `false` to disable the local provider. |
| `OLLAMA_BASE_URL` | No | `http://localhost:11434` | Ollama endpoint. |
| `OLLAMA_MODEL` | No | `gemma:2b` | Ollama model tag. |

Create a `.env` at the repository root:

```properties
DB_PASSWORD=<your-postgres-password>
GROQ_API_KEY=<your-groq-key>
GEMINI_API_KEY=<your-gemini-key>
OLLAMA_ENABLED=true
```

Every property uses `${VAR:default}` syntax, so any value you omit falls back to the default in the table above. `DB_PASSWORD` is the one exception — it has no default and must be supplied.

### Model identifiers drift

Provider catalogues change frequently, and a retired model id produces a hard `404` that looks like a configuration error. If a provider suddenly fails on a model that used to work, query the provider's live model list rather than guessing:

```powershell
# Groq
$key = $env:GROQ_API_KEY
(Invoke-RestMethod "https://api.groq.com/openai/v1/models" -Headers @{ Authorization = "Bearer $key" }).data.id
```

```powershell
# Ollama
(Invoke-RestMethod "http://localhost:11434/api/tags").models.name
```

---

## Database setup

The application expects PostgreSQL on port **5433** with database `taskmanagement_db` and user `postgres`. Schema is managed by Hibernate with `ddl-auto=update`, so tables are created on first start — no migration step is required.

Using Docker:

```powershell
docker run -d --name taskmanager-postgres `
  -e POSTGRES_DB=taskmanagement_db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=<your-postgres-password> `
  -p 5433:5432 postgres:16
```

Verify:

```powershell
docker ps --filter name=taskmanager-postgres
```

Backend tests use an in-memory H2 database and do **not** need this container.

---

## Running the application

Start the database, then the backend and frontend in separate terminals.

### 1. Database

```powershell
docker run -d --name taskmanager-postgres `
  -e POSTGRES_DB=taskmanagement_db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=<your-postgres-password> `
  -p 5433:5432 postgres:16
```

### 2. Backend

```powershell
# Repository root
$env:DB_PASSWORD = "<your-postgres-password>"
$env:GROQ_API_KEY = "<your-groq-key>"      # optional
$env:GEMINI_API_KEY = "<your-gemini-key>"  # optional
./mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
export DB_PASSWORD="<your-postgres-password>"
./mvnw spring-boot:run
```

Confirm it started:

```powershell
Invoke-RestMethod http://localhost:8081/api/tasks
```

### 3. Frontend

```powershell
cd frontend
npm install
npm start
```

Open <http://localhost:4201>. The root route redirects to `/dashboard`.

> There is no dev proxy. `TaskService` calls `http://localhost:8081/api/tasks` absolutely, so the backend must be running and CORS-enabled for the frontend origin. If you change the backend port, update `apiUrl` and `tcUrl` in `frontend/src/app/services/task.service.ts`.

### Ports at a glance

| Service | Port |
|---|---|
| Angular dev server | 4201 |
| Spring Boot API | 8081 |
| PostgreSQL | 5433 |
| Ollama | 11434 |

---

## AI test-case providers

### Selecting a provider

The task detail screen has a dropdown with four options:

| Option | Behaviour |
|---|---|
| `Auto (Ollama → Groq → Gemini)` | Tries each in order until one returns cases |
| `Local Ollama` | Ollama only; fails if unavailable |
| `Groq` | Groq only |
| `Google Gemini` | Gemini only |

Choosing an explicit provider is recommended for testing, because `auto` silently hides which provider actually served the request. The backend logs the resolved provider and model on every generation:

```
>>> Starting test case generation | Provider: groq
>>> Calling Groq model: qwen/qwen3.8-27b
>>> Groq HTTP 200 OK (attempt 1)
>>> Successfully generated 5 test cases from Groq!
```

### Provider notes

**Ollama** — fully local and free. Pull the model first (`ollama pull gemma:2b`). A cold start loads ~1.56 GB from disk and a generation can take 1–3 minutes; the backend read timeout is set to 300 s to accommodate this. Set `OLLAMA_KEEP_ALIVE=30m` to keep the model resident between requests and avoid repeat cold starts. Ollama 0.35 runs inference in a separate `llama-server` process; the `ollama` process itself stays near-idle, which is normal.

**Groq** — OpenAI-compatible, fast, and has a generous free tier. Best output quality of the three: it follows the requested JSON schema and writes task-specific cases. `llama-3.1-8b-instant` has been retired; `qwen/qwen3.8-27b` is the current default.

**Gemini** — native REST API. The free tier allows 20 generation requests per day per project; exceeding it returns `429 RESOURCE_EXHAUSTED` until the daily reset. `gemini-2.5-flash` has been retired in favour of `gemini-3.8-flash`, which is the current default.

### Retry behaviour

Transient provider errors are retried up to 4 attempts with quadratic backoff (1 s, 4 s, 9 s). Only `5xx` and `429` responses are retried. Because the loop covers `429`, a daily quota that will not clear within seconds still costs ~14 s before failing — raise `GROQ_MODEL`/`GEMINI_MODEL` rather than retrying a quota error.

---

## REST API

All endpoints are JSON over HTTP. Base URL `http://localhost:8081`.

### Tasks — `/api/tasks`

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/tasks` | List all tasks |
| `GET` | `/api/tasks/{id}` | Fetch one task |
| `POST` | `/api/tasks` | Create a task |
| `PUT` | `/api/tasks/{id}` | Replace a task |
| `PATCH` | `/api/tasks/{id}/status` | Change status |
| `DELETE` | `/api/tasks/{id}` | Delete a task and its cases |

### Test cases — `/api/testcases`

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/testcases/generate` | Generate cases from an LLM |
| `GET` | `/api/testcases/task/{taskId}` | List cases for a task |
| `PATCH` | `/api/testcases/{id}/review` | Approve or reject a case |
| `POST` | `/api/testcases/{id}/execute` | Record an execution result |
| `DELETE` | `/api/testcases/{id}` | Delete a case |

### Generate request body

```json
{
  "taskId": 14,
  "customPrompt": "Focus on payment edge cases",
  "provider": "groq"
}
```

`provider` accepts `auto`, `ollama`, `groq`, or `gemini`. An unrecognised value is rejected with a `400`. `customPrompt` is optional extra guidance appended to the generated prompt.

### Example

```powershell
Invoke-RestMethod "http://localhost:8081/api/testcases/generate" `
  -Method Post -ContentType "application/json" `
  -Body (@{ taskId = 14; provider = "groq" } | ConvertTo-Json)
```

> Generation **appends**. Calling it repeatedly on the same task adds another batch of cases rather than replacing existing ones. Use a fresh task when you need a clean count.

---

## Testing

### Backend

```powershell
./mvnw.cmd test
```

Runs unit and integration tests against an in-memory H2 database. No PostgreSQL and no LLM credentials required.

### Frontend unit tests

```powershell
cd frontend
npm test -- --watch=false
```

`--watch=false` is required for CI or scripted runs; without it Karma stays open.

### End-to-end tests

```powershell
cd frontend
npm run e2e
```

Playwright starts (or reuses) a dev server on `127.0.0.1:4201` and drives real Chrome. Specs live in `frontend/e2e/`:

| Spec | Coverage |
|---|---|
| `task-lifecycle.spec.ts` | Create task → generate → approve → PASS |
| `provider-selection.spec.ts` | Sends Gemini/Ollama/Groq choices, then review and execution |

Both specs **mock the REST API**, so e2e runs do not exercise PostgreSQL or any live provider. They verify frontend wiring and the review/execution guards, not provider behaviour. Validate providers separately by hand.

### Production build

```powershell
cd frontend
npm run build
```

Emits to `frontend/dist/frontend`, including prerendered static routes.

---

## Troubleshooting

**Backend fails to start, `Could not resolve placeholder 'DB_PASSWORD'`**
`DB_PASSWORD` has no default. Set it in the shell or in a root `.env` file. Confirm `.env` sits in the repository root, since `spring.config.import` resolves relative to the working directory.

**Frontend loads but every list is empty, network errors in the console**
The backend is not running or is on a different port. The frontend has no dev proxy — it calls `http://localhost:8081` directly. Verify with `Invoke-RestMethod http://localhost:8081/api/tasks`.

**CORS error in the browser console**
The frontend origin is not allowed by the backend. Confirm you started both from the expected hosts; Angular serves on `4201` and the API expects the development origin.

**`404` from Groq or Gemini mentioning the model does not exist**
The model id was retired. Query the provider's live model list (see [Model identifiers drift](#model-identifiers-drift)) and update `GROQ_MODEL` or `GEMINI_MODEL` in both `.env` and `application.properties`.

**`429 Too Many Requests` / `RESOURCE_EXHAUSTED` from Gemini**
The free tier allows 20 generations per day per project. Wait for the daily reset, enable billing, or use Groq or Ollama instead. This is an account limit, not a code fault.

**Ollama returns `Read timed out` or `I/O error`**
Usually a cold start — the model is loading 1.56 GB from disk and has not answered within the 300 s read timeout. Confirm progress by watching the `llama-server` process; it should show ~1.7 GB resident memory and rising CPU. Setting `OLLAMA_KEEP_ALIVE=30m` avoids repeat cold starts.

**Ollama returns `No LLM produced test cases` while the log shows `HTTP 200 OK`**
The call succeeded but parsing produced no usable cases. Small local models often ignore the requested schema, returning standalone JSON objects with renamed fields. The parser tolerates several shapes, but an unfamiliar shape can still yield nothing. Read the raw response and extend the field aliases in `TestCaseServiceImpl.parseCases()`.

**`ReferenceError: alert is not defined` during server-side rendering**
Error handlers call the browser global `alert()`, which does not exist in the Node SSR context. This appears in build/e2e server logs on error paths only; the browser build is unaffected. Guard these calls behind a platform check or replace them with an injectable notification service.

**Ollama test cases look generic, or steps contain raw JSON**
The 2B model underperformed the requested schema. Titles like "Positive Test Case" and JSON blobs inside the steps field are model output, not a parser bug. Use Groq, or a larger Ollama model, for review-ready output.

**Ollama process is idle during a request**
Expected on Ollama 0.35. Inference runs in a separate `llama-server` process; the `ollama` process is only the HTTP endpoint. Watch `llama-server`, not `ollama`, when diagnosing CPU activity.

**`npm test` never exits**
Karma is in watch mode. Use `npm test -- --watch=false`.

**Playwright cannot start or finds no browser**
The config targets the `chrome` channel, which requires a real Chrome install. Verify with `npx playwright test --list`, and confirm the dev server port 4201 is free.

---

## Project layout

```
task-manager/
├── pom.xml                      Maven build, Java 21, Spring Boot 4.1.1
├── mvnw / mvnw.cmd              Maven wrapper
├── .env                         Local secrets — gitignored, never commit
├── README.md                    This file
├── screenshots/                 Verification screenshots
├── doc/                         Project report (Markdown + PDF)
├── src/main/java/com/example/taskmanager/
│   ├── TaskmanagerApplication.java    Entry point, RestTemplate timeouts
│   ├── controller/                    TaskController, TestCaseController
│   ├── service/                       TaskServiceImpl, TestCaseServiceImpl
│   ├── entity/                        Task, TestCase, and 5 enums
│   ├── dto/                           Request/response payloads
│   ├── exception/                     GlobalExceptionHandler, business rules
│   └── repository/                    Spring Data JPA repositories
├── src/main/resources/application.properties   All configuration
├── src/test/                       Backend unit + integration tests
├── frontend/
│   ├── src/app/                   Components, services, routes
│   ├── e2e/                       Playwright specs
│   ├── playwright.config.ts       Port 4201, Chrome channel, webServer
│   └── package.json               Angular 22 scripts and dependencies
├── api-tests/                    Postman collection
└── performance/                  JMeter load test
```

---

## Further reading

- [`doc/project-report.md`](doc/project-report.md) — full project report with architecture detail and verification evidence
- [`doc/Task-Manager-Project-Report.pdf`](doc/Task-Manager-Project-Report.pdf) — same report, formatted PDF