<script setup lang="ts">
import {
  CircleDot,
  Globe2,
  ListTree,
  LoaderCircle,
  Network,
  PenLine,
  ScanSearch,
  Users
} from 'lucide-vue-next'
import type { AgentName, AgentEvent } from '../types/novel'

const agentPresentation = {
  supervisor: { label: 'Supervisor', icon: Network, tone: 'violet' },
  setting: { label: 'Setting', icon: Globe2, tone: 'teal' },
  character: { label: 'Character', icon: Users, tone: 'rose' },
  plot: { label: 'Plot', icon: ListTree, tone: 'blue' },
  writer: { label: 'Writer', icon: PenLine, tone: 'green' },
  reviewer: { label: 'Reviewer', icon: ScanSearch, tone: 'amber' },
  system: { label: 'System', icon: CircleDot, tone: 'neutral' }
} as const

defineProps<{
  events: AgentEvent[]
  isRunning: boolean
}>()

function presentationFor(agent: AgentName) {
  return agentPresentation[agent]
}
</script>

<template>
  <ol class="agent-timeline" aria-label="Agent activity">
    <li v-if="isRunning" class="agent-timeline__item agent-timeline__item--running">
      <LoaderCircle :size="18" class="agent-timeline__spinner" aria-hidden="true" />
      <div>
        <strong>AgentSky workflow</strong>
        <span>Waiting for the synchronous workflow to finish</span>
      </div>
    </li>
    <li
      v-for="event in events"
      v-else
      :key="event.id"
      class="agent-timeline__item"
      :class="`agent-timeline__item--${presentationFor(event.agent).tone}`"
    >
      <component :is="presentationFor(event.agent).icon" :size="18" aria-hidden="true" />
      <div>
        <strong>{{ presentationFor(event.agent).label }}</strong>
        <span>{{ event.message }}</span>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.agent-timeline {
  display: grid;
  gap: 0;
  padding: 0;
  margin: 0;
  list-style: none;
}

.agent-timeline__item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid #d9ddda;
  color: #68706b;
}

.agent-timeline__item:last-child { border-bottom: 1px solid #d9ddda; }

.agent-timeline__item div { min-width: 0; display: grid; gap: 3px; }

.agent-timeline__item strong { color: #27312c; font-size: 13px; }

.agent-timeline__item span { overflow-wrap: anywhere; font-size: 13px; line-height: 1.5; }

.agent-timeline__item--violet { color: #7053a4; }
.agent-timeline__item--teal { color: #15776f; }
.agent-timeline__item--rose { color: #b04e67; }
.agent-timeline__item--blue { color: #3976ae; }
.agent-timeline__item--green { color: #278457; }
.agent-timeline__item--amber { color: #a76c18; }
.agent-timeline__item--neutral { color: #68706b; }
.agent-timeline__item--running { color: #3976ae; }

.agent-timeline__spinner { animation: agent-timeline-spin 1s linear infinite; }

@keyframes agent-timeline-spin {
  to { transform: rotate(360deg); }
}
</style>
