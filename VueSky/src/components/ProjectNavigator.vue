<script lang="ts">
export type ContentSelection =
  | { type: 'agent' }
  | { type: 'chapter'; index: number }
  | { type: 'character'; index: number }
</script>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { BookOpen, Bot, Globe2, ListTree, Sparkles, Users } from 'lucide-vue-next'
import { normalizeCharacterCard, type NovelResult } from '../types/novel'

const props = defineProps<{
  result: NovelResult | null
  selection?: ContentSelection
}>()

const emit = defineEmits<{
  select: [selection: ContentSelection]
}>()

const selection = ref<ContentSelection>({ type: 'agent' })
const activeSelection = computed(() => props.selection ?? selection.value)

function isSelected(candidate: ContentSelection) {
  if (candidate.type === 'agent') return activeSelection.value.type === 'agent'

  return candidate.type === activeSelection.value.type
    && 'index' in activeSelection.value
    && candidate.index === activeSelection.value.index
}

function select(candidate: ContentSelection) {
  selection.value = candidate
  emit('select', candidate)
}

function itemLabel(item: unknown, fallback: string) {
  if (typeof item === 'string') return item
  if (typeof item === 'number') return String(item)
  if (item && typeof item === 'object') {
    const label = (item as Record<string, unknown>).name
      ?? (item as Record<string, unknown>).title
      ?? (item as Record<string, unknown>).label
    if (typeof label === 'string') return label
  }

  return fallback
}

function characterLabel(character: unknown) {
  return normalizeCharacterCard(character)?.name ?? 'Unnamed character'
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
        <template v-if="result?.plot_outline.length">
          <div
            v-for="(outline, index) in result.plot_outline"
            :key="index"
            class="project-navigator__item project-navigator__item--muted"
          >
            <ListTree :size="18" aria-hidden="true" />
            <span>{{ itemLabel(outline, `Outline ${index + 1}`) }}</span>
          </div>
        </template>
        <div v-else class="project-navigator__item project-navigator__item--muted">
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
          :key="`${characterLabel(character)}-${index}`"
          class="project-navigator__item"
          :class="{ 'project-navigator__item--active': isSelected({ type: 'character', index }) }"
          type="button"
          :aria-current="isSelected({ type: 'character', index }) ? 'page' : undefined"
          @click="select({ type: 'character', index })"
        >
          <Users :size="18" aria-hidden="true" />
          <span>{{ characterLabel(character) }}</span>
        </button>
      </section>

      <section class="project-navigator__section" aria-labelledby="world">
        <div class="project-navigator__heading">
          <h2 id="world">World</h2>
          <span class="project-navigator__count">{{ result?.world_settings.length ?? 0 }}</span>
        </div>
        <template v-if="result?.world_settings.length">
          <div
            v-for="(setting, index) in result.world_settings"
            :key="index"
            class="project-navigator__item project-navigator__item--muted"
          >
            <Globe2 :size="18" aria-hidden="true" />
            <span>{{ itemLabel(setting, `Setting ${index + 1}`) }}</span>
          </div>
        </template>
        <div v-else class="project-navigator__item project-navigator__item--muted">
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
