import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

describe('App', () => {
  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 })
  })

  function setViewport(width: number) {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
    window.dispatchEvent(new Event('resize'))
  }

  function stubHealthyConnection() {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'ok', service: 'agentsky' })
    }))
  }

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

  it('opens a returned chapter and character profile from project navigation', async () => {
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
            completed_chapters: ['First chapter.\n\nSecond paragraph.'],
            characters: [{
              name: 'Mara Venn',
              role_type: 'Protagonist',
              personality: 'Methodical under pressure',
              background: 'Raised among the city archivists',
              ability: 'Maps shifting city routes',
              motivation: 'Find her missing brother',
              relationships: [{ name: 'Toma', relation: 'Friend', dynamic: 'Trusted partner' }]
            }],
            world_settings: [],
            plot_outline: [],
            review_round: 1
          }
        })
      })
    vi.stubGlobal('fetch', fetchMock)
    render(App)
    await screen.findByText('Connected')

    expect(screen.getByRole('textbox', { name: 'Story idea' })).toBeTruthy()

    await fireEvent.update(screen.getByRole('textbox', { name: 'Story idea' }), 'A city above the clouds')
    await fireEvent.click(screen.getByRole('button', { name: 'Generate' }))
    await screen.findByRole('button', { name: 'Chapter 1' })

    await fireEvent.click(screen.getByRole('button', { name: 'Chapter 1' }))
    expect(screen.getByRole('heading', { name: 'Chapter 1' })).toBeTruthy()
    expect(screen.getByText((_, element) => element?.textContent === 'First chapter.\n\nSecond paragraph.').textContent).toBe(
      'First chapter.\n\nSecond paragraph.'
    )

    await fireEvent.click(screen.getByRole('button', { name: 'Mara Venn' }))
    expect(screen.getByRole('heading', { name: 'Mara Venn' })).toBeTruthy()
    expect(screen.getByText('Find her missing brother')).toBeTruthy()
    expect(screen.getByText('1 relationship')).toBeTruthy()
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

  it('opens and closes the tablet run metrics drawer without duplicate inspector content', async () => {
    setViewport(1024)
    stubHealthyConnection()

    render(App)

    const trigger = screen.getByRole('button', { name: 'Open run metrics' })
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    await fireEvent.click(trigger)

    expect(screen.getByRole('dialog', { name: 'Run metrics' })).toBeTruthy()
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getAllByRole('heading', { name: 'Run metrics' })).toHaveLength(1)
    await fireEvent.click(screen.getByRole('button', { name: 'Close run metrics' }))

    expect(screen.queryByRole('dialog', { name: 'Run metrics' })).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('opens and closes the mobile project drawer with an accessible backdrop', async () => {
    setViewport(390)
    stubHealthyConnection()

    render(App)

    const trigger = screen.getByRole('button', { name: 'Open project directory' })
    await fireEvent.click(trigger)

    expect(screen.getByRole('dialog', { name: 'Project directory' })).toBeTruthy()
    await fireEvent.click(screen.getByRole('button', { name: 'Close project directory backdrop' }))

    expect(screen.queryByRole('dialog', { name: 'Project directory' })).toBeNull()
    expect(document.activeElement).toBe(trigger)
  })

  it('closes each responsive drawer with Escape and returns focus to its trigger', async () => {
    setViewport(390)
    stubHealthyConnection()

    render(App)

    for (const label of ['Open project directory', 'Open run metrics']) {
      const trigger = screen.getByRole('button', { name: label })
      await fireEvent.click(trigger)
      await fireEvent.keyDown(window, { key: 'Escape' })

      expect(document.activeElement).toBe(trigger)
    }
  })

  it('closes the project drawer safely at the 759px to 760px boundary', async () => {
    setViewport(759)
    stubHealthyConnection()

    render(App)

    const trigger = screen.getByRole('button', { name: 'Open project directory' })
    expect(screen.queryByRole('navigation', { name: 'Project navigation' })).toBeNull()
    await fireEvent.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Project directory' })).toBeTruthy()

    setViewport(760)

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Project directory' })).toBeNull()
      expect(screen.queryByRole('button', { name: 'Open project directory' })).toBeNull()
      expect(screen.getAllByRole('navigation', { name: 'Project navigation' })).toHaveLength(1)
    })
    expect(document.activeElement).not.toBe(trigger)
  })

  it('moves focus into an open drawer and contains Tab navigation', async () => {
    setViewport(390)
    stubHealthyConnection()

    render(App)

    await fireEvent.click(screen.getByRole('button', { name: 'Open project directory' }))

    const close = screen.getByRole('button', { name: 'Close project directory' })
    const projectNavigationButton = screen.getByRole('button', { name: 'Agent workspace' })
    expect(document.activeElement).toBe(close)

    await fireEvent.keyDown(close, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(projectNavigationButton)

    await fireEvent.keyDown(projectNavigationButton, { key: 'Tab' })
    expect(document.activeElement).toBe(close)
  })

  it('uses the metrics drawer through 1179px and restores the desktop inspector at 1180px', async () => {
    setViewport(1179)
    stubHealthyConnection()

    render(App)

    const trigger = screen.getByRole('button', { name: 'Open run metrics' })
    await fireEvent.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Run metrics' })).toBeTruthy()

    setViewport(1180)

    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Run metrics' })).toBeNull()
      expect(screen.queryByRole('button', { name: 'Open run metrics' })).toBeNull()
      expect(screen.getAllByRole('heading', { name: 'Run metrics' })).toHaveLength(1)
    })
  })
})
