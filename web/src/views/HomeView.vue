<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { ensureMapRegion, getFoodCatalog, getFoodMarkers, getRegions, reverseMapLocation } from '../api'
import { useAuth } from '../auth'
import FoodMap from '../components/FoodMap.vue'
import FoodUploadModal from '../components/FoodUploadModal.vue'
import RegionDrawer from '../components/RegionDrawer.vue'
import type { Food, FoodMarker, MapBounds, MapCoordinate, MapFocus, Region } from '../types'

const foods = ref<Food[]>([])
const markerFoods = ref<FoodMarker[]>([])
const regions = ref<Region[]>([])
const catalogTotal = ref(0)
const catalogPage = ref(1)
const catalogPageSize = 30
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
const geolocationLoading = ref(false)
const geolocationLocated = ref(false)
const geolocationErrorKey = ref('')
const geolocationCoordinate = ref<MapCoordinate>()
const pickHint = ref('')
const mapBounds = ref<MapBounds>()
const activeFoodId = ref<number>()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()

const regionToggleLabel = computed(() => {
  const selectedRegion = regions.value.find((region) => region.id === selectedRegionId.value)
  return selectedRegion
    ? t('home.regionPath', { province: selectedRegion.province, city: selectedRegion.name })
    : t('home.allRegionsPath', { count: regions.value.length })
})

const catalogFoods = computed(() => foods.value)
const catalogPages = computed(() => Math.max(1, Math.ceil(catalogTotal.value / catalogPageSize)))
const canPrevCatalog = computed(() => catalogPage.value > 1)
const canNextCatalog = computed(() => catalogPage.value < catalogPages.value)
const activeFood = computed(() =>
  foods.value.find((food) => food.id === activeFoodId.value) ?? catalogFoods.value[0],
)
const displayedLocation = computed<MapCoordinate | undefined>(() => {
  if (pickedLatitude.value != null && pickedLongitude.value != null) {
    return { latitude: pickedLatitude.value, longitude: pickedLongitude.value }
  }
  return geolocationCoordinate.value
})

let catalogRequestSequence = 0
let markerRequestSequence = 0

async function loadCatalog(targetPage?: number) {
  const requestSequence = ++catalogRequestSequence
  const page = Math.max(1, targetPage ?? catalogPage.value)
  error.value = ''
  // 无数据时才显示整页 loading；已有数据时保留列表，仅顶部轻量提示。
  if (foods.value.length === 0) {
    loading.value = true
  }

  try {
    const next = await getFoodCatalog({
      keyword: keyword.value || undefined,
      regionId: selectedRegionId.value,
      page,
      pageSize: catalogPageSize,
    })
    if (requestSequence !== catalogRequestSequence) {
      return
    }
    foods.value = next.items
    catalogTotal.value = next.total
    catalogPage.value = next.page
  } catch {
    if (requestSequence === catalogRequestSequence) {
      error.value = t('home.loadError')
    }
  } finally {
    if (requestSequence === catalogRequestSequence) {
      loading.value = false
    }
  }
}

async function loadMarkers() {
  const requestSequence = ++markerRequestSequence
  try {
    const next = await getFoodMarkers({
      keyword: keyword.value || undefined,
      regionId: selectedRegionId.value,
      ...mapBounds.value,
    })
    if (requestSequence === markerRequestSequence) {
      markerFoods.value = next
    }
  } catch {
    // 地图标记刷新失败时保留旧图钉，不打断浏览（目录加载失败已有独立提示）。
  }
}

function changeCatalogPage(direction: -1 | 1) {
  const target = catalogPage.value + direction
  if (target < 1 || target > catalogPages.value) {
    return
  }
  void loadCatalog(target)
}

let boundsLoadTimer: ReturnType<typeof setTimeout> | undefined

// 只记录“真正触发过标记刷新”的视口，用于位移阈值节流：
// 地图拖动时视口只挪动几个像素（缩放抖动/惯性回弹）不值得重新拉取标记数据。
let lastMarkerView: { bounds: MapBounds, zoom: number } | undefined

function centerOf(bounds: MapBounds) {
  return {
    latitude: (bounds.minLatitude + bounds.maxLatitude) / 2,
    longitude: (bounds.minLongitude + bounds.maxLongitude) / 2,
  }
}

function movedEnough(previous: MapBounds, current: MapBounds) {
  const pan = centerOf(previous)
  const center = centerOf(current)
  const spanLatitude = previous.maxLatitude - previous.minLatitude
  const spanLongitude = previous.maxLongitude - previous.minLongitude
  const longitudeDelta = Math.abs(center.longitude - pan.longitude)
  const wrappedDelta = Math.min(longitudeDelta, 360 - longitudeDelta)
  return (
    Math.abs(center.latitude - pan.latitude) > spanLatitude * 0.08
    || wrappedDelta > spanLongitude * 0.08
  )
}

