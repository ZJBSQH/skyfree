# Freesky Creative Studio UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a responsive three-column novel creation workspace with a creation directory, Agent activity, foundational character profiles, chapter reading, and real runtime metrics.

**Architecture:** Keep API and run-state logic in `useNovelRun`, normalize the synchronous `/api/novels` response into stable frontend types, and render that state through focused Vue components. `App.vue` owns selection and responsive panels; replacing the synchronous adapter with SSE later will not change component interfaces.

**Tech Stack:** Vue 3.5, TypeScript 5.9, Vite 7, Vitest, Testing Library Vue, lucide-vue-next.

**Spec:** `docs/superpowers/specs/2026-08-18-creative-studio-ui-design.md`

## Global Constraints

- Browser traffic only targets SprintbootSky endpoints under `/api`; never expose `AGENTSKY_API_TOKEN`.
- Use only real API fields or browser elapsed time; unknown metrics render as `--`.
- Do not fake Agent progress while the API remains synchronous.
- Desktop uses three columns; tablet collapses metrics; mobile collapses both side panels.
- Cards use at most 6px radius and are not nested inside other cards.
- Use Lucide icons for navigation, status, and command buttons.

---

## File Structure

- `VueSky/src/types/novel.ts`: API and normalized UI types.
- `VueSky/src/composables/useNovelRun.ts`: health check, generation request, timer, log normalization, and retry state.
- `VueSky/src/components/ProjectNavigator.vue`: project directory and active selection.
- `VueSky/src/components/AgentWorkspace.vue`: creation command and run state.
- `VueSky/src/components/AgentTimeline.vue`: normalized Agent log timeline.
- `VueSky/src/components/CharacterProfile.vue`: foundational character profile.
- `VueSky/src/components/ChapterReader.vue`: reviewed chapter reader.
- `VueSky/src/components/RunInspector.vue`: service, Token, cost, review, and duration metrics.
- `VueSky/src/styles/main.css`: application shell, tokens, desktop and responsive layout.
- `VueSky/src/App.vue`: component composition, active content selection, and drawer state.

### Task 1: Run State And API Adapter

**Files:**
- Create: `VueSky/src/types/novel.ts`
- Create: `VueSky/src/composables/useNovelRun.ts`
- Create: `VueSky/src/composables/useNovelRun.test.ts`
- Modify: `VueSky/package.json`
- Modify: `VueSky/pnpm-lock.yaml`

**Interfaces:**
- Produces: `useNovelRun(): NovelRunController`.
- Produces: `parseAgentLogs(logs: string[]): AgentEvent[]`.
- Produces: `NovelResult`, `TokenUsage`, `AgentEvent`, and `RunStatus` types.

- [ ] **Step 1: Add the icon dependency**

Run: `pnpm add lucide-vue-next`

Expected: `package.json` and `pnpm-lock.yaml` contain `lucide-vue-next`.

- [ ] **Step 2: Write failing state tests**

Create tests that assert the exact controller contract:

```ts
const fetchMock = vi.fn()
  .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'ok' }) })
  .mockResolvedValueOnce({
    ok: true,
    json: async () => ({
      success: true,
      logs: ['[WriterAgent] 草拟 ch_01', '[ReviewerAgent] PASS'],
      result: {
        completed_chapters: ['chapter'],
        characters: [{ name: 'Lin', role_type: 'protagonist' }],
        world_settings: [],
        plot_outline: [],
        review_round: 1
      },
      token_usage: {
        input_tokens: 80,
        output_tokens: 40,
        total_tokens: 120,
        call_count: 2,
        cost_yuan: 0.001,
        model: 'deepseek-chat'
      }
    })
  })

expect(controller.status.value).toBe('completed')
expect(controller.events.value[0].agent).toBe('writer')
expect(controller.tokenUsage.value.total_tokens).toBe(120)
```

Also assert health failure sets `connected=false`, generation failure sets `status='failed'`, and a second `generate()` call while running does not issue another request.

- [ ] **Step 3: Run tests and verify red**

Run: `pnpm test -- src/composables/useNovelRun.test.ts`

Expected: FAIL because the composable and types do not exist.

- [ ] **Step 4: Implement normalized types and composable**

Define these exact core types:

```ts
export type RunStatus = 'idle' | 'running' | 'completed' | 'failed'
export type AgentName = 'supervisor' | 'setting' | 'character' | 'plot' | 'writer' | 'reviewer' | 'system'

export interface AgentEvent {
  id: string
  agent: AgentName
  message: string
  sequence: number
}

export interface TokenUsage {
  input_tokens: number
  output_tokens: number
  total_tokens: number
  call_count: number
  cost_yuan: number
  model: string
}
```

`generate(idea)` must set `running`, start a one-second elapsed timer, call `/api/novels`, normalize logs, store the result and metrics, then clear the timer in `finally`. `parseAgentLogs` maps known prefixes such as `[WriterAgent]` and `[ReviewerAgent]`; unmatched lines use `system`.

- [ ] **Step 5: Run tests and commit**

