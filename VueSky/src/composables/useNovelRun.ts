import { computed, ref } from 'vue'
import { ApiError, safeApiMessage } from '../api/client'
import { createNovel, normalizeNovelResult, normalizeTokenUsage } from '../api/novels'
import type { AgentKind, AgentLog, NovelResponse, NovelResult, NovelRunInput, RunStatus, TokenUsage } from '../types/api'

const agentPrefixes: Array<[string, AgentKind]> = [
  ['[SupervisorAgent]', 'supervisor'],
  ['[SettingAgent]', 'setting'],
  ['[CharacterAgent]', 'character'],
  ['[PlotAgent]', 'plot'],
  ['[WriterAgent]', 'writer'],
  ['[ReviewerAgent]', 'reviewer']
]

export function parseAgentLogs(lines: string[]): AgentLog[] {
  return lines.map((line, index) => {
    const matched = agentPrefixes.find(([prefix]) => line.startsWith(prefix))
    const [prefix, agent] = matched ?? ['', 'system']
    return {
      id: `agent-log-${index + 1}`,
      agent,
      message: line.slice(prefix.length).trim(),
      sequence: index + 1
    }
  })
}

interface NovelRunOptions {
  allowFallback?: boolean
  onUnauthorized?: () => void
}

function usageFromPayload(payload: unknown) {
  if (!payload || typeof payload !== 'object') return null
  return normalizeTokenUsage((payload as NovelResponse).token_usage)
}

export function useNovelRun(options: NovelRunOptions = {}) {
  const status = ref<RunStatus>('idle')
  const result = ref<NovelResult | null>(null)
  const logs = ref<AgentLog[]>([])
  const tokenUsage = ref<TokenUsage | null>(null)
  const error = ref('')
  const loading = computed(() => status.value === 'running')

  async function submit(input: NovelRunInput) {
    if (loading.value) return false
    status.value = 'running'
    result.value = null
    logs.value = []
    tokenUsage.value = null
    error.value = ''

    try {
      const response = await createNovel(input, options.allowFallback)
      tokenUsage.value = normalizeTokenUsage(response.token_usage)
      if (!response.success) {
        error.value = safeApiMessage(response, '创作失败，请稍后重试')
        result.value = normalizeNovelResult(response.result)
        logs.value = parseAgentLogs(Array.isArray(response.logs) ? response.logs : [])
        status.value = 'failed'
        return false
      }
      result.value = normalizeNovelResult(response.result)
      logs.value = parseAgentLogs(Array.isArray(response.logs) ? response.logs : [])
      status.value = 'completed'
      return true
    } catch (cause) {
      if (cause instanceof ApiError) {
        tokenUsage.value = usageFromPayload(cause.payload)
        if (cause.payload && typeof cause.payload === 'object') {
          const response = cause.payload as NovelResponse
          result.value = normalizeNovelResult(response.result)
          logs.value = parseAgentLogs(Array.isArray(response.logs) ? response.logs : [])
        }
        error.value = cause.message
        if (cause.status === 401) options.onUnauthorized?.()
      } else {
        error.value = '创作失败，请稍后重试'
      }
      status.value = 'failed'
      return false
    }
  }

  function clear() {
    status.value = 'idle'
    result.value = null
    logs.value = []
    tokenUsage.value = null
    error.value = ''
  }

  return { status, loading, result, logs, tokenUsage, error, submit, clear }
}
