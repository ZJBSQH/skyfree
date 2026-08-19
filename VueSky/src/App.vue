<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PanelLeftClose, PanelLeftOpen, PanelRightOpen } from 'lucide-vue-next'
import AgentWorkspace from './components/AgentWorkspace.vue'
import ChapterReader from './components/ChapterReader.vue'
import CharacterProfile from './components/CharacterProfile.vue'
import ProjectNavigator, { type ContentSelection } from './components/ProjectNavigator.vue'
import RunInspector from './components/RunInspector.vue'
import { useNovelRun } from './composables/useNovelRun'

const lastGeneratedIdea = ref('')
const selectedContent = ref<ContentSelection>({ type: 'agent' })
const viewportWidth = ref(typeof window === 'undefined' ? 1180 : window.innerWidth)
const openDrawer = ref<'project' | 'metrics' | null>(null)
const drawerTrigger = ref<HTMLElement | null>(null)
const drawerDialog = ref<HTMLElement | null>(null)
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
const selectedChapter = computed(() =>
  selectedContent.value.type === 'chapter'
    ? result.value?.completed_chapters[selectedContent.value.index]
    : undefined
)
const selectedCharacter = computed(() =>
  selectedContent.value.type === 'character'
    ? result.value?.characters[selectedContent.value.index]
    : undefined
)
const isDesktop = computed(() => viewportWidth.value >= 1180)
const isMobile = computed(() => viewportWidth.value < 760)
const isCompactTopbar = computed(() => viewportWidth.value <= 420)
const inspectorElapsedSeconds = computed(() =>
  status.value === 'idle' ? null : elapsedSeconds.value
)

watch(result, () => {
  if (selectedContent.value.type === 'chapter' && selectedChapter.value === undefined) {
    selectedContent.value = { type: 'agent' }
  }

  if (selectedContent.value.type === 'character' && !selectedCharacter.value) {
    selectedContent.value = { type: 'agent' }
  }
})

function generateNovel(idea: string) {
  lastGeneratedIdea.value = idea
  return generate(idea)
}

function retryNovel() {
  if (lastGeneratedIdea.value) return generate(lastGeneratedIdea.value)
}

function updateViewport() {
  viewportWidth.value = window.innerWidth
  if ((openDrawer.value === 'project' && !isMobile.value) || (openDrawer.value === 'metrics' && isDesktop.value)) {
    closeResponsiveDrawer(false)
  }
}

function openResponsiveDrawer(drawer: 'project' | 'metrics', event: MouseEvent) {
  drawerTrigger.value = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  openDrawer.value = drawer
  nextTick(focusFirstDrawerControl)
}

