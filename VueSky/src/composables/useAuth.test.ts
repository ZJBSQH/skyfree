import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const user = { id: 7, username: '云舟', email: 'yunzhou@example.com' }

function jsonResponse(payload: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }
}

describe('useAuth', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('登录成功后持久化 token 并公开当前用户', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ token: 'jwt-token', user }))
    vi.stubGlobal('fetch', fetchMock)
    const { useAuth } = await import('./useAuth')
    const auth = useAuth()

    const succeeded = await auth.login({ email: user.email, password: 'secret123' })

    expect(succeeded).toBe(true)
    expect(auth.user.value).toEqual(user)
    expect(auth.isAuthenticated.value).toBe(true)
    expect(localStorage.getItem('freesky.auth.token')).toBe('jwt-token')
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ email: user.email, password: 'secret123' })
    }))
  })

  it('使用持久化 token 恢复当前用户', async () => {
    localStorage.setItem('freesky.auth.token', 'saved-token')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(user))
    vi.stubGlobal('fetch', fetchMock)
    const { useAuth } = await import('./useAuth')
    const auth = useAuth()

    await auth.initialize()

    expect(auth.user.value).toEqual(user)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/me')
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer saved-token')
  })

  it('恢复会话遇到 401 时清除失效 token', async () => {
    localStorage.setItem('freesky.auth.token', 'expired-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'internal auth details' }, 401)))
    const { useAuth } = await import('./useAuth')
    const auth = useAuth()

    await auth.initialize()

    expect(auth.user.value).toBeNull()
    expect(auth.error.value).toBe('登录已过期，请重新登录')
    expect(localStorage.getItem('freesky.auth.token')).toBeNull()
  })

  it('注册时提交用户名、邮箱和密码并建立会话', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ token: 'new-token', user }))
    vi.stubGlobal('fetch', fetchMock)
    const { useAuth } = await import('./useAuth')
    const auth = useAuth()

    await auth.register({ username: user.username, email: user.email, password: 'secret123' })

    expect(auth.user.value).toEqual(user)
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/register', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ username: user.username, email: user.email, password: 'secret123' })
    }))
  })

  it('退出时仅清除本地会话', async () => {
    localStorage.setItem('freesky.auth.token', 'saved-token')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(user)))
    const { useAuth } = await import('./useAuth')
    const auth = useAuth()
    await auth.initialize()

    auth.logout()

    expect(auth.user.value).toBeNull()
    expect(auth.isAuthenticated.value).toBe(false)
    expect(localStorage.getItem('freesky.auth.token')).toBeNull()
  })
})
