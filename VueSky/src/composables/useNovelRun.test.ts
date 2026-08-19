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

  it('normalizes incomplete characters and malformed relationship payloads', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ status: 'ok' }) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          success: true,
          result: {
            completed_chapters: [],
            characters: [
              { role_type: 'supporting', relationships: 'not valid JSON' },
              { name: 42, relationships: [null, { name: 'Toma', relation: 'Friend' }] }
            ],
            world_settings: [],
            plot_outline: [],
            review_round: 0
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)
    const controller = useNovelRun()

    await controller.checkConnection()
    await controller.generate('A city above the clouds')

    expect(controller.result.value?.characters).toEqual([
      { name: '未命名人物', role_type: 'supporting', relationships: [] },
      { name: '未命名人物', relationships: [{ name: 'Toma', relation: 'Friend' }] }
    ])
  })

  it('keeps an absent review round unknown', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        result: {
          completed_chapters: [],
          characters: [],
          world_settings: [],
          plot_outline: []
        }
      })
    }))
    const controller = useNovelRun()

    await controller.generate('A city above the clouds')

    expect(controller.result.value?.review_round).toBeNull()
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
      json: async () => ({
        success: false,
        error: '正文在最大审核轮次内未通过，请调整创作灵感后重试',
        token_usage: {
          input_tokens: 8000,
          output_tokens: 4450,
          total_tokens: 12450,
          call_count: 8,
          cost_yuan: 0.0169,
          model: 'deepseek-chat'
        }
      })
    }))
    const controller = useNovelRun()

    await controller.generate('A city above the clouds')

    expect(controller.status.value).toBe('failed')
    expect(controller.error.value).toBe('正文在最大审核轮次内未通过，请调整创作灵感后重试')
    expect(controller.tokenUsage.value?.total_tokens).toBe(12450)
    expect(controller.tokenUsage.value?.call_count).toBe(8)
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
