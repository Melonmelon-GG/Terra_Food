<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import L from 'leaflet'
import markerIcon2xUrl from 'leaflet/dist/images/marker-icon-2x.png'
import markerIconUrl from 'leaflet/dist/images/marker-icon.png'
import markerShadowUrl from 'leaflet/dist/images/marker-shadow.png'

import type { Food, MapBounds, MapFocus } from '../types'
import 'leaflet/dist/leaflet.css'

// Vite 会内联 Leaflet CSS 中用于探测路径的图片，导致默认图标退回到不存在的
// /marker-icon.png。显式注入构建后的资源 URL，确保开发和生产环境使用同一图标。
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2xUrl,
  iconUrl: markerIconUrl,
  shadowUrl: markerShadowUrl,
})

const props = defineProps<{
  foods: Food[]
  focus?: MapFocus
}>()

const emit = defineEmits<{
  pick: [latitude: number, longitude: number]
  boundsChange: [bounds: MapBounds]
}>()

const { locale, t } = useI18n()

const mapElement = ref<HTMLDivElement>()
const mapReady = ref(false)
const mapLoadError = ref(false)
let map: L.Map | undefined
let markerLayer: L.LayerGroup | undefined
let tileLayer: L.TileLayer | undefined
let annotationLayer: L.TileLayer | undefined
let resizeObserver: ResizeObserver | undefined
let resizeFrame: number | undefined
let mountFrame: number | undefined
let primaryLoadTimer: ReturnType<typeof setTimeout> | undefined
let tileLoadGeneration = 0
let activeTileProvider: 'tianditu' | 'osm' = 'tianditu'

const tiandituKey = import.meta.env.VITE_TIANDITU_KEY?.trim()
const tiandituSubdomains = ['0', '1', '2', '3', '4', '5', '6', '7']

function tiandituTileUrl(layer: 'vec_w' | 'cva_w') {
  return `https://t{s}.tianditu.gov.cn/DataServer?T=${layer}&x={x}&y={y}&l={z}&tk=${encodeURIComponent(tiandituKey || '')}`
}

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  const layer = markerLayer
  // 筛选或切换语言后重建图层，避免残留旧标记和旧语言弹窗。
  layer.clearLayers()
  props.foods.forEach((food) => {
    if (food.latitude == null || food.longitude == null) {
      return
    }

    const position: L.LatLngExpression = [food.latitude, food.longitude]
    const popup = document.createElement('div')
    popup.className = 'map-popup'

    const name = document.createElement('strong')
    name.textContent = food.name

    const region = document.createElement('span')
    region.textContent = `${food.region.province} · ${food.region.name}`

    const summary = document.createElement('p')
    summary.textContent = food.summary

    const detailLink = document.createElement('a')
    detailLink.href = `/foods/${food.id}`
    detailLink.textContent = t('map.detail')

    popup.append(name, region, summary, detailLink)

    L.marker(position)
      .bindPopup(popup)
      .addTo(layer)
  })

}

function emitCurrentBounds() {
  if (!map) return
  const bounds = map.getBounds()
  emit('boundsChange', {
    minLatitude: bounds.getSouth(),
    maxLatitude: bounds.getNorth(),
    minLongitude: bounds.getWest(),
    maxLongitude: bounds.getEast(),
  })
}

function invalidateMapSize() {
  if (!map) return
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
  resizeFrame = requestAnimationFrame(() => {
    map?.invalidateSize({ animate: false, pan: false })
  })
}

function clearPrimaryLoadTimer() {
  if (primaryLoadTimer) {
    clearTimeout(primaryLoadTimer)
    primaryLoadTimer = undefined
  }
}

function markTilesReady(generation: number) {
  if (generation !== tileLoadGeneration) return

  clearPrimaryLoadTimer()
  mapReady.value = true
  mapLoadError.value = false
}

function switchToOpenStreetMap() {
  if (!map || activeTileProvider === 'osm') return

  const generation = ++tileLoadGeneration

  tileLayer?.remove()
  annotationLayer?.remove()
  clearPrimaryLoadTimer()
  activeTileProvider = 'osm'
  mapReady.value = false
  mapLoadError.value = false

  tileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 18,
    updateWhenIdle: true,
    keepBuffer: 3,
  })
    .on('tileload', () => markTilesReady(generation))
    .addTo(map)

  // 缩放时会并发请求很多瓦片，少量请求失败不代表整张地图不可用。
  // 只有一段时间内完全没有瓦片成功加载，才显示底图错误。
  primaryLoadTimer = setTimeout(() => {
    if (generation === tileLoadGeneration && !mapReady.value) {
      mapLoadError.value = true
    }
  }, 12_000)
}

