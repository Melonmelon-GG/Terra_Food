// 未提交表单草稿：写入 localStorage 持久化文本内容，误关弹窗再打开不丢失；
// 提交成功后由调用方清除。图片 File 对象无法序列化，仅在本页会话内缓存。
const DRAFT_PREFIX = 'terra-food.draft.'
// 表单文案量级很小，设置上限防止异常内容把存储写满。
const MAX_DRAFT_LENGTH = 32_000

export interface DraftImageMeta {
  name: string
  type: string
  size: number
}

const sessionImages = new Map<string, File>()
const sessionDrafts = new Map<string, string>()

export function saveDraft(key: string, value: object): boolean {
  const serialized = JSON.stringify(value)
  if (serialized.length > MAX_DRAFT_LENGTH) return false
  sessionDrafts.set(key, serialized)
  try {
    localStorage.setItem(DRAFT_PREFIX + key, serialized)
    return true
  } catch { return false }
}

export function readDraft<T>(key: string): T | undefined {
  try {
    const serialized = sessionDrafts.get(key) ?? localStorage.getItem(DRAFT_PREFIX + key)
    return serialized == null ? undefined : (JSON.parse(serialized) as T)
  } catch {
    const serialized = sessionDrafts.get(key)
    return serialized ? JSON.parse(serialized) as T : undefined
  }
}

export function clearDraft(key: string): void {
  sessionDrafts.delete(key)
  try {
    localStorage.removeItem(DRAFT_PREFIX + key)
  } catch {
    // 与 saveDraft 相同：存储不可用时无需清理
  }
}

export function cacheDraftImage(key: string, file: File): void {
  sessionImages.set(key, file)
}

export function getCachedDraftImage(key: string): File | undefined {
  return sessionImages.get(key)
}

export function forgetDraftImage(key: string): void {
  sessionImages.delete(key)
}

export function clearDraftsForUser(userId: number): void {
  const owns = (key: string) => key === `foodUpload.v2.${userId}` || key.startsWith(`foodEdit.v2:${userId}:`)
  try {
    const removals: string[] = []
    for (let index = 0; index < localStorage.length; index++) {
      const storageKey = localStorage.key(index)
      if (storageKey && storageKey.startsWith(DRAFT_PREFIX) && owns(storageKey.slice(DRAFT_PREFIX.length))) removals.push(storageKey)
    }
    removals.forEach((key) => localStorage.removeItem(key))
  } catch {
    // Storage-restricted contexts still clear the in-memory File references below.
  }
  for (const key of sessionDrafts.keys()) { if (owns(key)) sessionDrafts.delete(key) }
  for (const key of sessionImages.keys()) {
    if (owns(key)) sessionImages.delete(key)
  }
}
