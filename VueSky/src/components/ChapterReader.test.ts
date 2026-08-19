import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import ChapterReader from './ChapterReader.vue'

describe('ChapterReader', () => {
  it('shows the requested chapter number and preserves paragraph line breaks', () => {
    const content = 'The first paragraph.\n\nThe second paragraph.'
    render(ChapterReader, { props: { content, chapterNumber: 2 } })

    expect(screen.getByRole('heading', { name: '第 2 章' })).toBeTruthy()
    expect(screen.getByText((_, element) => element?.textContent === content).textContent).toBe(content)
  })
})
