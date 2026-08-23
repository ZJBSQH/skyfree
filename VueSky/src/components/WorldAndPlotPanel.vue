<script setup lang="ts">
import { Globe2, ListTree } from 'lucide-vue-next'

defineProps<{
  worldSettings: unknown[]
  plotOutline: unknown[]
}>()

function displayEntry(entry: unknown) {
  if (typeof entry === 'string') return entry
  if (!entry || typeof entry !== 'object') return String(entry ?? '')
  return Object.entries(entry as Record<string, unknown>)
    .map(([key, value]) => `${key}：${typeof value === 'string' ? value : JSON.stringify(value)}`)
    .join('；')
}
</script>

<template>
  <section class="panel detail-panel" aria-labelledby="world-plot-title">
    <div class="panel-heading panel-heading--compact">
      <div>
        <p class="eyebrow">设定资料</p>
        <h2 id="world-plot-title">世界观与大纲</h2>
      </div>
    </div>
    <div class="detail-group">
      <h3><Globe2 :size="16" aria-hidden="true" />世界观</h3>
      <p v-if="!worldSettings.length" class="muted-copy">暂无世界观设定</p>
      <ol v-else class="numbered-details">
        <li v-for="(item, index) in worldSettings" :key="index">{{ displayEntry(item) }}</li>
      </ol>
    </div>
    <div class="detail-group">
      <h3><ListTree :size="16" aria-hidden="true" />剧情大纲</h3>
      <p v-if="!plotOutline.length" class="muted-copy">暂无剧情大纲</p>
      <ol v-else class="numbered-details">
        <li v-for="(item, index) in plotOutline" :key="index">{{ displayEntry(item) }}</li>
      </ol>
    </div>
  </section>
</template>

