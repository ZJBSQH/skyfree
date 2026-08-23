# Freesky 多 Agent 小说创作工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 VueSky 改造成可通过 Spring Boot 认证并真实运行 AgentSky 创作流程的中文三栏工作台。

**Architecture:** 使用轻量 API 层隔离 HTTP、认证和 fallback；使用 Vue composables 管理共享会话、创作运行及健康状态；页面组件只通过 props/emits 组合。正式环境只访问 Spring Boot，开发环境可经 Vite 服务端代理安全 fallback 到 AgentSky。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Vite 7、Vitest 3、Testing Library、lucide-vue-next。

**Spec:** `VueSky/docs/superpowers/specs/2026-08-22-multi-agent-novel-studio-design.md`

## Global Constraints

- 只修改 `VueSky`。
- 不新增 Pinia、Element Plus、Ant Design 或其他大型依赖。
- 所有用户可见文案使用中文。
- 浏览器端不得包含 AgentSky 密钥、后端 traceback 或 Authorization 内容。
- 卡片圆角不超过 8px，不使用营销 Hero、装饰光球或全页紫色渐变。
- 不提交 `.env`、`node_modules` 或 `dist`。

---

### Task 1: API 类型、HTTP 客户端与认证状态

**Files:**
- Create: `VueSky/src/types/api.ts`
- Create: `VueSky/src/api/client.ts`
- Create: `VueSky/src/api/auth.ts`
- Create: `VueSky/src/composables/useAuth.ts`
- Create: `VueSky/src/composables/useAuth.test.ts`

**Interfaces:**
- Produces: `AuthUser`, `AuthResponse`, `ApiError`, `apiRequest<T>()`, `loginRequest()`, `registerRequest()`, `getCurrentUser()`, `useAuth()`.
- Persists: localStorage key `freesky.auth.token`.

- [ ] **Step 1: Write failing useAuth tests**

Cover literal observable outcomes: successful login stores token and user; initialization calls `/api/auth/me`; 401 removes the token and exposes `登录已过期，请重新登录`; register uses username/email/password; logout clears state.

- [ ] **Step 2: Run test to verify RED**

Run: `pnpm test -- src/composables/useAuth.test.ts`

Expected: FAIL because `useAuth.ts` and API modules do not exist.

- [ ] **Step 3: Implement typed API and auth state**

Implement `apiRequest<T>(path, options)` with JSON parsing, Bearer header injection, `ApiError.status`, and mappings for network/401/429/generic failures. Sanitize multiline, stack-like, secret-like, or overlong backend messages. Implement shared module refs for auth with explicit `initialize`, `login`, `register`, `logout`, and `expireSession` behavior.

- [ ] **Step 4: Run test to verify GREEN**

Run: `pnpm test -- src/composables/useAuth.test.ts`

Expected: all auth tests PASS with no unhandled errors.

### Task 2: Novel API、运行状态与健康检查

**Files:**
- Create: `VueSky/src/api/novels.ts`
- Rewrite: `VueSky/src/composables/useNovelRun.ts`
- Rewrite: `VueSky/src/composables/useNovelRun.test.ts`
- Create: `VueSky/src/composables/useHealthCheck.ts`
- Create: `VueSky/src/composables/useHealthCheck.test.ts`
- Modify: `VueSky/vite.config.ts`
- Modify: `VueSky/src/env.d.ts`

**Interfaces:**
- Consumes: `apiRequest<T>()`, `ApiError`, `NovelRunInput`, `NovelResponse`.
- Produces: `createNovel(input, options)`, `buildCreativeIdea(input)`, `useNovelRun(options?)`, `useHealthCheck()`.

- [ ] **Step 1: Write failing novel-run and health tests**

Cover exact request body with appended Chinese type/length/style requirements, Bearer-authenticated Spring success, duplicate submission suppression, failed response token retention, 401 callback, safe network error, clear result, dual health states, and development fallback selection.

- [ ] **Step 2: Run tests to verify RED**

Run: `pnpm test -- src/composables/useNovelRun.test.ts src/composables/useHealthCheck.test.ts`

