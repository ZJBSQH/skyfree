<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

interface CreateResponse {
  success: boolean
  error?: string
  result?: { completed_chapters?: string[] }
  token_usage?: { total_tokens?: number }
}

const idea = ref('')
const connected = ref(false)
const checked = ref(false)
const generating = ref(false)
const errorMessage = ref('')
const chapters = ref<string[]>([])
const totalTokens = ref<number | null>(null)

const canGenerate = computed(() =>
  connected.value && idea.value.trim().length > 0 && !generating.value
)

async function checkConnection() {
  try {
    const response = await fetch('/api/agent/health')
    if (!response.ok) throw new Error('HTTP ' + response.status)
    const health = await response.json()
    connected.value = health.status === 'ok'
    if (!connected.value) throw new Error('AgentSky unhealthy')
  } catch {
    connected.value = false
    errorMessage.value = 'AgentSky is unavailable'
  } finally {
    checked.value = true
  }
}

async function generateNovel() {
  if (!canGenerate.value) return
  generating.value = true
  errorMessage.value = ''
  chapters.value = []
  totalTokens.value = null

  try {
    const response = await fetch('/api/novels', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idea: idea.value.trim() })
    })
    const payload: CreateResponse = await response.json()
    if (!response.ok || !payload.success) {
      throw new Error(payload.error || 'HTTP ' + response.status)
    }
    chapters.value = payload.result?.completed_chapters ?? []
    totalTokens.value = payload.token_usage?.total_tokens ?? null
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Generation failed'
  } finally {
    generating.value = false
  }
}

onMounted(checkConnection)
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <p class="product">Freesky</p>
        <h1>Novel workspace</h1>
      </div>
      <div class="connection">
        <span class="status-dot" :class="{ connected, failed: checked && !connected }" aria-hidden="true" />
        <div>
          <strong>{{ connected ? 'Connected' : checked ? 'Unavailable' : 'Connecting' }}</strong>
          <span>VueSky -&gt; SprintbootSky -&gt; AgentSky</span>
        </div>
      </div>
    </header>

    <section class="workspace" aria-labelledby="idea-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">New project</p>
          <h2 id="idea-title">Story idea</h2>
        </div>
        <span class="counter">{{ idea.length }} / 2000</span>
      </div>

      <form class="idea-form" @submit.prevent="generateNovel">
        <label class="sr-only" for="story-idea">Story idea</label>
        <textarea
          id="story-idea"
          v-model="idea"
          maxlength="2000"
          placeholder="A courier discovers that every undelivered letter changes the city..."
        />
        <div class="form-actions">
          <span class="state-copy">{{ generating ? 'Agents are writing and reviewing...' : '' }}</span>
          <button type="submit" :disabled="!canGenerate">
            {{ generating ? 'Generating' : 'Generate' }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
    </section>

    <section v-if="chapters.length" class="results" aria-labelledby="results-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Reviewed output</p>
          <h2 id="results-title">Completed chapters</h2>
        </div>
        <span v-if="totalTokens !== null" class="counter">{{ totalTokens }} tokens</span>
      </div>
      <article v-for="(chapter, index) in chapters" :key="index" class="chapter">
        <h3>Chapter {{ index + 1 }}</h3>
        <p>{{ chapter }}</p>
      </article>
    </section>
  </main>
</template>

<style>
:root {
  color: #18201d;
  background: #eef1ee;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-synthesis: none;
}
* { box-sizing: border-box; }
body { margin: 0; min-width: 320px; min-height: 100vh; }
button, textarea { font: inherit; }
button:focus-visible, textarea:focus-visible {
  outline: 3px solid rgba(35, 104, 73, 0.2);
  outline-offset: 2px;
}
.app-shell {
  width: min(920px, calc(100% - 32px));
  margin: 0 auto;
  padding: 32px 0 64px;
}
.topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding-bottom: 24px;
  border-bottom: 1px solid #cbd2ce;
}
.product, .eyebrow {
  margin: 0 0 6px;
  color: #567066;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}
h1, h2, h3, p { margin-top: 0; }
h1 { margin-bottom: 0; font-size: 30px; line-height: 1.15; }
h2 { margin-bottom: 0; font-size: 20px; }
.connection { display: flex; align-items: center; gap: 10px; }
.connection div { display: grid; gap: 3px; }
.connection strong { font-size: 13px; }
.connection span:last-child { color: #68706b; font-size: 12px; }
.status-dot {
  width: 10px;
  height: 10px;
  flex: 0 0 10px;
  border-radius: 50%;
  background: #98a09b;
}
.status-dot.connected { background: #278457; }
.status-dot.failed { background: #b4473d; }
.workspace, .results { padding: 34px 0; border-bottom: 1px solid #d5dad7; }
.section-heading, .form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.counter, .state-copy { color: #68706b; font-size: 12px; }
.idea-form { margin-top: 20px; }
textarea {
  width: 100%;
  min-height: 176px;
  resize: vertical;
  padding: 16px;
  border: 1px solid #aeb7b1;
  border-radius: 6px;
  color: #18201d;
  background: #fff;
  line-height: 1.6;
}
textarea::placeholder { color: #89918c; }
.form-actions { min-height: 42px; margin-top: 12px; }
button {
  min-width: 112px;
  min-height: 40px;
  padding: 8px 18px;
  border: 1px solid #1e6645;
  border-radius: 6px;
  color: #fff;
  background: #236849;
  font-weight: 700;
  cursor: pointer;
}
button:hover:not(:disabled) { background: #194f37; }
button:disabled {
  border-color: #b8bfbb;
  color: #737b76;
  background: #dfe3e0;
  cursor: not-allowed;
}
.error {
  margin: 18px 0 0;
  padding: 12px 0;
  border-top: 1px solid #dfbbb7;
  color: #963a31;
}
.chapter { padding: 24px 0; border-bottom: 1px solid #d9ddda; }
.chapter:last-child { border-bottom: 0; }
.chapter h3 { margin-bottom: 10px; font-size: 15px; }
.chapter p {
  margin-bottom: 0;
  color: #323a36;
  line-height: 1.75;
  white-space: pre-wrap;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@media (max-width: 640px) {
  .app-shell { width: min(100% - 24px, 920px); padding-top: 24px; }
  .topbar { align-items: flex-start; flex-direction: column; }
  .connection { width: 100%; }
  .section-heading { align-items: flex-start; }
}
</style>
