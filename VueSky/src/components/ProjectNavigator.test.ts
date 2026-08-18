import { fireEvent, render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import ProjectNavigator from './ProjectNavigator.vue'
import type { NovelResult } from '../types/novel'

const result: NovelResult = {
  completed_chapters: ['A first chapter', 'A second chapter'],
  characters: [{ name: 'Mara Venn', role_type: 'protagonist' }],
  world_settings: [],
  plot_outline: [],
  review_round: 1
}

describe('ProjectNavigator', () => {
  it('shows project groups, item counts, and selects a character', async () => {
    const { emitted } = render(ProjectNavigator, { props: { result } })

    for (const group of ['Agent workspace', 'Outline', 'Chapters', 'Characters', 'World', 'Foreshadowing']) {
      expect(screen.getByRole('heading', { name: group })).toBeTruthy()
    }
    expect(screen.getByText('2')).toBeTruthy()
    expect(screen.getByText('1')).toBeTruthy()

    await fireEvent.click(screen.getByRole('button', { name: 'Mara Venn' }))

    expect(emitted('select')).toEqual([[{ type: 'character', index: 0 }]])
  })
})
