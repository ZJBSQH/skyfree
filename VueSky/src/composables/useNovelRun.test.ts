import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseAgentLogs, useNovelRun } from './useNovelRun'

describe('useNovelRun', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('stores a completed response with normalized agent events and token usage', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'ok' }) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          success: true,
          logs: ['[WriterAgent] Draft ch_01', '[ReviewerAgent] PASS'],
          result: {
            completed_chapters: ['chapter'],
            characters: [{ name: 'Lin', role_type: 'protagonist' }],
            world_settings: [],
            plot_outline: [],
            review_round: 1
          },
          token_usage: {
            input_tokens: 80,
            output_tokens: 40,
            total_tokens: 120,
            call_count: 2,
            cost_yuan: 0.001,
            model: 'deepseek-chat'
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)
    const controller = useNovelRun()

    await controller.checkConnection()
    await controller.generate('A city above the clouds')

    expect(controller.status.value).toBe('completed')
    expect(controller.events.value[0]).toMatchObject({
      agent: 'writer',
      message: 'Draft ch_01',
      sequence: 1
    })
    expect(controller.tokenUsage.value?.total_tokens).toBe(120)
    expect(controller.result.value?.completed_chapters).toEqual(['chapter'])
  })

  it('marks the service disconnected when its health endpoint fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({ status: 'unavailable' })
    }))
    const controller = useNovelRun()

    await controller.checkConnection()

    expect(controller.connected.value).toBe(false)
  })

  it('marks generation as failed when the API rejects the request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => ({ success: false, error: 'AgentSky unavailable' })
    }))
    const controller = useNovelRun()

    await controller.generate('A city above the clouds')

    expect(controller.status.value).toBe('failed')
    expect(controller.error.value).toBe('AgentSky unavailable')
  })

  it('ignores a second generate call while the first is running', async () => {
    let resolveRequest: (response: { ok: boolean; json: () => Promise<object> }) => void
    const pendingRequest = new Promise<{ ok: boolean; json: () => Promise<object> }>((resolve) => {
      resolveRequest = resolve
    })
    const fetchMock = vi.fn().mockReturnValue(pendingRequest)
    vi.stubGlobal('fetch', fetchMock)
    const controller = useNovelRun()

    const firstRun = controller.generate('A city above the clouds')
    await controller.generate('A second idea')

    expect(controller.status.value).toBe('running')
    expect(fetchMock).toHaveBeenCalledTimes(1)

    resolveRequest!({
      ok: true,
      json: async () => ({
        success: true,
        logs: [],
        result: {
          completed_chapters: [],
          characters: [],
          world_settings: [],
          plot_outline: [],
          review_round: 0
        },
        token_usage: {
          input_tokens: 0,
          output_tokens: 0,
          total_tokens: 0,
          call_count: 0,
          cost_yuan: 0,
          model: 'deepseek-chat'
        }
      })
    })
    await firstRun
  })
})

describe('parseAgentLogs', () => {
  it('maps recognized prefixes and falls back to system events', () => {
    expect(parseAgentLogs([
      '[WriterAgent] Draft ch_01',
      '[ReviewerAgent] PASS',
      'A server notice'
    ])).toMatchObject([
      { agent: 'writer', message: 'Draft ch_01', sequence: 1 },
      { agent: 'reviewer', message: 'PASS', sequence: 2 },
      { agent: 'system', message: 'A server notice', sequence: 3 }
    ])
  })
})
