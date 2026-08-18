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
  return knownNumber(value) ? new Intl.NumberFormat('en-US').format(value) : '--'
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

const statusLabel = computed(() => `${props.status.slice(0, 1).toUpperCase()}${props.status.slice(1)}`)
</script>

<template>
  <section class="run-inspector" aria-labelledby="run-inspector-title">
    <header class="run-inspector__header">
      <div>
        <p class="eyebrow">Runtime</p>
        <h2 id="run-inspector-title">Run metrics</h2>
      </div>
      <button
        v-if="dismissible"
        class="icon-button"
        type="button"
        aria-label="Close run metrics"
        title="Close run metrics"
        @click="emit('close')"
      >
        <PanelLeftClose :size="18" aria-hidden="true" />
      </button>
    </header>

    <dl class="run-inspector__metrics">
      <div class="run-inspector__row">
        <dt><Server :size="17" aria-hidden="true" />Service</dt>
        <dd>{{ connected ? 'Connected' : 'Unavailable' }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Activity :size="17" aria-hidden="true" />Run status</dt>
        <dd>{{ statusLabel }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />Total tokens</dt>
        <dd>{{ formatNumber(tokenUsage?.total_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />Input tokens</dt>
        <dd>{{ formatNumber(tokenUsage?.input_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />Output tokens</dt>
        <dd>{{ formatNumber(tokenUsage?.output_tokens) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />Estimated cost</dt>
        <dd>{{ formatYuan(tokenUsage?.cost_yuan) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Coins :size="17" aria-hidden="true" />API calls</dt>
        <dd>{{ knownNumber(tokenUsage?.call_count) ? `${tokenUsage?.call_count} calls` : '--' }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><Timer :size="17" aria-hidden="true" />Duration</dt>
        <dd>{{ formatElapsed(elapsedSeconds) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><MessagesSquare :size="17" aria-hidden="true" />Review round</dt>
        <dd>{{ formatNumber(reviewRound) }}</dd>
      </div>
      <div class="run-inspector__row">
        <dt><BookCheck :size="17" aria-hidden="true" />Completed chapters</dt>
        <dd>{{ formatNumber(completedChapterCount) }}</dd>
      </div>
    </dl>
  </section>
</template>
