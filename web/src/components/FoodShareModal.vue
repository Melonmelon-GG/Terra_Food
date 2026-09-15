<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import { toBlob } from 'html-to-image'
import type { Food } from '../types'
import { defaultLandmark, resolveLandmark } from '../landmarks'

const props = defineProps<{ food: Food }>()
const emit = defineEmits<{ close: [] }>()
const { t, locale } = useI18n()
const router = useRouter()
const WIDTH = 1000
const HEIGHT = 450
const dialog = ref<HTMLDialogElement>()
const viewport = ref<HTMLElement>()
const card = ref<HTMLElement>()
const scale = ref(1)
const loading = ref(true)
const exporting = ref(false)
const importing = ref(false)
const error = ref('')
const imageWarning = ref(false)
const foodImage = ref('')
const background = ref('')
const defaultBackground = ref('')
const qr = ref('')
const preview = ref('')
const controller = new AbortController()
let observer: ResizeObserver | undefined
let disposed = false
let uploadVersion = 0
const issuedAt = new Date()
const date = computed(() => issuedAt.toLocaleDateString(locale.value))
const shareUrl = new URL(router.resolve('/foods/' + props.food.id).href, window.location.origin).href
const localAddress = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)
const filename = computed(() => (props.food.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 70) || 'food') + '-' + props.food.id + '.png')
// Keep every encoded module and a four-module quiet zone intact.
// Finder ornaments stay within their original 7x7 footprints.
function createShareQr(url: string): string {
  const { modules } = QRCode.create(url, { errorCorrectionLevel: 'H' })
  const unit = 10
  const margin = 4
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = (modules.size + margin * 2) * unit
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Canvas unavailable')
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  const finders = [[0, 0], [modules.size - 7, 0], [0, modules.size - 7]]
  context.fillStyle = '#30231c'
  for (let row = 0; row < modules.size; row++) {
    for (let column = 0; column < modules.size; column++) {
      if (!modules.get(row, column) || finders.some(([x, y]) =>
        column >= x! && column < x! + 7 && row >= y! && row < y! + 7)) continue
      context.beginPath()
      context.roundRect((column + margin) * unit, (row + margin) * unit, unit, unit, 2)
      context.fill()
    }
  }
  for (const [column, row] of finders) {
    const x = (column! + margin) * unit
    const y = (row! + margin) * unit
    for (const [inset, width, color, radius] of [
      [0, 7, '#842d26', 12], [1, 5, '#ffffff', 6], [2, 3, '#842d26', 5],
    ] as const) {
      context.fillStyle = color
      context.beginPath()
      context.roundRect(x + inset * unit, y + inset * unit, width * unit, width * unit, radius)
      context.fill()
    }
  }
  return canvas.toDataURL('image/png')
}


