import { apiRequest, ApiError, SERVICE_UNAVAILABLE_MESSAGE } from './client'
import type {
  CharacterRelationship,
  NovelCharacter,
  NovelResponse,
  NovelResult,
  NovelRunInput,
  ReviewIssue,
  TokenUsage
} from '../types/api'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cleanText(value: unknown) {
  if (typeof value !== 'string') return undefined
  const text = value.trim()
  return text || undefined
}

function normalizeRelationships(value: unknown): CharacterRelationship[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    if (!isRecord(item)) return []
    const relationship = {
      name: cleanText(item.name),
      relation: cleanText(item.relation),
      dynamic: cleanText(item.dynamic)
    }
    return relationship.name || relationship.relation || relationship.dynamic ? [relationship] : []
  })
}

function normalizeCharacter(value: unknown): NovelCharacter | null {
  if (!isRecord(value)) return null
  return {
    name: cleanText(value.name) ?? '未命名人物',
    ...(cleanText(value.role_type) ? { role_type: cleanText(value.role_type) } : {}),
    ...(cleanText(value.appearance) ? { appearance: cleanText(value.appearance) } : {}),
    ...(cleanText(value.personality) ? { personality: cleanText(value.personality) } : {}),
    ...(cleanText(value.background) ? { background: cleanText(value.background) } : {}),
    ...(cleanText(value.ability) ? { ability: cleanText(value.ability) } : {}),
    ...(cleanText(value.motivation) ? { motivation: cleanText(value.motivation) } : {}),
    relationships: normalizeRelationships(value.relationships)
  }
}

function normalizeReviewIssue(value: unknown): ReviewIssue | null {
  if (!isRecord(value)) return null
  const issue = {
    severity: cleanText(value.severity),
    category: cleanText(value.category),
    description: cleanText(value.description),
    target_agent: cleanText(value.target_agent),
    suggestion: cleanText(value.suggestion)
  }
  return issue.severity || issue.category || issue.description || issue.target_agent || issue.suggestion
    ? issue
    : null
}

export function normalizeNovelResult(value: unknown): NovelResult {
  const record = isRecord(value) ? value : {}
  return {
    completed_chapters: Array.isArray(record.completed_chapters)
      ? record.completed_chapters.filter((chapter): chapter is string => typeof chapter === 'string')
      : [],
    current_draft: cleanText(record.current_draft) ?? '',
    review_issues: Array.isArray(record.review_issues)
      ? record.review_issues.flatMap((item) => {
          const issue = normalizeReviewIssue(item)
          return issue ? [issue] : []
        })
      : [],
    characters: Array.isArray(record.characters)
      ? record.characters.flatMap((item) => {
          const character = normalizeCharacter(item)
          return character ? [character] : []
        })
      : [],
    world_settings: Array.isArray(record.world_settings) ? record.world_settings : [],
    plot_outline: Array.isArray(record.plot_outline) ? record.plot_outline : [],
    review_round: typeof record.review_round === 'number' ? record.review_round : null
  }
}

function finiteNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

export function normalizeTokenUsage(value: unknown): TokenUsage | null {
  if (!isRecord(value)) return null
  return {
    input_tokens: finiteNumber(value.input_tokens),
    output_tokens: finiteNumber(value.output_tokens),
    total_tokens: finiteNumber(value.total_tokens),
    call_count: finiteNumber(value.call_count),
    cost_yuan: finiteNumber(value.cost_yuan),
    model: cleanText(value.model) ?? '未知模型'
  }
}

export function buildCreativeIdea(input: NovelRunInput) {
  return `${input.idea.trim()}\n\n创作参数：小说类型为${input.genre}；篇幅倾向为${input.length}；叙事风格为${input.style}。`
}

export async function createNovel(input: NovelRunInput, allowFallback = import.meta.env.DEV) {
  const body = JSON.stringify({ idea: buildCreativeIdea(input) })
  try {
    return await apiRequest<NovelResponse>('/api/novels', { method: 'POST', body })
  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 0 || !allowFallback) throw cause
    try {
      return await apiRequest<NovelResponse>('/agentsky-api/api/create', {
        method: 'POST',
        auth: false,
        body
      })
    } catch (fallbackCause) {
      if (fallbackCause instanceof ApiError && fallbackCause.status === 401) {
        throw new ApiError(SERVICE_UNAVAILABLE_MESSAGE, 0, fallbackCause.payload)
      }
      throw fallbackCause
    }
  }
}
