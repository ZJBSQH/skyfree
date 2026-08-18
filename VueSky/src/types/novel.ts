import type { Ref } from 'vue'

export type RunStatus = 'idle' | 'running' | 'completed' | 'failed'
export type AgentName = 'supervisor' | 'setting' | 'character' | 'plot' | 'writer' | 'reviewer' | 'system'

export interface AgentEvent {
  id: string
  agent: AgentName
  message: string
  sequence: number
}

export interface TokenUsage {
  input_tokens: number
  output_tokens: number
  total_tokens: number
  call_count: number
  cost_yuan: number
  model: string
}

export interface NovelCharacter {
  name: string
  role_type: string
}

export interface NovelResult {
  completed_chapters: string[]
  characters: NovelCharacter[]
  world_settings: unknown[]
  plot_outline: unknown[]
  review_round: number
}

export interface NovelRunController {
  connected: Ref<boolean>
  checked: Ref<boolean>
  status: Ref<RunStatus>
  events: Ref<AgentEvent[]>
  result: Ref<NovelResult | null>
  tokenUsage: Ref<TokenUsage | null>
  elapsedSeconds: Ref<number>
  error: Ref<string>
  checkConnection: () => Promise<void>
  generate: (idea: string) => Promise<void>
}
