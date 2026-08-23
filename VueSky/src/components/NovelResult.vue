<script setup lang="ts">
import { computed, ref } from 'vue'
import { Check, Clipboard, FileText, LoaderCircle, RotateCcw, Trash2 } from 'lucide-vue-next'
import type { NovelResult, RunStatus } from '../types/api'

const props = defineProps<{
  status: RunStatus
  result: NovelResult | null
  error: string
}>()

const emit = defineEmits<{
  clear: []
}>()

const copied = ref(false)
const hasChapters = computed(() => Boolean(props.result?.completed_chapters.length))
const hasDraft = computed(() => Boolean(props.result?.current_draft))
const hasReviewIssues = computed(() => Boolean(props.result?.review_issues.length))
const reviewIssueTitle = computed(() => props.status === 'failed' ? '审核未通过原因' : '审核提醒')

async function copyChapters() {
  const chapters = props.result?.completed_chapters ?? []
  const text = chapters.length > 0 ? chapters.join('\n\n') : props.result?.current_draft ?? ''
  if (!text || !navigator.clipboard) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    window.setTimeout(() => { copied.value = false }, 1600)
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <section class="panel result-panel" aria-labelledby="result-title">
    <div class="panel-heading result-heading">
      <div>
        <p class="eyebrow">创作成果</p>
        <h2 id="result-title">正文与运行结果</h2>
      </div>
      <div v-if="status === 'completed' || status === 'failed'" class="panel-actions">
        <button v-if="hasChapters || hasDraft" class="icon-text-button" type="button" aria-label="复制正文" @click="copyChapters">
          <Check v-if="copied" :size="16" aria-hidden="true" />
          <Clipboard v-else :size="16" aria-hidden="true" />
          {{ copied ? '已复制' : '复制正文' }}
        </button>
        <button class="icon-text-button icon-text-button--danger" type="button" aria-label="清空本次结果" @click="emit('clear')">
          <Trash2 :size="16" aria-hidden="true" />
          清空本次结果
        </button>
      </div>
    </div>

    <div v-if="status === 'idle'" class="result-empty">
      <FileText :size="30" aria-hidden="true" />
      <h3>等待你的创作灵感</h3>
      <p>完成登录并提交设定后，审核通过的正文会显示在这里。</p>
    </div>

    <div v-else-if="status === 'running'" class="result-empty result-empty--running" role="status" aria-live="polite">
      <LoaderCircle :size="30" class="spin" aria-hidden="true" />
      <h3>多 Agent 正在协作创作</h3>
      <p>设定、角色、剧情、正文与审核完成后将一次性返回结果。</p>
    </div>

    <div v-else-if="status === 'failed'" class="result-error" role="alert">
      <RotateCcw :size="22" aria-hidden="true" />
      <div>
        <h3>本次创作未完成</h3>
        <p>{{ error }}</p>
        <p v-if="result?.review_round" class="result-error__meta">审核轮次：第 {{ result.review_round }} 轮</p>
      </div>
    </div>

    <div v-if="hasReviewIssues" class="review-issues">
      <h3>{{ reviewIssueTitle }}</h3>
      <ul>
        <li v-for="(issue, index) in result?.review_issues" :key="index">
          <span class="issue-badge">{{ issue.severity || 'issue' }}</span>
          <div>
            <strong>{{ issue.description || '未提供具体原因' }}</strong>
            <p v-if="issue.suggestion">建议：{{ issue.suggestion }}</p>
          </div>
        </li>
      </ul>
    </div>

    <div v-if="hasChapters" class="chapter-list">
      <article v-for="(chapter, index) in result?.completed_chapters" :key="index" class="chapter-card">
        <header>
          <span>CHAPTER {{ String(index + 1).padStart(2, '0') }}</span>
          <h3>第 {{ index + 1 }} 章</h3>
        </header>
        <div class="prose">{{ chapter }}</div>
      </article>
    </div>

    <div v-else-if="status === 'failed' && hasDraft" class="chapter-list">
      <article class="chapter-card chapter-card--draft">
        <header>
          <span>DRAFT</span>
          <h3>已生成草稿</h3>
        </header>
        <div class="prose">{{ result?.current_draft }}</div>
      </article>
    </div>

    <div v-else-if="status === 'completed'" class="result-empty result-empty--compact">
      <p>创作流程已完成，但没有返回审核通过的正文。</p>
    </div>
  </section>
</template>
