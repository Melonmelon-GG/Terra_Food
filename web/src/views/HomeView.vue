<script setup lang="ts">
import axios from 'axios'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { getFoods, getRegions, resolveMapRegion } from '../api'
import { useAuth } from '../auth'
import FoodMap from '../components/FoodMap.vue'
import FoodUploadModal from '../components/FoodUploadModal.vue'
import RegionDrawer from '../components/RegionDrawer.vue'
import type { Food, MapBounds, MapFocus, Region } from '../types'

const foods = ref<Food[]>([])
const regions = ref<Region[]>([])
const keyword = ref('')
const selectedRegionId = ref<number>()
const loading = ref(true)
const error = ref('')
const uploadOpen = ref(false)
const regionDrawerOpen = ref(false)
const mapFocus = ref<MapFocus>()
const pickedLatitude = ref<number>()
const pickedLongitude = ref<number>()
const pickedRegionId = ref<number>()
const pickedAddress = ref('')
const locationError = ref('')
const locationResolving = ref(false)
const pickHint = ref('')
const mapBounds = ref<MapBounds>()
const { t } = useI18n()
const router = useRouter()
const auth = useAuth()

const regionToggleLabel = computed(() => {
  const selectedRegion = regions.value.find((region) => region.id === selectedRegionId.value)
  return selectedRegion
    ? t('home.regionPath', { province: selectedRegion.province, city: selectedRegion.name })
    : t('home.allRegionsPath', { count: regions.value.length })
})

let foodRequestSequence = 0

async function loadFoods() {
  const requestSequence = ++foodRequestSequence
  error.value = ''
  // 无数据时才显示整页 loading；已有旧数据时保留列表，仅顶部轻量提示，
  // 避免地图拖动导致下方目录整块卸载、页面布局与滚动位置抖动。
  if (foods.value.length === 0) {
    loading.value = true
  }

  try {
    const nextFoods = await getFoods({
      keyword: keyword.value || undefined,
      regionId: selectedRegionId.value,
      ...mapBounds.value,
    })
    if (requestSequence !== foodRequestSequence) {
      return
    }
    foods.value = nextFoods
  } catch {
    if (requestSequence === foodRequestSequence) {
      error.value = t('home.loadError')
    }
  } finally {
    if (requestSequence === foodRequestSequence) {
      loading.value = false
    }
  }
}

let boundsLoadTimer: ReturnType<typeof setTimeout> | undefined

function updateMapBounds(bounds: MapBounds) {
  // 关键词搜索是全局结果，不随地图视口过滤，拖动地图时跳过防抖刷新。
  if (keyword.value.trim()) return
  mapBounds.value = bounds
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  boundsLoadTimer = setTimeout(() => void loadFoods(), 250)
}

function submitSearch() {
  mapBounds.value = undefined
  void loadFoods()
}

onBeforeUnmount(() => {
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  locationLookupController?.abort()
})

async function chooseRegion(regionId?: number) {
  selectedRegionId.value = regionId
  mapBounds.value = undefined

  const region = regions.value.find((item) => item.id === regionId)
  if (!region) {
    mapFocus.value = { latitude: 35.5, longitude: 104.2, zoom: 4 }
    await loadFoods()
    return
  }

  if (region.centerLatitude != null && region.centerLongitude != null) {
    mapFocus.value = { latitude: region.centerLatitude, longitude: region.centerLongitude, zoom: 9 }
  }

  await loadFoods()

  if (region.centerLatitude == null || region.centerLongitude == null) {
    const locatedFoods = foods.value.filter((food) => food.latitude != null && food.longitude != null)
    mapFocus.value = locatedFoods.length
      ? {
          latitude: locatedFoods.reduce((sum, food) => sum + food.latitude, 0) / locatedFoods.length,
          longitude: locatedFoods.reduce((sum, food) => sum + food.longitude, 0) / locatedFoods.length,
          zoom: 9,
        }
      : { latitude: 35.5, longitude: 104.2, zoom: 4 }
  }
}

let locationLookupSequence = 0
let locationLookupController: AbortController | undefined

async function pickLocation(latitude: number, longitude: number) {
  pickedLatitude.value = latitude
  pickedLongitude.value = longitude
  pickHint.value = ''
  pickedAddress.value = ''
  locationError.value = ''
  locationResolving.value = true
  locationLookupController?.abort()
  locationLookupController = new AbortController()

  const lookupSequence = ++locationLookupSequence
  try {
    const resolvedLocation = await resolveMapRegion(latitude, longitude, locationLookupController.signal)
    if (lookupSequence === locationLookupSequence) {
      pickedRegionId.value = resolvedLocation.region.id
      pickedAddress.value = resolvedLocation.address
      // 地图↔地域寻味联动：选点成功后，上方的地区选择器同步到该地区并刷新目录。
      if (selectedRegionId.value !== resolvedLocation.region.id) {
        selectedRegionId.value = resolvedLocation.region.id
        await loadFoods()
      }
    }
  } catch (requestError) {
    if (lookupSequence === locationLookupSequence) {
      pickedRegionId.value = undefined
      // 地区数据只来自服务器白名单；点选未收录地区时给出明确提示，不再动态加入地区列表。
      const message = axios.isAxiosError(requestError)
        ? requestError.response?.data?.message
        : undefined
      if (message && message.includes('尚未收录')) {
        pickHint.value = t('home.regionNotEnrolled')
      } else {
        locationError.value = t('home.mapRegionError')
      }
    }
  } finally {
    if (lookupSequence === locationLookupSequence) {
      locationResolving.value = false
    }
  }
}

