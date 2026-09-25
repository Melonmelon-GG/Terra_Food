<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getFoodCatalog, getFoodMapClusters, getRegions, reverseMapLocation } from '../api'
import { useAuth } from '../auth'
import { requestBrowserLocation, validCoordinate } from '../location'
import FoodMap from '../components/FoodMap.vue'
const FoodUploadModal = defineAsyncComponent(() => import('../components/FoodUploadModal.vue'))
import type { Food, FoodMapClusterItem, FoodSort, MapBounds, MapCoordinate, MapFocus, Region } from '../types'

const foods = ref<Food[]>([])
const markerItems = ref<FoodMapClusterItem[]>([])
const regions = ref<Region[]>([])
const catalogTotal = ref(0)
const catalogPage = ref(1)
const catalogPageSize = 30
const keyword = ref('')
const selectedRegionId = ref<number>()
const selectedTasteIds = ref<number[]>([])
const selectedIngredientIds = ref<number[]>([])
const selectedCuisineIds = ref<number[]>([])
const sort = ref<FoodSort>('RELEVANCE')
const inBounds = ref(false)
const mapTruncated = ref(false)
const loading = ref(true)
const error = ref('')
const uploadOpen = ref(false)
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
const mapZoom = ref(4)
const activeFoodId = ref<number>()
const catalogCollapsed = ref(false)
const actionsOpen = ref(false)
const actionDock = ref<HTMLElement>()
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const auth = useAuth()

