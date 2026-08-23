import { afterEach, describe, expect, it, vi } from 'vitest'
import { useHealthCheck } from './useHealthCheck'

function jsonResponse(payload: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }
}

describe('useHealthCheck', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('分别确认 Java 后端与 AgentSky 服务在线', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ status: 'ok', service: 'sprintbootsky' }))
      .mockResolvedValueOnce(jsonResponse({ status: 'ok', service: 'agentsky' }))
    vi.stubGlobal('fetch', fetchMock)
    const health = useHealthCheck({ allowFallback: false })

    await health.check()

    expect(health.javaStatus.value).toBe('online')
    expect(health.agentStatus.value).toBe('online')
    expect(health.fallbackActive.value).toBe(false)
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/health', '/api/agent/health'])
  })

  it('Java 不可达时在开发模式通过独立代理探测 AgentSky', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('Spring offline'))
      .mockResolvedValueOnce(jsonResponse({ status: 'ok', service: 'agentsky' }))
    vi.stubGlobal('fetch', fetchMock)
    const health = useHealthCheck({ allowFallback: true })

    await health.check()

    expect(health.javaStatus.value).toBe('offline')
    expect(health.agentStatus.value).toBe('online')
    expect(health.fallbackActive.value).toBe(true)
    expect(fetchMock.mock.calls.map(([path]) => path)).toEqual(['/api/health', '/agentsky-api/api/health'])
  })

  it('两个服务都不可达时公开离线状态', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const health = useHealthCheck({ allowFallback: true })

    await health.check()

    expect(health.javaStatus.value).toBe('offline')
    expect(health.agentStatus.value).toBe('offline')
    expect(health.fallbackActive.value).toBe(false)
  })
})
