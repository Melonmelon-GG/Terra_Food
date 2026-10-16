// Opt-in, bounded reads. Identity-dependent data needs scoped keys/invalidation;
// writes are never cached or automatically retried here.
export function createSharedRead<T>(ttlMs: number, limit = 32) {
  type Entry = { controller: AbortController; promise: Promise<T>; consumers: number; ready: boolean; value?: T; expires: number }
  const entries = new Map<string, Entry>()
  const cancelled = () => new DOMException('Read cancelled', 'AbortError')
  // Callers supply JSON DTOs. Older mobile WebViews may lack structuredClone.
  const copy = (value: T): T => typeof structuredClone === 'function'
    ? structuredClone(value) : JSON.parse(JSON.stringify(value)) as T

  function clear() {
    const previous = [...entries.values()]
    entries.clear()
    previous.forEach(entry => { if (!entry.ready) entry.controller.abort() })
  }

  function read(key: string, load: (signal: AbortSignal) => Promise<T>, signal?: AbortSignal): Promise<T> {
    if (signal?.aborted) return Promise.reject(cancelled())
    let entry = entries.get(key)
    if (entry?.ready && entry.expires <= Date.now()) { entries.delete(key); entry = undefined }
    if (entry?.ready) {
      try { return Promise.resolve(copy(entry.value!)) }
      catch (error) { entries.delete(key); return Promise.reject(error) }
    }
    if (!entry) {
      for (const [oldKey, oldEntry] of entries) {
        if (entries.size < limit) break
        if (oldEntry.ready) entries.delete(oldKey)
      }
      // Do not evict a request another component still needs.
      if (entries.size >= limit) return load(signal ?? new AbortController().signal).then(copy)
      const controller = new AbortController()
      const created: Entry = { controller, consumers: 0, ready: false, expires: 0, promise: undefined! }
      created.promise = Promise.resolve().then(() => load(controller.signal)).then(value => {
        if (controller.signal.aborted) throw cancelled()
        if (entries.get(key) === created) {
          created.value = value; created.ready = true; created.expires = Date.now() + ttlMs
        }
        return value
      }).catch(error => { if (entries.get(key) === created) entries.delete(key); throw error })
      entry = created
      entries.set(key, entry)
    }
    const requested = entry
    requested.consumers++
    return new Promise<T>((resolve, reject) => {
      let settled = false
      function finish() {
        settled = true
        signal?.removeEventListener('abort', abort)
        requested.consumers--
        if (!requested.ready && requested.consumers === 0) {
          if (entries.get(key) === requested) entries.delete(key)
          requested.controller.abort()
        }
      }
      function abort() { if (!settled) { finish(); reject(cancelled()) } }
      signal?.addEventListener('abort', abort, { once: true })
      requested.promise.then(value => {
        if (settled) return
        finish()
        try { resolve(copy(value)) }
        catch (error) { if (entries.get(key) === requested) entries.delete(key); reject(error) }
      }, error => { if (!settled) { finish(); reject(error) } })
    })
  }
  return { read, clear }
}
