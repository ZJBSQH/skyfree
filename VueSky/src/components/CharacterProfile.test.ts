import { cleanup, render, screen } from '@testing-library/vue'
import { afterEach, describe, expect, it } from 'vitest'
import CharacterProfile from './CharacterProfile.vue'
import type { CharacterCard } from '../types/novel'

describe('CharacterProfile', () => {
  afterEach(cleanup)

  it('renders a foundational character profile from the returned character data', () => {
    render(CharacterProfile, {
      props: {
        character: {
          name: '  Mara Venn ',
          role_type: 'Protagonist',
          personality: 'Methodical under pressure',
          motivation: 'Find her missing brother',
          ability: 'Maps shifting city routes',
          background: 'Raised among the city archivists',
          relationships: [
            { name: 'Toma', relation: 'Friend', dynamic: 'Trusted courier partner' },
            { name: 'Iven', relation: 'Rival', dynamic: 'Competes for the same route' }
          ]
        }
      }
    })

    expect(screen.getByText('Ma')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Mara Venn' })).toBeTruthy()
    expect(screen.getByText('主角')).toBeTruthy()
    expect(screen.getByText('Methodical under pressure')).toBeTruthy()
    expect(screen.getByText('Find her missing brother')).toBeTruthy()
    expect(screen.getByText('Maps shifting city routes')).toBeTruthy()
    expect(screen.getByText('Raised among the city archivists')).toBeTruthy()
    expect(screen.getByText('2 条关系')).toBeTruthy()
    expect(screen.getByText('Toma')).toBeTruthy()
    expect(screen.getByText('Trusted courier partner')).toBeTruthy()
  })

  it('marks absent profile values as not provided', () => {
    render(CharacterProfile, {
      props: {
        character: {
          name: 'Lin',
          role_type: 'Supporting character'
        }
      }
    })

    expect(screen.getAllByText('未提供')).toHaveLength(4)
    expect(screen.getByText('0 条关系')).toBeTruthy()
  })

  it('uses a stable identity when the returned name is missing or not a string', async () => {
    const { rerender } = render(CharacterProfile, {
      props: {
        character: { role_type: 'Supporting character' } as unknown as CharacterCard
      }
    })

    expect(screen.getByRole('heading', { name: '未命名人物' })).toBeTruthy()
    expect(screen.getByText('未命')).toBeTruthy()

    await rerender({
      character: { name: 42, role_type: 'Supporting character' } as unknown as CharacterCard
    })

    expect(screen.getByRole('heading', { name: '未命名人物' })).toBeTruthy()
  })

  it('filters malformed relationships before counting or rendering them', async () => {
    const { rerender } = render(CharacterProfile, {
      props: {
        character: {
          name: 'Lin',
          role_type: 'Supporting character',
          relationships: 'unparseable relationship data'
        } as unknown as CharacterCard
      }
    })

    expect(screen.getByText('0 条关系')).toBeTruthy()

    await rerender({
      character: {
        name: 'Lin',
        role_type: 'Supporting character',
        relationships: [null, 'invalid entry', { name: 'Toma', relation: 'Friend' }]
      } as unknown as CharacterCard
    })

    expect(screen.getByText('1 条关系')).toBeTruthy()
    expect(screen.getByText('Toma')).toBeTruthy()
  })
})
