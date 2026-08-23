import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NovelRunInput } from '../types/api'
import { useNovelRun } from './useNovelRun'

const input: NovelRunInput = {
  idea: '一个被宗门视为废物的少年，意外觉醒仇恨值系统',
  genre: '玄幻',
  length: '长篇开篇',
  style: '热血'
}

const usage = {
  input_tokens: 800,
  output_tokens: 450,
  total_tokens: 1250,
  call_count: 6,
  cost_yuan: 0.0169,
  model: 'deepseek-chat'
}

function jsonResponse(payload: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }
}

describe('useNovelRun', () => {
  beforeEach(() => {
    localStorage.clear()
    localStorage.setItem('freesky.auth.token', 'jwt-token')
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('通过 Spring Boot 提交带创作参数的 idea 并保存完整结果', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({
      success: true,
      logs: ['[WriterAgent] Drafted chapter', '[ReviewerAgent] Approved'],
      result: {
        completed_chapters: ['第一章正文'],
        characters: [{ name: '林烬', role_type: '主角' }],
        world_settings: ['玄霄大陆'],
        plot_outline: ['少年觉醒系统'],
        review_round: 1
      },
      token_usage: usage
    }))
    vi.stubGlobal('fetch', fetchMock)
    const run = useNovelRun({ allowFallback: false })

    await run.submit(input)

    expect(run.status.value).toBe('completed')
    expect(run.result.value?.completed_chapters).toEqual(['第一章正文'])
    expect(run.logs.value.map((item) => item.agent)).toEqual(['writer', 'reviewer'])
    expect(run.tokenUsage.value).toEqual(usage)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/novels')
    const request = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer jwt-token')
    expect(request.body).toBe(JSON.stringify({
      idea: '一个被宗门视为废物的少年，意外觉醒仇恨值系统\n\n创作参数：小说类型为玄幻；篇幅倾向为长篇开篇；叙事风格为热血。'
    }))
  })

  it('阻止运行中的重复提交', async () => {
    let resolveRequest!: (response: ReturnType<typeof jsonResponse>) => void
    const pending = new Promise<ReturnType<typeof jsonResponse>>((resolve) => {
      resolveRequest = resolve
    })
    const fetchMock = vi.fn().mockReturnValue(pending)
    vi.stubGlobal('fetch', fetchMock)
    const run = useNovelRun({ allowFallback: false })

    const first = run.submit(input)
    await run.submit({ ...input, idea: '另一个灵感' })

    expect(run.loading.value).toBe(true)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    resolveRequest(jsonResponse({ success: true, result: {}, logs: [] }))
    await first
  })

  it('失败时展示安全后端错误并保留已产生的 token 用量', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      success: false,
      error: '正文未通过审核，请调整灵感后重试',
      logs: ['[ReviewerAgent] FAIL (1个问题)'],
      result: {
        current_draft: '最后一版草稿',
        review_round: 3,
        review_issues: [{
          severity: 'major',
          category: 'logic_flaw',
          description: '动机铺垫不足',
          target_agent: 'writer',
          suggestion: '补充主角行动原因'
        }]
      },
      token_usage: usage
    }, 500)))
    const run = useNovelRun({ allowFallback: false })

    await run.submit(input)

    expect(run.status.value).toBe('failed')
    expect(run.error.value).toBe('正文未通过审核，请调整灵感后重试')
    expect(run.result.value?.current_draft).toBe('最后一版草稿')
    expect(run.result.value?.review_round).toBe(3)
    expect(run.result.value?.review_issues[0]?.description).toBe('动机铺垫不足')
    expect(run.logs.value[0]?.agent).toBe('reviewer')
    expect(run.tokenUsage.value).toEqual(usage)
  })

  it('401 时通知账号状态失效并显示统一中文错误', async () => {
    const onUnauthorized = vi.fn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'JWT stack trace' }, 401)))
    const run = useNovelRun({ allowFallback: false, onUnauthorized })

    await run.submit(input)

    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(run.error.value).toBe('登录已过期，请重新登录')
  })

  it('开发 fallback 的服务令牌错误不会误清除用户登录态', async () => {
    const onUnauthorized = vi.fn()
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('Spring offline'))
      .mockResolvedValueOnce(jsonResponse({ detail: 'Invalid AgentSky service token' }, 401))
    vi.stubGlobal('fetch', fetchMock)
    const run = useNovelRun({ allowFallback: true, onUnauthorized })

    await run.submit(input)

    expect(onUnauthorized).not.toHaveBeenCalled()
    expect(run.error.value).toBe('服务暂时不可用，请确认后端已启动')
  })

  it('429 时展示请求频率提示', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ error: 'busy' }, 429)))
    const run = useNovelRun({ allowFallback: false })

    await run.submit(input)

    expect(run.error.value).toBe('请求过于频繁，请稍后再试')
  })

  it('网络失败时展示服务不可用且不暴露原始异常', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('connect ECONNREFUSED 127.0.0.1:8080')))
    const run = useNovelRun({ allowFallback: false })

    await run.submit(input)

    expect(run.error.value).toBe('服务暂时不可用，请确认后端已启动')
  })

  it('清空本次运行结果和用量并回到空闲状态', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({
      success: true,
      logs: ['[WriterAgent] Drafted chapter'],
      result: { completed_chapters: ['正文'] },
      token_usage: usage
    })))
    const run = useNovelRun({ allowFallback: false })
    await run.submit(input)

    run.clear()

    expect(run.status.value).toBe('idle')
    expect(run.result.value).toBeNull()
    expect(run.logs.value).toEqual([])
    expect(run.tokenUsage.value).toBeNull()
    expect(run.error.value).toBe('')
  })
})
