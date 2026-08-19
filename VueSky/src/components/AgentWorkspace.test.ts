import { cleanup, fireEvent, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'
import AgentWorkspace from './AgentWorkspace.vue'
import type { AgentEvent } from '../types/novel'

function renderWorkspace(overrides: Partial<{
  status: 'idle' | 'running' | 'completed' | 'failed'
  events: AgentEvent[]
  errorMessage: string
  connected: boolean
  elapsedSeconds: number
}> = {}) {
  return render(AgentWorkspace, {
    props: {
      status: 'idle',
      events: [],
      errorMessage: '',
      connected: true,
      elapsedSeconds: 0,
      ...overrides
    }
  })
}

describe('AgentWorkspace', () => {
  afterEach(cleanup)

  it('disables generation until a story idea is entered', () => {
    renderWorkspace()

    expect(screen.getByRole('textbox', { name: 'Story idea' })).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Generate' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('emits a trimmed story idea for generation', async () => {
    const { emitted } = renderWorkspace()

    await fireEvent.update(screen.getByRole('textbox', { name: 'Story idea' }), '  A city in the clouds  ')
    await fireEvent.click(screen.getByRole('button', { name: 'Generate' }))

    expect(emitted('generate')).toEqual([['A city in the clouds']])
  })

  it('shows the synchronous running state without allowing input changes', () => {
    renderWorkspace({ status: 'running', elapsedSeconds: 65 })

    expect(screen.getByText('Agents are working')).toBeTruthy()
    expect(screen.getByText('Elapsed 01:05')).toBeTruthy()
    expect((screen.getByRole('textbox', { name: 'Story idea' }) as HTMLTextAreaElement).disabled).toBe(true)
    expect(screen.getByText('AgentSky workflow')).toBeTruthy()
  })

  it('renders returned writer and reviewer timeline events', () => {
    renderWorkspace({
      status: 'completed',
      events: [
        { id: 'event-1', agent: 'writer', message: 'Drafted chapter one', sequence: 1 },
        { id: 'event-2', agent: 'reviewer', message: 'Approved the draft', sequence: 2 }
      ]
    })

    expect(screen.getByText('Drafted chapter one')).toBeTruthy()
    expect(screen.getByText('Approved the draft')).toBeTruthy()
  })

  it('shows an error and allows the failed request to be retried', async () => {
    const { emitted } = renderWorkspace({
      status: 'failed',
      errorMessage: 'Generation failed'
    })

    expect(screen.getByRole('alert').textContent).toContain('Generation failed')
    const retry = screen.getByRole('button', { name: 'Retry' })
    expect((retry as HTMLButtonElement).disabled).toBe(false)

    await fireEvent.click(retry)

    expect(emitted('retry')).toEqual([[]])
  })
})