Run: `pnpm test -- src/composables/useNovelRun.test.ts`

Expected: PASS.

```bash
git add VueSky/package.json VueSky/pnpm-lock.yaml VueSky/src/types VueSky/src/composables
git commit -m "feat: add novel run state adapter"
```

### Task 2: Project Navigation And Application Shell

**Files:**
- Create: `VueSky/src/components/ProjectNavigator.vue`
- Create: `VueSky/src/components/ProjectNavigator.test.ts`
- Create: `VueSky/src/styles/main.css`
- Modify: `VueSky/src/main.ts`
- Modify: `VueSky/src/App.vue`

**Interfaces:**
- Consumes: `NovelResult` from Task 1.
- Produces: `ContentSelection = { type: 'agent' } | { type: 'chapter'; index: number } | { type: 'character'; index: number }`.
- Emits: `select(selection: ContentSelection)`.

- [ ] **Step 1: Write failing navigator tests**

Render a result with two chapters and one character. Assert visible groups `Agent workspace`, `Outline`, `Chapters`, `Characters`, `World`, and `Foreshadowing`; assert count badges `2` and `1`; click the first character and expect `{ type: 'character', index: 0 }`.

- [ ] **Step 2: Run tests and verify red**

Run: `pnpm test -- src/components/ProjectNavigator.test.ts`

Expected: FAIL because `ProjectNavigator.vue` does not exist.

- [ ] **Step 3: Implement navigator and shell**

Use `BookOpen`, `Bot`, `Users`, `Globe2`, `ListTree`, and `Sparkles` icons. The shell CSS must use:

```css
.studio-shell {
  display: grid;
  grid-template-columns: 260px minmax(520px, 1fr) 300px;
  min-height: 100vh;
}
```

Navigation buttons must be full-width rows with an icon, label, count, visible active state, and `aria-current="page"` only when selected. Import `styles/main.css` from `main.ts` and remove page-level global CSS from `App.vue`.

- [ ] **Step 4: Run tests and commit**

Run: `pnpm test -- src/components/ProjectNavigator.test.ts`

Expected: PASS.

```bash
git add VueSky/src/components/ProjectNavigator.vue VueSky/src/components/ProjectNavigator.test.ts VueSky/src/styles/main.css VueSky/src/main.ts VueSky/src/App.vue
git commit -m "feat: add creative studio shell"
```

### Task 3: Agent Workspace And Timeline

**Files:**
- Create: `VueSky/src/components/AgentTimeline.vue`
- Create: `VueSky/src/components/AgentWorkspace.vue`
- Create: `VueSky/src/components/AgentWorkspace.test.ts`
- Modify: `VueSky/src/App.vue`

**Interfaces:**
- Consumes: `status`, `events`, `errorMessage`, `connected`, `elapsedSeconds` from `NovelRunController`.
- Emits: `generate(idea: string)` and `retry()`.

- [ ] **Step 1: Write failing workspace tests**

Assert idle state contains a labeled `Story idea` textbox and disabled Generate button when empty. Update the textbox and assert Generate emits the trimmed idea. For running state, assert `Agents are working`, elapsed time, and disabled input. For completed state, pass Writer and Reviewer events and assert both timeline messages. For failed state, assert an alert and enabled Retry button.

- [ ] **Step 2: Run tests and verify red**

Run: `pnpm test -- src/components/AgentWorkspace.test.ts`

Expected: FAIL because workspace components do not exist.

- [ ] **Step 3: Implement timeline and command surface**

Map agents to exact icons and colors:

```ts
const agentPresentation = {
  supervisor: { label: 'Supervisor', icon: Network, tone: 'violet' },
  setting: { label: 'Setting', icon: Globe2, tone: 'teal' },
  character: { label: 'Character', icon: Users, tone: 'rose' },
  plot: { label: 'Plot', icon: ListTree, tone: 'blue' },
  writer: { label: 'Writer', icon: PenLine, tone: 'green' },
  reviewer: { label: 'Reviewer', icon: ScanSearch, tone: 'amber' },
  system: { label: 'System', icon: CircleDot, tone: 'neutral' }
}
```

The running state shows one indeterminate activity row labeled `AgentSky workflow`; it must not rotate through fake Agent names. Use `Play`, `RotateCcw`, and `LoaderCircle` icons in command buttons.

- [ ] **Step 4: Run tests and commit**

Run: `pnpm test -- src/components/AgentWorkspace.test.ts`

Expected: PASS.

```bash
git add VueSky/src/components/AgentTimeline.vue VueSky/src/components/AgentWorkspace.vue VueSky/src/components/AgentWorkspace.test.ts VueSky/src/App.vue
git commit -m "feat: add agent activity workspace"
```

### Task 4: Character And Chapter Content Views

**Files:**
- Create: `VueSky/src/components/CharacterProfile.vue`
- Create: `VueSky/src/components/CharacterProfile.test.ts`
- Create: `VueSky/src/components/ChapterReader.vue`
- Create: `VueSky/src/components/ChapterReader.test.ts`
- Modify: `VueSky/src/App.vue`

