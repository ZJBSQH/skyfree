import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'
import RunInspector from './RunInspector.vue'

function renderInspector(overrides: Partial<{
  connected: boolean
  status: 'idle' | 'running' | 'completed' | 'failed'
  tokenUsage: {
    input_tokens: number
    output_tokens: number
    total_tokens: number
    call_count: number
    cost_yuan: number
    model: string
  } | null
  elapsedSeconds: number
  reviewRound: number
  completedChapterCount: number
}> = {}) {
  return render(RunInspector, {
    props: {
      connected: true,
      status: 'completed',
      tokenUsage: {
        input_tokens: 8000,
        output_tokens: 4450,
        total_tokens: 12450,
        call_count: 8,
        cost_yuan: 0.0321,
        model: 'deepseek-chat'
      },
      elapsedSeconds: 125,
      reviewRound: 2,
      completedChapterCount: 3,
      ...overrides
    }
  })
}

describe('RunInspector', () => {
  afterEach(cleanup)

  it('renders runtime values from the API and browser timer', () => {
    renderInspector()

    for (const metric of ['Connected', 'Completed', '12,450', '8,000', '4,450', '¥0.0321', '8 calls', '02:05', '2', '3']) {
      expect(screen.getByText(metric)).toBeTruthy()
    }
  })

  it('uses placeholders for unavailable usage metrics', () => {
    renderInspector({ tokenUsage: null })

    expect(screen.getAllByText('--')).toHaveLength(5)
  })
})
