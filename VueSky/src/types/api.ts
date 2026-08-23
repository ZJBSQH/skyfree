export interface AuthUser {
  id: number
  username: string
  email: string
}

export interface AuthResponse {
  token: string
  user: AuthUser
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  username: string
}

export type NovelGenre = '玄幻' | '都市' | '科幻' | '悬疑' | '仙侠' | '奇幻'
export type NovelLength = '短篇' | '中篇' | '长篇开篇'
export type NovelStyle = '热血' | '轻松' | '黑暗' | '史诗' | '爽文' | '细腻'

export interface NovelRunInput {
  idea: string
  genre: NovelGenre
  length: NovelLength
  style: NovelStyle
}

export interface CharacterRelationship {
  name?: string
  relation?: string
  dynamic?: string
}

export interface NovelCharacter {
  name: string
  role_type?: string
  appearance?: string
  personality?: string
  background?: string
  ability?: string
  motivation?: string
  relationships: CharacterRelationship[]
}

export interface ReviewIssue {
  severity?: 'critical' | 'major' | 'minor' | string
  category?: string
  description?: string
  target_agent?: string
  suggestion?: string
}

export interface NovelResult {
  completed_chapters: string[]
  current_draft: string
  review_issues: ReviewIssue[]
  characters: NovelCharacter[]
  world_settings: unknown[]
  plot_outline: unknown[]
  review_round: number | null
}

export interface TokenUsage {
  input_tokens: number
  output_tokens: number
  total_tokens: number
  call_count: number
  cost_yuan: number
  model: string
}

export type AgentKind = 'supervisor' | 'setting' | 'character' | 'plot' | 'writer' | 'reviewer' | 'system'

export interface AgentLog {
  id: string
  agent: AgentKind
  message: string
  sequence: number
}

export interface NovelResponse {
  success: boolean
  logs?: string[]
  result?: unknown
  error?: string
  token_usage?: Partial<TokenUsage>
}

export interface HealthResponse {
  status?: string
  service?: string
  [key: string]: unknown
}

export type RunStatus = 'idle' | 'running' | 'completed' | 'failed'
export type ServiceStatus = 'checking' | 'online' | 'offline'
