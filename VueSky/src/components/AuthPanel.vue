<script setup lang="ts">
import { ref } from 'vue'
import { LogIn, LogOut, UserPlus } from 'lucide-vue-next'
import type { AuthUser, LoginRequest, RegisterRequest } from '../types/api'

defineProps<{
  user: AuthUser | null
  loading: boolean
  error: string
}>()

const emit = defineEmits<{
  login: [credentials: LoginRequest]
  register: [details: RegisterRequest]
  logout: []
}>()

const mode = ref<'login' | 'register'>('login')
const username = ref('')
const email = ref('')
const password = ref('')

function submit() {
  if (mode.value === 'register') {
    emit('register', { username: username.value.trim(), email: email.value.trim(), password: password.value })
    return
  }
  emit('login', { email: email.value.trim(), password: password.value })
}
</script>

<template>
  <section class="panel auth-panel" aria-labelledby="auth-title">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">创作者账号</p>
        <h2 id="auth-title">{{ user ? '当前账号' : mode === 'login' ? '登录工作台' : '注册账号' }}</h2>
      </div>
    </div>

    <template v-if="user">
      <div class="account-card">
        <span class="account-avatar" aria-hidden="true">{{ user.username.slice(0, 1) }}</span>
        <div>
          <strong>{{ user.username }}</strong>
          <span>{{ user.email }}</span>
        </div>
      </div>
      <button class="button button--quiet button--full" type="button" @click="emit('logout')">
        <LogOut :size="16" aria-hidden="true" />
        退出登录
      </button>
    </template>

    <form v-else class="stack-form" @submit.prevent="submit">
      <label v-if="mode === 'register'">
        <span>用户名</span>
        <input v-model="username" name="username" autocomplete="username" minlength="2" maxlength="30" required />
      </label>
      <label>
        <span>邮箱</span>
        <input v-model="email" name="email" type="email" autocomplete="email" required />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" name="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" minlength="6" maxlength="72" required />
      </label>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="button button--primary button--full" type="submit" :disabled="loading">
        <UserPlus v-if="mode === 'register'" :size="16" aria-hidden="true" />
        <LogIn v-else :size="16" aria-hidden="true" />
        {{ loading ? '请稍候' : mode === 'login' ? '登录' : '注册并登录' }}
      </button>
      <button
        class="text-button"
        type="button"
        :aria-label="mode === 'login' ? '切换到注册' : '切换到登录'"
        @click="mode = mode === 'login' ? 'register' : 'login'"
      >
        {{ mode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录' }}
      </button>
    </form>
  </section>
</template>

