import { ref } from 'vue'
import { apiRequest } from '../api/client'
import type { HealthResponse, ServiceStatus } from '../types/api'

interface HealthCheckOptions {
  allowFallback?: boolean
}

function isHealthy(response: HealthResponse) {
  const status = typeof response.status === 'string' ? response.status.toLowerCase() : ''
  return ['ok', 'up', 'healthy', 'available'].includes(status)
}

async function probe(path: string) {
  try {
    return isHealthy(await apiRequest<HealthResponse>(path, { auth: false }))
  } catch {
    return false
  }
}

export function useHealthCheck(options: HealthCheckOptions = {}) {
  const javaStatus = ref<ServiceStatus>('checking')
  const agentStatus = ref<ServiceStatus>('checking')
  const fallbackActive = ref(false)

  async function check() {
    javaStatus.value = 'checking'
    agentStatus.value = 'checking'
    fallbackActive.value = false

    const javaOnline = await probe('/api/health')
    javaStatus.value = javaOnline ? 'online' : 'offline'

    if (javaOnline) {
      agentStatus.value = await probe('/api/agent/health') ? 'online' : 'offline'
      return
    }

    if (options.allowFallback) {
      const directAgentOnline = await probe('/agentsky-api/api/health')
      agentStatus.value = directAgentOnline ? 'online' : 'offline'
      fallbackActive.value = directAgentOnline
      return
    }

    agentStatus.value = 'offline'
  }

  return { javaStatus, agentStatus, fallbackActive, check }
}

