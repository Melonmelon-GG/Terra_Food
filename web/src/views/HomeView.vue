<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getFoodCatalog, getFoodMapClusters, getFoodTags, getMyFavoritesPage, getRegions, reverseMapLocation } from '../api'
import { useAuth } from '../auth'
import { requestBrowserLocation, validCoordinate } from '../location'
import FoodMap from '../components/FoodMap.vue'
const FoodUploadModal = defineAsyncComponent(() => import('../components/FoodUploadModal.vue'))
import type { Food, FoodMapClusterItem, FoodSort, FoodTag, MapBounds, MapCoordinate, MapFocus, Region } from '../types'

const foods = ref<Food[]>([])
const markerItems = ref<FoodMapClusterItem[]>([])
const regions = ref<Region[]>([])
const catalogTotal = ref(0)
const catalogPage = ref(1)
const catalogPageSize = 30
const drawerMode = ref<'filters' | 'favorites'>()
const drawer = ref<HTMLElement>()
let drawerOpener: HTMLElement | null = null
const filterTags = ref<FoodTag[]>([])
const tagKeyword = ref('')
const tagsLoading = ref(false)
const tagsError = ref(false)
let tagSequence = 0
const favorites = ref<Food[]>([])
const favoritesPage = ref(0)
const favoritesTotal = ref(0)
const favoritesLoading = ref(false)
const favoritesError = ref(false)
let favoritesSequence = 0
const tagTypes: FoodTag['type'][] = ['TASTE', 'INGREDIENT', 'CUISINE']
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
const mapError = ref(false)
const mapLoading = ref(false)
const regionsError = ref(false)
let active = true
const markerFilters = ref<ReturnType<typeof mapSearchParams>>()
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
  + selectedIngredientIds.value.length + selectedCuisineIds.value.length + (inBounds.value ? 1 : 0) + (selectedRegionId.value ? 1 : 0))
