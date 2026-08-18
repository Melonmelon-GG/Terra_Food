<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'

import type { Food } from '../types'
import 'leaflet/dist/leaflet.css'

const props = defineProps<{
  foods: Food[]
}>()

const emit = defineEmits<{
  pick: [latitude: number, longitude: number]
}>()

const mapElement = ref<HTMLElement>()
let map: L.Map | undefined
let markerLayer: L.LayerGroup | undefined

function renderMarkers() {
  if (!map || !markerLayer) {
    return
  }

  const layer = markerLayer
  layer.clearLayers()
  const bounds: L.LatLngExpression[] = []

  props.foods.forEach((food) => {
    if (food.latitude == null || food.longitude == null) {
      return
    }

    const position: L.LatLngExpression = [food.latitude, food.longitude]
    bounds.push(position)

    L.marker(position)
      .bindPopup(`
        <div class="map-popup">
          <strong>${food.name}</strong>
          <span>${food.region.province} · ${food.region.name}</span>
          <p>${food.summary}</p>
          <a href="/foods/${food.id}">查看珍馐志 →</a>
        </div>
      `)
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

watch(() => props.foods, renderMarkers, { deep: true })

onBeforeUnmount(() => {
  map?.remove()
})
</script>

<template>
  <div ref="mapElement" class="food-map"></div>
</template>
