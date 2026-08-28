<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import type { Region } from '../types'

const props = withDefaults(defineProps<{
  open: boolean
  regions: Region[]
  modelValue?: number
  allowNationwide?: boolean
}>(), {
  allowNationwide: false,
})

const emit = defineEmits<{
  close: []
  select: [regionId?: number]
}>()

const { t } = useI18n()
const activeProvince = ref('')
const searchKeyword = ref('')

const normalizedKeyword = computed(() => searchKeyword.value.trim().toLowerCase())
const selectedRegion = computed(() => props.regions.find((region) => region.id === props.modelValue))
const provinces = computed(() => {
  const keyword = normalizedKeyword.value
  return [...new Set(props.regions.map((region) => region.province))]
    .filter((province) => (
      !keyword
      || province.toLowerCase().includes(keyword)
      || props.regions.some((region) => region.province === province && region.name.toLowerCase().includes(keyword))
    ))
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
})
const visibleCities = computed(() => {
  const keyword = normalizedKeyword.value
  if (!activeProvince.value) return []
  return props.regions
    .filter((region) => (
      region.province === activeProvince.value
      && (!keyword || region.name.toLowerCase().includes(keyword))
    ))
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
})
// 搜索时左侧未选省份，直接平铺所有命中地区，避免两级面板挡住搜索结果。
const searchResults = computed(() => {
  const keyword = normalizedKeyword.value
  if (!keyword) return []
  return props.regions
    .filter((region) => (
      region.name.toLowerCase().includes(keyword)
      || region.province.toLowerCase().includes(keyword)
    ))
    .sort((left, right) => `${left.province}${left.name}`.localeCompare(`${right.province}${right.name}`, 'zh-CN'))
})

watch(
  () => props.open,
  (open) => {
    if (open) {
      activeProvince.value = selectedRegion.value?.province || ''
      searchKeyword.value = ''
    }
  },
  { immediate: true },
)

function chooseNationwide() {
  if (props.allowNationwide) {
    emit('select')
    emit('close')
    return
  }
  activeProvince.value = ''
}

function chooseRegion(region: Region) {
  emit('select', region.id)
  emit('close')
}

function handleKeydown(event: KeyboardEvent) {
  if (props.open && event.key === 'Escape') {
    emit('close')
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="region-drawer-mask" @click.self="emit('close')">
      <aside class="region-drawer" role="dialog" aria-modal="true" :aria-label="t('regionPicker.title')">
        <header class="region-drawer-header">
          <div>
            <small>{{ t('regionPicker.eyebrow') }}</small>
            <h2>{{ t('regionPicker.title') }}</h2>
          </div>
          <button class="region-drawer-close" type="button" :aria-label="t('regionPicker.close')" @click="emit('close')">×</button>
        </header>

        <nav class="region-breadcrumb" :aria-label="t('regionPicker.path')">
          <button type="button" @click="chooseNationwide">{{ t('regionPicker.nationwide') }}</button>
          <template v-if="activeProvince">
            <span>/</span>
            <button type="button" @click="activeProvince = activeProvince">{{ activeProvince }}</button>
          </template>
          <template v-if="selectedRegion && selectedRegion.province === activeProvince">
            <span>/</span>
            <strong>{{ selectedRegion.name }}</strong>
          </template>
        </nav>

        <div class="region-search">
          <input
            v-model="searchKeyword"
            type="search"
            :placeholder="t('regionPicker.searchPlaceholder')"
            :aria-label="t('regionPicker.searchPlaceholder')"
          >
        </div>

        <div class="region-drawer-body">
          <section class="province-panel">
            <div class="region-panel-heading">
              <span>{{ t('regionPicker.province') }}</span>
              <small>{{ t('regionPicker.provinceCount', { count: provinces.length }) }}</small>
            </div>
            <p v-if="searchKeyword && !provinces.length" class="region-search-empty">
              {{ t('regionPicker.searchEmpty') }}
            </p>
            <button
              v-for="province in provinces"
              :key="province"
              type="button"
              :class="{ active: activeProvince === province }"
              @click="activeProvince = province"
            >
              <span>{{ province }}</span>
              <small>{{ regions.filter((region) => region.province === province).length }}</small>
              <b>›</b>
            </button>
          </section>

          <section class="city-panel">
            <div v-if="activeProvince" class="region-panel-heading">
              <span>{{ activeProvince }}</span>
              <small>{{ t('regionPicker.cityCount', { count: visibleCities.length }) }}</small>
            </div>
            <div v-if="activeProvince" class="city-grid">
              <button
                v-for="region in visibleCities"
                :key="region.id"
                type="button"
                :class="{ active: modelValue === region.id }"
                @click="chooseRegion(region)"
              >
                <span>{{ region.name }}</span>
                <small>{{ t('regionPicker.choose') }}</small>
              </button>
            </div>
            <div v-else-if="searchKeyword" class="city-grid search-result-grid">
              <button
                v-for="region in searchResults"
                :key="region.id"
                type="button"
                :class="{ active: modelValue === region.id }"
                @click="chooseRegion(region)"
              >
                <span>{{ region.province }} · {{ region.name }}</span>
                <small>{{ t('regionPicker.choose') }}</small>
              </button>
            </div>
            <div v-else class="region-empty-guide">
              <span>州</span>
              <p>{{ t('regionPicker.provinceHint') }}</p>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </Teleport>
</template>
