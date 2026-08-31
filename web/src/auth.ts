import { readonly, ref } from 'vue'

import { getCurrentUser, login as requestLogin, logout as requestLogout } from './api'
import { useAchievementNotifications } from './achievement'
import type { AuthUser, LoginPayload, UserRole } from './types'

const currentUser = ref<AuthUser | null>(null)
const achievementNotifications = useAchievementNotifications()
let sessionChecked = false
let lastRestoreAttemptAt = 0
// 恢复失败后短时冷却，避免弱网下每次导航都重复打 /auth/me；
// 冷却期过后仍会重试（不把“单次失败”升级成永久登出）。
const RESTORE_COOLDOWN_MS = 30_000

export function isAdminRole(role?: UserRole): boolean {
  return role === 'ADMIN' || role === 'SUB_ADMIN'
}

export function useAuth() {
  async function restoreSession() {
    if (sessionChecked) {
      return
    }

    const now = Date.now()
    if (now - lastRestoreAttemptAt < RESTORE_COOLDOWN_MS) {
      return
    }
    lastRestoreAttemptAt = now

    try {
      currentUser.value = await getCurrentUser()
      sessionChecked = true
      await achievementNotifications.load()
    } catch {
      // 单次恢复失败（弱网/后端重启）不置位 sessionChecked：
      // 会话 cookie 仍在，下一次导航继续尝试；真正的会话过期由 401 拦截器统一处理。
    }
  }

  async function login(payload: LoginPayload) {
    currentUser.value = await requestLogin(payload)
    sessionChecked = true
    await achievementNotifications.load()
    return currentUser.value
  }

  async function logout() {
    try {
      await requestLogout()
    } finally {
      clearSession()
    }
  }

  function clearSession() {
    currentUser.value = null
    achievementNotifications.clear()
    sessionChecked = true
  }

  function setCurrentUser(user: AuthUser) {
    currentUser.value = user
  }

  return {
    currentUser: readonly(currentUser),
    restoreSession,
    login,
    logout,
    clearSession,
    setCurrentUser,
  }
}