function handleSaved(food: Food) {
  uploadOpen.value = false
  if (food.reviewStatus === 'APPROVED') {
    foods.value = [food, ...foods.value]
  }
}

async function openUpload() {
  if (!auth.currentUser.value) {
    await router.push({ path: '/login', query: { redirect: '/' } })
    return
  }

  // 必须先在地图上选点，避免带着默认坐标创建菜品。
  if (pickedLatitude.value == null || pickedLongitude.value == null) {
    pickHint.value = t('home.pickCoordinateFirst')
    return
  }

  pickHint.value = ''
  uploadOpen.value = true
}

onMounted(async () => {
  try {
    regions.value = await getRegions()
  } catch {
    error.value = t('home.regionError')
  }

  // 独立首载目录数据，不依赖地图初始化事件；地图 bounds 只作为后续增量刷新，
  // 避免地图初始化异常时下方列表一直停留在 loading。
  await loadFoods()
})
</script>

<template>
  <section class="hero">
    <p class="eyebrow">{{ t('home.eyebrow') }}</p>
    <h1>{{ t('home.heroTitle') }}<em>{{ t('home.heroEmphasis') }}</em></h1>
    <p>
      {{ t('home.heroDescription') }}
    </p>
    <form @submit.prevent="submitSearch">
      <input v-model="keyword" :placeholder="t('home.searchPlaceholder')">
      <button>{{ t('home.search') }}</button>
    </form>
  </section>

  <section class="content">
    <div class="section-title">
      <div>
        <small>{{ t('home.regionEyebrow') }}</small>
        <h2>{{ t('home.regionTitle') }}</h2>
      </div>
      <div class="section-actions">
        <p>{{ t('home.regionDescription') }}</p>
        <button
          class="outline-action region-toggle"
          type="button"
          :aria-expanded="regionDrawerOpen"
          @click="regionDrawerOpen = true"
        >
          {{ regionToggleLabel }}
        </button>
        <button class="outline-action" :disabled="locationResolving" @click="openUpload">
          {{ t('home.addFood') }}
        </button>
        <p v-if="pickHint" class="pick-hint">{{ pickHint }}</p>
      </div>
    </div>

     <section class="map-journal">
      <div class="map-journal-title">
        <div>
          <small>{{ t('home.mapEyebrow') }}</small>
          <h2>{{ t('home.mapTitle') }}</h2>
        </div>
        <p>{{ t('home.mapDescription') }}</p>
      </div>

      <div class="map-frame">
        <FoodMap :foods="foods" :focus="mapFocus" @pick="pickLocation" @bounds-change="updateMapBounds" />
        <div class="map-vignette"></div>
        <div class="map-hint">
          <span v-if="locationResolving">{{ t('home.mapRegionLoading') }}</span>
          <span v-else-if="locationError" class="error">{{ locationError }}</span>
          <span v-else-if="pickedLatitude !== undefined">
            {{ pickedAddress
              ? t('home.mapPickedAddress', {
                  address: pickedAddress,
                  latitude: pickedLatitude.toFixed(3),
                  longitude: pickedLongitude?.toFixed(3),
                })
              : t('home.mapPicked', {
                  latitude: pickedLatitude.toFixed(3),
                  longitude: pickedLongitude?.toFixed(3),
                })
            }}
          </span>
          <span v-else>{{ t('home.mapHint') }}</span>
        </div>
      </div>
    </section>

    <div class="catalog-heading">
      <small>{{ t('home.catalogEyebrow') }}</small>
      <span>{{ t('home.recordCount', { count: foods.length }) }}</span>
    </div>

    <!-- 目录常驻渲染：地图拖动刷新只更新容器内部，不影响页面整体布局与滚动位置 -->
    <div class="catalog-scroll" :aria-busy="loading">
      <p v-if="loading && foods.length" class="state catalog-refreshing">{{ t('home.refreshing') }}</p>
      <p v-if="error" class="state error">{{ error }}</p>

      <p v-if="!foods.length && loading" class="state">{{ t('home.loading') }}</p>
      <p v-else-if="!foods.length" class="state">{{ t('home.empty') }}</p>

      <div v-else class="grid">
        <RouterLink
          v-for="food in foods"
          :key="food.id"
          :to="`/foods/${food.id}`"
          class="card"
        >
          <div
            class="photo"
            :class="{ 'no-cover': !food.imageUrl }"
            :style="{ backgroundImage: food.imageUrl ? `url(${food.imageUrl})` : undefined }"
          >
            <span>{{ food.region.province }} · {{ food.region.name }}</span>
          </div>
          <div class="card-body">
            <h3>{{ food.name }}</h3>
            <p>{{ food.summary }}</p>
            <div>
              <b>{{ t('home.heat', { value: food.heat }) }}</b>
              <span>{{ t('home.readMore') }}</span>
            </div>
          </div>
        </RouterLink>
      </div>
    </div>
  </section>

  <FoodUploadModal
    v-if="uploadOpen"
    :regions="regions"
    :latitude="pickedLatitude"
    :longitude="pickedLongitude"
    :region-id="pickedRegionId"
    :address="pickedAddress"
    @close="uploadOpen = false"
    @saved="handleSaved"
  />

  <RegionDrawer
    :open="regionDrawerOpen"
    :regions="regions"
    :model-value="selectedRegionId"
    allow-nationwide
    @close="regionDrawerOpen = false"
    @select="chooseRegion"
  />
</template>
