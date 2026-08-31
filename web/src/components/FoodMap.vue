<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import L from 'leaflet'

import type { FoodMarker, MapBounds, MapFocus } from '../types'
import 'leaflet/dist/leaflet.css'

// 使用项目自身的朱砂标记，避免 Leaflet 默认图片路径在开发/生产环境中退化成破图。
const foodMarkerIcon = L.divIcon({
  className: 'food-map-marker',
  html: '<span aria-hidden="true">炎</span>',
  iconSize: [32, 32],
  iconAnchor: [16, 16],
  popupAnchor: [0, -18],
})

const props = defineProps<{
  foods: FoodMarker[]
  focus?: MapFocus
}>()

const emit = defineEmits<{
  pick: [latitude: number, longitude: number]
  boundsChange: [bounds: MapBounds, zoom: number]
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
// 数据仍严格限定中国境内；视野额外保留一圈东亚缓冲，方便把被底部目录遮住的华南区域向上拖出。
const chinaDataBounds = L.latLngBounds([18, 73], [54, 135.2])
const chinaTileBounds = L.latLngBounds([-10, 0], [70, 180])
// 以中国完整露出侧栏为上限，保留少量横向拖动余量，不再允许拖入无瓦片的灰色区域。
const chinaViewportBounds = L.latLngBounds([-5, 15], [65, 175])
const chinaCenter: L.LatLngExpression = [35.5, 99.5]

function tiandituTileUrl(layer: 'vec_w' | 'cva_w') {
  return `https://t{s}.tianditu.gov.cn/DataServer?T=${layer}&x={x}&y={y}&l={z}&tk=${encodeURIComponent(tiandituKey || '')}`
}

function createFoodPopup(food: FoodMarker) {
  const popup = document.createElement('div')
  popup.className = 'map-popup'

  const name = document.createElement('strong')
  name.textContent = food.name

  const region = document.createElement('span')
  region.textContent = food.region.province + ' · ' + food.region.name

  const summary = document.createElement('p')
  summary.textContent = food.summary

  const detailLink = document.createElement('a')
  detailLink.href = '/foods/' + food.id
  detailLink.textContent = t('map.detail')

  popup.append(name, region, summary, detailLink)
  return popup
}

function createClusterIcon(count: number) {
  return L.divIcon({
    className: 'food-map-cluster',
    html: '<span aria-hidden="true">' + count + '</span>',
    iconSize: [38, 38],
    iconAnchor: [19, 19],
  })
}

function createClusterPopup(foods: FoodMarker[]) {
  const popup = document.createElement('div')
  popup.className = 'map-popup map-cluster-popup'

  const title = document.createElement('strong')
  title.textContent = t('home.recordCount', { count: foods.length })
  const list = document.createElement('ul')

  foods.forEach((food) => {
    const item = document.createElement('li')
    const detailLink = document.createElement('a')
    detailLink.href = '/foods/' + food.id
    detailLink.textContent = food.name
    item.append(detailLink)
    list.append(item)
  })

  popup.append(title, list)
  return popup
}

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  const layer = markerLayer
  layer.clearLayers()
  const zoom = map.getZoom()
  const cellSize = zoom <= 5 ? 58 : zoom <= 7 ? 48 : zoom <= 10 ? 40 : 30
  const groups = new Map<string, { foods: FoodMarker[], latitude: number, longitude: number }>()

  for (const food of props.foods) {
    if (food.latitude == null || food.longitude == null) continue

    const projected = map.project([food.latitude, food.longitude], zoom)
    const key = Math.floor(projected.x / cellSize) + ':' + Math.floor(projected.y / cellSize)
    const group = groups.get(key)
    if (group) {
      group.foods.push(food)
      group.latitude += food.latitude
      group.longitude += food.longitude
    } else {
      groups.set(key, {
        foods: [food],
        latitude: food.latitude,
        longitude: food.longitude,
      })
    }
  }

  groups.forEach((group) => {
    const count = group.foods.length
    const position: L.LatLngExpression = [
      group.latitude / count,
      group.longitude / count,
    ]
    if (count === 1) {
      L.marker(position, { icon: foodMarkerIcon })
        .bindPopup(createFoodPopup(group.foods[0]))
        .addTo(layer)
      return
    }

    const memberPositions = group.foods.map(
      (food) => L.latLng(food.latitude, food.longitude),
    )
    const memberBounds = L.latLngBounds(memberPositions)
    const samePosition = memberBounds.getNorthEast().equals(memberBounds.getSouthWest())
    const clusterMarker = L.marker(position, { icon: createClusterIcon(count) })

    if (samePosition || zoom >= 14) {
      clusterMarker.bindPopup(createClusterPopup(group.foods))
    } else {
      clusterMarker.on('click', () => {
        map?.fitBounds(memberBounds.pad(0.3), {
          animate: true,
          duration: 0.65,
          maxZoom: Math.min(zoom + 3, 14),
        })
      })
    }
    clusterMarker.addTo(layer)
  })
}
function emitCurrentBounds() {
  if (!map) return
  renderMarkers()
  const bounds = map.getBounds()
  emit('boundsChange', {
    minLatitude: Math.max(bounds.getSouth(), chinaDataBounds.getSouth()),
    maxLatitude: Math.min(bounds.getNorth(), chinaDataBounds.getNorth()),
    minLongitude: Math.max(bounds.getWest(), chinaDataBounds.getWest()),
    maxLongitude: Math.min(bounds.getEast(), chinaDataBounds.getEast()),
  }, map.getZoom())
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
    bounds: chinaTileBounds,
    noWrap: true,
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
    bounds: chinaTileBounds,
    noWrap: true,
    subdomains: tiandituSubdomains,
    updateWhenIdle: true,
    keepBuffer: 3,
  })
    .on('tileload', () => markTilesReady(generation))
    .addTo(map!)

  // 天地图把道路底图和中文地名标注拆成两个图层，需要按顺序叠加。
  annotationLayer = L.tileLayer(tiandituTileUrl('cva_w'), {
    maxZoom: 18,
    bounds: chinaTileBounds,
    noWrap: true,
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
    center: props.focus && chinaDataBounds.contains([props.focus.latitude, props.focus.longitude])
      ? [props.focus.latitude, props.focus.longitude]
      : chinaCenter,
    zoom: props.focus?.zoom ?? 4,
    minZoom: 4,
    maxZoom: 18,
    maxBounds: chinaViewportBounds,
    maxBoundsViscosity: 0.9,
    worldCopyJump: false,
    zoomControl: true,
    preferCanvas: true,
  })
  // Leaflet 1.8+ 默认 attribution 前缀内嵌乌克兰旗 SVG，与本项目政治中立立场不符；
  // 覆写为纯文字署名，保留瓦片版权（OSM 政策要求署名，不整体关闭控件）。
  map.attributionControl?.setPrefix('Leaflet')

  createTileLayer()

  markerLayer = L.layerGroup().addTo(map)
  map.on('click', (event: L.LeafletMouseEvent) => {
    if (chinaDataBounds.contains(event.latlng)) {
      emit('pick', event.latlng.lat, event.latlng.lng)
    }
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
      const destination: L.LatLngExpression = [focus.latitude, focus.longitude]
      if (chinaDataBounds.contains(destination)) {
        map.flyTo(destination, focus.zoom, { duration: 0.8 })
      } else {
        map.flyTo(chinaCenter, 4, { duration: 0.8 })
      }
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
