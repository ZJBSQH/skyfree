import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

describe('App', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows the Vue to Spring to AgentSky connection', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'ok', service: 'agentsky' })
    }))

    render(App)

    await waitFor(() => {
      expect(screen.getByText('VueSky -> SprintbootSky -> AgentSky')).toBeTruthy()
      expect(screen.getByText('Connected')).toBeTruthy()
    })
    expect(fetch).toHaveBeenCalledWith('/api/agent/health')
  })

  it('passes a completed novel result through to project navigation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ status: 'ok', service: 'agentsky' })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          success: true,
          result: {
            completed_chapters: ['The reviewed first chapter.'],
            characters: [{ name: 'Mara Venn', role_type: 'protagonist' }],
            world_settings: ['Cinderfall'],
            plot_outline: ['The lost map'],
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
    render(App)
    await screen.findByText('Connected')

    await fireEvent.update(
      screen.getByRole('textbox', { name: 'Story idea' }),
      'A city above the clouds'
    )
    await fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    await waitFor(() => {
      expect(screen.getByText('The reviewed first chapter.')).toBeTruthy()
      expect(screen.getByText('Mara Venn')).toBeTruthy()
      expect(screen.getByText('Cinderfall')).toBeTruthy()
      expect(screen.getByText('The lost map')).toBeTruthy()
    })
    for (const group of ['Outline', 'Chapters', 'Characters', 'World']) {
      const heading = screen.getByRole('heading', { name: group })
      expect(heading.parentElement?.textContent).toContain('1')
    }
    expect(fetchMock).toHaveBeenLastCalledWith('/api/novels', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ idea: 'A city above the clouds' })
    }))
  })

  it('retries a failed generation once with the original trimmed idea', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ status: 'ok', service: 'agentsky' })
      })
      .mockResolvedValueOnce({
        ok: false,
        json: async () => ({ success: false, error: 'Generation failed' })
      })
      .mockResolvedValueOnce({
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
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)
    render(App)
    await screen.findByText('Connected')

    await fireEvent.update(
      screen.getByRole('textbox', { name: 'Story idea' }),
      '  A city above the clouds  '
    )
    await fireEvent.click(screen.getByRole('button', { name: 'Generate' }))
    await screen.findByRole('button', { name: 'Retry' })

    await fireEvent.click(screen.getByRole('button', { name: 'Retry' }))

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(3)
    })
    const generationCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/novels')
    expect(generationCalls).toHaveLength(2)
    expect(generationCalls[1]).toEqual([
      '/api/novels',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ idea: 'A city above the clouds' })
      })
    ])
  })

  it('shows unavailable when the service chain cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    render(App)

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toBe('AgentSky is unavailable')
    })
  })
})