function focusableDrawerElements() {
  if (!drawerDialog.value) return []

  return Array.from(drawerDialog.value.querySelectorAll<HTMLElement>(
    'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  )).filter((element) => element.tabIndex >= 0)
}

function focusFirstDrawerControl() {
  focusableDrawerElements()[0]?.focus()
}

function closeResponsiveDrawer(restoreFocus = true) {
  const trigger = drawerTrigger.value
  openDrawer.value = null
  if (!restoreFocus) return

  nextTick(() => {
    if (!trigger?.isConnected) return

    const style = window.getComputedStyle(trigger)
    if (style.display !== 'none' && style.visibility !== 'hidden') trigger.focus()
  })
}

function selectContent(selection: ContentSelection) {
  selectedContent.value = selection
  if (openDrawer.value === 'project') closeResponsiveDrawer()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && openDrawer.value) closeResponsiveDrawer()
  if (event.key !== 'Tab' || !openDrawer.value) return

  const focusableElements = focusableDrawerElements()
  if (!focusableElements.length) return

  const first = focusableElements[0]!
  const last = focusableElements[focusableElements.length - 1]!
  const activeElement = document.activeElement

  if (event.shiftKey && (activeElement === first || !drawerDialog.value?.contains(activeElement))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && (activeElement === last || !drawerDialog.value?.contains(activeElement))) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  checkConnection()
  window.addEventListener('resize', updateViewport)
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateViewport)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="studio-shell">
    <ProjectNavigator v-if="!isMobile" :result="result" :selection="selectedContent" @select="selectContent" />

    <main class="studio-canvas" :data-content-selection="selectedContent.type">
      <header class="topbar" :class="{ 'topbar--compact': isCompactTopbar }">
        <div>
          <p class="product">Freesky</p>
          <h1>Novel workspace</h1>
        </div>
        <div class="topbar__actions">
          <button
            v-if="isMobile"
            class="icon-button"
            type="button"
            aria-label="Open project directory"
            title="Open project directory"
            aria-controls="responsive-drawer"
            :aria-expanded="openDrawer === 'project'"
            @click="openResponsiveDrawer('project', $event)"
          >
            <PanelLeftOpen :size="19" aria-hidden="true" />
          </button>
          <button
            v-if="!isDesktop"
            class="icon-button"
            type="button"
            aria-label="Open run metrics"
            title="Open run metrics"
            aria-controls="responsive-drawer"
            :aria-expanded="openDrawer === 'metrics'"
            @click="openResponsiveDrawer('metrics', $event)"
          >
            <PanelRightOpen :size="19" aria-hidden="true" />
          </button>
          <div class="connection">
            <span class="status-dot" :class="{ connected, failed: checked && !connected }" aria-hidden="true" />
            <div>
              <strong>{{ connected ? 'Connected' : checked ? 'Unavailable' : 'Connecting' }}</strong>
              <span>VueSky -&gt; SprintbootSky -&gt; AgentSky</span>
            </div>
          </div>
        </div>
      </header>

      <template v-if="selectedContent.type === 'agent'">
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
      </template>
      <ChapterReader
        v-else-if="selectedChapter !== undefined"
        :content="selectedChapter"
        :chapter-number="selectedContent.index + 1"
      />
      <CharacterProfile
        v-else-if="selectedCharacter"
        :character="selectedCharacter"
      />
    </main>

    <aside v-if="isDesktop" class="studio-inspector" aria-label="Run metrics">
      <RunInspector
        :connected="connected"
        :status="status"
        :token-usage="tokenUsage"
        :elapsed-seconds="inspectorElapsedSeconds"
        :review-round="result?.review_round ?? null"
        :completed-chapter-count="result?.completed_chapters.length ?? null"
      />
    </aside>
  </div>

  <div v-if="openDrawer" class="drawer-layer">
    <button
      class="drawer-backdrop"
      type="button"
      :aria-label="openDrawer === 'project' ? 'Close project directory backdrop' : 'Close run metrics backdrop'"
      @click="closeResponsiveDrawer()"
    />
    <aside
      class="side-drawer"
      :class="`side-drawer--${openDrawer}`"
      id="responsive-drawer"
      ref="drawerDialog"
      role="dialog"
      aria-modal="true"
      :aria-label="openDrawer === 'project' ? 'Project directory' : 'Run metrics'"
    >
      <template v-if="openDrawer === 'project'">
        <header class="side-drawer__header">
          <h2>Project directory</h2>
          <button class="icon-button" type="button" aria-label="Close project directory" title="Close project directory" @click="closeResponsiveDrawer()">
            <PanelLeftClose :size="18" aria-hidden="true" />
          </button>
        </header>
        <ProjectNavigator :result="result" :selection="selectedContent" @select="selectContent" />
      </template>
      <RunInspector
        v-else
        :connected="connected"
        :status="status"
        :token-usage="tokenUsage"
        :elapsed-seconds="inspectorElapsedSeconds"
        :review-round="result?.review_round ?? null"
        :completed-chapter-count="result?.completed_chapters.length ?? null"
        dismissible
        @close="closeResponsiveDrawer"
      />
    </aside>
  </div>
</template>
