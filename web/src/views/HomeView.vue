<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { ensureMapRegion, getFoods, getRegions, reverseMapLocation } from '../api'
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
const pickedProvince = ref('')
const pickedCity = ref('')
const pickedAddress = ref('')
const locationError = ref('')
const locationResolving = ref(false)
const pickHint = ref('')
const mapBounds = ref<MapBounds>()
const activeFoodId = ref<number>()
const { t } = useI18n()
const router = useRouter()
const auth = useAuth()

const regionToggleLabel = computed(() => {
  const selectedRegion = regions.value.find((region) => region.id === selectedRegionId.value)
  return selectedRegion
    ? t('home.regionPath', { province: selectedRegion.province, city: selectedRegion.name })
    : t('home.allRegionsPath', { count: regions.value.length })
})

const catalogFoods = computed(() => foods.value)
const activeFood = computed(() =>
  foods.value.find((food) => food.id === activeFoodId.value) ?? catalogFoods.value[0],
)

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
  pickedProvince.value = ''
  pickedCity.value = ''
  pickedRegionId.value = undefined
  locationError.value = ''
  locationResolving.value = true
  locationLookupController?.abort()
  locationLookupController = new AbortController()

  const lookupSequence = ++locationLookupSequence
  try {
    const resolvedLocation = await reverseMapLocation(
      latitude,
      longitude,
      locationLookupController.signal,
    )
    if (lookupSequence === locationLookupSequence) {
      pickedProvince.value = resolvedLocation.province
      pickedCity.value = resolvedLocation.city
      pickedAddress.value = resolvedLocation.address
      // 新城市也能精准显示；已经收录的城市继续联动现有目录。
      const existingRegion = regions.value.find((region) =>
        region.province === resolvedLocation.province && region.name === resolvedLocation.city,
      )
      pickedRegionId.value = existingRegion?.id
      if (selectedRegionId.value !== existingRegion?.id) {
        selectedRegionId.value = existingRegion?.id
        await loadFoods()
      }
    }
  } catch (requestError) {
    if (lookupSequence === locationLookupSequence) {
      pickedRegionId.value = undefined
      locationError.value = t('home.mapRegionError')
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

function focusFood(food: Food) {
  activeFoodId.value = food.id
  mapFocus.value = { latitude: food.latitude, longitude: food.longitude, zoom: 12 }
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

  if (!pickedRegionId.value) {
    if (!pickedProvince.value || !pickedCity.value) {
      pickHint.value = t('home.mapRegionError')
      return
    }
    locationResolving.value = true
    try {
      const region = await ensureMapRegion(pickedProvince.value, pickedCity.value)
      if (!regions.value.some((item) => item.id === region.id)) {
        regions.value = [...regions.value, region]
      }
      pickedRegionId.value = region.id
      selectedRegionId.value = region.id
    } catch {
      pickHint.value = t('home.mapRegionError')
      return
    } finally {
      locationResolving.value = false
    }
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
  <section class="map-explorer">
    <div class="explorer-map" :aria-label="t('home.mapTitle')">
      <FoodMap
        :foods="foods"
        :focus="mapFocus"
        @pick="pickLocation"
        @bounds-change="updateMapBounds"
      />
    </div>
    <div class="explorer-map-wash"></div>

    <aside class="explorer-sidebar explorer-panel">
      <div>
        <p class="eyebrow">{{ t('home.eyebrow') }}</p>
        <h1>{{ t('home.heroTitle') }}<em>{{ t('home.heroEmphasis') }}</em></h1>
        <p class="explorer-intro">{{ t('home.heroDescription') }}</p>
      </div>

      <form class="explorer-search" @submit.prevent="submitSearch">
        <input v-model="keyword" :placeholder="t('home.searchPlaceholder')">
        <button>{{ t('home.search') }}</button>
      </form>

      <div class="explorer-sidebar-actions">
        <button
          class="explorer-outline"
          type="button"
          :aria-expanded="regionDrawerOpen"
          @click="regionDrawerOpen = true"
        >
          <span>{{ t('home.regionEyebrow') }}</span>
          <b>{{ regionToggleLabel }}</b>
        </button>
        <button
          class="explorer-outline"
          type="button"
          :disabled="locationResolving"
          @click="openUpload"
        >
          <span>{{ t('home.mapEyebrow') }}</span>
          <b>{{ t('home.addFood') }}</b>
        </button>
      </div>

      <p v-if="pickHint" class="explorer-notice">{{ pickHint }}</p>
      <div class="explorer-sidebar-foot">
        <small>{{ t('home.catalogEyebrow') }}</small>
        <strong>{{ t('home.recordCount', { count: foods.length }) }}</strong>
      </div>
    </aside>

    <div class="explorer-map-hint" role="status">
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

    <section class="explorer-catalog explorer-panel" :aria-busy="loading">
      <header class="explorer-catalog-heading">
        <small>{{ t('home.catalogEyebrow') }}</small>
        <span>{{ t('home.recordCount', { count: foods.length }) }}</span>
      </header>

      <p v-if="loading && foods.length" class="explorer-state">{{ t('home.refreshing') }}</p>
      <p v-if="error" class="explorer-state error">{{ error }}</p>
      <p v-if="!foods.length && loading" class="explorer-state">{{ t('home.loading') }}</p>
      <p v-else-if="!foods.length" class="explorer-state">{{ t('home.empty') }}</p>

      <div v-else class="explorer-cards">
        <RouterLink
          v-for="food in catalogFoods"
          :key="food.id"
          :to="'/foods/' + food.id"
          class="explorer-card"
          @mouseenter="focusFood(food)"
          @focus="focusFood(food)"
        >
          <div
            class="explorer-card-photo"
            :class="{ 'no-cover': !food.imageUrl }"
            :style="{ backgroundImage: food.imageUrl ? 'url(' + food.imageUrl + ')' : undefined }"
          >
            <span>{{ food.region.province }} · {{ food.region.name }}</span>
          </div>
          <div class="explorer-card-body">
            <h3>{{ food.name }}</h3>
            <p>{{ food.summary }}</p>
            <div>
              <b>{{ t('home.heat', { value: food.heat }) }}</b>
              <span>{{ t('home.readMore') }}</span>
            </div>
          </div>
        </RouterLink>
      </div>
    </section>

    <aside class="explorer-preview explorer-panel">
      <template v-if="activeFood">
        <div
          class="explorer-preview-photo"
          :class="{ 'no-cover': !activeFood.imageUrl }"
          :style="{ backgroundImage: activeFood.imageUrl ? 'url(' + activeFood.imageUrl + ')' : undefined }"
        ></div>
        <div class="explorer-preview-content">
          <small>{{ activeFood.region.province }} · {{ activeFood.region.name }}</small>
          <h2>{{ activeFood.name }}</h2>
          <b>{{ t('home.heat', { value: activeFood.heat }) }}</b>
          <p>{{ activeFood.summary }}</p>
          <dl>
            <div>
              <dt>{{ t('home.regionEyebrow') }}</dt>
              <dd>{{ activeFood.region.name }}</dd>
            </div>
            <div>
              <dt>{{ t('home.catalogEyebrow') }}</dt>
              <dd>{{ activeFood.creator.displayName }}</dd>
            </div>
          </dl>
          <RouterLink :to="'/foods/' + activeFood.id" class="explorer-preview-link">
            {{ t('home.readMore') }}
          </RouterLink>
        </div>
      </template>
      <div v-else class="explorer-preview-empty">
        <span class="seal">炎</span>
        <p>{{ loading ? t('home.loading') : t('home.empty') }}</p>
      </div>
    </aside>
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