Expected: FAIL against the old fetch-coupled composable and missing health module.

- [ ] **Step 3: Implement novel and health flows**

Normalize backend arrays defensively, parse Agent prefixes into Chinese-ready typed events, keep `token_usage` on failed payloads, and expose `loading`, `status`, `result`, `logs`, `tokenUsage`, `error`, `submit`, and `clear`. Configure `/api` to Spring Boot and `/agentsky-api` to AgentSky; read non-public `AGENTSKY_API_TOKEN` only inside Vite proxy configuration and add `X-AgentSky-Token` there.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `pnpm test -- src/composables/useNovelRun.test.ts src/composables/useHealthCheck.test.ts`

Expected: all targeted tests PASS.

### Task 3: 工作台组件与页面组合

**Files:**
- Create: `VueSky/src/components/AppShell.vue`
- Create: `VueSky/src/components/AuthPanel.vue`
- Create: `VueSky/src/components/CreativeStudio.vue`
- Rewrite: `VueSky/src/components/AgentTimeline.vue`
- Create: `VueSky/src/components/NovelResult.vue`
- Create: `VueSky/src/components/CharacterPanel.vue`
- Create: `VueSky/src/components/WorldAndPlotPanel.vue`
- Create: `VueSky/src/components/TokenUsage.vue`
- Create: `VueSky/src/components/HealthBadge.vue`
- Rewrite: `VueSky/src/App.vue`
- Rewrite: `VueSky/src/App.test.ts`
- Rewrite: `VueSky/src/App.selection.test.ts`

**Interfaces:**
- Consumes: controllers from `useAuth`, `useNovelRun`, and `useHealthCheck`.
- Produces: accessible Chinese form, three-column result workspace, copy/clear interactions, and single-column mobile reading order.

- [ ] **Step 1: Write failing page interaction tests**

Test real rendered behavior: login panel appears without a session; empty idea shows `请输入小说灵感`; authenticated submit renders body/log/character/world/plot/token; button shows loading and is disabled; copy writes full chapter text; clear removes only run output; all primary labels are Chinese.

- [ ] **Step 2: Run page tests to verify RED**

Run: `pnpm test -- src/App.test.ts src/App.selection.test.ts`

Expected: FAIL because the approved component structure and auth flow are absent.

- [ ] **Step 3: Implement components and App wiring**

Use semantic headings, labels, status regions, alerts and button names. Keep every card radius at `8px` or less. Use only lucide-vue-next icons. Order DOM as left input, center output, right details so the responsive grid collapses naturally into the required mobile sequence.

- [ ] **Step 4: Run page tests to verify GREEN**

Run: `pnpm test -- src/App.test.ts src/App.selection.test.ts`

Expected: page tests PASS.

### Task 4: Visual system、regression cleanup and full verification

**Files:**
- Rewrite: `VueSky/src/styles/main.css`
- Modify only if required by regressions: existing `VueSky/src/components/*.test.ts` and obsolete unused components.

**Interfaces:**
- Consumes: component class names from Task 3.
- Produces: desktop three-column layout, tablet two-area wrap, mobile single-column layout, focus visibility, loading states and print-safe readable text.

- [ ] **Step 1: Implement restrained responsive visual system**

Define neutral color tokens, 1px borders, maximum 8px radii, compact form controls, readable prose typography, sticky desktop sidebars where space permits, and breakpoints at 1180px and 760px.

- [ ] **Step 2: Run the complete test suite**

Run: `pnpm test`

Expected: all Vitest files PASS; update only regressions that assert intentionally replaced UI behavior.

- [ ] **Step 3: Run the production build**

Run: `pnpm build`

Expected: `vue-tsc -b` and Vite build exit 0 without TypeScript errors.

- [ ] **Step 4: Verify scope and generated-file safety**

Run: `git status --short -- VueSky` and `git diff --check -- VueSky`

Expected: changes are limited to `VueSky`; no `.env`, `node_modules`, or `dist` is tracked; whitespace check exits 0.
