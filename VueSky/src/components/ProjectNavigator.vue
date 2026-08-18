<script lang="ts">
export type ContentSelection =
  | { type: 'agent' }
  | { type: 'chapter'; index: number }
  | { type: 'character'; index: number }
</script>

<script setup lang="ts">
import { ref } from 'vue'
import { BookOpen, Bot, Globe2, ListTree, Sparkles, Users } from 'lucide-vue-next'
import type { NovelResult } from '../types/novel'

const props = defineProps<{
  result: NovelResult | null
}>()

const emit = defineEmits<{
  select: [selection: ContentSelection]
}>()

const selection = ref<ContentSelection>({ type: 'agent' })

function isSelected(candidate: ContentSelection) {
  if (candidate.type === 'agent') return selection.value.type === 'agent'

  return candidate.type === selection.value.type
    && 'index' in selection.value
    && candidate.index === selection.value.index
}

function select(candidate: ContentSelection) {
  selection.value = candidate
  emit('select', candidate)
}
</script>

<template>
  <aside class="project-navigator">
    <div class="project-navigator__brand">
      <Sparkles :size="18" aria-hidden="true" />
      <span>Freesky Studio</span>
    </div>

    <nav aria-label="Project navigation">
      <section class="project-navigator__section" aria-labelledby="agent-workspace">
        <h2 id="agent-workspace">Agent workspace</h2>
        <button
          class="project-navigator__item"
          :class="{ 'project-navigator__item--active': isSelected({ type: 'agent' }) }"
          type="button"
          :aria-current="isSelected({ type: 'agent' }) ? 'page' : undefined"
          @click="select({ type: 'agent' })"
        >
          <Bot :size="18" aria-hidden="true" />
          <span>Agent workspace</span>
        </button>
      </section>

      <section class="project-navigator__section" aria-labelledby="outline">
        <div class="project-navigator__heading">
          <h2 id="outline">Outline</h2>
          <span class="project-navigator__count">{{ result?.plot_outline.length ?? 0 }}</span>
        </div>
        <div class="project-navigator__item project-navigator__item--muted">
          <ListTree :size="18" aria-hidden="true" />
          <span>Story outline</span>
        </div>
      </section>

      <section class="project-navigator__section" aria-labelledby="chapters">
        <div class="project-navigator__heading">
          <h2 id="chapters">Chapters</h2>
          <span class="project-navigator__count">{{ result?.completed_chapters.length ?? 0 }}</span>
        </div>
        <button
          v-for="(_, index) in result?.completed_chapters ?? []"
          :key="index"
          class="project-navigator__item"
          :class="{ 'project-navigator__item--active': isSelected({ type: 'chapter', index }) }"
          type="button"
          :aria-current="isSelected({ type: 'chapter', index }) ? 'page' : undefined"
          @click="select({ type: 'chapter', index })"
        >
          <BookOpen :size="18" aria-hidden="true" />
          <span>Chapter {{ index + 1 }}</span>
        </button>
      </section>

      <section class="project-navigator__section" aria-labelledby="characters">
        <div class="project-navigator__heading">
          <h2 id="characters">Characters</h2>
          <span class="project-navigator__count">{{ result?.characters.length ?? 0 }}</span>
        </div>
        <button
          v-for="(character, index) in result?.characters ?? []"
          :key="`${character.name}-${index}`"
          class="project-navigator__item"
          :class="{ 'project-navigator__item--active': isSelected({ type: 'character', index }) }"
          type="button"
          :aria-current="isSelected({ type: 'character', index }) ? 'page' : undefined"
          @click="select({ type: 'character', index })"
        >
          <Users :size="18" aria-hidden="true" />
          <span>{{ character.name }}</span>
        </button>
      </section>

      <section class="project-navigator__section" aria-labelledby="world">
        <div class="project-navigator__heading">
          <h2 id="world">World</h2>
          <span class="project-navigator__count">{{ result?.world_settings.length ?? 0 }}</span>
        </div>
        <div class="project-navigator__item project-navigator__item--muted">
          <Globe2 :size="18" aria-hidden="true" />
          <span>World settings</span>
        </div>
      </section>

      <section class="project-navigator__section" aria-labelledby="foreshadowing">
        <h2 id="foreshadowing">Foreshadowing</h2>
        <div class="project-navigator__item project-navigator__item--muted">
          <Sparkles :size="18" aria-hidden="true" />
          <span>Story threads</span>
        </div>
      </section>
    </nav>
  </aside>
</template>
