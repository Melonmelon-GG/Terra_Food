<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  layerOne: string[]
  editable?: boolean
}>(), { editable: false })

const emit = defineEmits<{ paint: [index: number] }>()
const rows = [8, 9, 10, 11, 12, 13, 14, 15, 14, 13, 12, 11, 10, 9, 8]
const radius = 9.5
const cellWidth = Math.sqrt(3) * radius
const cells = computed(() => {
  let index = 0
  return rows.flatMap((count, row) => {
    const y = 50 + row * radius * 1.5
    const startX = 150 - ((count - 1) * cellWidth) / 2
    return Array.from({ length: count }, (_, column) => ({ index: index++, x: startX + column * cellWidth, y }))
  })
})

function points(x: number, y: number) {
  return Array.from({ length: 6 }, (_, i) => {
    const angle = (60 * i - 30) * Math.PI / 180
    return `${x + radius * Math.cos(angle)},${y + radius * Math.sin(angle)}`
  }).join(' ')
}

function paint(index: number, event?: PointerEvent) {
  if (!props.editable || (event && event.type === 'pointerenter' && event.buttons !== 1)) return
  emit('paint', index)
}
</script>

<template>
  <svg class="hex-etching" viewBox="0 0 300 300" role="img">
    <!-- 原蜂窝阵列是左右尖角；整体旋转 30° 后，蚀刻章外轮廓尖角朝上。 -->
    <g transform="rotate(30 150 150)">
      <g>
        <polygon v-for="cell in cells" :key="`one-${cell.index}`" :points="points(cell.x, cell.y)"
          :fill="layerOne[cell.index] || '#f4ead9'" class="etching-bean base" />
      </g>
      <g>
        <polygon v-for="cell in cells" :key="`grid-${cell.index}`" :points="points(cell.x, cell.y)"
          fill="transparent" class="etching-grid-cell" :class="{ editable }"
          @pointerdown.prevent="paint(cell.index)" @pointerenter="paint(cell.index, $event)" />
      </g>
    </g>
  </svg>
</template>

<style scoped>
.hex-etching { display: block; width: 100%; max-width: 300px; touch-action: none; user-select: none; }
.etching-bean { stroke: #fff8ed; stroke-width: 1.6; }
.etching-grid-cell { stroke: #8e735f55; stroke-width: 1; }
.etching-grid-cell.editable { cursor: crosshair; }
</style>