function readBlob(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = reject
    reader.readAsDataURL(blob)
  })
}
async function decodeImage(src: string) {
  const img = new Image()
  img.src = src
  await img.decode()
  if (img.naturalWidth * img.naturalHeight > 24_000_000) throw new Error('Image too large')
}
async function embedImage(url?: string, optional = false): Promise<string> {
  if (!url) return ''
  try {
    const response = await fetch(url, { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]) })
    if (!response.ok) throw new Error('Image unavailable')
    const blob = await response.blob()
    if (blob.size > 10 * 1024 * 1024) throw new Error('Image too large')
    const data = await readBlob(blob)
    await decodeImage(data)
    return data
  } catch {
    if (!optional && !disposed) imageWarning.value = true
    return ''
  }
}
async function load() {
  loading.value = true
  error.value = ''
  imageWarning.value = false
  clearPreview()
  try {
    qr.value = createShareQr(shareUrl)
    const artwork = resolveLandmark(props.food.region.province)
    const [dishImage, art] = await Promise.all([embedImage(props.food.imageUrl), embedImage(artwork.image, true)])
    if (disposed) return
    foodImage.value = dishImage
    const resolvedArt = art || (artwork.image !== defaultLandmark.image ? await embedImage(defaultLandmark.image, true) : '')
    if (disposed) return
    if (background.value === defaultBackground.value) background.value = resolvedArt
    defaultBackground.value = resolvedArt
  } catch {
    if (!disposed) error.value = t('share.loadError')
  } finally {
    if (!disposed) loading.value = false
  }
}
function clearPreview() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
}
async function importBackground(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || loading.value || exporting.value || importing.value) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
    error.value = t('archive.ticketFileError')
    return
  }
  const version = ++uploadVersion
  importing.value = true
  error.value = ''
  try {
    const data = await readBlob(file)
    await decodeImage(data)
    if (disposed || version !== uploadVersion) return
    background.value = data
    clearPreview()
  } catch {
    if (!disposed) error.value = t('archive.ticketFileError')
  } finally {
    if (!disposed && version === uploadVersion) importing.value = false
  }
}
function resetBackground() {
  if (exporting.value || importing.value) return
  background.value = defaultBackground.value
  clearPreview()
  error.value = ''
}
async function exportCard() {
  if (!card.value || loading.value || exporting.value || importing.value || !qr.value) return
  exporting.value = true
  error.value = ''
  try {
    await document.fonts.ready
    await nextTick()
    await Promise.all(Array.from(card.value.querySelectorAll('img')).map(img => img.decode()))
    // Export the fixed ticket canvas, independent of the scaled mobile preview.
    const blob = await toBlob(card.value, { width: WIDTH, height: HEIGHT, pixelRatio: 2, skipFonts: true })
    if (!blob) throw new Error('Empty export')
    if (disposed) return
    clearPreview()
    preview.value = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = preview.value
    link.download = filename.value
    document.body.append(link)
    link.click()
    link.remove()
  } catch {
    if (!disposed) error.value = t('share.exportError')
  } finally {
    if (!disposed) exporting.value = false
  }
}
onMounted(() => {
  dialog.value?.showModal()
  observer = new ResizeObserver(entries => {
    const width = entries[0]?.contentRect.width
    if (width) scale.value = width / WIDTH
  })
  if (viewport.value) {
    scale.value = viewport.value.clientWidth / WIDTH
    observer.observe(viewport.value)
  }
  void load()
})
onBeforeUnmount(() => {
  disposed = true
  controller.abort()
  observer?.disconnect()
  clearPreview()
})
</script>

<template>
  <dialog ref="dialog" class="food-share-dialog" :aria-label="t('archive.ticketTitle')" @close="emit('close')" @click="event => { if (event.target === dialog) dialog?.close() }">
    <header class="share-toolbar">
      <div><small>{{ t('archive.ticketStub') }}</small><h2>{{ t('archive.ticketTitle') }}</h2></div>
      <button class="share-close" type="button" :aria-label="t('share.close')" @click="dialog?.close()">×</button>
    </header>
    <p class="share-intro">{{ t('archive.ticketIntro') }}</p>
    <p v-if="loading" class="share-notice" role="status">{{ t('archive.ticketPreparing') }}</p>
    <p v-if="localAddress" class="share-notice">{{ t('share.localAddress') }}</p>
    <p v-if="imageWarning" class="share-notice" role="status">{{ t('share.imageWarning') }}</p>
    <div ref="viewport" class="share-viewport">
      <div class="share-scale" :style="{ transform: 'scale(' + scale + ')' }">
        <article ref="card" class="share-card">
          <img v-if="background" class="ticket-art" :src="background" alt="">
          <section class="ticket-main share-dish">
            <header class="ticket-heading"><strong>{{ t('common.appName') }}</strong><span>{{ t('archive.ticket') }}</span></header>
            <div class="share-dish-image"><img v-if="foodImage" :src="foodImage" :alt="food.name"><span v-else>{{ t('archive.noCover') }}</span></div>
            <div class="ticket-dish-copy">
              <p class="share-region">{{ food.region.province }} · {{ food.region.name }}</p>
              <h2>{{ food.name }}</h2>
              <p class="share-summary">{{ food.summary }}</p>
            </div>
            <div class="share-ingredients"><small>{{ t('detail.ingredients') }}</small><p>{{ food.ingredients }}</p></div>
          </section>
          <section class="ticket-stub">
            <div class="ticket-stub-copy">
              <small>{{ t('archive.ticketStub') }}</small>
              <strong>NO. {{ String(food.id).padStart(5, '0') }}</strong>
              <span>{{ t('archive.ticketDate') }}</span>
              <time>{{ date }}</time>
              <p>{{ t('archive.motto') }}</p>
              <div class="ticket-perforation" aria-hidden="true"></div>
            </div>
            <div class="share-qr"><img v-if="qr" :src="qr" :alt="t('archive.ticketHint')"><p>{{ t('archive.ticketHint') }}</p></div>
          </section>
        </article>
      </div>
    </div>
    <div class="share-controls">
      <label class="share-import">{{ t('share.import') }}<input type="file" accept="image/jpeg,image/png,image/webp" :disabled="loading || exporting || importing" @change="importBackground"></label>
      <button type="button" :disabled="loading || exporting || importing" @click="resetBackground">{{ t('share.reset') }}</button>
      <button class="share-export" type="button" :disabled="loading || exporting || importing || !qr" @click="exportCard">{{ exporting ? t('share.exporting') : t('archive.ticketDownload') }}</button>
    </div>
    <p class="share-note">{{ t('archive.ticketNote') }}</p>
    <p v-if="error" class="share-error" role="alert">{{ error }} <button v-if="!qr" type="button" @click="load">{{ t('share.retry') }}</button></p>
    <div v-if="preview" class="share-export-result" role="status">
      <p>{{ t('archive.ticketSaved') }}</p>
      <a :href="preview" :download="filename">{{ t('archive.ticketDownload') }}</a>
      <img :src="preview" :alt="food.name">
    </div>
  </dialog>
