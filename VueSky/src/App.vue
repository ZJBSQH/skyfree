<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AgentWorkspace from './components/AgentWorkspace.vue'
import ProjectNavigator, { type ContentSelection } from './components/ProjectNavigator.vue'
import { useNovelRun } from './composables/useNovelRun'

const lastGeneratedIdea = ref('')
const selectedContent = ref<ContentSelection>({ type: 'agent' })
const {
  connected,
  checked,
  status,
  events,
  result,
  tokenUsage,
  error,
  elapsedSeconds,
  checkConnection,
  generate
} = useNovelRun()

const errorMessage = computed(() =>
  error.value || (checked.value && !connected.value ? 'AgentSky is unavailable' : '')
)

function generateNovel(idea: string) {
  lastGeneratedIdea.value = idea
  return generate(idea)
}

function retryNovel() {
  if (lastGeneratedIdea.value) return generate(lastGeneratedIdea.value)
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

      <AgentWorkspace
        :status="status"
        :events="events"
        :error-message="errorMessage"
        :connected="connected"
        :elapsed-seconds="elapsedSeconds"
        @generate="generateNovel"
        @retry="retryNovel"
      />

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
