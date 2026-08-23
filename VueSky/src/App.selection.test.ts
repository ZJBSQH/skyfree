import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => {
  cleanup()
  localStorage.clear()
  vi.unstubAllGlobals()
})

it('未登录时保留创作内容并引导用户登录或注册', async () => {
  localStorage.clear()
  vi.resetModules()
  const fetchMock = vi.fn((path: string) => Promise.resolve({
    ok: true,
    status: 200,
    json: async () => ({ status: 'ok', service: path.includes('agent') ? 'agentsky' : 'sprintbootsky' })
  }))
  vi.stubGlobal('fetch', fetchMock)
  const App = (await import('./App.vue')).default
  render(App)

  const idea = screen.getByRole('textbox', { name: '小说灵感' })
  await fireEvent.update(idea, '被遗忘的机械神明在海底苏醒')
  await fireEvent.click(screen.getByRole('button', { name: '开始创作' }))

  expect(screen.getByRole('alert').textContent).toBe('请先登录或注册后再开始创作')
  expect((idea as HTMLTextAreaElement).value).toBe('被遗忘的机械神明在海底苏醒')
  expect(fetchMock.mock.calls.some(([path]) => path === '/api/novels')).toBe(false)
})
