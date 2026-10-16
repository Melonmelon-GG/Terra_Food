import test from 'node:test'
import assert from 'node:assert/strict'
import { createSharedRead } from '../src/sharedRead.ts'

test('same-key concurrent reads share work but never share mutable results', async () => {
  const cache = createSharedRead(1000)
  let calls = 0
  const load = async () => { calls++; return [{ id: 1 }] }
  const [a, b] = await Promise.all([cache.read('k', load), cache.read('k', load)])
  a.push({ id: 2 })
  assert.equal(b.length, 1)
  assert.equal((await cache.read('k', load)).length, 1)
  assert.equal(calls, 1)
})

test('one cancelling consumer cannot cancel another consumer', async () => {
  const cache = createSharedRead(1000)
  let complete, upstream
  const load = signal => { upstream = signal; return new Promise(resolve => { complete = resolve }) }
  const controller = new AbortController()
  const cancelled = cache.read('k', load, controller.signal)
  const kept = cache.read('k', load)
  await Promise.resolve()
  controller.abort()
  await assert.rejects(cancelled, { name: 'AbortError' })
  assert.equal(upstream.aborted, false)
  complete({ id: 1 })
  assert.deepEqual(await kept, { id: 1 })
})

test('last consumer cancellation aborts upstream and releases the key', async () => {
  const cache = createSharedRead(1000)
  let upstream
  const controller = new AbortController()
  const pending = cache.read('k', signal => {
    upstream = signal
    return new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(new DOMException('stop', 'AbortError'))))
  }, controller.signal)
  await Promise.resolve()
  controller.abort()
  await assert.rejects(pending, { name: 'AbortError' })
  assert.equal(upstream.aborted, true)
  assert.equal(await cache.read('k', async () => 2), 2)
})

test('expiry, eviction and invalidation prevent stale reuse', async () => {
  const cache = createSharedRead(10, 1)
  const originalNow = Date.now
  let now = 1000, calls = 0
  Date.now = () => now
  try {
    const load = async () => ++calls
    assert.equal(await cache.read('a', load), 1)
    now += 11
    assert.equal(await cache.read('a', load), 2)
    assert.equal(await cache.read('b', load), 3)
    assert.equal(await cache.read('a', load), 4)
    cache.clear()
    assert.equal(await cache.read('a', load), 5)
  } finally { Date.now = originalNow }
})

test('failure is not cached, automatically retried or allowed to poison a key', async () => {
  const cache = createSharedRead(1000)
  let calls = 0
  await assert.rejects(cache.read('k', async () => { calls++; throw new Error('offline') }), /offline/)
  assert.equal(calls, 1)
  assert.equal(await cache.read('k', async () => { calls++; return 7 }), 7)
  assert.equal(calls, 2)
})

test('invalidation rejects an old in-flight value even if upstream ignores abort', async () => {
  const cache = createSharedRead(1000)
  let complete
  const old = cache.read('k', () => new Promise(resolve => { complete = resolve }))
  await Promise.resolve()
  cache.clear()
  const fresh = cache.read('k', async () => 'new')
  complete('old')
  await assert.rejects(old, { name: 'AbortError' })
  assert.equal(await fresh, 'new')
  assert.equal(await cache.read('k', async () => 'unexpected'), 'new')
})

test('JSON DTO copy works without structuredClone on older mobile runtimes', async () => {
  const original = globalThis.structuredClone
  globalThis.structuredClone = undefined
  try {
    const cache = createSharedRead(1000)
    const first = await cache.read('k', async () => [{ nested: { id: 1 } }])
    first[0].nested.id = 2
    assert.equal((await cache.read('k', async () => []))[0].nested.id, 1)
  } finally { globalThis.structuredClone = original }
})

test('a clone error rejects the consumer rather than leaving it pending', async () => {
  const cache = createSharedRead(1000)
  await assert.rejects(cache.read('k', async () => () => 1))
  assert.equal(await cache.read('k', async () => 2), 2)
})
