import { readonly, ref } from 'vue'
import { isAxiosError } from 'axios'

import { getCurrentUser, login as requestLogin, logout as requestLogout } from './api'
import { useAchievementNotifications } from './achievement'
import type { AuthUser, LoginPayload, UserRole } from './types'
import { clearDraftsForUser } from './drafts'

const currentUser = ref<AuthUser | null>(null)
const achievementNotifications = useAchievementNotifications()
let sessionChecked = false
let lastRestoreAttemptAt = 0
const sessionRevision = ref(0)
const sessionConfirmed = ref(false)
let foregroundCheckAt = 0
let lastExternalSignal: string | null = null
let channel: BroadcastChannel | undefined
try { channel = new BroadcastChannel('terra-auth-session') } catch { /* Storage/focus fallback remains available. */ }
let restoring: { revision: number; promise: Promise<void> } | undefined
export const AUTH_SESSION_CHANGE_KEY = 'dayan-auth-session-change'

function notifySessionChange() {
  const signal = crypto.randomUUID()
  channel?.postMessage({ type: 'session-changed', signal })
  try {
    // 只广播变更信号；身份凭证始终由 HttpOnly Cookie 管理。
    localStorage.setItem(AUTH_SESSION_CHANGE_KEY, signal)
  } catch {
    // 禁用浏览器存储时仍可通过切回页面刷新会话。
  }
}

function applyUser(user: AuthUser | null) {
  if (currentUser.value?.id !== user?.id) achievementNotifications.clear()
  currentUser.value = user
}
// 恢复失败后短时冷却，避免弱网下每次导航都重复打 /auth/me；
// 冷却期过后仍会重试（不把“单次失败”升级成永久登出）。
const RESTORE_COOLDOWN_MS = 30_000

export function isAdminRole(role?: UserRole): boolean {
  return role === 'ADMIN' || role === 'SUB_ADMIN'
}

export function useAuth() {
  function getSessionRevision() { return sessionRevision.value }

  async function restoreSession(force = false) {
    if (restoring?.revision === sessionRevision.value) return restoring.promise
    if (!force && sessionChecked) {
      return
    }

    const now = Date.now()
    if (!force && now - lastRestoreAttemptAt < RESTORE_COOLDOWN_MS) {
      return
    }
    lastRestoreAttemptAt = now

    sessionConfirmed.value = false
    const revision = sessionRevision.value
    const previousId = currentUser.value?.id
    const promise = (async () => {
      try {
        const user = await getCurrentUser()
        if (revision !== sessionRevision.value) return
        if (previousId !== user.id && previousId != null) ++sessionRevision.value
        applyUser(user)
        sessionChecked = true
        sessionConfirmed.value = true
        void achievementNotifications.load()
      } catch (error) {
        if (revision !== sessionRevision.value) return
        if (isAxiosError(error) && error.response?.status === 401) {
          applyUser(null)
          sessionChecked = true
          sessionConfirmed.value = true
        } else {
          // 网络故障不等于登出，保留当前身份并允许后续重试。
          sessionChecked = false
        }
      } finally {
        if (restoring?.revision === revision) restoring = undefined
      }
    })()
    restoring = { revision, promise }
    return promise
  }

  async function login(payload: LoginPayload) {
    const revision = ++sessionRevision.value
    sessionConfirmed.value = false
    const user = await requestLogin(payload)
    if (revision !== sessionRevision.value) throw new Error('登录状态已在其他页面发生变化')
    ++sessionRevision.value // Login rotates the server session and its CSRF token.
    applyUser(user)
    sessionChecked = true
    sessionConfirmed.value = true
    notifySessionChange()
    void achievementNotifications.load()
    return user
  }

  async function logout() {
    const exitingUserId = currentUser.value?.id
    ++sessionRevision.value
    try {
      await requestLogout()
    } finally {
      if (exitingUserId != null) clearDraftsForUser(exitingUserId)
      clearSession(true)
    }
  }

  function clearSession(broadcast = true) {
    ++sessionRevision.value
    currentUser.value = null
    sessionConfirmed.value = true
    achievementNotifications.clear()
    sessionChecked = true
    if (broadcast) notifySessionChange()
  }

  function setCurrentUser(user: AuthUser, expectedRevision = sessionRevision.value, expectedUserId = currentUser.value?.id) {
    if (expectedRevision !== sessionRevision.value || expectedUserId !== currentUser.value?.id || user.id !== expectedUserId) return false
    applyUser(user)
    return true
  }

  async function handleExternalSessionChange(signal?: string | null) {
    if (signal && signal === lastExternalSignal) return
    lastExternalSignal = signal || null
    clearSession(false)
    sessionChecked = false
    await restoreSession(true)
  }

  async function verifyForegroundSession() {
    if (document.visibilityState === 'hidden') return
    const now = Date.now()
    if (now - foregroundCheckAt < 1000) return
    foregroundCheckAt = now
    await restoreSession(true)
  }

  return {
    sessionConfirmed: readonly(sessionConfirmed),
    sessionRevision: readonly(sessionRevision),
    verifyForegroundSession,
    currentUser: readonly(currentUser),
    restoreSession,
    login,
    logout,
    clearSession,
    setCurrentUser,
    getSessionRevision,
    handleExternalSessionChange,
  }
}

channel?.addEventListener('message', (event) => {
  if (event.data?.type === 'session-changed') void useAuth().handleExternalSessionChange(event.data.signal)
})
