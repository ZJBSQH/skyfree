<script setup lang="ts">
import { Activity, BookOpenText, RefreshCw } from 'lucide-vue-next'
import AgentTimeline from './AgentTimeline.vue'
import AuthPanel from './AuthPanel.vue'
import CharacterPanel from './CharacterPanel.vue'
import CreativeStudio from './CreativeStudio.vue'
import HealthBadge from './HealthBadge.vue'
import NovelResult from './NovelResult.vue'
import TokenUsagePanel from './TokenUsage.vue'
import WorldAndPlotPanel from './WorldAndPlotPanel.vue'
import type {
  AgentLog,
  AuthUser,
  LoginRequest,
  NovelResult as NovelResultData,
  NovelRunInput,
  RegisterRequest,
  RunStatus,
  ServiceStatus,
  TokenUsage
} from '../types/api'

defineProps<{
  authUser: AuthUser | null
  authLoading: boolean
  authError: string
  runStatus: RunStatus
  runLoading: boolean
  runResult: NovelResultData | null
  runLogs: AgentLog[]
  runTokenUsage: TokenUsage | null
  runError: string
  javaStatus: ServiceStatus
  agentStatus: ServiceStatus
  fallbackActive: boolean
}>()

const emit = defineEmits<{
  login: [credentials: LoginRequest]
  register: [details: RegisterRequest]
  logout: []
  submit: [input: NovelRunInput]
  clear: []
  refreshHealth: []
}>()
</script>

<template>
  <div class="workbench-shell">
    <aside class="workbench-left" aria-label="创作设置">
      <header class="brand-header">
        <div class="brand-mark" aria-hidden="true"><BookOpenText :size="21" /></div>
        <div>
          <p>FREESKY</p>
          <h1>多 Agent 小说创作工作台</h1>
        </div>
      </header>

      <CreativeStudio
        :authenticated="Boolean(authUser)"
        :loading="runLoading"
        @submit="emit('submit', $event)"
      />

      <section class="panel health-panel" aria-labelledby="health-title">
        <div class="panel-heading panel-heading--compact">
          <div>
            <p class="eyebrow">运行环境</p>
            <h2 id="health-title">服务状态</h2>
          </div>
          <button class="icon-button" type="button" aria-label="重新检查服务" @click="emit('refreshHealth')">
            <RefreshCw :size="16" aria-hidden="true" />
          </button>
        </div>
        <div class="health-list">
          <HealthBadge label="Spring Boot " :status="javaStatus" />
          <HealthBadge label="AgentSky " :status="agentStatus" />
        </div>
        <p v-if="fallbackActive" class="fallback-note">
          <Activity :size="14" aria-hidden="true" />
          已启用本地 AgentSky 开发通道
        </p>
      </section>

      <AuthPanel
        :user="authUser"
        :loading="authLoading"
        :error="authError"
        @login="emit('login', $event)"
        @register="emit('register', $event)"
        @logout="emit('logout')"
      />
    </aside>

    <main class="workbench-main">
      <NovelResult
        :status="runStatus"
        :result="runResult"
        :error="runError"
        @clear="emit('clear')"
      />
    </main>

    <aside class="workbench-right" aria-label="创作详情">
      <section class="panel detail-panel" aria-labelledby="timeline-title">
        <div class="panel-heading panel-heading--compact">
          <div>
            <p class="eyebrow">协作过程</p>
            <h2 id="timeline-title">Agent 日志</h2>
          </div>
          <span class="count-badge">{{ runLogs.length }}</span>
        </div>
        <AgentTimeline :events="runLogs" :is-running="runLoading" />
        <p v-if="!runLogs.length && !runLoading" class="muted-copy">开始创作后显示 Agent 协作记录。</p>
      </section>

      <CharacterPanel :characters="runResult?.characters ?? []" />
      <WorldAndPlotPanel
        :world-settings="runResult?.world_settings ?? []"
        :plot-outline="runResult?.plot_outline ?? []"
      />
      <TokenUsagePanel :usage="runTokenUsage" />
    </aside>
  </div>
</template>

