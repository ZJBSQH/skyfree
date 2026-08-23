import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const user = { id: 7, username: '云舟', email: 'yunzhou@example.com' }

function jsonResponse(payload: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(payload)
  }
}

function standardResponse(path: string) {
  if (path === '/api/health') return jsonResponse({ status: 'ok', service: 'sprintbootsky' })
  if (path === '/api/agent/health') return jsonResponse({ status: 'ok', service: 'agentsky' })
  if (path === '/api/auth/me') return jsonResponse(user)
  throw new Error(`Unexpected request: ${path}`)
}

async function renderFreshApp() {
  vi.resetModules()
  const App = (await import('./App.vue')).default
  return render(App)
}

describe('Freesky 创作工作台', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('首屏展示中文创作输入、服务状态和登录注册入口', async () => {
    vi.stubGlobal('fetch', vi.fn((path: string) => Promise.resolve(standardResponse(path))))

    await renderFreshApp()

    expect(screen.getByRole('heading', { name: '多 Agent 小说创作工作台' })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: '小说灵感' })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: '小说类型' })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: '篇幅倾向' })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: '创作风格' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '登录' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '切换到注册' })).toBeTruthy()
    await screen.findByText('Spring Boot 正常')
    expect(await screen.findByText('AgentSky 正常')).toBeTruthy()
  })

  it('灵感为空时显示中文校验且不发送创作请求', async () => {
    const fetchMock = vi.fn((path: string) => Promise.resolve(standardResponse(path)))
    vi.stubGlobal('fetch', fetchMock)
    await renderFreshApp()

    await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))

    expect(screen.getByRole('alert').textContent).toBe('请输入小说灵感')
    expect(fetchMock.mock.calls.some(([path]) => path === '/api/novels')).toBe(false)
  })

  it('登录用户可生成并查看正文、日志、角色、世界观、大纲和 token 用量', async () => {
    localStorage.setItem('freesky.auth.token', 'jwt-token')
    const fetchMock = vi.fn((path: string) => {
      if (path === '/api/novels') {
        return Promise.resolve(jsonResponse({
          success: true,
          logs: ['[WriterAgent] 完成第一章草稿', '[ReviewerAgent] 审核通过'],
          result: {
            completed_chapters: ['少年在断崖下睁开双眼，系统的声音第一次响起。'],
            characters: [{
              name: '林烬',
              role_type: '主角',
              motivation: '洗刷废物之名',
              relationships: [{ name: '苏璃', relation: '同门', dynamic: '从轻视到信任' }]
            }],
            world_settings: ['玄霄大陆以宗门和王朝共同维持秩序'],
            plot_outline: ['林烬通过收集仇恨值完成首次逆袭'],
            review_round: 1,
            review_issues: [{
              severity: 'minor',
              category: 'style',
              description: '部分句子节奏略快',
              target_agent: 'writer',
              suggestion: '后续章节可增加场景停顿'
            }]
          },
          token_usage: {
            input_tokens: 800,
            output_tokens: 450,
            total_tokens: 1250,
            call_count: 6,
            cost_yuan: 0.0169,
            model: 'deepseek-chat'
          }
        }))
      }
      return Promise.resolve(standardResponse(path))
    })
    vi.stubGlobal('fetch', fetchMock)
    await renderFreshApp()
    await screen.findByText('云舟')

    await fireEvent.update(screen.getByRole('textbox', { name: '小说灵感' }), '废物少年觉醒仇恨值系统')
    await fireEvent.update(screen.getByRole('combobox', { name: '小说类型' }), '玄幻')
    await fireEvent.update(screen.getByRole('combobox', { name: '篇幅倾向' }), '长篇开篇')
    await fireEvent.update(screen.getByRole('combobox', { name: '创作风格' }), '热血')
    await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))

    expect(await screen.findByText('少年在断崖下睁开双眼，系统的声音第一次响起。')).toBeTruthy()
    expect(screen.getByText('完成第一章草稿')).toBeTruthy()
    expect(screen.getByText('林烬')).toBeTruthy()
    expect(screen.getByText('玄霄大陆以宗门和王朝共同维持秩序')).toBeTruthy()
    expect(screen.getByText('林烬通过收集仇恨值完成首次逆袭')).toBeTruthy()
    expect(screen.getByText('审核提醒')).toBeTruthy()
    expect(screen.getByText('部分句子节奏略快')).toBeTruthy()
    expect(screen.getByText('1,250')).toBeTruthy()
  })

  it('创作中显示 loading 并禁止重复提交', async () => {
    localStorage.setItem('freesky.auth.token', 'jwt-token')
    let resolveNovel!: (value: ReturnType<typeof jsonResponse>) => void
    const pendingNovel = new Promise<ReturnType<typeof jsonResponse>>((resolve) => {
      resolveNovel = resolve
    })
    const fetchMock = vi.fn((path: string) => {
      if (path === '/api/novels') return pendingNovel
      return Promise.resolve(standardResponse(path))
    })
    vi.stubGlobal('fetch', fetchMock)
    await renderFreshApp()
    await screen.findByText('云舟')

    await fireEvent.update(screen.getByRole('textbox', { name: '小说灵感' }), '一座会移动的天空城')
    await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))

    const runningButton = screen.getByRole('button', { name: '创作中' }) as HTMLButtonElement
    expect(runningButton.disabled).toBe(true)
    await fireEvent.click(runningButton)
    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/novels')).toHaveLength(1)
    resolveNovel(jsonResponse({ success: true, logs: [], result: {} }))
    await waitFor(() => expect(screen.getByRole('button', { name: '开始创作' })).toBeTruthy())
  })

  it('创作未完成时展示草稿、审核问题和审核轮次', async () => {
    localStorage.setItem('freesky.auth.token', 'jwt-token')
    vi.stubGlobal('fetch', vi.fn((path: string) => {
      if (path === '/api/novels') {
        return Promise.resolve(jsonResponse({
          success: false,
          error: '正文在最大审核轮次内未通过，请调整创作灵感后重试',
          logs: ['[ReviewerAgent] FAIL (1个问题)'],
          result: {
            current_draft: '主角在荒漠里找到生灵之心，但转折仍显突兀。',
            review_round: 3,
            review_issues: [{
              severity: 'major',
              category: 'logic_flaw',
              description: '主角获得生灵之心的因果铺垫不足',
              target_agent: 'writer',
              suggestion: '补充遗迹线索和选择代价'
            }]
          }
        }))
      }
      return Promise.resolve(standardResponse(path))
    }))
    await renderFreshApp()
    await screen.findByText('云舟')

    await fireEvent.update(screen.getByRole('textbox', { name: '小说灵感' }), '荒漠中的文明复苏')
    await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))

    expect(await screen.findByText('本次创作未完成')).toBeTruthy()
    expect(screen.getByText('审核轮次：第 3 轮')).toBeTruthy()
    expect(screen.getByText('主角在荒漠里找到生灵之心，但转折仍显突兀。')).toBeTruthy()
    expect(screen.getByText('主角获得生灵之心的因果铺垫不足')).toBeTruthy()
    expect(screen.getByText(/补充遗迹线索和选择代价/)).toBeTruthy()
  })

  it('支持复制全部正文并清空本次结果', async () => {
    localStorage.setItem('freesky.auth.token', 'jwt-token')
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } })
    vi.stubGlobal('fetch', vi.fn((path: string) => {
      if (path === '/api/novels') {
        return Promise.resolve(jsonResponse({
          success: true,
          logs: [],
          result: { completed_chapters: ['第一章', '第二章'] }
        }))
      }
      return Promise.resolve(standardResponse(path))
    }))
    await renderFreshApp()
    await screen.findByText('云舟')
    await fireEvent.update(screen.getByRole('textbox', { name: '小说灵感' }), '双城之间的信使')
    await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))
    await screen.findByText('第一章')

    await fireEvent.click(screen.getByRole('button', { name: '复制正文' }))
    expect(writeText).toHaveBeenCalledWith('第一章\n\n第二章')

    await fireEvent.click(screen.getByRole('button', { name: '清空本次结果' }))
    expect(screen.queryByText('第一章')).toBeNull()
    expect((screen.getByRole('textbox', { name: '小说灵感' }) as HTMLTextAreaElement).value).toBe('双城之间的信使')
  })
})
