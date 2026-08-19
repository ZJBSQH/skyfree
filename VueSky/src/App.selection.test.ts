import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NovelResult } from './types/novel'

const mockedRun = vi.hoisted(() => ({ controller: undefined as unknown }))

vi.mock('./composables/useNovelRun', async () => {
  const { ref } = await import('vue')
  const controller = {
    connected: ref(true),
    checked: ref(true),
    status: ref<'idle' | 'running' | 'completed' | 'failed'>('idle'),
    events: ref([]),
    result: ref<NovelResult | null>(null),
    tokenUsage: ref(null),
    elapsedSeconds: ref(0),
    error: ref(''),
    checkConnection: vi.fn(),
    generate: vi.fn()
  }
  mockedRun.controller = controller

  return { useNovelRun: () => controller }
})

import App from './App.vue'

const initialResult: NovelResult = {
  completed_chapters: ['A first chapter'],
  characters: [],
  world_settings: [],
  plot_outline: [],
  review_round: 0
}

function controller() {
  return mockedRun.controller as { result: { value: NovelResult | null } }
}

describe('App content selection', () => {
  beforeEach(() => {
    controller().result.value = initialResult
  })

  afterEach(cleanup)

  it('returns to Agent workspace when a selected chapter disappears from the result', async () => {
    render(App)

    await fireEvent.click(screen.getByRole('button', { name: '第 1 章' }))
    expect(screen.getByRole('heading', { name: '第 1 章' })).toBeTruthy()

    controller().result.value = {
      ...initialResult,
      completed_chapters: []
    }

    await waitFor(() => {
      expect(screen.getByRole('textbox', { name: '创作灵感' })).toBeTruthy()
      expect(screen.getByRole('button', { name: '智能体工作区' }).getAttribute('aria-current')).toBe('page')
    })
  })
})
