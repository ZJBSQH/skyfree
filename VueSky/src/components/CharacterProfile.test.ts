import { render, screen } from '@testing-library/vue'
import { describe, expect, it } from 'vitest'
import CharacterProfile from './CharacterProfile.vue'

describe('CharacterProfile', () => {
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
    expect(screen.getByText('Protagonist')).toBeTruthy()
    expect(screen.getByText('Methodical under pressure')).toBeTruthy()
    expect(screen.getByText('Find her missing brother')).toBeTruthy()
    expect(screen.getByText('Maps shifting city routes')).toBeTruthy()
    expect(screen.getByText('Raised among the city archivists')).toBeTruthy()
    expect(screen.getByText('2 relationships')).toBeTruthy()
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

    expect(screen.getAllByText('Not provided')).toHaveLength(4)
    expect(screen.getByText('0 relationships')).toBeTruthy()
  })
})
