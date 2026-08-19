<script setup lang="ts">
import { computed, ref } from 'vue'
import { LoaderCircle, Play, RotateCcw } from 'lucide-vue-next'
import AgentTimeline from './AgentTimeline.vue'
import type { AgentEvent, RunStatus } from '../types/novel'

const props = defineProps<{
  status: RunStatus
  events: AgentEvent[]
  errorMessage: string
  connected: boolean
  elapsedSeconds: number
}>()

const emit = defineEmits<{
  generate: [idea: string]
  retry: []
}>()

const idea = ref('')
const isRunning = computed(() => props.status === 'running')
const canGenerate = computed(() =>
  props.connected && idea.value.trim().length > 0 && !isRunning.value
)

const elapsedTime = computed(() => {
  const minutes = Math.floor(props.elapsedSeconds / 60)
  const seconds = props.elapsedSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

function submit() {
  if (!canGenerate.value) return
  emit('generate', idea.value.trim())
}
</script>

<template>
  <section class="agent-workspace" aria-labelledby="agent-workspace-title">
    <div class="agent-workspace__heading">
      <div>
        <p class="eyebrow">新建作品</p>
        <h2 id="agent-workspace-title">智能体工作区</h2>
      </div>
      <span v-if="isRunning" class="agent-workspace__elapsed">已用时 {{ elapsedTime }}</span>
    </div>

    <p v-if="isRunning" class="agent-workspace__state" aria-live="polite">智能体正在协作</p>

    <AgentTimeline :events="events" :is-running="isRunning" />

    <form class="agent-workspace__form" @submit.prevent="submit">
      <label for="story-idea">创作灵感</label>
      <textarea
        id="story-idea"
        v-model="idea"
        maxlength="2000"
        :disabled="isRunning"
        placeholder="例如：一名失明少年在湖边遇见鬼魂，为拯救同伴踏上未知旅程……"
      />
      <div class="agent-workspace__commands">
        <span class="agent-workspace__counter">{{ idea.length }} / 2000</span>
        <button v-if="status === 'failed'" type="button" @click="emit('retry')">
          <RotateCcw :size="17" aria-hidden="true" />
          <span>重试</span>
        </button>
        <button v-else type="submit" :disabled="!canGenerate">
          <LoaderCircle v-if="isRunning" :size="17" class="agent-workspace__spinner" aria-hidden="true" />
          <Play v-else :size="17" aria-hidden="true" />
          <span>{{ isRunning ? '创作中' : '开始创作' }}</span>
        </button>
      </div>
    </form>

    <p v-if="errorMessage" class="agent-workspace__error" role="alert">{{ errorMessage }}</p>
  </section>
</template>

<style scoped>
.agent-workspace { padding: 34px 0; border-bottom: 1px solid #d5dad7; }

.agent-workspace__heading, .agent-workspace__commands {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.agent-workspace__elapsed, .agent-workspace__counter {
  color: #68706b;
  font-size: 12px;
}

.agent-workspace__state {
  margin: 20px 0 0;
  color: #236849;
  font-size: 13px;
  font-weight: 700;
}

.agent-workspace__form { margin-top: 24px; }

.agent-workspace__form label {
  display: block;
  margin-bottom: 8px;
  color: #3a4540;
  font-size: 13px;
  font-weight: 700;
}

.agent-workspace__commands { min-height: 42px; margin-top: 12px; }

.agent-workspace__commands button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 112px;
  min-height: 40px;
  gap: 8px;
  padding: 8px 18px;
  border: 1px solid #1e6645;
  border-radius: 6px;
  color: #fff;
  background: #236849;
  font-weight: 700;
  cursor: pointer;
}

.agent-workspace__commands button:hover:not(:disabled) { background: #194f37; }

.agent-workspace__commands button:disabled {
  border-color: #b8bfbb;
  color: #737b76;
  background: #dfe3e0;
  cursor: not-allowed;
}

.agent-workspace__error {
  margin: 18px 0 0;
  padding: 12px 0;
  border-top: 1px solid #dfbbb7;
  color: #963a31;
}

.agent-workspace__spinner { animation: agent-workspace-spin 1s linear infinite; }

@keyframes agent-workspace-spin {
  to { transform: rotate(360deg); }
}
</style>
