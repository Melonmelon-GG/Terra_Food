import { readonly, ref } from 'vue'

import { getAchievementNotifications, markAchievementNotificationRead } from './api'
import type { Achievement } from './types'

const notifications = ref<Achievement[]>([])
let loading: Promise<void> | undefined
let generation = 0

export function useAchievementNotifications() {
  async function load() {
    if (loading) return loading
    const requestedGeneration = generation

    loading = (async () => {
      try {
        const incoming = await getAchievementNotifications()
        if (requestedGeneration !== generation) return
        const existingIds = new Set(notifications.value.map((achievement) => achievement.id))
        notifications.value.push(...incoming.filter((achievement) => !existingIds.has(achievement.id)))
      } catch {
        // 会话恢复和登录不应因通知接口暂时不可用而失败。
      } finally {
        if (requestedGeneration === generation) loading = undefined
      }
    })()
    return loading
  }

  async function dismiss(achievementId: number) {
    const requestedGeneration = generation
    try {
      await markAchievementNotificationRead(achievementId)
    } finally {
      if (requestedGeneration === generation) {
        notifications.value = notifications.value.filter((achievement) => achievement.id !== achievementId)
      }
    }
  }

  function clear() {
    ++generation
    loading = undefined
    notifications.value = []
  }

  return {
    notifications: readonly(notifications),
    load,
    dismiss,
    clear,
  }
}
