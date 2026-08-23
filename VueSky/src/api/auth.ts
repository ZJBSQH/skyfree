import { apiRequest } from './client'
import type { AuthResponse, AuthUser, LoginRequest, RegisterRequest } from '../types/api'

export function loginRequest(credentials: LoginRequest) {
  return apiRequest<AuthResponse>('/api/auth/login', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(credentials)
  })
}

export function registerRequest(details: RegisterRequest) {
  return apiRequest<AuthResponse>('/api/auth/register', {
    method: 'POST',
    auth: false,
    body: JSON.stringify(details)
  })
}

export function getCurrentUser(token: string) {
  return apiRequest<AuthUser>('/api/auth/me', { token })
}

