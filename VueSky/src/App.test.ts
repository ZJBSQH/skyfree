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

  it('submits an idea through Spring and renders reviewed chapters', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ status: 'ok', service: 'agentsky' })
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          success: true,
          result: { completed_chapters: ['The reviewed first chapter.'] },
          token_usage: { total_tokens: 120 }
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
    })
    expect(fetchMock).toHaveBeenLastCalledWith('/api/novels', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ idea: 'A city above the clouds' })
    }))
  })

  it('shows unavailable when the service chain cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    render(App)

    await waitFor(() => {
      expect(screen.getByRole('alert').textContent).toBe('AgentSky is unavailable')
    })
  })
})