</template>

<style scoped>
.food-share-dialog{width:min(1080px,calc(100vw - 24px));max-height:calc(100dvh - 24px);padding:24px;border:1px solid var(--border-paper);background:var(--color-paper);color:var(--color-ink);box-sizing:border-box;overflow:auto;overscroll-behavior:contain;border-radius:4px 18px 4px 4px}
.food-share-dialog::backdrop{background:#241910a8;backdrop-filter:blur(4px)}
.share-toolbar{display:flex;justify-content:space-between;gap:16px;align-items:start}.share-toolbar small{font-size:10px;letter-spacing:3px;color:var(--color-accent-label)}.share-toolbar h2{margin:8px 0;font-size:24px}
.share-close{flex:0 0 44px;width:44px;height:44px;border:1px solid var(--border-paper);background:none;color:var(--color-ink);font-size:26px;cursor:pointer}
.share-intro,.share-note,.share-notice{font-size:13px;line-height:1.8;color:var(--muted)}.share-viewport{width:100%;aspect-ratio:20/9;overflow:hidden;margin:20px auto;background:#f5f0e5}.share-scale{width:1000px;height:450px;transform-origin:top left}
.share-card{--color-ink:#30231c;--color-paper:#fbf8f0;--color-accent:#842d26;position:relative;isolation:isolate;box-sizing:border-box;width:1000px;height:450px;display:grid;grid-template-columns:800px 200px;overflow:hidden;background:#fbf8f0;color:#30231c;border:1px solid #d8cbb8;font:16px/1.5 Arial,"Microsoft YaHei",sans-serif;text-align:left;border-radius:12px}
/* Reset detail-page article spacing so preview and export use the same fixed canvas. */
.share-card{padding:0;gap:0}
.share-card *{box-sizing:border-box}.share-card p{margin:0}.ticket-art{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:.13;z-index:-1}
.ticket-main{position:relative;isolation:isolate;min-width:0;padding:26px 28px;display:grid;grid-template-columns:minmax(0,1fr) 330px;grid-template-rows:auto minmax(0,1fr) auto;gap:16px 24px;overflow:hidden;color:#fff;background:#2b1b14}
.ticket-main::before{content:"";position:absolute;inset:0;z-index:-1;background:linear-gradient(90deg,rgba(25,15,10,.04) 0%,rgba(25,15,10,.12) 34%,rgba(25,15,10,.74) 58%,rgba(25,15,10,.96) 100%),linear-gradient(0deg,rgba(25,15,10,.42),transparent 34%)}
.ticket-heading{position:relative;z-index:1;grid-column:1/-1;display:flex;justify-content:space-between;align-items:center;gap:12px;min-height:32px;border-bottom:1px solid #ffffff73;padding-bottom:11px;text-shadow:0 1px 5px #24150f}
.ticket-heading strong{font-size:22px;letter-spacing:3px;font-family:serif}.ticket-heading span{font-size:12px;color:#f3c5b5;letter-spacing:3px}
.share-dish-image{position:absolute;inset:0;z-index:-2;display:grid;place-items:center;background:#6e5442;overflow:hidden}.share-dish-image img{width:100%;height:100%;object-fit:cover}.share-dish-image span{font-size:16px;letter-spacing:3px;color:#eadfce}
.ticket-dish-copy{grid-column:2;grid-row:2;min-width:0;align-self:center;display:flex;flex-direction:column;gap:10px;text-shadow:0 1px 6px #1a0e09}.ticket-dish-copy .share-region{display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere}
.share-region{font-size:13px;letter-spacing:2px;color:#f3c5b5}
.share-dish h2{font-size:32px;line-height:1.25;font-family:"Microsoft YaHei",sans-serif;margin:0;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere;flex-shrink:0}
.share-summary{font-size:15px;line-height:1.65;color:#f3ebe3;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:3;overflow:hidden;overflow-wrap:anywhere;flex-shrink:0}
.share-ingredients{grid-column:2;grid-row:3;border-top:1px solid #ffffff73;padding-top:10px;text-shadow:0 1px 5px #1a0e09}.share-ingredients small{font-size:11px;color:#f3c5b5;letter-spacing:2px}.share-ingredients p{margin-top:3px;font-size:13px;line-height:1.55;color:#f3ebe3;display:-webkit-box;-webkit-box-orient:vertical;-webkit-line-clamp:2;overflow:hidden;overflow-wrap:anywhere}
.ticket-stub{position:relative;min-width:0;border-left:2px dashed #b89f80;padding:20px 18px;display:flex;flex-direction:column;justify-content:space-between;gap:10px;background:#efe5d447}
.ticket-stub::before,.ticket-stub::after{content:"";position:absolute;left:-16px;width:30px;height:30px;border:1px solid #d8cbb8;border-radius:50%;background:#f5f0e5}.ticket-stub::before{top:-16px}.ticket-stub::after{bottom:-16px}
.ticket-stub-copy{display:flex;flex-direction:column;align-items:start;gap:4px}.ticket-stub-copy>small{color:#842d26;letter-spacing:3px;font-size:12px}.ticket-stub-copy strong{font:22px monospace;letter-spacing:2px;margin:3px 0}.ticket-stub-copy>span{color:#826f60;font-size:11px}.ticket-stub-copy time{font:16px monospace}.ticket-stub-copy p{font-size:11px;color:#826f60;letter-spacing:1px}
.ticket-perforation{height:10px;width:100%;margin-top:5px;background:repeating-linear-gradient(90deg,#842d2666 0 1px,transparent 1px 5px)}
.share-qr{align-self:center;text-align:center}.share-qr img{display:block;width:156px;height:156px;background:#fff;border:5px solid #fff}.share-qr p{font-size:12px;color:#665347;margin-top:7px}
.share-controls{display:flex;gap:8px;flex-wrap:wrap;margin-top:16px}.share-controls button,.share-import{display:inline-flex;align-items:center;justify-content:center;min-height:44px;padding:10px 12px;font:13px inherit;border:1px solid var(--border-paper);color:var(--color-ink);background:var(--color-input);cursor:pointer}
.share-import{position:relative;overflow:hidden}.share-import input{position:absolute;inset:0;width:100%;opacity:0;cursor:pointer}.share-import:focus-within{outline:2px solid var(--color-accent);outline-offset:2px}.share-controls button:disabled{opacity:.5;cursor:wait}
.share-controls .share-export{flex-basis:100%;background:var(--color-accent);color:var(--color-paper);border-color:var(--color-accent)}
.share-error{color:var(--color-accent-label);font-size:13px}.share-export-result{font-size:13px;line-height:1.8}.share-export-result img{display:block;width:100%;margin-top:12px}.share-export-result a{display:inline-flex;align-items:center;min-height:44px;color:var(--color-accent)}
@media(max-width:600px){.food-share-dialog{padding:16px;width:calc(100vw - 16px);max-height:calc(100dvh - 16px)}.share-toolbar h2{font-size:21px}.share-viewport{margin:14px auto}.share-controls>*{flex:1}.share-note{font-size:12px}}
</style>
