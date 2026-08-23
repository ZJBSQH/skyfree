<script setup lang="ts">
import { CheckCircle2, CircleAlert, LoaderCircle } from 'lucide-vue-next'
import type { ServiceStatus } from '../types/api'

defineProps<{
  label: string
  status: ServiceStatus
}>()
</script>

<template>
  <div class="health-badge" :class="`health-badge--${status}`">
    <LoaderCircle v-if="status === 'checking'" :size="16" class="spin" aria-hidden="true" />
    <CheckCircle2 v-else-if="status === 'online'" :size="16" aria-hidden="true" />
    <CircleAlert v-else :size="16" aria-hidden="true" />
    <span>{{ label }}{{ status === 'checking' ? '检查中' : status === 'online' ? '正常' : '离线' }}</span>
  </div>
</template>