**Interfaces:**
- `CharacterProfile` consumes `character: CharacterCard`.
- `ChapterReader` consumes `content: string` and `chapterNumber: number`.

- [ ] **Step 1: Write failing content-view tests**

For `CharacterProfile`, assert name, role type, personality, motivation, ability, background, and relationship count. Missing values must render `Not provided`. For `ChapterReader`, assert `Chapter 2` and preserve paragraph line breaks.

- [ ] **Step 2: Run tests and verify red**

Run: `pnpm test -- src/components/CharacterProfile.test.ts src/components/ChapterReader.test.ts`

Expected: FAIL because both components do not exist.

- [ ] **Step 3: Implement foundational profiles and reader**

Character initials are derived from the first two trimmed name characters. Use an unframed header with the initials block, name, and role; render profile fields in a two-column definition grid and relationships as compact rows. Do not generate fake portraits or relationships.

ChapterReader uses a constrained reading width of `72ch`, `white-space: pre-wrap`, and line-height `1.8`. App switches center content based on the `ContentSelection` emitted by navigation.

- [ ] **Step 4: Run tests and commit**

Run: `pnpm test -- src/components/CharacterProfile.test.ts src/components/ChapterReader.test.ts`

Expected: PASS.

```bash
git add VueSky/src/components/CharacterProfile.vue VueSky/src/components/CharacterProfile.test.ts VueSky/src/components/ChapterReader.vue VueSky/src/components/ChapterReader.test.ts VueSky/src/App.vue
git commit -m "feat: add character and chapter views"
```

### Task 5: Runtime Inspector And Responsive Panels

**Files:**
- Create: `VueSky/src/components/RunInspector.vue`
- Create: `VueSky/src/components/RunInspector.test.ts`
- Modify: `VueSky/src/App.vue`
- Modify: `VueSky/src/styles/main.css`

**Interfaces:**
- Consumes: `connected`, `status`, `tokenUsage`, `elapsedSeconds`, `reviewRound`, and `completedChapterCount`.
- Emits: `close` on tablet and mobile.

- [ ] **Step 1: Write failing inspector tests**

Assert formatting of `12,450` total tokens, `8,000` input, `4,450` output, `¥0.0321`, `8 calls`, `02:05`, review round `2`, and completed chapters `3`. With no usage, assert metric values are `--`.

- [ ] **Step 2: Run tests and verify red**

Run: `pnpm test -- src/components/RunInspector.test.ts`

Expected: FAIL because `RunInspector.vue` does not exist.

- [ ] **Step 3: Implement inspector and responsive behavior**

Use `Activity`, `Coins`, `Timer`, `MessagesSquare`, `BookCheck`, and `Server` icons. Metrics are simple bordered rows, not nested cards.

At widths below 1180px, hide the right panel and expose an icon button labeled `Open run metrics`. Below 760px also hide navigation and expose `Open project directory`. Drawers use fixed positioning, a backdrop button with an accessible name, and a `PanelLeftClose` close button. Escape closes any open drawer and focus returns to the trigger.

- [ ] **Step 4: Run tests and commit**

Run: `pnpm test -- src/components/RunInspector.test.ts src/App.test.ts`

Expected: PASS.

```bash
git add VueSky/src/components/RunInspector.vue VueSky/src/components/RunInspector.test.ts VueSky/src/App.vue VueSky/src/styles/main.css
git commit -m "feat: add runtime metrics and responsive panels"
```

### Task 6: Full Integration And Visual Verification

**Files:**
- Modify: `VueSky/src/App.test.ts`
- Modify: `VueSky/src/styles/main.css`

**Interfaces:**
- Consumes all component contracts from Tasks 1-5.
- Produces the complete responsive creative studio at `http://127.0.0.1:5173`.

- [ ] **Step 1: Complete application integration tests**

Mock health and generation responses. Assert this user path: Connected status appears, user submits an idea, real returned Agent logs render, Token count appears, Chapters count updates, clicking Chapter 1 opens its content, and clicking a character opens the profile. Add a failure test proving Retry submits the preserved idea once.

- [ ] **Step 2: Run complete automated verification**

Run:

```bash
pnpm test
pnpm build
```

Expected: all Vitest tests pass and Vite production build exits 0.

- [ ] **Step 3: Verify the running application in a browser**

Keep AgentSky, SprintbootSky, and VueSky running. Verify at 1440x900, 1024x768, and 390x844:

- no horizontal overflow;
- no overlapping directory, center content, or inspector;
- desktop three-column layout is visible;
- tablet metrics drawer opens and closes;
- mobile directory and metrics drawers open and close;
- long Chinese character names and chapter text remain inside their containers;
- browser console contains no errors.

- [ ] **Step 4: Run final repository checks and commit**

Run:

```bash
git diff --check -- VueSky
git status --short -- VueSky
```

Review the diff to ensure no generated `dist` files or unrelated changes are staged.

```bash
git add VueSky
git commit -m "feat: build Freesky creative studio UI"
```
