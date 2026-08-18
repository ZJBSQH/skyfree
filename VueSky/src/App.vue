<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ProjectNavigator, { type ContentSelection } from './components/ProjectNavigator.vue'
import { useNovelRun } from './composables/useNovelRun'

const idea = ref('')
const selectedContent = ref<ContentSelection>({ type: 'agent' })
const {
  connected,
  checked,
  status,
  result,
  tokenUsage,
  error,
  checkConnection,
  generate
} = useNovelRun()

const generating = computed(() => status.value === 'running')
const errorMessage = computed(() =>
  error.value || (checked.value && !connected.value ? 'AgentSky is unavailable' : '')
)

const canGenerate = computed(() =>
  connected.value && idea.value.trim().length > 0 && !generating.value
)

function generateNovel() {
  return generate(idea.value)
}

onMounted(checkConnection)
</script>

<template>
  <div class="studio-shell">
    <ProjectNavigator :result="result" @select="selectedContent = $event" />

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

      <section v-if="result?.completed_chapters.length" class="results" aria-labelledby="results-title">
        <div class="section-heading">
          <div>
            <p class="eyebrow">Reviewed output</p>
            <h2 id="results-title">Completed chapters</h2>
          </div>
          <span v-if="tokenUsage" class="counter">{{ tokenUsage.total_tokens }} tokens</span>
        </div>
        <article v-for="(chapter, index) in result?.completed_chapters ?? []" :key="index" class="chapter">
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
