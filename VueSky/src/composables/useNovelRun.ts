import { ref } from 'vue'
import { normalizeNovelResult } from '../types/novel'
import type {
  AgentEvent,
  AgentName,
  NovelResult,
  NovelRunController,
  TokenUsage
} from '../types/novel'

interface HealthResponse {
  status?: string
}

interface GenerateResponse {
  success?: boolean
  error?: string
  logs?: string[]
  result?: unknown
  token_usage?: TokenUsage
}

const agentPrefixes: Array<[string, AgentName]> = [
  ['[SupervisorAgent]', 'supervisor'],
  ['[SettingAgent]', 'setting'],
  ['[CharacterAgent]', 'character'],
  ['[PlotAgent]', 'plot'],
  ['[WriterAgent]', 'writer'],
  ['[ReviewerAgent]', 'reviewer']
]

export function parseAgentLogs(logs: string[]): AgentEvent[] {
  return logs.map((line, index) => {
    const prefix = agentPrefixes.find(([candidate]) => line.startsWith(candidate))
    const [agentPrefix, agent] = prefix ?? ['', 'system']

    return {
      id: `event-${index + 1}`,
      agent,
      message: line.slice(agentPrefix.length).trim(),
      sequence: index + 1
    }
  })
}

export function useNovelRun(): NovelRunController {
  const connected = ref(false)
  const checked = ref(false)
  const status = ref<'idle' | 'running' | 'completed' | 'failed'>('idle')
  const events = ref<AgentEvent[]>([])
  const result = ref<NovelResult | null>(null)
  const tokenUsage = ref<TokenUsage | null>(null)
  const elapsedSeconds = ref(0)
  const error = ref('')
  let elapsedTimer: ReturnType<typeof setInterval> | undefined

  async function checkConnection() {
    try {
      const response = await fetch('/api/agent/health')
      if (!response.ok) throw new Error(`HTTP ${response.status}`)

      const health: HealthResponse = await response.json()
      connected.value = health.status === 'ok'
      if (!connected.value) throw new Error('AgentSky 服务未就绪')
    } catch {
      connected.value = false
    } finally {
      checked.value = true
    }
  }

  async function generate(idea: string) {
    if (status.value === 'running') return

    status.value = 'running'
    error.value = ''
    events.value = []
    result.value = null
    tokenUsage.value = null
    elapsedSeconds.value = 0
    elapsedTimer = setInterval(() => {
      elapsedSeconds.value += 1
    }, 1000)

    try {
      const response = await fetch('/api/novels', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idea: idea.trim() })
      })
      const payload: GenerateResponse = await response.json()
      if (!response.ok || !payload.success) {
        throw new Error('创作失败，请稍后重试')
      }

      events.value = parseAgentLogs(payload.logs ?? [])
      result.value = normalizeNovelResult(payload.result)
      tokenUsage.value = payload.token_usage ?? null
      status.value = 'completed'
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '创作失败，请稍后重试'
      status.value = 'failed'
    } finally {
      if (elapsedTimer) clearInterval(elapsedTimer)
      elapsedTimer = undefined
    }
  }

  return {
    connected,
    checked,
    status,
    events,
    result,
    tokenUsage,
    elapsedSeconds,
    error,
    checkConnection,
    generate
  }
}