function discoveryQuery() {
  return {
    ...(keyword.value.trim() ? { q: keyword.value.trim() } : {}),
    ...(selectedRegionId.value ? { region: String(selectedRegionId.value) } : {}),
    ...(selectedTasteIds.value.length ? { taste: selectedTasteIds.value.join(',') } : {}),
    ...(selectedIngredientIds.value.length ? { ingredient: selectedIngredientIds.value.join(',') } : {}),
    ...(selectedCuisineIds.value.length ? { cuisine: selectedCuisineIds.value.join(',') } : {}),
    ...(sort.value !== 'RELEVANCE' ? { sort: sort.value } : {}),
    ...(inBounds.value && mapBounds.value ? { bounds: '1', box: Object.values(mapBounds.value).join(','), zoom: String(mapZoom.value) } : {}),
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

function boundsFromRoute(): MapBounds | undefined {
  const values = String(route.query.box || '').split(',').map(Number)
  if (values.length !== 4 || values.some((value) => !Number.isFinite(value))) return undefined
  const [minLatitude, maxLatitude, minLongitude, maxLongitude] = values
  if (minLatitude < -90 || maxLatitude > 90 || minLatitude >= maxLatitude || Math.abs(minLongitude) > 180 || Math.abs(maxLongitude) > 180) return undefined
  return { minLatitude, maxLatitude, minLongitude, maxLongitude }
}
function searchParams() {
  // API requests use the submitted URL, never the editable input field.
  const bounds = boundsFromRoute()
  return {
    keyword: typeof route.query.q === 'string' ? route.query.q.trim() || undefined : undefined,
    regionId: queryNumber(route.query.region),
    tasteIds: queryIds(route.query.taste), ingredientIds: queryIds(route.query.ingredient), cuisineIds: queryIds(route.query.cuisine),
    sort: (['RELEVANCE', 'HEAT', 'NEWEST'].includes(String(route.query.sort)) ? String(route.query.sort) : 'RELEVANCE') as FoodSort,
    inBounds: route.query.bounds === '1' && !!bounds,
    ...(route.query.bounds === '1' ? bounds : undefined),
  }
}
function mapSearchParams() { return { ...searchParams(), ...mapBounds.value, inBounds: true, zoom: mapZoom.value } }
const initialBounds = boundsFromRoute()
if (initialBounds && inBounds.value) {
  mapBounds.value = initialBounds
  mapZoom.value = Math.max(4, Math.min(18, Number(route.query.zoom) || 4))
  mapFocus.value = { latitude: (initialBounds.minLatitude + initialBounds.maxLatitude) / 2, longitude: (initialBounds.minLongitude + initialBounds.maxLongitude) / 2, zoom: mapZoom.value }
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
  if (!active) return
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
    if (!active || requestSequence !== catalogRequestSequence) {
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
  if (!active || !mapBounds.value) return
  const requestSequence = ++markerRequestSequence
  markerRequestController?.abort()
  markerRequestController = new AbortController()
  mapLoading.value = true
  const requestedFilters = mapSearchParams()
  try {
    const next = await getFoodMapClusters(requestedFilters, markerRequestController.signal)
    if (active && requestSequence === markerRequestSequence) {
      mapError.value = false
      markerFilters.value = requestedFilters
      markerItems.value = next.items
      mapTruncated.value = false
    }
  } catch (cause) {
    if (active && requestSequence === markerRequestSequence && !axios.isCancel(cause)) mapError.value = true
  } finally { if (requestSequence === markerRequestSequence) mapLoading.value = false }
}

function changeCatalogPage(direction: -1 | 1) {
  const target = catalogPage.value + direction
  if (target < 1 || target > catalogPages.value) return
  void router.push({ path: '/', query: { ...route.query, page: target > 1 ? String(target) : undefined } })
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
    if (!active) return
    if (route.query.bounds === '1') {
      void router.replace({ path: '/', query: { ...route.query, page: undefined, box: [bounds.minLatitude, bounds.maxLatitude, bounds.minLongitude, bounds.maxLongitude].join(','), zoom: String(zoom) } })
    } else void loadMarkers()
  }, 250)
}

function submitSearch() {
  void applyDiscovery()
}

function applyDiscovery() {
  const target = { path: '/', query: discoveryQuery() }
  if (router.resolve(target).fullPath === route.fullPath) { void Promise.all([loadCatalog(1), loadMarkers()]); return }
  void router.push(target)
}

function clearFilters() {
  keyword.value = ''
  selectedRegionId.value = undefined
  sort.value = 'RELEVANCE'
  selectedTasteIds.value = []
  selectedIngredientIds.value = []
  selectedCuisineIds.value = []
  inBounds.value = false
  void applyDiscovery()
}

onBeforeUnmount(() => {
  active = false
  ++catalogRequestSequence
  ++markerRequestSequence
  ++favoritesSequence
  ++tagSequence
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
    void Promise.all([loadCatalog(1), loadMarkers()])
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
    if (route.path !== '/' || !active || route.query.map === 'reset') return
    restoreDiscoveryFromRoute()
    const savedBounds = boundsFromRoute()
    if (inBounds.value && savedBounds && (!mapBounds.value || movedEnough(mapBounds.value, savedBounds))) {
      mapFocus.value = { ...centerOf(savedBounds), zoom: Math.max(4, Math.min(18, Number(route.query.zoom) || 4)) }
      mapBounds.value = savedBounds
    }
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
  void loadRegions()
  void loadCatalog().finally(() => performance.mark('terra:catalog-settled'))
  // 地图初始化发出首个视口后再加载聚合点位。
})

async function loadRegions() {
  regionsError.value = false
  try { const result = await getRegions(); if (active) regions.value = result }
  catch { if (active) regionsError.value = true }
}
async function loadFilterTags() {
  const sequence = ++tagSequence
  tagsLoading.value = true; tagsError.value = false
  try {
    const result = await getFoodTags(undefined, tagKeyword.value.trim() || undefined)
    if (active && sequence === tagSequence) filterTags.value = result.filter((tag) => tag.status === 'APPROVED')
  } catch { if (active && sequence === tagSequence) tagsError.value = true }
  finally { if (sequence === tagSequence) tagsLoading.value = false }
}
function selectedTags(type: FoodTag['type']) {
  return type === 'TASTE' ? selectedTasteIds : type === 'INGREDIENT' ? selectedIngredientIds : selectedCuisineIds
}
function toggleFilter(tag: FoodTag) {
  const target = selectedTags(tag.type)
  if (target.value.includes(tag.id)) target.value = target.value.filter((id) => id !== tag.id)
  else if (target.value.length < 10) target.value = [...target.value, tag.id]
  applyDiscovery()
}
async function loadFavorites(reset = false) {
  if (!auth.currentUser.value || !auth.sessionConfirmed.value || (favoritesLoading.value && !reset)) return
  const sequence = ++favoritesSequence
  const revision = auth.getSessionRevision()
  favoritesLoading.value = true; favoritesError.value = false
  try {
    const result = await getMyFavoritesPage(reset ? 1 : favoritesPage.value + 1, 10)
    if (!active || revision !== auth.getSessionRevision() || sequence !== favoritesSequence) return
    favorites.value = reset ? result.items : [...favorites.value, ...result.items]
    favoritesPage.value = result.page; favoritesTotal.value = result.total
  } catch { if (active && sequence === favoritesSequence) favoritesError.value = true }
  finally { if (sequence === favoritesSequence) favoritesLoading.value = false }
}
async function openDrawer(mode: 'filters' | 'favorites') {
  actionsOpen.value = false
  drawerOpener = document.activeElement as HTMLElement | null
  drawerMode.value = mode
  if (mode === 'filters') void loadFilterTags()
  else void loadFavorites(true)
  await nextTick()
  drawer.value?.querySelector<HTMLElement>('button, input, select, a')?.focus()
}
function closeDrawer() { drawerMode.value = undefined; drawerOpener?.focus() }
function handleDrawerKey(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); closeDrawer(); return }
  if (event.key !== 'Tab') return
  const targets = Array.from(drawer.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href]') || [])
  const first = targets[0], last = targets[targets.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
}
watch(() => auth.currentUser.value?.id, () => {
  ++favoritesSequence
  favorites.value = []; favoritesPage.value = 0; favoritesTotal.value = 0; favoritesLoading.value = false; favoritesError.value = false
  uploadOpen.value = false
  if (drawerMode.value === 'favorites') void loadFavorites(true)
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
      <button type="button" class="discovery-filter-trigger" @click="openDrawer('filters')">{{ t('audit.filters') }}<span v-if="selectedFilterCount"> {{ selectedFilterCount }}</span></button>
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
        :filters="markerFilters || mapSearchParams()"
        :focus="mapFocus"
        :picked-location="displayedLocation"
        @pick="handleMapPick"
        @bounds-change="updateMapBounds"
      />
    </div>
    <div class="explorer-map-wash"></div>

    <div class="explorer-map-hint" role="status">
      <span v-if="mapError" class="error">{{ t('audit.mapStale') }} <button type="button" @click="loadMarkers">{{ t('share.retry') }}</button></span>
      <span v-else-if="mapLoading">{{ t('home.refreshing') }}</span>
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
        <button type="button" :tabindex="actionsOpen ? 0 : -1" @click="openDrawer('favorites')">
          <span aria-hidden="true">♡</span><b>{{ t('audit.savedFoods') }}</b>
        </button>
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
      <div v-else-if="!foods.length && !error" class="explorer-state">
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
          :style="{ backgroundImage: activeFood.imageUrl ? 'url(' + (activeFood.imageVariants?.medium || activeFood.imageUrl) + ')' : undefined }"
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

  <div v-if="drawerMode" class="discovery-drawer-mask" @click.self="closeDrawer">
    <section ref="drawer" class="discovery-drawer" role="dialog" aria-modal="true" :aria-label="t(drawerMode === 'filters' ? 'audit.filters' : 'audit.savedFoods')" @keydown="handleDrawerKey">
      <header><h2>{{ t(drawerMode === 'filters' ? 'audit.filters' : 'audit.savedFoods') }}</h2><button type="button" @click="closeDrawer">{{ t('audit.close') }}</button></header>
      <template v-if="drawerMode === 'filters'">
        <p v-if="regionsError" role="alert">{{ t('audit.regionsError') }} <button type="button" @click="loadRegions">{{ t('share.retry') }}</button></p>
        <label>{{ t('upload.region') }}<select v-model="selectedRegionId" @change="applyDiscovery"><option :value="undefined">{{ t('audit.allRegions') }}</option><option v-for="region in regions" :key="region.id" :value="region.id">{{ region.province }} · {{ region.name }}</option></select></label>
        <label>{{ t('audit.sort') }}<select v-model="sort" @change="applyDiscovery"><option value="RELEVANCE">{{ t('audit.relevance') }}</option><option value="HEAT">{{ t('audit.heat') }}</option><option value="NEWEST">{{ t('audit.newest') }}</option></select></label>
        <label class="bounds-option"><input v-model="inBounds" type="checkbox" @change="applyDiscovery">{{ t('audit.onlyMapBounds') }}</label>
        <form class="tag-filter-search" @submit.prevent="loadFilterTags"><input v-model="tagKeyword" maxlength="30" :placeholder="t('audit.findTags')"><button :disabled="tagsLoading">{{ t('home.search') }}</button></form>
        <p v-if="tagsError" role="alert">{{ t('tagPicker.loadFailed') }} <button type="button" @click="loadFilterTags">{{ t('share.retry') }}</button></p>
        <p v-if="tagsLoading" role="status">{{ t('common.loading') }}</p>
        <fieldset v-for="type in tagTypes" :key="type"><legend>{{ t('home.tagType' + type) }}</legend><div class="discovery-tag-options"><button v-for="tag in filterTags.filter(item => item.type === type)" :key="tag.id" type="button" :aria-pressed="selectedTags(type).value.includes(tag.id)" @click="toggleFilter(tag)">{{ tag.name }}</button></div></fieldset>
        <p>{{ t('audit.partialTagCoverage') }}</p><button type="button" @click="clearFilters">{{ t('home.clearFilters') }}</button>
      </template>
      <template v-else>
        <p v-if="!auth.currentUser.value"><RouterLink to="/login">{{ t('audit.loginForFavorites') }}</RouterLink></p>
        <template v-else>
          <p>{{ t('home.recordCount', { count: favoritesTotal }) }}</p>
          <p v-if="favoritesError" role="alert">{{ t('profile.collectionError') }} <button type="button" @click="loadFavorites(true)">{{ t('share.retry') }}</button></p>
          <p v-else-if="!favorites.length && !favoritesLoading">{{ t('profile.favoriteEmpty') }}</p>
          <article v-for="food in favorites" :key="food.id" class="discovery-favorite"><img v-if="food.imageUrl" :src="food.imageVariants?.small || food.imageUrl" loading="lazy" decoding="async" :alt="food.name" @error="fallbackToOriginal($event, food.imageUrl)"><div><RouterLink :to="'/foods/' + food.id">{{ food.name }}</RouterLink><small>{{ food.region.province }} · {{ food.region.name }}</small><button type="button" @click="focusFood(food); closeDrawer()">{{ t('home.mapView') }}</button></div></article>
          <p v-if="favoritesLoading" role="status">{{ t('common.loading') }}</p>
          <button v-if="favorites.length < favoritesTotal" :disabled="favoritesLoading" type="button" @click="loadFavorites()">{{ t('home.loadMoreFavorites') }}</button>
          <RouterLink to="/profile?tab=favorites">{{ t('audit.manageFavorites') }}</RouterLink>
        </template>
      </template>
    </section>
  </div>

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

<style scoped>
.header-food-search{grid-template-columns:minmax(0,1fr) auto auto;gap:4px}.header-food-search .discovery-filter-trigger{min-width:48px;padding:0 8px}
.discovery-drawer-mask{position:fixed;inset:0;z-index:2100;background:#0007;display:flex;justify-content:flex-end}
.discovery-drawer{width:min(440px,100%);max-height:100dvh;overflow:auto;background:var(--surface-strong,#f8f3e8);color:var(--ink,#332c27);padding:24px;display:flex;flex-direction:column;gap:18px;box-shadow:-8px 0 30px #0003}
.discovery-drawer header{display:flex;align-items:center;justify-content:space-between;gap:16px}
.discovery-drawer label{display:grid;gap:8px}.discovery-drawer select,.discovery-drawer input{max-width:100%;padding:8px;background:var(--surface-soft);color:inherit;border:1px solid var(--border-paper)}
.discovery-drawer .bounds-option{display:flex;align-items:center}.discovery-drawer button{min-height:36px}.discovery-tag-options{display:flex;flex-wrap:wrap;gap:8px}.discovery-tag-options [aria-pressed=true]{background:var(--cinnabar,#a44335);color:white}
.discovery-drawer fieldset{border:1px solid var(--border-paper);padding:12px}.tag-filter-search{display:flex;gap:6px}.tag-filter-search input{min-width:0;flex:1}.discovery-favorite{display:flex;gap:12px}.discovery-favorite img{width:76px;height:76px;object-fit:cover}.discovery-favorite div{display:grid;gap:4px}.discovery-favorite small{color:var(--muted)}
</style>