function updateMapBounds(bounds: MapBounds, zoom: number) {
  // 关键词搜索是全局结果，不随地图视口过滤，拖动地图时跳过刷新。
  if (keyword.value.trim()) return
  // 全国视野（zoom ≤ 4）数据全集不变，拖动不刷新（初始加载已覆盖）。
  if (zoom <= 4) return
  if (lastMarkerView) {
    const zoomChanged = lastMarkerView.zoom !== zoom
    if (!zoomChanged && !movedEnough(lastMarkerView.bounds, bounds)) return
  }
  lastMarkerView = { bounds, zoom }
  mapBounds.value = bounds
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  boundsLoadTimer = setTimeout(() => void loadMarkers(), 250)
}

function submitSearch() {
  mapBounds.value = undefined
  const keywordActive = keyword.value.trim().length > 0
  void loadCatalog(1)
  // 关键词搜索让地图标记随搜索重置为全局命中集合。
  if (keywordActive) void loadMarkers()
}

onBeforeUnmount(() => {
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  geolocationSequence += 1
  locationLookupController?.abort()
})

async function chooseRegion(regionId?: number) {
  selectedRegionId.value = regionId
  lastMarkerView = undefined
  mapBounds.value = undefined

  const region = regions.value.find((item) => item.id === regionId)
  if (!region) {
    mapFocus.value = { latitude: 35.5, longitude: 104.2, zoom: 4 }
    await loadCatalog(1)
    // 重置为全国视野后标记集合需要与目录一致（bounds 已清空，直接全量拉取）。
    await loadMarkers()
    return
  }

  if (region.centerLatitude != null && region.centerLongitude != null) {
    mapFocus.value = { latitude: region.centerLatitude, longitude: region.centerLongitude, zoom: 9 }
  }

  await loadCatalog(1)

  if (region.centerLatitude == null || region.centerLongitude == null) {
    const locatedFoods = markerFoods.value.filter((food) => food.latitude != null && food.longitude != null)
    mapFocus.value = locatedFoods.length
      ? {
          latitude: locatedFoods.reduce((sum, food) => sum + food.latitude, 0) / locatedFoods.length,
          longitude: locatedFoods.reduce((sum, food) => sum + food.longitude, 0) / locatedFoods.length,
          zoom: 9,
        }
      : { latitude: 35.5, longitude: 104.2, zoom: 4 }
  }

  // 地图飞到目标城市后 moveend 会按新视口再收窄一次标记；这里先无界拉取该地区的全量标记，
  // 避免飞行动画期间地图上没有任何图钉。
  await loadMarkers()
}

let locationLookupSequence = 0
let locationLookupController: AbortController | undefined
let geolocationSequence = 0

function geolocationErrorMessage(error: GeolocationPositionError) {
  if (error.code === error.PERMISSION_DENIED) return 'home.geolocationDenied'
  if (error.code === error.TIMEOUT) return 'home.geolocationTimeout'
  return 'home.geolocationUnavailable'
}

function locateUser() {
  const sequence = ++geolocationSequence
  geolocationLocated.value = false
  geolocationErrorKey.value = ''
  geolocationCoordinate.value = undefined

  if (!window.isSecureContext) {
    geolocationErrorKey.value = 'home.geolocationInsecure'
    return
  }
  if (!navigator.geolocation) {
    geolocationErrorKey.value = 'home.geolocationUnsupported'
    return
  }

  geolocationLoading.value = true
  navigator.geolocation.getCurrentPosition(
    (position) => {
      if (sequence !== geolocationSequence) return
      geolocationLoading.value = false

      const { latitude, longitude, accuracy } = position.coords
      // GPS 坐标只用于本机地图聚焦；用户点击地图确认后才进入现有地址反查流程。
      if (latitude < 18 || latitude > 54 || longitude < 73 || longitude > 135.2) {
        geolocationErrorKey.value = 'home.geolocationOutside'
        return
      }

      const zoom = accuracy <= 100 ? 16 : accuracy <= 1000 ? 14 : 12
      mapFocus.value = { latitude, longitude, zoom }
      geolocationCoordinate.value = { latitude, longitude }
      geolocationLocated.value = true
    },
    (error) => {
      if (sequence !== geolocationSequence) return
      geolocationLoading.value = false
      geolocationErrorKey.value = geolocationErrorMessage(error)
    },
    {
      enableHighAccuracy: true,
      timeout: 12_000,
      maximumAge: 60_000,
    },
  )
}

function handleMapPick(latitude: number, longitude: number) {
  // 用户手动确认优先于仍在等待中的 GPS 结果，避免稍后回调覆盖当前视角。
  geolocationSequence += 1
  geolocationLoading.value = false
  geolocationLocated.value = false
  geolocationErrorKey.value = ''
  geolocationCoordinate.value = undefined
  void pickLocation(latitude, longitude)
}

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
      // 地图点击只准备上传所需的地区信息，不改变当前目录筛选。
      // 浏览地区只能通过地区抽屉显式切换，避免点击无菜区域把目录筛空。
      const existingRegion = regions.value.find((region) =>
        region.province === resolvedLocation.province && region.name === resolvedLocation.city,
      )
      pickedRegionId.value = existingRegion?.id
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
    catalogTotal.value += 1
  }
}

