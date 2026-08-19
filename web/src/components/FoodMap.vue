<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import L from 'leaflet'

import type { Food } from '../types'
import 'leaflet/dist/leaflet.css'

const props = defineProps<{
  foods: Food[]
}>()

const emit = defineEmits<{
  pick: [latitude: number, longitude: number]
}>()

const { locale, t } = useI18n()

const mapElement = ref<HTMLElement>()
let map: L.Map | undefined
let markerLayer: L.LayerGroup | undefined

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  const layer = markerLayer
  // 筛选或切换语言后重建图层，避免残留旧标记和旧语言弹窗。
  layer.clearLayers()
  const bounds: L.LatLngExpression[] = []

  props.foods.forEach((food) => {
    if (food.latitude == null || food.longitude == null) {
      return
    }

    const position: L.LatLngExpression = [food.latitude, food.longitude]
    bounds.push(position)

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

  if (bounds.length > 0) {
    map.fitBounds(L.latLngBounds(bounds), {
      padding: [50, 50],
      maxZoom: 6,
    })
  }
}

onMounted(() => {
  // Leaflet 必须在真实 DOM 挂载后创建，否则无法正确计算地图尺寸。
  map = L.map(mapElement.value!, {
    center: [35.5, 104.2],
    zoom: 4,
    zoomControl: true,
  })

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap contributors',
    maxZoom: 18,
  }).addTo(map)

  markerLayer = L.layerGroup().addTo(map)
  map.on('click', (event: L.LeafletMouseEvent) => {
    emit('pick', event.latlng.lat, event.latlng.lng)
  })

  renderMarkers()
})

watch([() => props.foods, locale], renderMarkers, { deep: true })

onBeforeUnmount(() => {
  // 主动释放地图事件和 DOM 引用，避免路由往返时重复初始化。
  map?.remove()
})
</script>

<template>
  <div ref="mapElement" class="food-map"></div>
</template>
