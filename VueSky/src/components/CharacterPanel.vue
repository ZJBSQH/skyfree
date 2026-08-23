<script setup lang="ts">
import { Users } from 'lucide-vue-next'
import type { NovelCharacter } from '../types/api'

defineProps<{
  characters: NovelCharacter[]
}>()

const details = [
  ['personality', '性格'],
  ['background', '背景'],
  ['ability', '能力'],
  ['motivation', '动机']
] as const
</script>

<template>
  <section class="panel detail-panel" aria-labelledby="characters-title">
    <div class="panel-heading panel-heading--compact">
      <div>
        <p class="eyebrow">人物档案</p>
        <h2 id="characters-title">角色</h2>
      </div>
      <span class="count-badge">{{ characters.length }}</span>
    </div>

    <div v-if="!characters.length" class="small-empty">
      <Users :size="20" aria-hidden="true" />
      <span>创作后生成角色卡</span>
    </div>
    <article v-for="character in characters" v-else :key="character.name" class="character-card">
      <div class="character-title">
        <span class="character-avatar" aria-hidden="true">{{ character.name.slice(0, 1) }}</span>
        <div>
          <h3>{{ character.name }}</h3>
          <span>{{ character.role_type || '角色' }}</span>
        </div>
      </div>
      <dl v-if="details.some(([key]) => character[key])" class="compact-list">
        <template v-for="([key, label]) in details" :key="key">
          <div v-if="character[key]">
            <dt>{{ label }}</dt>
            <dd>{{ character[key] }}</dd>
          </div>
        </template>
      </dl>
      <div v-if="character.relationships.length" class="relationships">
        <h4>人物关系</h4>
        <p v-for="(relationship, index) in character.relationships" :key="`${relationship.name}-${index}`">
          <strong>{{ relationship.name || '未命名角色' }}</strong>
          <span>{{ [relationship.relation, relationship.dynamic].filter(Boolean).join(' · ') }}</span>
        </p>
      </div>
    </article>
  </section>
</template>

