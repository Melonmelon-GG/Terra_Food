import { readonly, ref } from 'vue'

import { getCurrentUser, login as requestLogin, logout as requestLogout } from './api'
import type { AuthUser, LoginPayload } from './types'

const currentUser = ref<AuthUser | null>(null)
let sessionChecked = false

export function useAuth() {
  async function restoreSession() {
    if (sessionChecked) {
      return
    }

    try {
      currentUser.value = await getCurrentUser()
    } catch {
      currentUser.value = null
    } finally {
      sessionChecked = true
    }
  }

  async function login(payload: LoginPayload) {
    currentUser.value = await requestLogin(payload)
    sessionChecked = true
    return currentUser.value
  }

  async function logout() {
    try {
      await requestLogout()
    } finally {
      currentUser.value = null
      sessionChecked = true
    }
  }

  return {
    currentUser: readonly(currentUser),
    restoreSession,
    login,
    logout,
  }
}
