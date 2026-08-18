<script setup lang="ts">
import { computed } from 'vue'
import type { CharacterCard } from '../types/novel'

const props = defineProps<{
  character: CharacterCard
}>()

const initials = computed(() => Array.from(props.character.name.trim()).slice(0, 2).join(''))
const displayName = computed(() => props.character.name.trim())
const relationshipCount = computed(() => props.character.relationships?.length ?? 0)

function provided(value: string | undefined) {
  return value?.trim() || 'Not provided'
}
</script>

<template>
  <section class="character-profile" aria-labelledby="character-profile-name">
    <header class="character-profile__header">
      <span class="character-profile__initials" aria-hidden="true">{{ initials }}</span>
      <div>
        <p class="eyebrow">Character profile</p>
        <h2 id="character-profile-name">{{ displayName }}</h2>
        <p class="character-profile__role">{{ provided(character.role_type) }}</p>
      </div>
    </header>

    <dl class="character-profile__details">
      <div>
        <dt>Personality</dt>
        <dd>{{ provided(character.personality) }}</dd>
      </div>
      <div>
        <dt>Motivation</dt>
        <dd>{{ provided(character.motivation) }}</dd>
      </div>
      <div>
        <dt>Ability</dt>
        <dd>{{ provided(character.ability) }}</dd>
      </div>
      <div>
        <dt>Background</dt>
        <dd>{{ provided(character.background) }}</dd>
      </div>
    </dl>

    <section class="character-profile__relationships" aria-labelledby="relationships-title">
      <div class="character-profile__relationships-heading">
        <h3 id="relationships-title">Relationships</h3>
        <span>{{ relationshipCount }} {{ relationshipCount === 1 ? 'relationship' : 'relationships' }}</span>
      </div>
      <div v-if="relationshipCount" class="character-profile__relationship-list">
        <div v-for="(relationship, index) in character.relationships" :key="index" class="character-profile__relationship">
          <strong v-if="relationship.name">{{ relationship.name }}</strong>
          <span v-if="relationship.relation">{{ relationship.relation }}</span>
          <span v-if="relationship.dynamic">{{ relationship.dynamic }}</span>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.character-profile { padding: 34px 0; }

.character-profile__header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 24px;
  border-bottom: 1px solid #d5dad7;
}

.character-profile__initials {
  display: grid;
  width: 52px;
  height: 52px;
  flex: 0 0 52px;
  place-items: center;
  border: 1px solid #9bb8aa;
  border-radius: 6px;
  color: #174e35;
  background: #e1eee6;
  font-size: 17px;
  font-weight: 750;
}

.character-profile h2 { margin-bottom: 4px; }

.character-profile__role { margin: 0; color: #68706b; font-size: 14px; }

.character-profile__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin: 24px 0 0;
  border-top: 1px solid #d5dad7;
}

.character-profile__details > div {
  min-width: 0;
  padding: 18px 16px 18px 0;
  border-bottom: 1px solid #d5dad7;
}

.character-profile__details > div:nth-child(odd) { padding-right: 24px; }
.character-profile__details > div:nth-child(even) { padding-left: 24px; border-left: 1px solid #d5dad7; }

dt, .character-profile__relationships h3 {
  margin: 0 0 7px;
  color: #68706b;
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0;
  text-transform: uppercase;
}

dd { margin: 0; color: #323a36; font-size: 14px; line-height: 1.6; }

.character-profile__relationships { margin-top: 28px; }

.character-profile__relationships-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.character-profile__relationships-heading span { color: #68706b; font-size: 12px; }

.character-profile__relationship-list { border-top: 1px solid #d5dad7; }

.character-profile__relationship {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 0.8fr) minmax(0, 1.4fr);
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #d5dad7;
  color: #68706b;
  font-size: 13px;
  line-height: 1.45;
}

.character-profile__relationship strong { color: #323a36; }

@media (max-width: 640px) {
  .character-profile__details { grid-template-columns: 1fr; }
  .character-profile__details > div:nth-child(odd), .character-profile__details > div:nth-child(even) {
    padding-right: 0;
    padding-left: 0;
    border-left: 0;
  }
  .character-profile__relationship { grid-template-columns: 1fr; gap: 4px; }
}
</style>
