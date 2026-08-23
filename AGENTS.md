# Repository Guidelines

## Project Structure & Module Organization

Freesky is a multi-agent creative writing assistant with three runnable parts:

- `AgentSky/`: Python FastAPI and LangGraph agent runtime. Agents live in `agents/`, routing in `graph/`, model config in `llm/`, tests in `tests/`, and RAG references in `data/reference/`.
- `SprintbootSky/`: Java 21 Spring Boot service for business APIs and calls to the Python layer. Source is in `src/main/java/`, config in `src/main/resources/`, tests in `src/test/java/`.
- `VueSky/`: Vue 3 + Vite frontend. Components live in `src/components/`, composables in `src/composables/`, types in `src/types/`, and global styles in `src/styles/`.

Design notes are in `Design/` and `docs/`. `study/` contains learning exercises, not production code.

## Build, Test, and Development Commands

- `cd AgentSky && pip install -r requirements.txt`: install agent/backend dependencies.
- `cd AgentSky && python main.py "灵感文本"`: run the novel workflow from the CLI.
- `cd AgentSky && python server.py`: start the FastAPI agent service on port `8765`.
- `cd AgentSky && pytest`: run Python tests.
- `cd SprintbootSky && .\mvnw test`: run Spring Boot tests.
- `cd SprintbootSky && .\mvnw spring-boot:run`: start the Java service.
- `cd VueSky && pnpm install`: install frontend dependencies.
- `cd VueSky && pnpm dev`: start Vite on port `5173`.
- `cd VueSky && pnpm test && pnpm build`: run Vitest, type-check, and build.

## Coding Style & Naming Conventions

Follow existing language defaults: Python uses 4-space indentation and snake_case modules/functions; Java uses package `com.freesky.sprintbootsky`, PascalCase classes, and camelCase methods; Vue components use PascalCase filenames. Keep boundaries clear: Java handles business APIs, Python handles LLM orchestration, and Vue handles presentation.

## Testing Guidelines

Use `pytest` for `AgentSky/tests/test_*.py`, JUnit/Spring tests for `SprintbootSky/src/test/java/**/*Test.java`, and Vitest with Vue Testing Library for `VueSky/src/**/*.test.ts`. Update tests when changing routing, API contracts, security behavior, composables, or visible UI state.

## Commit & Pull Request Guidelines

Recent history uses concise Conventional Commit-style subjects such as `feat: localize creative studio in Chinese` and `fix: preserve usage metrics on failed runs`. Prefer `feat:`, `fix:`, `docs:`, `test:`, `chore:`, or `merge:`. Pull requests should explain the change, list tests run, link issues/docs, and include screenshots for UI changes.

## Security & Configuration Tips

Copy `AgentSky/.env.example` to `.env` and keep API keys out of git. Do not commit generated vector indexes, local virtualenvs, build outputs, or personal runtime files.
