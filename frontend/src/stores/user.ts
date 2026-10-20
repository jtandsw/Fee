import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'
import type { LoginPayload, UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const accessToken = ref(getAccessToken())
  const user = ref<UserInfo | null>(null)

  const role = computed(() => user.value?.role ?? '')
  const displayName = computed(() => user.value?.realName || user.value?.username || '')
  const isStudent = computed(() => role.value === 'STUDENT')

  async function login(payload: LoginPayload): Promise<UserInfo> {
    const result = await authApi.login(payload)
    setTokens(result.accessToken, result.refreshToken)
    accessToken.value = result.accessToken
    user.value = result.user
    return result.user
  }

  async function fetchMe(): Promise<UserInfo> {
    const info = await authApi.me()
    user.value = info
    accessToken.value = getAccessToken()
    return info
  }

  async function logout(): Promise<void> {
    const refresh = getRefreshToken()
    try {
      if (refresh) {
        await authApi.logout(refresh)
      }
    } catch {
      // 登出接口失败也要清理本地登录态
    } finally {
      clearTokens()
      accessToken.value = ''
      user.value = null
    }
  }

  function reset(): void {
    clearTokens()
    accessToken.value = ''
    user.value = null
  }

  return { accessToken, user, role, displayName, isStudent, login, fetchMe, logout, reset }
})

export function homePath(role: string): string {
  if (role === 'STUDENT') {
    return '/student/home'
  }
  return '/finance/dashboard'
}