function queryNumber(value: unknown): number | undefined {
  const parsed = Number(Array.isArray(value) ? value[0] : value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

function queryIds(value: unknown): number[] {
  const values = Array.isArray(value) ? value : value == null ? [] : String(value).split(',')
  return values.map(Number).filter((id) => Number.isSafeInteger(id) && id > 0).slice(0, 10)
}

keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
selectedRegionId.value = queryNumber(route.query.region)
selectedTasteIds.value = queryIds(route.query.taste)
selectedIngredientIds.value = queryIds(route.query.ingredient)
selectedCuisineIds.value = queryIds(route.query.cuisine)
sort.value = ['RELEVANCE', 'HEAT', 'NEWEST'].includes(String(route.query.sort))
  ? String(route.query.sort) as FoodSort : 'RELEVANCE'
inBounds.value = route.query.bounds === '1'
catalogPage.value = queryNumber(route.query.page) ?? 1

const selectedFilterCount = computed(() => selectedTasteIds.value.length
  + selectedIngredientIds.value.length + selectedCuisineIds.value.length + (inBounds.value ? 1 : 0))
function discoveryQuery() {
  return {
    ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
    ...(selectedRegionId.value ? { region: String(selectedRegionId.value) } : {}),
    ...(selectedTasteIds.value.length ? { taste: selectedTasteIds.value.join(',') } : {}),
    ...(selectedIngredientIds.value.length ? { ingredient: selectedIngredientIds.value.join(',') } : {}),
    ...(selectedCuisineIds.value.length ? { cuisine: selectedCuisineIds.value.join(',') } : {}),
    ...(sort.value !== 'RELEVANCE' ? { sort: sort.value } : {}),
    ...(inBounds.value ? { bounds: '1' } : {}),
  }
}

function restoreDiscoveryFromRoute() {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  selectedRegionId.value = queryNumber(route.query.region)
  selectedTasteIds.value = queryIds(route.query.taste)
  selectedIngredientIds.value = queryIds(route.query.ingredient)
  selectedCuisineIds.value = queryIds(route.query.cuisine)
  sort.value = ['RELEVANCE', 'HEAT', 'NEWEST'].includes(String(route.query.sort))
    ? String(route.query.sort) as FoodSort : 'RELEVANCE'
  inBounds.value = route.query.bounds === '1'
  catalogPage.value = queryNumber(route.query.page) ?? 1
}

function searchParams() {
  return {
    keyword: keyword.value.trim() || undefined,
    regionId: selectedRegionId.value,
    tasteIds: selectedTasteIds.value,
    ingredientIds: selectedIngredientIds.value,
    cuisineIds: selectedCuisineIds.value,
    sort: sort.value,
    inBounds: inBounds.value,
    ...(inBounds.value ? mapBounds.value : undefined),
  }
}

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
let catalogRequestController: AbortController | undefined
let markerRequestController: AbortController | undefined

async function loadCatalog(targetPage?: number) {
  const requestSequence = ++catalogRequestSequence
  catalogRequestController?.abort()
  catalogRequestController = new AbortController()
  const page = Math.max(1, targetPage ?? catalogPage.value)
  error.value = ''
  // 无数据时才显示整页 loading；已有数据时保留列表，仅顶部轻量提示。
  if (foods.value.length === 0) {
    loading.value = true
  }

  try {
    const next = await getFoodCatalog({
      ...searchParams(),
      page,
      pageSize: catalogPageSize,
      compact: true,
    }, catalogRequestController.signal)
    if (requestSequence !== catalogRequestSequence) {
      return
    }
    foods.value = next.items
    catalogTotal.value = next.total
    catalogPage.value = next.page
  } catch (cause) {
    if (requestSequence === catalogRequestSequence) {
      if (!axios.isCancel(cause)) error.value = t('home.loadError')
    }
  } finally {
    if (requestSequence === catalogRequestSequence) {
      loading.value = false
    }
  }
}

async function loadMarkers() {
  if (!mapBounds.value) return
  const requestSequence = ++markerRequestSequence
  markerRequestController?.abort()
  markerRequestController = new AbortController()
  try {
    const next = await getFoodMapClusters({
      ...searchParams(),
      ...mapBounds.value,
      inBounds: true,
      zoom: mapZoom.value,
    }, markerRequestController.signal)
    if (requestSequence === markerRequestSequence) {
      markerItems.value = next.items
      mapTruncated.value = false
    }
  } catch {
    // 地图标记刷新失败时保留旧图钉，不打断浏览（目录加载失败已有独立提示）。
  }
}

let suppressRouteReload = false

async function changeCatalogPage(direction: -1 | 1) {
  const target = catalogPage.value + direction
  if (target < 1 || target > catalogPages.value) {
    return
  }
  suppressRouteReload = true
  try {
    await router.replace({ path: '/', query: { ...discoveryQuery(), ...(target > 1 ? { page: String(target) } : {}) } })
  } finally {
    suppressRouteReload = false
  }
  await loadCatalog(target)
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
  if (lastMarkerView) {
    const zoomChanged = lastMarkerView.zoom !== zoom
    if (!zoomChanged && !movedEnough(lastMarkerView.bounds, bounds)) return
  }
  lastMarkerView = { bounds, zoom }
  mapBounds.value = bounds
  mapZoom.value = zoom
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  boundsLoadTimer = setTimeout(() => {
    void loadMarkers()
    if (inBounds.value) void loadCatalog(1)
  }, 250)
}

function submitSearch() {
  void applyDiscovery()
}

async function applyDiscovery() {
  suppressRouteReload = true
  try { await router.replace({ path: '/', query: discoveryQuery() }) }
  finally { suppressRouteReload = false }
  await Promise.all([loadCatalog(1), loadMarkers()])
}

function clearFilters() {
  selectedTasteIds.value = []
  selectedIngredientIds.value = []
  selectedCuisineIds.value = []
  inBounds.value = false
  void applyDiscovery()
}

onBeforeUnmount(() => {
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  catalogRequestController?.abort()
  markerRequestController?.abort()
  stopGeolocation()
  cancelLocationLookup()
  document.removeEventListener('pointerdown', closeActions)
  document.removeEventListener('keydown', closeActionsOnEscape)
})

let locationLookupSequence = 0
let locationLookupController: AbortController | undefined

let cancelGeolocation: (() => void) | undefined
function stopGeolocation() {
  cancelGeolocation?.()
  geolocationLoading.value = false
}
function cancelLocationLookup() {
  ++locationLookupSequence
  locationLookupController?.abort()
  locationResolving.value = false
}
function locateUser() {
  stopGeolocation()
  pickHint.value = ''
  geolocationLocated.value = false
  geolocationErrorKey.value = ''
  geolocationCoordinate.value = undefined
  geolocationLoading.value = true
  cancelGeolocation = requestBrowserLocation(({ latitude, longitude }, accuracy) => {
    geolocationLoading.value = false
    mapFocus.value = { latitude, longitude, zoom: accuracy <= 100 ? 16 : accuracy <= 1000 ? 14 : 12 }
    geolocationCoordinate.value = { latitude, longitude }
    geolocationLocated.value = true
  }, key => { geolocationLoading.value = false; geolocationErrorKey.value = key })
}

function handleMapPick(latitude: number, longitude: number) {
  // 用户手动确认优先于仍在等待中的 GPS 结果，避免稍后回调覆盖当前视角。
  stopGeolocation()
  geolocationLoading.value = false
  geolocationLocated.value = false
  geolocationErrorKey.value = ''
  geolocationCoordinate.value = undefined
  void pickLocation(latitude, longitude)
}

async function pickLocation(latitude: number, longitude: number) {
  if (!validCoordinate(latitude, longitude)) return
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
  const controller = locationLookupController
  const deadline = setTimeout(() => controller.abort(), 8_000)

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
      locationError.value = t('location.addressUnavailable')
    }
  } finally {
    clearTimeout(deadline)
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
  cancelLocationLookup()
  stopGeolocation()
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

watch(
  () => route.fullPath,
  () => {
    if (route.path !== '/' || suppressRouteReload || route.query.map === 'reset') return
    restoreDiscoveryFromRoute()
    void Promise.all([loadCatalog(catalogPage.value), loadMarkers()])
  },
  { flush: 'sync' },
)

async function openUpload() {
  if (!auth.currentUser.value) {
    await router.push({ path: '/login', query: { redirect: '/' } })
    return
  }

  // Location services are optional. Freeze enrichment before opening so a late
  // address response cannot overwrite user edits or an in-flight submission.
  stopGeolocation()
  cancelLocationLookup()
  pickHint.value = ''
  uploadOpen.value = true
}

function openAgent() {
  actionsOpen.value = false
  if (!auth.currentUser.value) {
    void router.push({ path: '/login', query: { redirect: '/' } })
    return
  }
  window.setTimeout(() => window.dispatchEvent(new CustomEvent('home:open-agent')), 0)
}

function openMusic() {
  actionsOpen.value = false
  window.setTimeout(() => window.dispatchEvent(new CustomEvent('home:open-music')), 0)
}

function locateFromDock() {
  actionsOpen.value = false
  locateUser()
}

function uploadFromDock() {
  actionsOpen.value = false
  void openUpload()
}

function chooseUploadLocation(focus?: MapFocus) {
  uploadOpen.value = false
  pickHint.value = t('home.pickCoordinateFirst')
  if (focus) mapFocus.value = focus
}

function closeActions(event: PointerEvent) {
  if (actionsOpen.value && actionDock.value && !actionDock.value.contains(event.target as Node)) {
    actionsOpen.value = false
  }
}

function closeActionsOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') actionsOpen.value = false
}

onMounted(async () => {
  document.addEventListener('pointerdown', closeActions)
  document.addEventListener('keydown', closeActionsOnEscape)
  performance.mark('terra:home-mounted')
  void getRegions().then((value) => { regions.value = value }).catch(() => {})
  void loadCatalog().finally(() => performance.mark('terra:catalog-settled'))
  // 地图初始化发出首个视口后再加载聚合点位。
})

function imageSrcSet(food: Food) {
  const variants = food.imageVariants
  return [variants?.small && `${variants.small} 320w`, variants?.medium && `${variants.medium} 640w`, variants?.large && `${variants.large} 1280w`]
    .filter(Boolean).join(', ') || undefined
}

function fallbackToOriginal(event: Event, original?: string) {
  const image = event.currentTarget as HTMLImageElement
  if (!original || image.dataset.originalFallback === 'done' || image.currentSrc === original) return
  image.dataset.originalFallback = 'done'
  image.removeAttribute('srcset')
  image.src = original
}
</script>

<template>
  <Teleport to="#home-search-slot">
    <form class="header-food-search" role="search" @submit.prevent="submitSearch">
      <input v-model="keyword" :placeholder="t('home.searchPlaceholder')">
      <button>{{ t('home.search') }}</button>
    </form>
  </Teleport>

  <section
    class="map-explorer"
    :class="{
      'catalog-is-collapsed': catalogCollapsed,
    }"
  >
    <div class="explorer-map" :aria-label="t('home.mapTitle')">
      <FoodMap
        :items="markerItems"
        :filters="searchParams()"
        :focus="mapFocus"
        :picked-location="displayedLocation"
        @pick="handleMapPick"
        @bounds-change="updateMapBounds"
      />
    </div>
    <div class="explorer-map-wash"></div>

    <div class="explorer-map-hint" role="status">
      <span v-if="mapTruncated">{{ t('home.mapTruncated') }}</span>
      <span v-if="pickHint">{{ pickHint }}</span>
      <span v-else-if="geolocationLoading">{{ t('home.geolocationLoading') }}</span>
      <span v-else-if="locationResolving">{{ t('home.mapRegionLoading') }}</span>
      <span v-else-if="geolocationErrorKey" class="error">
        {{ t(geolocationErrorKey) }}
        <button type="button" @click="locateUser">{{ t('home.retryLocation') }}</button>
        <button type="button" @click="openUpload">{{ t('location.manualEntry') }}</button>
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

    <nav ref="actionDock" class="home-action-dock" :class="{ 'is-open': actionsOpen }" :aria-label="t('home.quickActions')">
      <div class="home-action-list" :aria-hidden="!actionsOpen">
        <button type="button" :tabindex="actionsOpen ? 0 : -1" @click="uploadFromDock">
          <span aria-hidden="true">+</span><b>{{ t('home.addFood') }}</b>
        </button>
        <button type="button" :tabindex="actionsOpen ? 0 : -1" @click="openAgent">
          <span aria-hidden="true">AI</span><b>{{ t('home.openAgent') }}</b>
        </button>
        <button type="button" :tabindex="actionsOpen ? 0 : -1" @click="openMusic">
          <span aria-hidden="true">&#9835;</span><b>{{ t('home.openMusic') }}</b>
        </button>
        <button type="button" :tabindex="actionsOpen ? 0 : -1" @click="locateFromDock">
          <span aria-hidden="true">&#9678;</span><b>{{ t('home.locate') }}</b>
        </button>
      </div>
      <button
        class="home-action-trigger"
        type="button"
        :aria-label="t(actionsOpen ? 'home.closeQuickActions' : 'home.openQuickActions')"
        :aria-expanded="actionsOpen"
        @click="actionsOpen = !actionsOpen"
      >
        <span aria-hidden="true">+</span>
      </button>
    </nav>

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
          <button
            class="explorer-catalog-toggle"
            type="button"
            :aria-expanded="!catalogCollapsed"
            @click="catalogCollapsed = !catalogCollapsed"
          >
            <span>{{ t(catalogCollapsed ? 'home.expandCatalog' : 'home.collapseCatalog') }}</span>
            <b aria-hidden="true">{{ catalogCollapsed ? '⌃' : '⌄' }}</b>
          </button>
        </div>
      </header>

      <p v-if="loading && foods.length" class="explorer-state">{{ t('home.refreshing') }}</p>
      <p v-if="error" class="explorer-state error">{{ error }}</p>
      <p v-if="!foods.length && loading" class="explorer-state">{{ t('home.loading') }}</p>
      <div v-else-if="!foods.length" class="explorer-state">
        <p>{{ t('home.emptyWithFilters') }}</p>
        <button v-if="keyword" type="button" @click="keyword = ''; applyDiscovery()">{{ t('home.clearKeyword') }}</button>
        <button v-if="selectedFilterCount" type="button" @click="clearFilters">{{ t('home.clearFilters') }}</button>
        <button type="button" @click="openUpload">{{ t('home.addFood') }}</button>
      </div>

      <div v-else class="explorer-cards">
        <article
          v-for="(food, index) in catalogFoods"
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
          <div class="explorer-card-photo" :class="{ 'no-cover': !food.imageUrl }">
            <img v-if="food.imageUrl" :src="food.imageVariants?.medium || food.imageUrl" :srcset="imageSrcSet(food)" sizes="(max-width: 700px) 45vw, 260px" :alt="food.name" :loading="index < 2 ? 'eager' : 'lazy'" :fetchpriority="index < 2 ? 'high' : 'auto'" decoding="async" @error="fallbackToOriginal($event, food.imageUrl)">
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
    :province="pickedProvince"
    :city="pickedCity"
    @close="uploadOpen = false"
    @saved="handleSaved"
    @pick-on-map="chooseUploadLocation"
  />
</template>
