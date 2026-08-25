<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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

async function loadFoods() {
  loading.value = true
  error.value = ''

  try {
    foods.value = await getFoods({
      keyword: keyword.value || undefined,
      regionId: selectedRegionId.value,
      ...mapBounds.value,
    })
  } catch {
    error.value = t('home.loadError')
  } finally {
    loading.value = false
  }
}

let boundsLoadTimer: ReturnType<typeof setTimeout> | undefined

function updateMapBounds(bounds: MapBounds) {
  mapBounds.value = bounds
  if (boundsLoadTimer) clearTimeout(boundsLoadTimer)
  boundsLoadTimer = setTimeout(() => void loadFoods(), 250)
}

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
  pickedAddress.value = ''
  locationError.value = ''
  locationResolving.value = true
  locationLookupController?.abort()
  locationLookupController = new AbortController()

  const lookupSequence = ++locationLookupSequence
  try {
    const resolvedLocation = await resolveMapRegion(latitude, longitude, locationLookupController.signal)
    if (lookupSequence === locationLookupSequence) {
      const resolvedRegion = resolvedLocation.region
      pickedRegionId.value = resolvedRegion.id
      pickedAddress.value = resolvedLocation.address
      if (!regions.value.some((region) => region.id === resolvedRegion.id)) {
        regions.value = [...regions.value, resolvedRegion]
      }
    }
  } catch {
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

async function openUpload() {
  if (!auth.currentUser.value) {
    await router.push({ path: '/login', query: { redirect: '/' } })
    return
  }

  uploadOpen.value = true
}

onMounted(async () => {
  try {
    regions.value = await getRegions()
  } catch {
    error.value = t('home.regionError')
  }

})
</script>

<template>
  <section class="hero">
    <p class="eyebrow">{{ t('home.eyebrow') }}</p>
    <h1>{{ t('home.heroTitle') }}<em>{{ t('home.heroEmphasis') }}</em></h1>
    <p>
      {{ t('home.heroDescription') }}
    </p>
    <form @submit.prevent="loadFoods">
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
          <span v-else-if="pickedLatitude">
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

    <p v-if="loading" class="state">{{ t('home.loading') }}</p>
    <p v-else-if="error" class="state error">{{ error }}</p>

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

    <p v-if="!loading && !error && foods.length === 0" class="state">
      {{ t('home.empty') }}
    </p>
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
