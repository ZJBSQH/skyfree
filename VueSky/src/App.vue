<script setup lang="ts">
import { onMounted } from 'vue'
import AppShell from './components/AppShell.vue'
import { useAuth } from './composables/useAuth'
import { useHealthCheck } from './composables/useHealthCheck'
import { useNovelRun } from './composables/useNovelRun'

const {
  user,
  loading: authLoading,
  error: authError,
  initialize,
  login,
  register,
  logout,
  expireSession
} = useAuth()

const {
  javaStatus,
  agentStatus,
  fallbackActive,
  check: checkHealth
} = useHealthCheck({ allowFallback: import.meta.env.DEV })

const {
  status: runStatus,
  loading: runLoading,
  result: runResult,
  logs: runLogs,
  tokenUsage: runTokenUsage,
  error: runError,
  submit,
  clear
} = useNovelRun({
  allowFallback: import.meta.env.DEV,
  onUnauthorized: expireSession
})

onMounted(() => {
  void initialize()
  void checkHealth()
})
</script>

<template>
  <AppShell
    :auth-user="user"
    :auth-loading="authLoading"
    :auth-error="authError"
    :run-status="runStatus"
    :run-loading="runLoading"
    :run-result="runResult"
    :run-logs="runLogs"
    :run-token-usage="runTokenUsage"
    :run-error="runError"
    :java-status="javaStatus"
    :agent-status="agentStatus"
    :fallback-active="fallbackActive"
    @login="login"
    @register="register"
    @logout="logout"
    @submit="submit"
    @clear="clear"
    @refresh-health="checkHealth"
  />
</template>
