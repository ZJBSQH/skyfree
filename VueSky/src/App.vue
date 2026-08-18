<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ProjectNavigator, { type ContentSelection } from './components/ProjectNavigator.vue'
import type { NovelResult } from './types/novel'

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
const selectedContent = ref<ContentSelection>({ type: 'agent' })

const navigatorResult = computed<NovelResult>(() => ({
  completed_chapters: chapters.value,
  characters: [],
  world_settings: [],
  plot_outline: [],
  review_round: 0
}))

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
  <div class="studio-shell">
    <ProjectNavigator :result="navigatorResult" @select="selectedContent = $event" />

    <main class="studio-canvas" :data-content-selection="selectedContent.type">
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

    <aside class="studio-inspector" aria-label="Project inspector">
      <h2>Project inspector</h2>
    </aside>
  </div>
</template>
