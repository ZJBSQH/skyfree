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

export interface CharacterRelationship {
  name?: string
  relation?: string
  dynamic?: string
}

export interface AgentSkyCharacterCard {
  name?: unknown
  role_type?: unknown
  appearance?: unknown
  personality?: unknown
  background?: unknown
  ability?: unknown
  motivation?: unknown
  relationships?: unknown
}

export interface CharacterCard {
  name: string
  role_type?: string
  appearance?: string
  personality?: string
  background?: string
  ability?: string
  motivation?: string
  relationships?: CharacterRelationship[]
}

export type NovelCharacter = CharacterCard

export interface NovelResult {
  completed_chapters: string[]
  characters: CharacterCard[]
  world_settings: unknown[]
  plot_outline: unknown[]
  review_round: number
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function stringValue(value: unknown) {
  const text = typeof value === 'string' ? value.trim() : ''
  return text || undefined
}

export function normalizeRelationships(value: unknown): CharacterRelationship[] {
  if (!Array.isArray(value)) return []

  return value.flatMap((relationship) => {
    if (!isRecord(relationship)) return []

    const normalized = {
      name: stringValue(relationship.name),
      relation: stringValue(relationship.relation),
      dynamic: stringValue(relationship.dynamic)
    }

    return normalized.name || normalized.relation || normalized.dynamic ? [normalized] : []
  })
}

export function normalizeCharacterCard(value: unknown): CharacterCard | null {
  if (!isRecord(value)) return null

  return {
    name: stringValue(value.name) ?? 'Unnamed character',
    ...(stringValue(value.role_type) ? { role_type: stringValue(value.role_type) } : {}),
    ...(stringValue(value.appearance) ? { appearance: stringValue(value.appearance) } : {}),
    ...(stringValue(value.personality) ? { personality: stringValue(value.personality) } : {}),
    ...(stringValue(value.background) ? { background: stringValue(value.background) } : {}),
    ...(stringValue(value.ability) ? { ability: stringValue(value.ability) } : {}),
    ...(stringValue(value.motivation) ? { motivation: stringValue(value.motivation) } : {}),
    relationships: normalizeRelationships(value.relationships)
  }
}

export function normalizeNovelResult(value: unknown): NovelResult | null {
  if (!isRecord(value)) return null

  const characters = Array.isArray(value.characters)
    ? value.characters.flatMap((character) => {
      const normalized = normalizeCharacterCard(character)
      return normalized ? [normalized] : []
    })
    : []

  return {
    completed_chapters: Array.isArray(value.completed_chapters)
      ? value.completed_chapters.filter((chapter): chapter is string => typeof chapter === 'string')
      : [],
    characters,
    world_settings: Array.isArray(value.world_settings) ? value.world_settings : [],
    plot_outline: Array.isArray(value.plot_outline) ? value.plot_outline : [],
    review_round: typeof value.review_round === 'number' ? value.review_round : 0
  }
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
