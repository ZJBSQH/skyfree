export const AUTH_TOKEN_KEY = 'freesky.auth.token'

export const SERVICE_UNAVAILABLE_MESSAGE = '服务暂时不可用，请确认后端已启动'
const EXPIRED_ERROR = '登录已过期，请重新登录'
const RATE_LIMIT_ERROR = '请求过于频繁，请稍后再试'
const GENERIC_ERROR = '操作失败，请稍后重试'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status = 0,
    public readonly payload: unknown = null
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface ApiRequestOptions extends RequestInit {
  auth?: boolean
  token?: string | null
}

function getStoredToken() {
  return typeof localStorage === 'undefined' ? null : localStorage.getItem(AUTH_TOKEN_KEY)
}

function readErrorMessage(payload: unknown) {
  if (!payload || typeof payload !== 'object') return ''
  const record = payload as Record<string, unknown>
  const candidate = [record.error, record.message, record.detail]
    .find((value): value is string => typeof value === 'string')
    ?.trim() ?? ''

  if (!candidate || candidate.length > 160 || /[\r\n]/.test(candidate)) return ''
  if (/traceback|stack trace|authorization|bearer\s|api[_-]?key|secret|password|token/i.test(candidate)) return ''
  return candidate
}

function errorForStatus(status: number, payload: unknown) {
  if (status === 401) return EXPIRED_ERROR
  if (status === 429) return RATE_LIMIT_ERROR
  return readErrorMessage(payload) || GENERIC_ERROR
}

export function safeApiMessage(payload: unknown, fallback = GENERIC_ERROR) {
  return readErrorMessage(payload) || fallback
}

async function readJson(response: { json: () => Promise<unknown> }) {
  try {
    return await response.json()
  } catch {
    return null
  }
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { auth = true, token = getStoredToken(), ...requestOptions } = options
  const headers = new Headers(requestOptions.headers)

  if (requestOptions.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (auth && token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(path, { ...requestOptions, headers })
  } catch {
    throw new ApiError(SERVICE_UNAVAILABLE_MESSAGE)
  }

  const payload = await readJson(response)
  if (!response.ok) {
    throw new ApiError(errorForStatus(response.status, payload), response.status, payload)
  }
  return payload as T
}
