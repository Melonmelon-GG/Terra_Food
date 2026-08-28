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

export function saveDraft(key: string, value: object): void {
  try {
    const serialized = JSON.stringify(value)
    if (serialized.length <= MAX_DRAFT_LENGTH) {
      localStorage.setItem(DRAFT_PREFIX + key, serialized)
    }
  } catch {
    // 隐私模式或配额不足时静默降级：草稿仅保留在会话内
  }
}

export function readDraft<T>(key: string): T | undefined {
  try {
    const serialized = localStorage.getItem(DRAFT_PREFIX + key)
    return serialized == null ? undefined : (JSON.parse(serialized) as T)
  } catch {
    return undefined
  }
}

export function clearDraft(key: string): void {
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