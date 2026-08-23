import { computed, ref } from 'vue'
import { getCurrentUser, loginRequest, registerRequest } from '../api/auth'
import { ApiError, AUTH_TOKEN_KEY } from '../api/client'
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../types/api'

const storedToken = typeof localStorage === 'undefined' ? null : localStorage.getItem(AUTH_TOKEN_KEY)
const token = ref<string | null>(storedToken)
const user = ref<AuthUser | null>(null)
const loading = ref(false)
const initialized = ref(false)
const error = ref('')

function persistSession(response: AuthResponse) {
  token.value = response.token
  user.value = response.user
  localStorage.setItem(AUTH_TOKEN_KEY, response.token)
}

function clearSession() {
  token.value = null
  user.value = null
  localStorage.removeItem(AUTH_TOKEN_KEY)
}

function setFailure(cause: unknown) {
  error.value = cause instanceof ApiError ? cause.message : '操作失败，请稍后重试'
}

export function useAuth() {
  const isAuthenticated = computed(() => Boolean(token.value && user.value))

  async function initialize() {
    if (initialized.value) return
    error.value = ''
    const currentToken = token.value
    if (!currentToken) {
      initialized.value = true
      return
    }

    loading.value = true
    try {
      user.value = await getCurrentUser(currentToken)
    } catch (cause) {
      clearSession()
      setFailure(cause)
    } finally {
      initialized.value = true
      loading.value = false
    }
  }

  async function login(credentials: LoginRequest) {
    loading.value = true
    error.value = ''
    try {
      persistSession(await loginRequest(credentials))
      initialized.value = true
      return true
    } catch (cause) {
      setFailure(cause)
      return false
    } finally {
      loading.value = false
    }
  }

  async function register(details: RegisterRequest) {
    loading.value = true
    error.value = ''
    try {
      persistSession(await registerRequest(details))
      initialized.value = true
      return true
    } catch (cause) {
      setFailure(cause)
      return false
    } finally {
      loading.value = false
    }
  }

  function logout() {
    clearSession()
    error.value = ''
    initialized.value = true
  }

  function expireSession() {
    clearSession()
    error.value = '登录已过期，请重新登录'
    initialized.value = true
  }

  return {
    token,
    user,
    loading,
    initialized,
    error,
    isAuthenticated,
    initialize,
    login,
    register,
    logout,
    expireSession
  }
}