function createTileLayer() {
  const generation = ++tileLoadGeneration

  tileLayer?.remove()
  annotationLayer?.remove()
  clearPrimaryLoadTimer()
  activeTileProvider = 'tianditu'
  mapReady.value = false
  mapLoadError.value = false

  if (!tiandituKey) {
    switchToOpenStreetMap()
    return
  }

  tileLayer = L.tileLayer(tiandituTileUrl('vec_w'), {
    attribution: '&copy; <a href="https://www.tianditu.gov.cn/" target="_blank" rel="noopener">天地图</a>',
    maxZoom: 18,
    subdomains: tiandituSubdomains,
    updateWhenIdle: true,
    keepBuffer: 3,
  })
    .on('tileload', () => markTilesReady(generation))
    .addTo(map!)

  // 天地图把道路底图和中文地名标注拆成两个图层，需要按顺序叠加。
  annotationLayer = L.tileLayer(tiandituTileUrl('cva_w'), {
    maxZoom: 18,
    subdomains: tiandituSubdomains,
    updateWhenIdle: true,
    keepBuffer: 3,
  }).addTo(map!)

  // 海外或运营商网络若无法及时连接天地图，自动启用国际备用底图。
  primaryLoadTimer = setTimeout(() => {
    if (generation === tileLoadGeneration && !mapReady.value) switchToOpenStreetMap()
  }, 8_000)
}
function initializeMap() {
  if (!mapElement.value || map) return

  // Leaflet 必须在真实 DOM 挂载后创建，否则无法正确计算地图尺寸。
  map = L.map(mapElement.value!, {
    center: props.focus ? [props.focus.latitude, props.focus.longitude] : [35.5, 104.2],
    zoom: props.focus?.zoom ?? 4,
    zoomControl: true,
    preferCanvas: true,
  })
  // Leaflet 1.8+ 默认 attribution 前缀内嵌乌克兰旗 SVG，与本项目政治中立立场不符；
  // 覆写为纯文字署名，保留瓦片版权（OSM 政策要求署名，不整体关闭控件）。
  map.attributionControl?.setPrefix('Leaflet')

  createTileLayer()

  markerLayer = L.layerGroup().addTo(map)
  map.on('click', (event: L.LeafletMouseEvent) => {
    emit('pick', event.latlng.lat, event.latlng.lng)
  })
  map.on('moveend', emitCurrentBounds)

  renderMarkers()
  emitCurrentBounds()

  // 手机地址栏伸缩、横竖屏切换和父容器重排都会改变地图尺寸。
  // 监听真实容器，而不是只监听 window.resize，避免 Leaflet 保留过期尺寸。
  resizeObserver = new ResizeObserver(invalidateMapSize)
  resizeObserver.observe(mapElement.value)
  window.visualViewport?.addEventListener('resize', invalidateMapSize)
  window.addEventListener('orientationchange', invalidateMapSize)
  invalidateMapSize()
}

function retryTiles() {
  createTileLayer()
  invalidateMapSize()
}

onMounted(async () => {
  await nextTick()
  // 等一帧，确保移动端媒体查询已经完成布局后再读取容器尺寸。
  mountFrame = requestAnimationFrame(initializeMap)
})

watch([() => props.foods, locale], renderMarkers, { deep: true })
watch(
  () => props.focus,
  (focus) => {
    if (map && focus) {
      map.flyTo([focus.latitude, focus.longitude], focus.zoom, { duration: 0.8 })
    }
  },
  { deep: true },
)

onBeforeUnmount(() => {
  // 主动释放地图事件和 DOM 引用，避免路由往返时重复初始化。
  resizeObserver?.disconnect()
  window.visualViewport?.removeEventListener('resize', invalidateMapSize)
  window.removeEventListener('orientationchange', invalidateMapSize)
  if (resizeFrame !== undefined) cancelAnimationFrame(resizeFrame)
  if (mountFrame !== undefined) cancelAnimationFrame(mountFrame)
  if (primaryLoadTimer) clearTimeout(primaryLoadTimer)
  map?.remove()
})
</script>

<template>
  <div class="food-map-shell" :aria-busy="!mapReady">
    <div ref="mapElement" class="food-map"></div>
    <div v-if="!mapReady" class="map-loading" role="status">
      <template v-if="mapLoadError">
        <span>{{ t('map.loadError') }}</span>
        <button type="button" @click="retryTiles">{{ t('map.retry') }}</button>
      </template>
      <span v-else>{{ t('map.loading') }}</span>
    </div>
  </div>
</template>
