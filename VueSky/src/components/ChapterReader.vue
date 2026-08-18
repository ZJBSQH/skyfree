<script setup lang="ts">
defineProps<{
  content: string
  chapterNumber: number
}>()
</script>

<template>
  <article class="chapter-reader" :aria-labelledby="`chapter-${chapterNumber}-title`">
    <header class="chapter-reader__header">
      <p class="eyebrow">Reviewed chapter</p>
      <h2 :id="`chapter-${chapterNumber}-title`">Chapter {{ chapterNumber }}</h2>
    </header>
    <div class="chapter-reader__content">{{ content }}</div>
  </article>
</template>

<style scoped>
.chapter-reader { max-width: 72ch; padding: 34px 0; }

.chapter-reader__header {
  padding-bottom: 24px;
  border-bottom: 1px solid #d5dad7;
}

.chapter-reader__content {
  padding: 28px 0;
  color: #323a36;
  font-size: 16px;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