function focusFood(food: Food) {
  activeFoodId.value = food.id
  mapFocus.value = { latitude: food.latitude, longitude: food.longitude, zoom: 12 }
}

// 回到地图默认视角：清空选中与选点状态，地图回全国范围（保留地区筛选与列表结果）。
function resetMapView() {
  geolocationSequence += 1
  geolocationLoading.value = false
  geolocationLocated.value = false
  geolocationErrorKey.value = ''
  geolocationCoordinate.value = undefined
  activeFoodId.value = undefined
  pickedLatitude.value = undefined
  pickedLongitude.value = undefined
  pickedRegionId.value = undefined
  pickedProvince.value = ''
  pickedCity.value = ''
  pickedAddress.value = ''
  pickHint.value = ''
  locationError.value = ''
  lastMarkerView = undefined
  mapBounds.value = undefined
  mapFocus.value = { latitude: 35.5, longitude: 104.2, zoom: 4 }
  // 全国视野下 moveend 会被节流跳过，这里显式重拉标记，让图钉与目录范围一致。
  void loadMarkers()
}

// 顶部菜单"珍馐图鉴"在首页点击时通过 ?map=reset 触发回到地图视角，避免同路由死链。
watch(
  () => route.query.map,
  (marker) => {
    if (marker === 'reset') {
      resetMapView()
      void router.replace({ path: '/', query: {} })
    }
  },
)

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
  // 首次进入首页即请求浏览器位置权限；失败时保留完整的手动选点流程。
  locateUser()
  try {
    regions.value = await getRegions()
  } catch {
    error.value = t('home.regionError')
  }

  // 目录与地图标记走独立数据源并同时首载：目录受 keyword/regionId 分页驱动，
  // 标记受地图视口驱动；地图 bounds 只在后续拖动时作为标记刷新条件。
  await Promise.all([loadCatalog(), loadMarkers()])
})
</script>

<template>
  <section class="map-explorer">
    <div class="explorer-map" :aria-label="t('home.mapTitle')">
      <FoodMap
        :foods="markerFoods"
        :focus="mapFocus"
        :picked-location="displayedLocation"
        @pick="handleMapPick"
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
        <strong>{{ t('home.recordCount', { count: catalogTotal }) }}</strong>
      </div>
    </aside>

    <div class="explorer-map-hint" role="status">
      <span v-if="geolocationLoading">{{ t('home.geolocationLoading') }}</span>
      <span v-else-if="locationResolving">{{ t('home.mapRegionLoading') }}</span>
      <span v-else-if="geolocationErrorKey" class="error">
        {{ t(geolocationErrorKey) }}
        <button type="button" @click="locateUser">{{ t('home.retryLocation') }}</button>
      </span>
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
      <span v-else-if="geolocationLocated">{{ t('home.geolocationReady') }}</span>
      <span v-else>{{ t('home.mapHint') }}</span>
    </div>

    <section class="explorer-catalog explorer-panel" :aria-busy="loading">
      <header class="explorer-catalog-heading">
        <small>{{ t('home.catalogEyebrow') }}</small>
        <div class="explorer-catalog-meta">
          <span>{{ t('home.recordCount', { count: catalogTotal }) }}</span>
          <span v-if="catalogPages > 1" class="explorer-catalog-pager">
            <button
              type="button"
              :disabled="!canPrevCatalog"
              :aria-label="t('home.prevPage')"
              @click="changeCatalogPage(-1)"
            >‹</button>
            <b>{{ catalogPage }} / {{ catalogPages }}</b>
            <button
              type="button"
              :disabled="!canNextCatalog"
              :aria-label="t('home.nextPage')"
              @click="changeCatalogPage(1)"
            >›</button>
          </span>
          <button class="explorer-map-view" type="button" @click="resetMapView">
            {{ t('home.mapView') }}
          </button>
        </div>
      </header>

      <p v-if="loading && foods.length" class="explorer-state">{{ t('home.refreshing') }}</p>
      <p v-if="error" class="explorer-state error">{{ error }}</p>
      <p v-if="!foods.length && loading" class="explorer-state">{{ t('home.loading') }}</p>
      <p v-else-if="!foods.length" class="explorer-state">{{ t('home.empty') }}</p>

      <div v-else class="explorer-cards">
        <article
          v-for="food in catalogFoods"
          :key="food.id"
          class="explorer-card"
          :class="{ 'is-active': activeFoodId === food.id }"
        >
          <button
            class="explorer-card-focus"
            type="button"
            :aria-label="t('home.focusFood', { name: food.name })"
            @click="focusFood(food)"
          ></button>
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
              <RouterLink
                :to="'/foods/' + food.id"
                class="explorer-card-link"
                @click.stop
              >
                {{ t('home.readMore') }}
              </RouterLink>
            </div>
          </div>
        </article>
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
