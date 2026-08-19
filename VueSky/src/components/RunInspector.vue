<script setup lang="ts">
import { computed } from 'vue'
import { Activity, BookCheck, Coins, MessagesSquare, PanelLeftClose, Server, Timer } from 'lucide-vue-next'
import type { RunStatus, TokenUsage } from '../types/novel'

const props = withDefaults(defineProps<{
  connected: boolean
  status: RunStatus
  tokenUsage: TokenUsage | null
  elapsedSeconds: number | null
  reviewRound: number | null
  completedChapterCount: number | null
  dismissible?: boolean
}>(), {
  dismissible: false
})

const emit = defineEmits<{
  close: []
}>()

function knownNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function formatNumber(value: number | null | undefined) {
  return knownNumber(value) ? new Intl.NumberFormat('zh-CN').format(value) : '--'
}

function formatYuan(value: number | null | undefined) {
  return knownNumber(value) ? `¥${value.toFixed(4)}` : '--'
}

function formatElapsed(value: number | null) {
  if (!knownNumber(value) || value < 0) return '--'

  const wholeSeconds = Math.floor(value)
  const minutes = Math.floor(wholeSeconds / 60)
  const seconds = wholeSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const statusLabels: Record<RunStatus, string> = {
  idle: '待命',
  running: '运行中',
  completed: '已完成',
  failed: '失败'
}
const statusLabel = computed(() => statusLabels[props.status])
</script>

<template>
  <section class="run-inspector" aria-labelledby="run-inspector-title">
    <header class="run-inspector__header">
      <div>
        <p class="eyebrow">运行状态</p>
        <h2 id="run-inspector-title">运行数据</h2>
      </div>
      <button
        v-if="dismissible"
        class="icon-button"
        type="button"
        aria-label="关闭运行数据"
        title="关闭运行数据"
        @click="emit('close')"
      >
        <PanelLeftClose :size="18" aria-hidden="true" />
      </button>
    </header>

    <dl class="run-inspector__metrics">
      <div class="run-inspector__row">
        <dt><Server :size="17" aria-hidden="true" />服务</dt>
        <dd>{{ connected ? '已连接' : '不可用' }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Activity :size="17" aria-hidden="true" />运行状态</dt>
        <dd>{{ statusLabel }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />Token 总量</dt>
        <dd>{{ formatNumber(tokenUsage?.total_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />输入 Token</dt>
        <dd>{{ formatNumber(tokenUsage?.input_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />输出 Token</dt>
        <dd>{{ formatNumber(tokenUsage?.output_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />预估费用</dt>
        <dd>{{ formatYuan(tokenUsage?.cost_yuan) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />API 调用</dt>
        <dd>{{ knownNumber(tokenUsage?.call_count) ? `${tokenUsage?.call_count} 次` : '--' }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Timer :size="17" aria-hidden="true" />用时</dt>
        <dd>{{ formatElapsed(elapsedSeconds) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><MessagesSquare :size="17" aria-hidden="true" />审核轮次</dt>
        <dd>{{ formatNumber(reviewRound) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><BookCheck :size="17" aria-hidden="true" />完成章节</dt>
        <dd>{{ formatNumber(completedChapterCount) }}</dd>
      </div>
    </dl>
  </section>
</template>
